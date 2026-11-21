package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent
import com.hari.ftqr.model.WifiSecurity

object ContentParser {

    private const val FORMAT_CODE_128 = 1
    private const val FORMAT_CODE_39 = 2
    private const val FORMAT_CODE_93 = 4
    private const val FORMAT_CODABAR = 8
    private const val FORMAT_DATA_MATRIX = 16
    private const val FORMAT_EAN_13 = 32
    private const val FORMAT_EAN_8 = 64
    private const val FORMAT_ITF = 128
    private const val FORMAT_QR_CODE = 256
    private const val FORMAT_UPC_A = 512
    private const val FORMAT_UPC_E = 1024
    private const val FORMAT_PDF417 = 2048
    private const val FORMAT_AZTEC = 4096

    private const val BYTE_ORDER_MARK = '﻿'

    private val productFormatNames = mapOf(
        FORMAT_EAN_13 to "EAN-13",
        FORMAT_EAN_8 to "EAN-8",
        FORMAT_UPC_A to "UPC-A",
        FORMAT_UPC_E to "UPC-E",
    )

    private val nonProductFormats = setOf(
        FORMAT_CODE_128, FORMAT_CODE_39, FORMAT_CODE_93, FORMAT_CODABAR, FORMAT_DATA_MATRIX,
        FORMAT_ITF, FORMAT_QR_CODE, FORMAT_PDF417, FORMAT_AZTEC,
    )

