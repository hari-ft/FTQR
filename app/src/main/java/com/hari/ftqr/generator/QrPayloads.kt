package com.hari.ftqr.generator

import java.math.RoundingMode
import java.net.URLEncoder

object QrPayloads {

    const val SECURITY_WPA = "WPA"
    const val SECURITY_WEP = "WEP"
    const val SECURITY_NONE = "nopass"

    private val urlSchemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val opaqueSchemeRegex = Regex(
        "^(mailto|tel|sms|smsto|mms|geo|market|intent|upi|whatsapp|skype|bitcoin|data):",
        RegexOption.IGNORE_CASE,
    )

    fun wifi(ssid: String, password: String, security: String, hidden: Boolean): String {
        val type = when (security.trim().uppercase()) {
            SECURITY_WPA -> SECURITY_WPA
            SECURITY_WEP -> SECURITY_WEP
            else -> SECURITY_NONE
        }
        return buildString {
            append("WIFI:T:").append(type)
            append(";S:").append(escapeWifi(ssid))
            if (type != SECURITY_NONE) append(";P:").append(escapeWifi(password))
            if (hidden) append(";H:true")
            append(";;")
        }
    }

    fun contact(
        name: String,
        phone: String,
        email: String,
        organization: String,
        website: String,
    ): String {
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
        val fullName = name.trim()
        if (fullName.isNotEmpty()) {
            val parts = fullName.split(Regex("\\s+"))
            val family = if (parts.size > 1) parts.last() else ""
            val given = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else parts.first()
            lines += "N:${escapeVCard(family)};${escapeVCard(given)};;;"
            lines += "FN:${escapeVCard(fullName)}"
        } else {
            lines += "N:;;;;"
            lines += "FN:${escapeVCard(organization.trim())}"
        }
        if (organization.isNotBlank()) lines += "ORG:${escapeVCard(organization.trim())}"
        if (phone.isNotBlank()) lines += "TEL;TYPE=CELL:${escapeVCard(phone.trim())}"
        if (email.isNotBlank()) lines += "EMAIL;TYPE=INTERNET:${escapeVCard(email.trim())}"
        if (website.isNotBlank()) lines += "URL:${escapeVCard(url(website))}"
        lines += "END:VCARD"
        return lines.joinToString(separator = "\r\n", postfix = "\r\n")
    }

    fun phone(number: String): String = "tel:" + cleanNumber(number)

    fun email(to: String, subject: String, body: String): String {
        val query = listOf("subject" to subject, "body" to body)
            .filter { it.second.isNotBlank() }
            .joinToString("&") { "${it.first}=${encode(it.second)}" }
        val address = to.trim()
        return if (query.isEmpty()) "mailto:$address" else "mailto:$address?$query"
    }

    fun sms(number: String, message: String): String = "SMSTO:${cleanNumber(number)}:$message"

    fun upi(vpa: String, name: String, amount: String, note: String): String {
        val normalizedAmount = amount.trim().toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.toPlainString()
            ?: amount.trim()
        val params = mutableListOf<String>()
        if (vpa.isNotBlank()) params += "pa=" + encode(vpa.trim()).replace("%40", "@")
        if (name.isNotBlank()) params += "pn=" + encode(name.trim())
        if (normalizedAmount.isNotBlank()) params += "am=" + encode(normalizedAmount)
        params += "cu=INR"
        if (note.isNotBlank()) params += "tn=" + encode(note.trim())
        return "upi://pay?" + params.joinToString("&")
    }

    fun url(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (urlSchemeRegex.containsMatchIn(trimmed) || opaqueSchemeRegex.containsMatchIn(trimmed)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun cleanNumber(number: String): String =
        number.trim().filter { it.isDigit() || it == '+' }

    private fun escapeWifi(value: String): String = buildString {
        for (c in value) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }

    private fun escapeVCard(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")
}
