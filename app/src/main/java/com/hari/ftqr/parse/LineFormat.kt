package com.hari.ftqr.parse

import java.nio.charset.Charset

internal class PropertyLine(val name: String, private val parameters: List<String>, val rawValue: String) {

    fun parameter(key: String): String? =
        parameters.firstOrNull { it.contains('=') && it.substringBefore('=').trim().equals(key, ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')

    fun decodedValue(): String =
        if (isQuotedPrintable()) TextCodec.decodeQuotedPrintable(rawValue, charset()) else rawValue

    private fun isQuotedPrintable(): Boolean =
        parameters.any { it.trim().equals(QUOTED_PRINTABLE, ignoreCase = true) } ||
            parameter("ENCODING").equals(QUOTED_PRINTABLE, ignoreCase = true)

    private fun charset(): Charset =
        parameter("CHARSET")?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8

    companion object {
        const val QUOTED_PRINTABLE = "QUOTED-PRINTABLE"
    }
}

internal object LineFormat {

    fun unfoldLines(text: String): List<String> {
        val rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val lines = ArrayList<String>()
        for (line in rawLines) {
            val previous = lines.lastOrNull()
            when {
                previous != null && (line.startsWith(" ") || line.startsWith("\t")) ->
                    lines[lines.size - 1] = previous + line.substring(1)
                previous != null && previous.endsWith("=") && isQuotedPrintableLine(previous) ->
                    lines[lines.size - 1] = previous.dropLast(1) + line
                else -> lines.add(line)
            }
        }
        return lines.filter { it.isNotBlank() }
    }

    fun parsePropertyLine(line: String): PropertyLine? {
        val separator = indexOfValueSeparator(line)
        if (separator <= 0) return null
        val head = line.substring(0, separator)
        val headParts = head.split(';')
        val name = headParts[0].substringAfterLast('.').trim().uppercase()
        if (name.isEmpty()) return null
        val parameters = headParts.drop(1).map { it.trim() }
        return PropertyLine(name, parameters, line.substring(separator + 1))
    }

    fun joinParts(value: String, separator: String): String? =
        TextCodec.splitUnescaped(value, ';')
            .mapNotNull { TextCodec.cleanText(it, translateNewline = true) }
            .joinToString(separator)
            .ifEmpty { null }

    private fun isQuotedPrintableLine(line: String): Boolean {
        val separator = indexOfValueSeparator(line)
        if (separator <= 0) return false
        return line.substring(0, separator).uppercase().contains(PropertyLine.QUOTED_PRINTABLE)
    }

    private fun indexOfValueSeparator(line: String): Int {
        var insideQuotes = false
        for (index in line.indices) {
            val ch = line[index]
            if (ch == '"') {
                insideQuotes = !insideQuotes
            } else if (ch == ':' && !insideQuotes) {
                return index
            }
        }
        return -1
    }
}
