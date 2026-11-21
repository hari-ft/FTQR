package com.hari.ftqr.parse

import com.hari.ftqr.model.ContentKind
import com.hari.ftqr.model.ScanContent
import com.hari.ftqr.model.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDescriptionsTest {

    private fun contact(
        name: String? = null,
        organization: String? = null,
        phones: List<String> = emptyList(),
    ) = ScanContent.Contact(name, organization, null, phones, emptyList(), null, null, null)

    private fun payment(name: String?, amount: String?, currency: String? = "INR") =
        ScanContent.Payment("shop@upi", name, amount, currency, null, null, "upi://pay?pa=shop@upi")

    @Test
    fun kinds() {
        assertEquals(ContentKind.LINK, ScanContent.Link("https://a.com").kind())
        assertEquals(ContentKind.WIFI, ScanContent.Wifi("n", null, WifiSecurity.OPEN, false).kind())
        assertEquals(ContentKind.CONTACT, contact().kind())
        assertEquals(ContentKind.PHONE, ScanContent.Phone("1").kind())
        assertEquals(ContentKind.EMAIL, ScanContent.Email("a@b", null, null).kind())
        assertEquals(ContentKind.SMS, ScanContent.Sms("1", null).kind())
        assertEquals(ContentKind.GEO, ScanContent.Geo(1.0, 2.0, null).kind())
        assertEquals(ContentKind.EVENT, ScanContent.Event(null, null, null, false, null, null).kind())
        assertEquals(ContentKind.PAYMENT, payment(null, null).kind())
        assertEquals(ContentKind.PRODUCT, ScanContent.Product("123", "EAN-13").kind())
        assertEquals(ContentKind.TEXT, ScanContent.Text("x").kind())
    }

    @Test
    fun linkTitleShowsHostAndPath() {
        assertEquals("example.com/a/b", ScanContent.Link("https://www.example.com/a/b/?q=1#top").title())
        assertEquals("example.com", ScanContent.Link("https://example.com/").title())
        assertEquals("details", ScanContent.Link("market://details?id=x").title())
    }

    @Test
    fun wifiContactPhoneTitles() {
        assertEquals("HomeNet", ScanContent.Wifi("HomeNet", "x", WifiSecurity.WPA, false).title())
        assertEquals("Jane", contact(name = "Jane", organization = "Acme").title())
        assertEquals("Acme", contact(organization = "Acme").title())
        assertEquals("12345", contact(phones = listOf("12345")).title())
        assertEquals("Contact", contact().title())
        assertEquals("+15550100", ScanContent.Phone("+15550100").title())
    }

    @Test
    fun paymentTitles() {
        assertEquals("Hari Stores · ₹150.00", payment("Hari Stores", "150.00").title())
        assertEquals("shop@upi", payment(null, null).title())
        assertEquals("shop@upi · ₹5", payment(null, "5", null).title())
        assertEquals("Shop · USD 5", payment("Shop", "5", "USD").title())
    }

    @Test
    fun otherTitles() {
        assertEquals("a@b.com", ScanContent.Email("a@b.com", "Hi", null).title())
        assertEquals("555", ScanContent.Sms("555", "hello").title())
        assertEquals("12.5, -45.25", ScanContent.Geo(12.5, -45.25, null).title())
        assertEquals("Cafe", ScanContent.Geo(12.5, -45.25, "Cafe").title())
        assertEquals("Standup", ScanContent.Event("Standup", null, null, false, null, null).title())
        assertEquals("Event", ScanContent.Event(null, null, null, false, null, null).title())
        assertEquals("8901234567890", ScanContent.Product("8901234567890", "EAN-13").title())
    }

    @Test
    fun textTitleUsesFirstLineTruncated() {
        assertEquals("First line", ScanContent.Text("\n  First line  \nSecond line").title())
        val long = ScanContent.Text("a".repeat(100)).title()
        assertEquals(80, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals("Text", ScanContent.Text("   ").title())
    }
}