    private val webLinkPattern = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    private val bareWwwPattern = Regex("^www\\.[^\\s.]+(\\.[^\\s.]+)+\\S*$", RegexOption.IGNORE_CASE)
    private val genericSchemeLinkPattern = Regex("^[a-z][a-z0-9+.\\-]*://\\S+$", RegexOption.IGNORE_CASE)
    private val coordinateQueryPattern = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:\\((.*)\\))?\\s*$")

    fun parse(raw: String, format: Int): ScanContent = try {
        classify(raw, format)
    } catch (error: Exception) {
        ScanContent.Text(raw)
    }

    private fun classify(raw: String, format: Int): ScanContent {
        val text = raw.trim().trimStart(BYTE_ORDER_MARK).trim()
        if (text.isEmpty()) return ScanContent.Text(raw)
        productOrNull(text, format)?.let { return it }
        val lower = text.lowercase()
        val parsed: ScanContent? = when {
            lower.startsWith("upi://") -> UpiParser.parse(text)
            text.startsWith("000201") -> EmvQrParser.parse(text)
            lower.startsWith("wifi:") -> parseWifi(text.substring(5))
            lower.startsWith("begin:vcard") -> VCardParser.parse(text)
            lower.startsWith("mecard:") -> parseMeCard(text.substring(7))
            lower.startsWith("begin:vcalendar") || lower.startsWith("begin:vevent") -> EventParser.parse(text)
            lower.startsWith("matmsg:") -> parseMatMsg(text.substring(7))
            lower.startsWith("mailto:") -> parseMailto(text.substring(7))
            lower.startsWith("tel:") -> parsePhone(text.substring(4))
            lower.startsWith("smsto:") -> parseSms(text.substring(6))
            lower.startsWith("sms:") -> parseSms(text.substring(4))
            lower.startsWith("geo:") -> parseGeo(text.substring(4))
            webLinkPattern.matches(text) -> ScanContent.Link(text)
            bareWwwPattern.matches(text) -> ScanContent.Link("https://$text")
            genericSchemeLinkPattern.matches(text) -> ScanContent.Link(text)
            else -> null
        }
        return parsed ?: ScanContent.Text(raw)
    }

    private fun productOrNull(text: String, format: Int): ScanContent.Product? {
        if (format in nonProductFormats) return null
        val formatName = productFormatNames[format] ?: return null
        if (!text.all { it in '0'..'9' }) return null
        return ScanContent.Product(text, formatName)
    }

    private fun parseWifi(body: String): ScanContent.Wifi? {
        val fields = FieldList.parse(body)
        val ssid = fields.first("S")?.let(::decodeWifiValue)
        if (ssid.isNullOrEmpty()) return null
        val password = fields.first("P")?.let(::decodeWifiValue)?.ifEmpty { null }
        val type = fields.first("T")?.let(::decodeWifiValue)?.trim()?.uppercase().orEmpty()
        val security = when (type) {
            "WEP" -> WifiSecurity.WEP
            "NOPASS", "NONE", "OPEN" -> WifiSecurity.OPEN
            "" -> if (password == null) WifiSecurity.OPEN else WifiSecurity.WPA
            else -> WifiSecurity.WPA
        }
        val hiddenFlag = fields.first("H")?.trim()
        val hidden = hiddenFlag.equals("true", ignoreCase = true) || hiddenFlag == "1"
        return ScanContent.Wifi(
            ssid = ssid,
            password = if (security == WifiSecurity.OPEN) null else password,
            security = security,
            hidden = hidden,
        )
    }

    private fun decodeWifiValue(raw: String): String {
        val quoted = raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") && !raw.endsWith("\\\"")
        val inner = if (quoted) raw.substring(1, raw.length - 1) else raw
        return TextCodec.unescapeBackslash(inner, translateNewline = false)
    }

    private fun parseMeCard(body: String): ScanContent.Contact {
        val fields = FieldList.parse(body)
        val name = fields.first("N")?.let(::meCardName) ?: fields.first("NICKNAME")?.let(::cleanField)
        return ScanContent.Contact(
            name = name,
            organization = fields.first("ORG")?.let(::cleanField),
            title = fields.first("TITLE")?.let(::cleanField),
            phones = fields.all("TEL").mapNotNull(::cleanField),
            emails = fields.all("EMAIL").mapNotNull(::cleanField),
            address = fields.first("ADR")?.let(::meCardAddress),
            website = fields.first("URL")?.let(::cleanField),
            note = fields.first("NOTE")?.let(::cleanField),
        )
    }

    private fun meCardName(raw: String): String? {
        val parts = TextCodec.splitUnescaped(raw, ',').map { cleanField(it).orEmpty() }
        val ordered = if (parts.size >= 2) parts.drop(1) + parts.first() else parts
        return ordered.filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { null }
    }

    private fun meCardAddress(raw: String): String? =
        TextCodec.splitUnescaped(raw, ',').mapNotNull(::cleanField).joinToString(", ").ifEmpty { null }

    private fun cleanField(raw: String): String? = TextCodec.cleanText(raw, translateNewline = false)

    private fun parseMatMsg(body: String): ScanContent.Email? {
        val fields = FieldList.parse(body)
        val address = fields.first("TO")?.let(::cleanField) ?: return null
        return ScanContent.Email(
            address = address,
            subject = fields.first("SUB")?.let(::cleanField),
            body = fields.first("BODY")?.let(::cleanField),
        )
    }

    private fun parseMailto(rest: String): ScanContent.Email? {
        val params = TextCodec.parseQuery(rest.substringAfter('?', ""), plusAsSpace = true)
        val address = TextCodec.percentDecode(rest.substringBefore('?'), plusAsSpace = false).trim()
            .ifEmpty { params["to"]?.trim().orEmpty() }
        if (address.isEmpty()) return null
        return ScanContent.Email(
            address = address,
            subject = params["subject"]?.ifEmpty { null },
            body = params["body"]?.ifEmpty { null },
        )
    }

    private fun parsePhone(rest: String): ScanContent.Phone? {
        val number = TextCodec.percentDecode(rest, plusAsSpace = false).trim()
        return if (number.isEmpty()) null else ScanContent.Phone(number)
    }

    private fun parseSms(rest: String): ScanContent.Sms? {
        val colon = rest.indexOf(':')
        val queryStart = rest.indexOf('?')
        val parts: Pair<String, String?> = when {
            colon >= 0 && (queryStart < 0 || colon < queryStart) -> rest.substring(0, colon) to rest.substring(colon + 1)
            queryStart >= 0 -> rest.substring(0, queryStart) to TextCodec.parseQuery(rest.substring(queryStart + 1), plusAsSpace = true)["body"]
            else -> rest to null
        }
        val number = TextCodec.percentDecode(parts.first, plusAsSpace = false).trim()
        val message = parts.second?.ifEmpty { null }
        if (number.isEmpty() && message == null) return null
        return ScanContent.Sms(number, message)
    }

    private fun parseGeo(rest: String): ScanContent.Geo? {
        val coordinates = rest.substringBefore('?').substringBefore(';').split(',')
        val latitude = coordinates.getOrNull(0)?.trim()?.toDoubleOrNull()
        val longitude = coordinates.getOrNull(1)?.trim()?.toDoubleOrNull()
        val query = TextCodec.parseQuery(rest.substringAfter('?', ""), plusAsSpace = true)["q"]?.trim()
        val queryMatch = query?.let { coordinateQueryPattern.matchEntire(it) }
        if (queryMatch != null) {
            val label = queryMatch.groups[3]?.value?.trim()?.ifEmpty { null }
            if (latitude != null && longitude != null && !(latitude == 0.0 && longitude == 0.0)) {
                return geoOrNull(latitude, longitude, label)
            }
            return geoOrNull(queryMatch.groupValues[1].toDouble(), queryMatch.groupValues[2].toDouble(), label)
        }
        if (latitude == null || longitude == null) return null
        return geoOrNull(latitude, longitude, query?.ifEmpty { null })
    }

    private fun geoOrNull(latitude: Double, longitude: Double, label: String?): ScanContent.Geo? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return ScanContent.Geo(latitude, longitude, label)
    }
}

internal class FieldList(private val entries: List<Pair<String, String>>) {

    fun first(key: String): String? = entries.firstOrNull { it.first == key }?.second

    fun all(key: String): List<String> = entries.filter { it.first == key }.map { it.second }

    companion object {
        fun parse(body: String): FieldList {
            val entries = TextCodec.splitUnescaped(body, ';').mapNotNull { field ->
                val separator = field.indexOf(':')
                if (separator <= 0) null else field.substring(0, separator).trim().uppercase() to field.substring(separator + 1)
            }
            return FieldList(entries)
        }
    }
}
