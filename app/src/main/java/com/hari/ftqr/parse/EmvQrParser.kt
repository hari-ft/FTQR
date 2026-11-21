package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent

object EmvQrParser {

    private const val PAYLOAD_FORMAT_INDICATOR = "000201"
    private const val TAG_GUID = "00"
    private const val TAG_VPA = "01"
    private const val TAG_MERCHANT_CATEGORY = "52"
    private const val TAG_CURRENCY = "53"
    private const val TAG_AMOUNT = "54"
    private const val TAG_MERCHANT_NAME = "59"
    private const val TAG_ADDITIONAL_DATA = "62"
    private const val TAG_CRC = "63"
    private const val TAG_REFERENCE_LABEL = "05"
    private const val TAG_PURPOSE = "08"
    private const val CRC_LENGTH = "04"
    private const val INDIAN_RUPEE_CODE = "356"
    private const val DEFAULT_CURRENCY = "INR"
    private const val USER_ENTERED_PLACEHOLDER = "***"

    private val merchantAccountTags = (26..51).map { it.toString() }
    private val upiGuidMarkers = listOf("a000000677", "npci", "upi")
    private val vpaPattern = Regex("^[A-Za-z0-9._\\-]+@[A-Za-z0-9._\\-]+$")

    fun parse(payload: String): ScanContent.Payment? = try {
        parseOrNull(payload.trim())
    } catch (error: Exception) {
        null
    }

    private fun parseOrNull(payload: String): ScanContent.Payment? {
        if (!payload.startsWith(PAYLOAD_FORMAT_INDICATOR)) return null
        if (!hasValidChecksum(payload)) return null
        val fields = parseTlv(payload)
        val currencyCode = fields[TAG_CURRENCY]?.trim()
        if (currencyCode != null && currencyCode != INDIAN_RUPEE_CODE) return null
        val vpa = findVpa(fields) ?: return null
        val name = fields[TAG_MERCHANT_NAME]?.trim()?.ifEmpty { null }
        val amount = fields[TAG_AMOUNT]?.trim()?.ifEmpty { null }
        val merchantCategory = fields[TAG_MERCHANT_CATEGORY]?.trim()?.ifEmpty { null }
        val additional = fields[TAG_ADDITIONAL_DATA]?.let(::parseTlv).orEmpty()
        val note = additional[TAG_PURPOSE]?.trim()?.ifEmpty { null }
        val reference = additional[TAG_REFERENCE_LABEL]?.trim()
            ?.takeIf { it.isNotEmpty() && it != USER_ENTERED_PLACEHOLDER }
        val uriValues = mapOf(
            UpiUri.KEY_PAYEE_ADDRESS to vpa,
            UpiUri.KEY_PAYEE_NAME to name,
            UpiUri.KEY_AMOUNT to amount,
            UpiUri.KEY_CURRENCY to DEFAULT_CURRENCY,
            UpiUri.KEY_NOTE to note,
            UpiUri.KEY_REFERENCE to reference,
            UpiUri.KEY_MERCHANT_CODE to merchantCategory,
        )
        return ScanContent.Payment(
            payeeAddress = vpa,
            payeeName = name,
            amount = amount,
            currency = DEFAULT_CURRENCY,
            note = note,
            merchantCode = merchantCategory,
            upiUri = UpiUri.build(uriValues, isMandate = false),
            amountValid = UpiUri.isValidAmount(amount),
        )
    }

    internal fun parseTlv(data: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var index = 0
        while (index + 4 <= data.length) {
            val tag = data.substring(index, index + 2)
            val lengthText = data.substring(index + 2, index + 4)
            if (!lengthText.all { it in '0'..'9' }) break
            val valueStart = index + 4
            val valueEnd = valueStart + lengthText.toInt()
            if (valueEnd > data.length) break
            if (!result.containsKey(tag)) result[tag] = data.substring(valueStart, valueEnd)
            index = valueEnd
        }
        return result
    }

    internal fun crc16(data: String): String {
        var crc = 0xFFFF
        for (byte in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }

    private fun hasValidChecksum(payload: String): Boolean {
        var index = 0
        while (index + 4 <= payload.length) {
            val tag = payload.substring(index, index + 2)
            val lengthText = payload.substring(index + 2, index + 4)
            if (!lengthText.all { it in '0'..'9' }) return true
            val valueStart = index + 4
            val valueEnd = valueStart + lengthText.toInt()
            if (valueEnd > payload.length) return true
            if (tag == TAG_CRC) {
                if (lengthText != CRC_LENGTH) return false
                return crc16(payload.substring(0, valueStart)) == payload.substring(valueStart, valueEnd).uppercase()
            }
            index = valueEnd
        }
        return true
    }

    private fun findVpa(fields: Map<String, String>): String? {
        for (tag in merchantAccountTags) {
            val template = fields[tag] ?: continue
            val nested = parseTlv(template)
            if (!isUpiGuid(nested[TAG_GUID])) continue
            val preferred = nested[TAG_VPA]?.trim()
            if (preferred != null && vpaPattern.matches(preferred)) return preferred
            val fallback = nested
                .filterKeys { it != TAG_GUID && it != TAG_VPA }
                .values
                .map { it.trim() }
                .firstOrNull { vpaPattern.matches(it) }
            if (fallback != null) return fallback
        }
        return null
    }

    private fun isUpiGuid(guid: String?): Boolean {
        val lower = guid?.trim()?.lowercase() ?: return false
        return upiGuidMarkers.any { lower.contains(it) }
    }
}
