package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                upiUri = uri,
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
    }

    @Test
    fun parameterNamesAreCaseInsensitiveAndEmptyValuesIgnored() {
        val payment = UpiParser.parse("upi://pay?PA=shop@ybl&PN=&AM=10")
        assertEquals("shop@ybl", payment?.payeeAddress)
        assertNull(payment?.payeeName)
        assertEquals("10", payment?.amount)
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
}
