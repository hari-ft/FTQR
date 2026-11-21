package com.hari.ftqr.parse

internal object UpiUri {

    const val KEY_PAYEE_ADDRESS = "pa"
    const val KEY_PAYEE_NAME = "pn"
    const val KEY_AMOUNT = "am"
    const val KEY_CURRENCY = "cu"
    const val KEY_NOTE = "tn"
    const val KEY_REFERENCE = "tr"
    const val KEY_MERCHANT_CODE = "mc"

    private const val HOST_PAY = "pay"
    private const val HOST_MANDATE = "mandate"

    private val singleValuedKeys = setOf(
        KEY_PAYEE_ADDRESS, KEY_PAYEE_NAME, KEY_AMOUNT, KEY_CURRENCY, KEY_NOTE, KEY_REFERENCE, KEY_MERCHANT_CODE,
        "mode", "purpose",
    )

    private val preservedKeys = listOf(
        KEY_PAYEE_ADDRESS, KEY_PAYEE_NAME, KEY_AMOUNT, KEY_CURRENCY, KEY_NOTE, KEY_REFERENCE, KEY_MERCHANT_CODE,
        "mode", "purpose", "orgid", "mid", "msid", "mtid", "tid", "url", "sign",
    )

    private val amountPattern = Regex("^\\d+(\\.\\d{1,2})?$")

    fun parseParameters(query: String): Map<String, String>? {
        val counts = HashMap<String, Int>()
        val values = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val separator = pair.indexOf('=')
            val rawKey = if (separator >= 0) pair.substring(0, separator) else pair
            val rawValue = if (separator >= 0) pair.substring(separator + 1) else ""
            val key = TextCodec.percentDecode(rawKey, plusAsSpace = true).trim().lowercase()
            if (key.isEmpty()) continue
            val count = (counts[key] ?: 0) + 1
            counts[key] = count
            if (count > 1 && key in singleValuedKeys) return null
            val value = TextCodec.percentDecode(rawValue, plusAsSpace = true).trim()
            if (count == 1 && value.isNotEmpty()) values[key] = value
        }
        return values
    }

    fun hostIsMandate(host: String): Boolean? = when (host) {
        HOST_PAY -> false
        HOST_MANDATE -> true
        else -> null
    }

    fun build(values: Map<String, String?>, isMandate: Boolean): String {
        val host = if (isMandate) HOST_MANDATE else HOST_PAY
        val query = preservedKeys.mapNotNull { key ->
            values[key]?.takeIf { it.isNotEmpty() }?.let { "$key=${TextCodec.percentEncode(it)}" }
        }.joinToString("&")
        return "upi://$host?$query"
    }

    fun isValidAmount(amount: String?): Boolean {
        if (amount == null) return true
        if (!amountPattern.matches(amount)) return false
        return amount.toBigDecimal().signum() > 0
    }
}
