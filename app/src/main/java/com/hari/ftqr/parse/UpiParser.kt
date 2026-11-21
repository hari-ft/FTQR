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
        val afterScheme = trimmed.substring(UPI_SCHEME_PREFIX.length)
        val host = afterScheme.substringBefore('?').substringBefore('#').trimEnd('/').lowercase()
        val isMandate = UpiUri.hostIsMandate(host) ?: return null
        val query = afterScheme.substringAfter('?', "").substringBefore('#')
        val params = UpiUri.parseParameters(query) ?: return null
        val payeeAddress = params[UpiUri.KEY_PAYEE_ADDRESS] ?: return null
        val amount = params[UpiUri.KEY_AMOUNT]
        return ScanContent.Payment(
            payeeAddress = payeeAddress,
            payeeName = params[UpiUri.KEY_PAYEE_NAME],
            amount = amount,
            currency = params[UpiUri.KEY_CURRENCY],
            note = params[UpiUri.KEY_NOTE],
            merchantCode = params[UpiUri.KEY_MERCHANT_CODE],
            upiUri = UpiUri.build(params, isMandate),
            isMandate = isMandate,
            amountValid = UpiUri.isValidAmount(amount),
        )
    }
}
