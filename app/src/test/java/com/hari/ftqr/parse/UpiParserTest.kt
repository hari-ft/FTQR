package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiParserTest {

    @Test
    fun parsesAllParametersWithDecoding() {
        val uri = "upi://pay?pa=hari%40okicici&pn=Hari+Prasad%20M&am=250.50&cu=INR&tn=Rent%20for%20May%26June&mc=5411"
        val payment = UpiParser.parse("  $uri  ")
        assertEquals(
            ScanContent.Payment(
                payeeAddress = "hari@okicici",
                payeeName = "Hari Prasad M",
                amount = "250.50",
                currency = "INR",
                note = "Rent for May&June",
                merchantCode = "5411",
                upiUri = "upi://pay?pa=hari@okicici&pn=Hari%20Prasad%20M&am=250.50&cu=INR&tn=Rent%20for%20May%26June&mc=5411",
            ),
            payment,
        )
    }

    @Test
    fun onlyPayeeAddressIsRequired() {
        val payment = UpiParser.parse("UPI://pay?pa=shop@ybl")
        assertEquals("shop@ybl", payment?.payeeAddress)
        assertNull(payment?.payeeName)
        assertNull(payment?.amount)
        assertNull(payment?.currency)
        assertNull(payment?.note)
        assertNull(payment?.merchantCode)
        assertEquals("upi://pay?pa=shop@ybl", payment?.upiUri)
    }

    @Test
    fun parameterNamesAreCaseInsensitiveAndEmptyValuesIgnored() {
        val payment = UpiParser.parse("upi://pay?PA=shop@ybl&PN=&AM=10")
        assertEquals("shop@ybl", payment?.payeeAddress)
        assertNull(payment?.payeeName)
        assertEquals("10", payment?.amount)
        assertEquals("upi://pay?pa=shop@ybl&am=10", payment?.upiUri)
    }

    @Test
    fun malformedPercentEscapesDoNotThrow() {
        val payment = UpiParser.parse("upi://pay?pa=a@b&pn=50%25%zz%")
        assertEquals("50%%zz%", payment?.payeeName)
    }

    @Test
    fun missingPayeeAddressReturnsNull() {
        assertNull(UpiParser.parse("upi://pay?pn=Someone&am=10"))
        assertNull(UpiParser.parse("upi://pay?pa=&pn=Someone"))
        assertNull(UpiParser.parse("upi://pay"))
        assertNull(UpiParser.parse("https://example.com/?pa=a@b"))
    }

    @Test
    fun duplicateKeysAfterCaseFoldingAreRejected() {
        assertNull(UpiParser.parse("upi://pay?PA=shop@okaxis&AM=10&pa=scam@ybl&am=9999"))
        assertNull(UpiParser.parse("upi://pay?pa=shop@okaxis&p%61=scam@ybl"))
        assertNull(UpiParser.parse("upi://pay?pa=shop@okaxis&am=10&Am=9999"))
        assertNull(UpiParser.parse("upi://pay?pa=shop@okaxis&tn=a&TN=b"))
        assertNull(UpiParser.parse("upi://pay?pa=shop@okaxis&pa="))
        assertEquals(
            ScanContent.Text("upi://pay?PA=shop@okaxis&pa=scam@ybl"),
            ContentParser.parse("upi://pay?PA=shop@okaxis&pa=scam@ybl", 256),
        )
    }

    @Test
    fun uriIsRebuiltWithLowercaseSchemeHostAndKeys() {
        val payment = UpiParser.parse("UPI://PAY?PA=shop%40okaxis&PN=My+Shop&TR=ORD-1&MODE=02&ORGID=159&foo=bar&Sign=abc%3D")
        assertEquals("upi://pay?pa=shop@okaxis&pn=My%20Shop&tr=ORD-1&mode=02&orgid=159&sign=abc%3D", payment?.upiUri)
    }

    @Test
    fun encodedKeyIsDecodedAndRebuiltPlainly() {
        val payment = UpiParser.parse("upi://pay?p%61=shop@okaxis&%41M=5")
        assertEquals("shop@okaxis", payment?.payeeAddress)
        assertEquals("upi://pay?pa=shop@okaxis&am=5", payment?.upiUri)
    }

    @Test
    fun mandateIsFlaggedAndOtherHostsRejected() {
        val mandate = UpiParser.parse("upi://mandate?pa=netflix@icici&pn=Netflix&am=649.00&mn=Monthly")
        assertTrue(mandate!!.isMandate)
        assertEquals("upi://mandate?pa=netflix@icici&pn=Netflix&am=649.00", mandate.upiUri)
        assertFalse(UpiParser.parse("upi://pay?pa=a@b")!!.isMandate)
        assertFalse(UpiParser.parse("upi://pay/?pa=a@b")!!.isMandate)
        assertNull(UpiParser.parse("upi://collect?pa=a@b&am=10"))
        assertNull(UpiParser.parse("upi://?pa=a@b"))
        assertNull(UpiParser.parse("upi://payx?pa=a@b"))
        assertEquals(ScanContent.Text("upi://collect?pa=a@b"), ContentParser.parse("upi://collect?pa=a@b", 256))
    }

    @Test
    fun amountValidation() {
        assertTrue(UpiParser.parse("upi://pay?pa=a@b&am=10")!!.amountValid)
        assertTrue(UpiParser.parse("upi://pay?pa=a@b&am=10.5")!!.amountValid)
        assertTrue(UpiParser.parse("upi://pay?pa=a@b&am=0.01")!!.amountValid)
        assertTrue(UpiParser.parse("upi://pay?pa=a@b")!!.amountValid)
        for (bad in listOf("0", "0.00", "-5", "1.234", "1e3", "abc", "1,000", ".5", "5.")) {
            val payment = UpiParser.parse("upi://pay?pa=a@b&am=$bad")!!
            assertFalse(bad, payment.amountValid)
            assertEquals(bad, payment.amount)
        }
    }
}
