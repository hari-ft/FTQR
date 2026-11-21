package com.hari.ftqr.parse

import com.hari.ftqr.model.ScanContent

object UpiParser {

    private const val UPI_SCHEME_PREFIX = "upi://"

    fun parse(uri: String): ScanContent.Payment? = try {
        parseOrNull(uri.trim())
    } catch (error: Exception) {
        null
    }

    private fun parseOrNull(trimmed: String): ScanContent.Payment? {
        if (!trimmed.startsWith(UPI_SCHEME_PREFIX, ignoreCase = true)) return null
        val query = trimmed.substringAfter('?', "").substringBefore('#')
        val params = TextCodec.parseQuery(query, plusAsSpace = true)
            .mapValues { it.value.trim() }
            .filterValues { it.isNotEmpty() }
        val payeeAddress = params["pa"] ?: return null
        return ScanContent.Payment(
            payeeAddress = payeeAddress,
            payeeName = params["pn"],
            amount = params["am"],
            currency = params["cu"],
            note = params["tn"],
            merchantCode = params["mc"],
            upiUri = trimmed,
        )
    }
}
