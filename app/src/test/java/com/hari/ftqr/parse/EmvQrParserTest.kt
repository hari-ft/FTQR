package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmvQrParserTest {

    private fun tlv(tag: String, value: String): String =
        tag + value.length.toString().padStart(2, '0') + value

    private fun payload(vararg fields: String): String =
        tlv("00", "01") + tlv("01", "12") + fields.joinToString("") + "6304ABCD"

    @Test
    fun tlvHelperProducesExpectedEncoding() {
        assertEquals("000201", tlv("00", "01"))
        assertEquals("5904Hari", tlv("59", "Hari"))
    }

    @Test
    fun extractsVpaFromUpiTemplate() {
        val merchantAccount = tlv("00", "A000000677010111") + tlv("01", "shop@upi")
        val raw = payload(
            tlv("26", merchantAccount),
            tlv("52", "5411"),
            tlv("53", "356"),
            tlv("54", "99.50"),
            tlv("58", "IN"),
            tlv("59", "Hari Stores"),
            tlv("60", "Chennai"),
            tlv("62", tlv("08", "Groceries")),
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
                upiUri = "upi://pay?pa=shop@upi&pn=Hari%20Stores&am=99.50&cu=INR&mc=5411",
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
    fun findsVpaInAnyMerchantTemplate() {
        val bharatQr = tlv("00", "A000000524") + tlv("05", "vendor@paytm")
        val raw = payload(tlv("31", bharatQr), tlv("53", "356"))
        val payment = EmvQrParser.parse(raw)
        assertEquals("vendor@paytm", payment?.payeeAddress)
        assertEquals("upi://pay?pa=vendor@paytm&cu=INR", payment?.upiUri)
        assertNull(payment?.payeeName)
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
        val valid = payload(tlv("26", tlv("00", "A000000677010111") + tlv("01", "a@b")))
        for (cut in valid.indices) {
            EmvQrParser.parse(valid.substring(0, cut))
        }
        assertTrue(EmvQrParser.parse(valid) != null)
    }

    @Test
    fun contentParserUsesEmvPayment() {
        val raw = payload(tlv("26", tlv("00", "A000000677010111") + tlv("01", "cafe@sbi")), tlv("59", "Cafe"))
        val result = ContentParser.parse(raw, 256)
        assertTrue(result is ScanContent.Payment)
        assertEquals("cafe@sbi", (result as ScanContent.Payment).payeeAddress)
    }
}
