package com.hari.ftqr.parse

import com.hari.ftqr.model.LinkWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkSafetyTest {

    @Test
    fun cleanHttpsLinkHasNoWarnings() {
        val assessment = LinkSafety.assess("https://example.com/path?q=1")
        assertEquals("https", assessment.scheme)
        assertEquals("example.com", assessment.host)
        assertEquals(emptyList<LinkWarning>(), assessment.warnings)
    }

    @Test
    fun schemeAndHostAreLowercasedAndStandardPortIgnored() {
        val assessment = LinkSafety.assess("HTTPS://Example.COM:443/Index")
        assertEquals("https", assessment.scheme)
        assertEquals("example.com", assessment.host)
        assertEquals(emptyList<LinkWarning>(), assessment.warnings)
    }

    @Test
    fun insecureHttp() {
        assertEquals(listOf(LinkWarning.INSECURE_HTTP), LinkSafety.assess("http://example.com").warnings)
        assertEquals(listOf(LinkWarning.INSECURE_HTTP), LinkSafety.assess("http://example.com:80/").warnings)
    }

    @Test
    fun shortener() {
        assertEquals(listOf(LinkWarning.SHORTENER), LinkSafety.assess("https://bit.ly/abc").warnings)
        assertEquals(listOf(LinkWarning.SHORTENER), LinkSafety.assess("https://www.tinyurl.com/x").warnings)
        assertEquals(listOf(LinkWarning.SHORTENER), LinkSafety.assess("https://t.co/x").warnings)
        assertEquals(emptyList<LinkWarning>(), LinkSafety.assess("https://microsoft.co/x").warnings)
    }

    @Test
    fun ipAddresses() {
        val ipv4 = LinkSafety.assess("https://192.168.1.10/login")
        assertEquals("192.168.1.10", ipv4.host)
        assertEquals(listOf(LinkWarning.IP_ADDRESS), ipv4.warnings)

        val ipv6 = LinkSafety.assess("https://[2001:db8::1]:8443/")
        assertEquals("[2001:db8::1]", ipv6.host)
        assertEquals(listOf(LinkWarning.IP_ADDRESS, LinkWarning.NON_STANDARD_PORT), ipv6.warnings)
    }

    @Test
    fun lookalikeDomains() {
        assertEquals(listOf(LinkWarning.LOOKALIKE_DOMAIN), LinkSafety.assess("https://xn--pple-43d.com/").warnings)
        val cyrillic = LinkSafety.assess("https://аpple.com/")
        assertEquals(listOf(LinkWarning.LOOKALIKE_DOMAIN), cyrillic.warnings)
    }

    @Test
    fun credentialsInUrl() {
        val assessment = LinkSafety.assess("https://paypal.com@evil.example/login")
        assertEquals("evil.example", assessment.host)
        assertEquals(listOf(LinkWarning.CREDENTIALS_IN_URL), assessment.warnings)
        val withPassword = LinkSafety.assess("https://user:pass@example.com:8080/")
        assertEquals("example.com", withPassword.host)
        assertEquals(listOf(LinkWarning.CREDENTIALS_IN_URL, LinkWarning.NON_STANDARD_PORT), withPassword.warnings)
    }

    @Test
    fun nonStandardPort() {
        val assessment = LinkSafety.assess("https://example.com:8080/")
        assertEquals("example.com", assessment.host)
        assertEquals(listOf(LinkWarning.NON_STANDARD_PORT), assessment.warnings)
    }

    @Test
    fun unusualScheme() {
        val market = LinkSafety.assess("market://details?id=com.example")
        assertEquals("market", market.scheme)
        assertEquals("details", market.host)
        assertEquals(listOf(LinkWarning.UNUSUAL_SCHEME), market.warnings)

        val intent = LinkSafety.assess("INTENT://scan/#Intent;end")
        assertEquals("intent", intent.scheme)
        assertTrue(LinkWarning.UNUSUAL_SCHEME in intent.warnings)
    }

    @Test
    fun combinedWarnings() {
        val assessment = LinkSafety.assess("http://admin@10.0.0.1:8080/")
        assertEquals(
            listOf(
                LinkWarning.INSECURE_HTTP,
                LinkWarning.IP_ADDRESS,
                LinkWarning.CREDENTIALS_IN_URL,
                LinkWarning.NON_STANDARD_PORT,
            ),
            assessment.warnings,
        )
    }

    @Test
    fun garbageDoesNotThrow() {
        val assessment = LinkSafety.assess("not a url at all")
        assertEquals("", assessment.scheme)
        LinkSafety.assess("")
        LinkSafety.assess("https://")
        LinkSafety.assess("https://[::1")
    }
}
