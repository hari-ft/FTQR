package com.hari.ftqr.parse

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

internal object TextCodec {

    fun percentDecode(value: String, plusAsSpace: Boolean): String =
        decodeEscapedBytes(value, '%', plusAsSpace, Charsets.UTF_8)

    fun decodeQuotedPrintable(value: String, charset: Charset): String =
        decodeEscapedBytes(value, '=', false, charset)

    fun percentEncode(value: String): String {
        val builder = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val ch = code.toChar()
            if (code < 0x80 && (ch.isLetterOrDigit() || ch in "-_.~@")) {
                builder.append(ch)
            } else {
                builder.append('%')
                builder.append(HEX_DIGITS[code shr 4])
                builder.append(HEX_DIGITS[code and 0x0F])
            }
        }
        return builder.toString()
    }

    fun parseQuery(query: String, plusAsSpace: Boolean): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val separator = pair.indexOf('=')
            val rawKey = if (separator >= 0) pair.substring(0, separator) else pair
            val rawValue = if (separator >= 0) pair.substring(separator + 1) else ""
            val key = percentDecode(rawKey, plusAsSpace).trim().lowercase()
            if (key.isNotEmpty() && !result.containsKey(key)) {
                result[key] = percentDecode(rawValue, plusAsSpace)
            }
        }
        return result
    }

    fun splitUnescaped(text: String, delimiter: Char): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '\\' && index + 1 < text.length) {
                current.append(ch).append(text[index + 1])
                index += 2
                continue
            }
            if (ch == delimiter) {
                parts.add(current.toString())
                current.setLength(0)
            } else {
                current.append(ch)
            }
            index++
        }
        parts.add(current.toString())
        return parts
    }

    fun unescapeBackslash(text: String, translateNewline: Boolean): String {
        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '\\' && index + 1 < text.length) {
                val next = text[index + 1]
                builder.append(if (translateNewline && (next == 'n' || next == 'N')) '\n' else next)
                index += 2
            } else {
                builder.append(ch)
                index++
            }
        }
        return builder.toString()
    }

    fun cleanText(value: String, translateNewline: Boolean): String? =
        unescapeBackslash(value, translateNewline).trim().ifEmpty { null }

    private fun decodeEscapedBytes(value: String, escape: Char, plusAsSpace: Boolean, charset: Charset): String {
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == escape && index + 2 < value.length) {
                val high = Character.digit(value[index + 1], 16)
                val low = Character.digit(value[index + 2], 16)
                if (high >= 0 && low >= 0) {
                    bytes.write(high * 16 + low)
                    index += 3
                    continue
                }
            }
            if (ch == '+' && plusAsSpace) {
                bytes.write(' '.code)
                index++
                continue
            }
            val end = if (ch.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()) index + 2 else index + 1
            val encoded = value.substring(index, end).toByteArray(charset)
            bytes.write(encoded, 0, encoded.size)
            index = end
        }
        return String(bytes.toByteArray(), charset)
    }

    private const val HEX_DIGITS = "0123456789ABCDEF"
}
