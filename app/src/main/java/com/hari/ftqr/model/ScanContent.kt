package com.hari.ftqr.model

sealed interface ScanContent {
    data class Link(val url: String) : ScanContent
    data class Wifi(val ssid: String, val password: String?, val security: WifiSecurity, val hidden: Boolean) : ScanContent
    data class Contact(
        val name: String?,
        val organization: String?,
        val title: String?,
        val phones: List<String>,
        val emails: List<String>,
        val address: String?,
        val website: String?,
        val note: String?,
    ) : ScanContent
    data class Phone(val number: String) : ScanContent
    data class Email(val address: String, val subject: String?, val body: String?) : ScanContent
    data class Sms(val number: String, val message: String?) : ScanContent
    data class Geo(val latitude: Double, val longitude: Double, val label: String?) : ScanContent
    data class Event(
        val summary: String?,
        val startMillis: Long?,
        val endMillis: Long?,
        val allDay: Boolean,
        val location: String?,
        val description: String?,
    ) : ScanContent
    data class Payment(
        val payeeAddress: String,
        val payeeName: String?,
        val amount: String?,
        val currency: String?,
        val note: String?,
        val merchantCode: String?,
        val upiUri: String,
    ) : ScanContent
    data class Product(val code: String, val formatName: String) : ScanContent
    data class Text(val text: String) : ScanContent
}

enum class WifiSecurity { OPEN, WEP, WPA }

enum class ContentKind(val label: String) {
    LINK("Link"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
    PHONE("Phone"),
    EMAIL("Email"),
    SMS("SMS"),
    GEO("Location"),
    EVENT("Event"),
    PAYMENT("Payment"),
    PRODUCT("Product"),
    TEXT("Text"),
}

enum class LinkWarning(val message: String) {
    INSECURE_HTTP("Not encrypted (http). Data you enter can be read by others."),
    SHORTENER("Shortened link. The real destination is hidden."),
    IP_ADDRESS("Points to a raw IP address instead of a domain name."),
    LOOKALIKE_DOMAIN("Domain uses look-alike characters and may imitate another site."),
    CREDENTIALS_IN_URL("Contains text before '@' that can disguise the real domain."),
    NON_STANDARD_PORT("Uses an unusual port number."),
    UNUSUAL_SCHEME("Not a web link. It may open another app."),
}

data class LinkAssessment(val url: String, val scheme: String, val host: String, val warnings: List<LinkWarning>)
