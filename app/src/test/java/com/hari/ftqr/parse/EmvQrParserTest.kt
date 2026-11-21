package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmvQrParserTest {

    private fun tlv(tag: String, value: String): String =
        tag + value.length.toString().padStart(2, '0') + value

    private fun withCrc(body: String): String {
        val prefix = body + "6304"
        return prefix + EmvQrParser.crc16(prefix)
    }

    private fun payload(vararg fields: String): String =
        withCrc(tlv("00", "01") + tlv("01", "12") + fields.joinToString(""))

    private fun upiAccount(vpa: String): String = tlv("00", "A000000677010111") + tlv("01", vpa)

    @Test
    fun tlvHelperProducesExpectedEncoding() {
        assertEquals("000201", tlv("00", "01"))
        assertEquals("5904Hari", tlv("59", "Hari"))
    }

    @Test
    fun crcMatchesCcittFalseCheckValue() {
        assertEquals("29B1", EmvQrParser.crc16("123456789"))
        assertEquals("FFFF", EmvQrParser.crc16(""))
    }

    @Test
    fun extractsVpaFromUpiTemplate() {
        val raw = payload(
            tlv("26", upiAccount("shop@upi")),
            tlv("52", "5411"),
            tlv("53", "356"),
            tlv("54", "99.50"),
            tlv("58", "IN"),
            tlv("59", "Hari Stores"),
            tlv("60", "Chennai"),
            tlv("62", tlv("05", "INV-7") + tlv("08", "Groceries")),
        )
        val payment = EmvQrParser.parse(raw)
        assertEquals(
            ScanContent.Payment(
                payeeAddress = "shop@upi",
                payeeName = "Hari Stores",
                amount = "99.50",
                currency = "INR",
                note = "Groceries",
                merchantCode = "5411",
                upiUri = "upi://pay?pa=shop@upi&pn=Hari%20Stores&am=99.50&cu=INR&tn=Groceries&tr=INV-7&mc=5411",
            ),
            payment,
        )
    }

    @Test
    fun prefersUpiTemplateOverOtherNetworks() {
        val visa = tlv("00", "A0000000031010") + tlv("01", "other@visa")
        val upi = tlv("00", "com.npci.upi") + tlv("02", "real@okaxis")
        val raw = payload(tlv("26", visa), tlv("28", upi), tlv("59", "Store"))
        assertEquals("real@okaxis", EmvQrParser.parse(raw)?.payeeAddress)
    }

    @Test
    fun prefersSubTagOneInsideUpiTemplate() {
        val upi = tlv("00", "A000000677010111") + tlv("02", "second@ybl") + tlv("01", "primary@okaxis")
        assertEquals("primary@okaxis", EmvQrParser.parse(payload(tlv("26", upi)))?.payeeAddress)
    }

    @Test
    fun findsVpaInAnyUpiMerchantTemplate() {
        val raw = payload(tlv("31", tlv("00", "UPI") + tlv("05", "vendor@paytm")), tlv("53", "356"))
        val payment = EmvQrParser.parse(raw)
        assertEquals("vendor@paytm", payment?.payeeAddress)
        assertEquals("upi://pay?pa=vendor@paytm&cu=INR", payment?.upiUri)
        assertNull(payment?.payeeName)
    }

    @Test
    fun nonUpiTemplatesAreIgnored() {
        val bharatQr = tlv("00", "A000000524") + tlv("05", "vendor@paytm")
        assertNull(EmvQrParser.parse(payload(tlv("31", bharatQr), tlv("53", "356"))))
        val pix = tlv("00", "br.gov.bcb.pix") + tlv("01", "someone@example.com")
        assertNull(EmvQrParser.parse(payload(tlv("26", pix), tlv("53", "986"), tlv("59", "Fulano"))))
        assertNull(EmvQrParser.parse(payload(tlv("26", pix), tlv("59", "Fulano"))))
        assertNull(EmvQrParser.parse(payload(tlv("26", "plain@vpa"))))
    }

    @Test
    fun nonRupeeCurrencyIsRejected() {
        assertNull(EmvQrParser.parse(payload(tlv("26", upiAccount("shop@upi")), tlv("53", "840"))))
        assertTrue(EmvQrParser.parse(payload(tlv("26", upiAccount("shop@upi")), tlv("53", "356"))) != null)
    }

    @Test
    fun checksumMismatchIsRejected() {
        val body = tlv("00", "01") + tlv("26", upiAccount("shop@upi")) + "6304"
        assertNull(EmvQrParser.parse(body + "ABCD"))
        val valid = withCrc(tlv("00", "01") + tlv("26", upiAccount("shop@upi")))
        assertTrue(EmvQrParser.parse(valid) != null)
        assertTrue(EmvQrParser.parse(valid.dropLast(4) + valid.takeLast(4).lowercase()) != null)
        val tampered = valid.replace("shop@upi", "scam@upi")
        assertNull(EmvQrParser.parse(tampered))
        assertTrue(EmvQrParser.parse(tlv("00", "01") + tlv("26", upiAccount("shop@upi"))) != null)
    }

    @Test
    fun invalidAmountIsFlagged() {
        val zero = EmvQrParser.parse(payload(tlv("26", upiAccount("a@b")), tlv("54", "0")))
        assertEquals("0", zero?.amount)
        assertFalse(zero!!.amountValid)
        val good = EmvQrParser.parse(payload(tlv("26", upiAccount("a@b")), tlv("54", "12.5")))
        assertTrue(good!!.amountValid)
    }

    @Test
    fun noVpaReturnsNull() {
        val card = tlv("00", "A000000677010112") + tlv("01", "1234567890IFSC0001")
        assertNull(EmvQrParser.parse(payload(tlv("26", card), tlv("59", "Shop"))))
        assertNull(EmvQrParser.parse("hello"))
        assertNull(EmvQrParser.parse(""))
    }

    @Test
    fun truncatedAndCorruptPayloadsDoNotThrow() {
        assertNull(EmvQrParser.parse("000201" + "2650" + "0016A000"))
        assertNull(EmvQrParser.parse("0002012"))
        assertNull(EmvQrParser.parse("000201XYZZ26"))
        val valid = payload(tlv("26", upiAccount("a@b")))
        for (cut in valid.indices) {
            EmvQrParser.parse(valid.substring(0, cut))
        }
        assertTrue(EmvQrParser.parse(valid) != null)
    }

    @Test
    fun contentParserUsesEmvPayment() {
        val raw = payload(tlv("26", upiAccount("cafe@sbi")), tlv("59", "Cafe"))
        val result = ContentParser.parse(raw, 256)
        assertTrue(result is ScanContent.Payment)
        assertEquals("cafe@sbi", (result as ScanContent.Payment).payeeAddress)
    }
}
