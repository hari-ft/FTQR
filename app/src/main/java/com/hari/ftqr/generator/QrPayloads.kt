package com.hari.ftqr.generator

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

object QrPayloads {

    const val SECURITY_WPA = "WPA"
    const val SECURITY_WPA3 = "SAE"
    const val SECURITY_WEP = "WEP"
    const val SECURITY_NONE = "nopass"

    private val UPI_MAX_AMOUNT = BigDecimal("100000")
    private const val UPI_MAX_FRACTION_DIGITS = 2

    private val urlSchemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val opaqueSchemeRegex = Regex(
        "^(mailto|tel|sms|smsto|mms|geo|market|intent|upi|whatsapp|skype|bitcoin|data):",
        RegexOption.IGNORE_CASE,
    )
    private val upiIdRegex = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,63}$")
    private val upiAmountRegex = Regex("^(\\d+(\\.\\d*)?|\\.\\d+)$")
    private val hexRegex = Regex("^[0-9A-Fa-f]{64}$")

    fun wifi(ssid: String, password: String, security: String, hidden: Boolean): String {
        val type = when (security.trim().uppercase()) {
            SECURITY_WPA -> SECURITY_WPA
            SECURITY_WPA3, "WPA3" -> SECURITY_WPA3
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

    fun isValidWpaPassword(password: String): Boolean =
        password.length in 8..63 || hexRegex.matches(password)

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
        if (phone.isNotBlank()) lines += "TEL;TYPE=CELL:${escapeNewlines(phone.trim())}"
        if (email.isNotBlank()) lines += "EMAIL;TYPE=INTERNET:${escapeNewlines(email.trim())}"
        if (website.isNotBlank()) lines += "URL:${escapeNewlines(url(website))}"
        lines += "END:VCARD"
        return lines.joinToString(separator = "\r\n", postfix = "\r\n")
    }

    fun phone(number: String): String = "tel:" + cleanNumber(number).replace("#", "%23")

    fun email(to: String, subject: String, body: String): String {
        val query = listOf("subject" to subject, "body" to body)
            .filter { it.second.isNotBlank() }
            .joinToString("&") { "${it.first}=${encode(it.second)}" }
        val address = to.trim()
        return if (query.isEmpty()) "mailto:$address" else "mailto:$address?$query"
    }

    fun sms(number: String, message: String): String = "SMSTO:${cleanNumber(number)}:$message"

    fun isValidUpiId(vpa: String): Boolean = upiIdRegex.matches(vpa.trim())

    fun filterUpiAmountInput(previous: String, input: String): String {
        val cleaned = input.filter { it in '0'..'9' || it == '.' }
        if (cleaned.count { it == '.' } > 1) return previous
        if (cleaned.substringAfter('.', "").length > UPI_MAX_FRACTION_DIGITS) return previous
        return cleaned
    }

    fun upiAmountError(amount: String): String? {
        val trimmed = amount.trim()
        if (trimmed.isEmpty()) return null
        if (!upiAmountRegex.matches(trimmed)) return "Enter a valid amount"
        if (trimmed.substringAfter('.', "").length > UPI_MAX_FRACTION_DIGITS) return "Max 2 decimal places"
        val scaled = trimmed.toBigDecimalOrNull()?.setScale(UPI_MAX_FRACTION_DIGITS, RoundingMode.UNNECESSARY)
            ?: return "Enter a valid amount"
        if (scaled.signum() <= 0) return "Enter an amount greater than 0"
        if (scaled > UPI_MAX_AMOUNT) return "UPI limit is ₹1,00,000"
        return null
    }

    fun normalizeUpiAmount(amount: String): String? {
        if (amount.isBlank() || upiAmountError(amount) != null) return null
        return amount.trim().toBigDecimal().setScale(UPI_MAX_FRACTION_DIGITS, RoundingMode.UNNECESSARY).toPlainString()
    }

    fun upi(vpa: String, name: String, amount: String, note: String): String {
        val normalizedAmount = normalizeUpiAmount(amount)
        val params = mutableListOf<String>()
        if (vpa.isNotBlank()) params += "pa=" + encode(vpa.trim()).replace("%40", "@")
        if (name.isNotBlank()) params += "pn=" + encode(name.trim())
        if (normalizedAmount != null) params += "am=$normalizedAmount"
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

    private fun cleanNumber(number: String): String {
        val trimmed = number.trim()
        val leadingPlus = trimmed.startsWith("+")
        val body = trimmed.filter { it in '0'..'9' || it == '*' || it == '#' || it == ',' || it == ';' }
        return if (leadingPlus) "+$body" else body
    }

    private fun escapeWifi(value: String): String = buildString {
        for (c in value) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }

    private fun escapeNewlines(value: String): String = value
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    private fun escapeVCard(value: String): String = escapeNewlines(
        value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
    )
}
