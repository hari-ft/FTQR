package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent

internal object VCardParser {

    fun parse(text: String): ScanContent.Contact {
        var formattedName: String? = null
        var structuredName: String? = null
        var organization: String? = null
        var title: String? = null
        val phones = ArrayList<String>()
        val emails = ArrayList<String>()
        var address: String? = null
        var website: String? = null
        var note: String? = null

        for (line in LineFormat.unfoldLines(text)) {
            val property = LineFormat.parsePropertyLine(line) ?: continue
            val value = property.decodedValue()
            when (property.name) {
                "FN" -> if (formattedName == null) formattedName = TextCodec.cleanText(value, true)
                "N" -> if (structuredName == null) structuredName = structuredNameOf(value)
                "ORG" -> if (organization == null) organization = LineFormat.joinParts(value, ", ")
                "TITLE" -> if (title == null) title = TextCodec.cleanText(value, true)
                "TEL" -> TextCodec.cleanText(value, true)?.let { phones.add(removePrefixIgnoringCase(it, "tel:")) }
                "EMAIL" -> TextCodec.cleanText(value, true)?.let { emails.add(removePrefixIgnoringCase(it, "mailto:")) }
                "ADR" -> if (address == null) address = LineFormat.joinParts(value, ", ")
                "URL" -> if (website == null) website = TextCodec.cleanText(value, true)
                "NOTE" -> if (note == null) note = TextCodec.cleanText(value, true)
            }
        }

        return ScanContent.Contact(
            name = formattedName ?: structuredName,
            organization = organization,
            title = title,
            phones = phones.filter { it.isNotBlank() },
            emails = emails.filter { it.isNotBlank() },
            address = address,
            website = website,
            note = note,
        )
    }

    private fun structuredNameOf(value: String): String? {
        val parts = TextCodec.splitUnescaped(value, ';').map { TextCodec.cleanText(it, true).orEmpty() }
        val family = parts.getOrNull(0)
        val given = parts.getOrNull(1)
        val additional = parts.getOrNull(2)
        val prefix = parts.getOrNull(3)
        val suffix = parts.getOrNull(4)
        return listOfNotNull(prefix, given, additional, family, suffix)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { null }
    }

    private fun removePrefixIgnoringCase(value: String, prefix: String): String =
        if (value.startsWith(prefix, ignoreCase = true)) value.substring(prefix.length).trim() else value
}
