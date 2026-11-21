package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent

object EmvQrParser {

    private const val PAYLOAD_FORMAT_INDICATOR = "000201"
    private const val TAG_GUID = "00"
    private const val TAG_MERCHANT_CATEGORY = "52"
    private const val TAG_CURRENCY = "53"
    private const val TAG_AMOUNT = "54"
    private const val TAG_MERCHANT_NAME = "59"
    private const val TAG_ADDITIONAL_DATA = "62"
    private const val TAG_PURPOSE = "08"
    private const val DEFAULT_CURRENCY = "INR"

    private val merchantAccountTags = (26..51).map { it.toString() }
    private val upiGuidMarkers = listOf("a000000677", "npci")
    private val vpaPattern = Regex("^[A-Za-z0-9._\\-]+@[A-Za-z0-9._\\-]+$")
    private val currencyNames = mapOf(
        "356" to "INR",
        "840" to "USD",
        "978" to "EUR",
        "826" to "GBP",
        "784" to "AED",
        "702" to "SGD",
    )

    fun parse(payload: String): ScanContent.Payment? = try {
        parseOrNull(payload.trim())
    } catch (error: Exception) {
        null
    }

    private fun parseOrNull(payload: String): ScanContent.Payment? {
        if (!payload.startsWith(PAYLOAD_FORMAT_INDICATOR)) return null
        val fields = parseTlv(payload)
        val vpa = findVpa(fields) ?: return null
        val name = fields[TAG_MERCHANT_NAME]?.trim()?.ifEmpty { null }
        val amount = fields[TAG_AMOUNT]?.trim()?.ifEmpty { null }
        val merchantCategory = fields[TAG_MERCHANT_CATEGORY]?.trim()?.ifEmpty { null }
        val currencyCode = fields[TAG_CURRENCY]?.trim()?.ifEmpty { null }
        val currency = currencyCode?.let { currencyNames[it] ?: it } ?: DEFAULT_CURRENCY
        val note = fields[TAG_ADDITIONAL_DATA]?.let { parseTlv(it)[TAG_PURPOSE] }?.trim()?.ifEmpty { null }
        return ScanContent.Payment(
            payeeAddress = vpa,
            payeeName = name,
            amount = amount,
            currency = currency,
            note = note,
            merchantCode = merchantCategory,
            upiUri = buildUpiUri(vpa, name, amount, merchantCategory),
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

    private fun findVpa(fields: Map<String, String>): String? {
        val templates = merchantAccountTags.mapNotNull { tag -> fields[tag] }
        val ordered = templates.sortedByDescending { isUpiTemplate(it) }
        for (template in ordered) {
            val nested = parseTlv(template)
            for ((tag, value) in nested) {
                if (tag == TAG_GUID) continue
                val candidate = value.trim()
                if (vpaPattern.matches(candidate)) return candidate
            }
            val wholeValue = template.trim()
            if (nested.isEmpty() && vpaPattern.matches(wholeValue)) return wholeValue
        }
        return null
    }

    private fun isUpiTemplate(template: String): Boolean {
        val guid = parseTlv(template)[TAG_GUID]?.lowercase() ?: return false
        return upiGuidMarkers.any { guid.contains(it) }
    }

    private fun buildUpiUri(vpa: String, name: String?, amount: String?, merchantCategory: String?): String {
        val params = listOf(
            "pa" to vpa,
            "pn" to name,
            "am" to amount,
            "cu" to DEFAULT_CURRENCY,
            "mc" to merchantCategory,
        )
        return "upi://pay?" + params
            .filter { !it.second.isNullOrEmpty() }
            .joinToString("&") { "${it.first}=${TextCodec.percentEncode(it.second.orEmpty())}" }
    }
}
