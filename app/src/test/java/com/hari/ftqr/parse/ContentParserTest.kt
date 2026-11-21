package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent
import com.hari.ftqr.model.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ContentParserTest {

    private val qr = 256
    private val ean13 = 32
    private val ean8 = 64
    private val upcA = 512
    private val upcE = 1024
    private val code128 = 1

    private inline fun <reified T : ScanContent> parseAs(raw: String, format: Int = qr): T {
        val result = ContentParser.parse(raw, format)
        assertTrue("Expected ${T::class.simpleName} but was $result", result is T)
        return result as T
    }

    @Test
    fun productFormatsWithDigitsBecomeProducts() {
        assertEquals(ScanContent.Product("8901234567890", "EAN-13"), ContentParser.parse("8901234567890", ean13))
        assertEquals(ScanContent.Product("96385074", "EAN-8"), ContentParser.parse("96385074", ean8))
        assertEquals(ScanContent.Product("036000291452", "UPC-A"), ContentParser.parse("036000291452", upcA))
        assertEquals(ScanContent.Product("01234565", "UPC-E"), ContentParser.parse("01234565", upcE))
    }

    @Test
    fun digitsInNonProductFormatsAreText() {
        assertEquals(ScanContent.Text("8901234567890"), ContentParser.parse("8901234567890", qr))
        assertEquals(ScanContent.Text("12345"), ContentParser.parse("12345", code128))
        assertEquals(ScanContent.Text("89012A4567890"), ContentParser.parse("89012A4567890", ean13))
    }

    @Test
    fun webLinks() {
        assertEquals(ScanContent.Link("https://example.com/a?b=c"), ContentParser.parse("https://example.com/a?b=c", qr))
        assertEquals(ScanContent.Link("http://example.com"), ContentParser.parse("  HTTP://EXAMPLE.COM \n", qr))
        assertEquals(ScanContent.Link("https://www.example.com/path"), ContentParser.parse("www.example.com/path", qr))
        assertEquals(ScanContent.Link("market://details?id=com.example"), ContentParser.parse("market://details?id=com.example", qr))
        assertEquals(
            ScanContent.Link("intent://scan/#Intent;scheme=zxing;end"),
            ContentParser.parse("intent://scan/#Intent;scheme=zxing;end", qr),
        )
    }

    @Test
    fun uppercaseSchemesAreLowercased() {
        assertEquals(ScanContent.Link("https://example.com/A1"), ContentParser.parse("HTTPS://EXAMPLE.COM/A1", qr))
        assertEquals(ScanContent.Link("https://User@example.com:8080/P?Q=R"), ContentParser.parse("Https://User@Example.COM:8080/P?Q=R", qr))
        assertEquals(ScanContent.Link("market://details?id=Com.X"), ContentParser.parse("MARKET://details?id=Com.X", qr))
        assertEquals(ScanContent.Link("https://www.example.com/Path"), ContentParser.parse("WWW.Example.com/Path", qr))
    }

    @Test
    fun bookmarks() {
        assertEquals(ScanContent.Link("http://example.com"), ContentParser.parse("MEBKM:TITLE:Site;URL:http\\://example.com;;", qr))
        assertEquals(ScanContent.Link("https://example.com/a"), ContentParser.parse("mebkm:URL:HTTPS\\://Example.com/a;TITLE:x;;", qr))
        assertEquals(ScanContent.Link("https://example.org"), ContentParser.parse("MEBKM:TITLE:x;URL:example.org;;", qr))
        assertEquals(ScanContent.Link("https://example.com/x"), ContentParser.parse("URLTO:My Site:https://example.com/x", qr))
        assertEquals(ScanContent.Link("http://a.b"), ContentParser.parse("urlto::http://a.b", qr))
        assertEquals(ScanContent.Text("MEBKM:TITLE:x;;"), ContentParser.parse("MEBKM:TITLE:x;;", qr))
        assertEquals(ScanContent.Text("URLTO:title"), ContentParser.parse("URLTO:title", qr))
    }

    @Test
    fun sentencesContainingLinksAreText() {
        val raw = "Visit https://example.com today"
        assertEquals(ScanContent.Text(raw), ContentParser.parse(raw, qr))
        assertEquals(ScanContent.Text("www.example"), ContentParser.parse("www.example", qr))
    }

    @Test
    fun wifiStandard() {
        val wifi = parseAs<ScanContent.Wifi>("WIFI:T:WPA;S:MyNetwork;P:secret123;H:true;;")
        assertEquals(ScanContent.Wifi("MyNetwork", "secret123", WifiSecurity.WPA, true), wifi)
    }

    @Test
    fun wifiAnyFieldOrderAndCaseInsensitivePrefix() {
        val wifi = parseAs<ScanContent.Wifi>("wifi:P:pw;H:false;S:Office;T:WEP;;")
        assertEquals(ScanContent.Wifi("Office", "pw", WifiSecurity.WEP, false), wifi)
    }

    @Test
    fun wifiEscapedCharacters() {
        val wifi = parseAs<ScanContent.Wifi>("""WIFI:S:My\;Net\,\:x\\;T:WPA2;P:p\;a\\ss\:;;""")
        assertEquals("My;Net,:x\\", wifi.ssid)
        assertEquals("p;a\\ss:", wifi.password)
        assertEquals(WifiSecurity.WPA, wifi.security)
    }

    @Test
    fun wifiSecurityVariants() {
        assertEquals(WifiSecurity.SAE, parseAs<ScanContent.Wifi>("WIFI:T:WPA3;S:a;P:b;;").security)
        assertEquals(WifiSecurity.SAE, parseAs<ScanContent.Wifi>("WIFI:T:sae;S:a;P:b;;").security)
        assertEquals(WifiSecurity.WPA, parseAs<ScanContent.Wifi>("WIFI:T:WPA2;S:a;P:b;;").security)
        assertEquals(WifiSecurity.WPA, parseAs<ScanContent.Wifi>("WIFI:S:a;P:b;;").security)
        val open = parseAs<ScanContent.Wifi>("WIFI:T:nopass;S:Cafe;P:;;")
        assertEquals(WifiSecurity.OPEN, open.security)
        assertNull(open.password)
        assertFalse(open.hidden)
        val missingType = parseAs<ScanContent.Wifi>("WIFI:S:Lobby;;")
        assertEquals(WifiSecurity.OPEN, missingType.security)
    }

    @Test
    fun wifiSecuredWithoutPasswordKeepsType() {
        assertEquals(ScanContent.Wifi("a", null, WifiSecurity.WPA, false), ContentParser.parse("WIFI:T:WPA;S:a;;", qr))
        assertEquals(ScanContent.Wifi("a", null, WifiSecurity.WPA, false), ContentParser.parse("WIFI:T:WPA2;S:a;P:;;", qr))
        assertEquals(ScanContent.Wifi("a", null, WifiSecurity.SAE, false), ContentParser.parse("WIFI:T:SAE;S:a;;", qr))
    }

    @Test
    fun wifiQuotedSsid() {
        assertEquals("Home", parseAs<ScanContent.Wifi>("WIFI:S:\"Home\";T:WPA;P:x;;").ssid)
    }

    @Test
    fun wifiWithoutSsidIsText() {
        assertEquals(ScanContent.Text("WIFI:T:WPA;P:x;;"), ContentParser.parse("WIFI:T:WPA;P:x;;", qr))
    }

    @Test
    fun vcardThree() {
        val raw = listOf(
            "BEGIN:VCARD",
            "VERSION:3.0",
            "N:Doe;John;;Dr.;",
            "FN:Dr. John Doe",
            "ORG:Acme Inc.;Sales",
            "TITLE:Manager",
            "TEL;TYPE=CELL:+1 555 0100",
            "TEL;TYPE=WORK,VOICE:+1 555 0101",
            "EMAIL;TYPE=INTERNET:john@example.com",
            "ADR;TYPE=WORK:;;123 Main St;Springfield;IL;62701;USA",
            "URL:https://example.com",
            "NOTE:Line one\\nLine two\\, continued",
            "END:VCARD",
        ).joinToString("\r\n")
        val contact = parseAs<ScanContent.Contact>(raw)
        assertEquals("Dr. John Doe", contact.name)
        assertEquals("Acme Inc., Sales", contact.organization)
        assertEquals("Manager", contact.title)
        assertEquals(listOf("+1 555 0100", "+1 555 0101"), contact.phones)
        assertEquals(listOf("john@example.com"), contact.emails)
        assertEquals("123 Main St, Springfield, IL, 62701, USA", contact.address)
        assertEquals("https://example.com", contact.website)
        assertEquals("Line one\nLine two, continued", contact.note)
    }

    @Test
    fun vcardLineFolding() {
        val raw = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane\r\nNOTE:This is a long\r\n  folded note\r\n\twith a tab\r\nEND:VCARD"
        val contact = parseAs<ScanContent.Contact>(raw)
        assertEquals("This is a long folded notewith a tab", contact.note)
    }

    @Test
    fun vcardStructuredNameWhenNoFormattedName() {
        val raw = "BEGIN:VCARD\nVERSION:2.1\nN:Smith;Jane\nTEL;CELL:12345\nEND:VCARD"
        val contact = parseAs<ScanContent.Contact>(raw)
        assertEquals("Jane Smith", contact.name)
        assertEquals(listOf("12345"), contact.phones)
    }

    @Test
    fun vcardFourUriValuesAndGroups() {
        val raw = "BEGIN:VCARD\nVERSION:4.0\nFN:Ann\nitem1.TEL;VALUE=uri;TYPE=cell:tel:+1-555-0100\nitem2.EMAIL:ann@example.org\nEND:VCARD"
        val contact = parseAs<ScanContent.Contact>(raw)
        assertEquals(listOf("+1-555-0100"), contact.phones)
        assertEquals(listOf("ann@example.org"), contact.emails)
    }

    @Test
    fun vcardQuotedPrintable() {
        val raw = "BEGIN:VCARD\nVERSION:2.1\nFN:Bob\nNOTE;ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8:Caf=C3=A9 =\nopen\nEND:VCARD"
        val contact = parseAs<ScanContent.Contact>(raw)
        assertEquals("Caf\u00E9 open", contact.note)
    }

    @Test
    fun mecard() {
        val raw = """MECARD:N:Doe,John;TEL:12345;TEL:67890;EMAIL:j@x.com;ADR:1 Main St,Town;URL:https\://x.com;NOTE:Hi\;there;ORG:Acme;;"""
        val contact = parseAs<ScanContent.Contact>(raw)
        assertEquals("John Doe", contact.name)
        assertEquals(listOf("12345", "67890"), contact.phones)
        assertEquals(listOf("j@x.com"), contact.emails)
        assertEquals("1 Main St, Town", contact.address)
        assertEquals("https://x.com", contact.website)
        assertEquals("Hi;there", contact.note)
        assertEquals("Acme", contact.organization)
    }

    @Test
    fun phone() {
        assertEquals(ScanContent.Phone("+15550100"), ContentParser.parse("tel:+15550100", qr))
        assertEquals(ScanContent.Phone("+1 555 0100"), ContentParser.parse("TEL:+1%20555%200100", qr))
        assertEquals(ScanContent.Text("tel:"), ContentParser.parse("tel:", qr))
    }

    @Test
    fun mailto() {
        val email = parseAs<ScanContent.Email>("mailto:someone@example.com?subject=Hello%20there&body=Line+one%0ALine%20two")
        assertEquals(ScanContent.Email("someone@example.com", "Hello there", "Line+one\nLine two"), email)
        assertEquals("1+1", parseAs<ScanContent.Email>("mailto:a@b.com?subject=1+1").subject)
        assertEquals(ScanContent.Email("a+tag@b.com", null, null), ContentParser.parse("MAILTO:a+tag@b.com", qr))
    }

    @Test
    fun matmsg() {
        val email = parseAs<ScanContent.Email>("""MATMSG:TO:a@b.com;SUB:Hi;BODY:Text\; more;;""")
        assertEquals(ScanContent.Email("a@b.com", "Hi", "Text; more"), email)
    }

    @Test
    fun sms() {
        assertEquals(ScanContent.Sms("+15550100", "Hello: there"), ContentParser.parse("SMSTO:+15550100:Hello: there", qr))
        assertEquals(ScanContent.Sms("+15550100", "Hi you"), ContentParser.parse("sms:+15550100?body=Hi%20you", qr))
        assertEquals(ScanContent.Sms("12345", null), ContentParser.parse("sms:12345", qr))
    }

    @Test
    fun geo() {
        assertEquals(ScanContent.Geo(12.9716, 77.5946, null), ContentParser.parse("geo:12.9716,77.5946", qr))
        assertEquals(ScanContent.Geo(12.97, 77.59, "Bangalore Palace"), ContentParser.parse("geo:12.97,77.59?q=Bangalore+Palace", qr))
        assertEquals(ScanContent.Geo(12.5, -45.25, "Cafe"), ContentParser.parse("geo:0,0?q=12.5,-45.25(Cafe)", qr))
        assertEquals(ScanContent.Geo(-33.8, 151.2, null), ContentParser.parse("GEO:-33.8,151.2,10;u=35", qr))
        assertEquals(ScanContent.Text("geo:abc"), ContentParser.parse("geo:abc", qr))
        assertEquals(ScanContent.Text("geo:95,10"), ContentParser.parse("geo:95,10", qr))
    }

    @Test
    fun geoAddressQuery() {
        assertEquals(
            ScanContent.Geo(0.0, 0.0, "1600 Amphitheatre Pkwy", "1600 Amphitheatre Pkwy"),
            ContentParser.parse("geo:0,0?q=1600+Amphitheatre+Pkwy", qr),
        )
        assertEquals(ScanContent.Geo(0.0, 0.0, null), ContentParser.parse("geo:0,0", qr))
        assertEquals(ScanContent.Geo(0.0, 0.0, null), ContentParser.parse("geo:0,0?q=", qr))
    }

    @Test
    fun eventUtcInCalendar() {
        val raw = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "BEGIN:VEVENT",
            "SUMMARY:Team Sync",
            "DTSTART:20250310T093000Z",
            "DTEND:20250310T103000Z",
            "LOCATION:Room 4\\, Floor 2",
            "DESCRIPTION:Weekly sync",
            "BEGIN:VALARM",
            "DESCRIPTION:Reminder",
            "END:VALARM",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n")
        val event = parseAs<ScanContent.Event>(raw)
        assertEquals("Team Sync", event.summary)
        assertEquals(Instant.parse("2025-03-10T09:30:00Z").toEpochMilli(), event.startMillis)
        assertEquals(Instant.parse("2025-03-10T10:30:00Z").toEpochMilli(), event.endMillis)
        assertFalse(event.allDay)
        assertEquals("Room 4, Floor 2", event.location)
        assertEquals("Weekly sync", event.description)
    }

    @Test
    fun eventAllDay() {
        val raw = "BEGIN:VEVENT\nSUMMARY:Holiday\nDTSTART;VALUE=DATE:20251225\nDTEND;VALUE=DATE:20251226\nEND:VEVENT"
        val event = parseAs<ScanContent.Event>(raw)
        assertTrue(event.allDay)
        assertEquals(LocalDate.of(2025, 12, 25).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), event.startMillis)
        assertEquals(LocalDate.of(2025, 12, 26).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), event.endMillis)
    }

    @Test
    fun eventLocalTimeAndTimeZoneParameter() {
        val local = parseAs<ScanContent.Event>("BEGIN:VEVENT\nDTSTART:20250601T140000\nEND:VEVENT")
        val expectedLocal = LocalDateTime.of(2025, 6, 1, 14, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedLocal, local.startMillis)
        assertNull(local.endMillis)
        assertFalse(local.allDay)

        val zoned = parseAs<ScanContent.Event>("BEGIN:VEVENT\nDTSTART;TZID=Asia/Kolkata:20250601T140000\nEND:VEVENT")
        val expectedZoned = LocalDateTime.of(2025, 6, 1, 14, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertEquals(expectedZoned, zoned.startMillis)
    }

    @Test
    fun eventTimeZonePrefixesAndWindowsNames() {
        val kolkata = LocalDateTime.of(2025, 6, 1, 14, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val prefixed = parseAs<ScanContent.Event>("BEGIN:VEVENT\nDTSTART;TZID=/mozilla.org/20050126_1/Asia/Kolkata:20250601T140000\nEND:VEVENT")
        assertEquals(kolkata, prefixed.startMillis)
        val windows = parseAs<ScanContent.Event>("BEGIN:VEVENT\nDTSTART;TZID=\"India Standard Time\":20250601T140000\nEND:VEVENT")
        assertEquals(kolkata, windows.startMillis)
        val pacific = LocalDateTime.of(2025, 6, 1, 14, 0).atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()
        assertEquals(pacific, parseAs<ScanContent.Event>("BEGIN:VEVENT\nDTSTART;TZID=Pacific Standard Time:20250601T140000\nEND:VEVENT").startMillis)
        assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"), EventParser.zoneFor("/tzurl.org/x/America/Argentina/Buenos_Aires"))
        assertEquals(ZoneId.of("UTC"), EventParser.zoneFor("Coordinated Universal Time"))
        assertEquals(ZoneId.of("Europe/London"), EventParser.zoneFor("gmt standard time"))
        assertEquals(ZoneId.systemDefault(), EventParser.zoneFor("Nowhere/Land"))
        assertEquals(ZoneId.systemDefault(), EventParser.zoneFor(null))
    }

    @Test
    fun eventWithBadDateKeepsOtherFields() {
        val event = parseAs<ScanContent.Event>("BEGIN:VEVENT\nSUMMARY:Party\nDTSTART:20251399T250000\nEND:VEVENT")
        assertEquals("Party", event.summary)
        assertNull(event.startMillis)
    }

    @Test
    fun upiThroughContentParser() {
        val raw = "upi://pay?pa=merchant@okaxis&pn=Hari%20Stores&am=150.00&cu=INR&tn=Order+42&mc=5411"
        val payment = parseAs<ScanContent.Payment>(raw)
        assertEquals("merchant@okaxis", payment.payeeAddress)
        assertEquals("Hari Stores", payment.payeeName)
        assertEquals("Order 42", payment.note)
        assertEquals("upi://pay?pa=merchant@okaxis&pn=Hari%20Stores&am=150.00&cu=INR&tn=Order%2042&mc=5411", payment.upiUri)
    }

    @Test
    fun upiWithoutAddressIsText() {
        assertEquals(ScanContent.Text("upi://pay?pn=Nobody"), ContentParser.parse("upi://pay?pn=Nobody", qr))
    }

    @Test
    fun emvWithoutVpaIsText() {
        val raw = "000201" + "5303356" + "5902AB"
        assertEquals(ScanContent.Text(raw), ContentParser.parse(raw, qr))
    }

    @Test
    fun plainTextAndMalformedInputFallBackToText() {
        assertEquals(ScanContent.Text("Hello world"), ContentParser.parse("Hello world", qr))
        assertEquals(ScanContent.Text(""), ContentParser.parse("", qr))
        assertEquals(ScanContent.Text("WIFI:"), ContentParser.parse("WIFI:", qr))
        assertEquals(ScanContent.Text("MATMSG:SUB:x;;"), ContentParser.parse("MATMSG:SUB:x;;", qr))
        assertEquals(ScanContent.Text("mailto:"), ContentParser.parse("mailto:", qr))
        assertEquals(ScanContent.Text("BEGIN:VCALENDAR\nEND:VCALENDAR"), ContentParser.parse("BEGIN:VCALENDAR\nEND:VCALENDAR", qr))
    }
}
