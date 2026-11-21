package com.hari.ftqr.parse

import com.hari.ftqr.model.ContentKind
import com.hari.ftqr.model.ScanContent
import java.util.Locale

private const val MAX_TITLE_LENGTH = 80
private const val ELLIPSIS = "…"
private const val RUPEE_SIGN = "₹"
private const val TITLE_SEPARATOR = " · "

fun ScanContent.kind(): ContentKind = when (this) {
    is ScanContent.Link -> ContentKind.LINK
    is ScanContent.Wifi -> ContentKind.WIFI
    is ScanContent.Contact -> ContentKind.CONTACT
    is ScanContent.Phone -> ContentKind.PHONE
    is ScanContent.Email -> ContentKind.EMAIL
    is ScanContent.Sms -> ContentKind.SMS
    is ScanContent.Geo -> ContentKind.GEO
    is ScanContent.Event -> ContentKind.EVENT
    is ScanContent.Payment -> ContentKind.PAYMENT
    is ScanContent.Product -> ContentKind.PRODUCT
    is ScanContent.Text -> ContentKind.TEXT
}

fun ScanContent.title(): String {
    val headline = when (this) {
        is ScanContent.Link -> linkTitle(url)
        is ScanContent.Wifi -> ssid
        is ScanContent.Contact -> listOfNotNull(name, organization, phones.firstOrNull(), emails.firstOrNull())
            .firstOrNull { it.isNotBlank() }
        is ScanContent.Phone -> number
        is ScanContent.Email -> address.ifBlank { subject.orEmpty() }
        is ScanContent.Sms -> number.ifBlank { message?.let(::firstLine).orEmpty() }
        is ScanContent.Geo -> label?.takeIf { it.isNotBlank() }
            ?: "${formatCoordinate(latitude)}, ${formatCoordinate(longitude)}"
        is ScanContent.Event -> summary?.takeIf { it.isNotBlank() } ?: location
        is ScanContent.Payment -> paymentTitle(this)
        is ScanContent.Product -> code
        is ScanContent.Text -> firstLine(text)
    }
    val cleaned = headline?.let(::firstLine)?.takeIf { it.isNotBlank() } ?: kind().label
    return shorten(cleaned)
}

private fun linkTitle(url: String): String {
    val host = LinkSafety.assess(url).host.removePrefix("www.")
    val afterScheme = if (url.contains("://")) url.substringAfter("://") else url.substringAfter(':')
    val withoutQuery = afterScheme.substringBefore('?').substringBefore('#')
    val path = withoutQuery.substringAfter('/', "").trimEnd('/')
    val combined = when {
        host.isEmpty() -> withoutQuery.trimEnd('/')
        path.isEmpty() -> host
        else -> "$host/$path"
    }
    return combined.ifBlank { url }
}

private fun paymentTitle(payment: ScanContent.Payment): String {
    val payee = payment.payeeName?.takeIf { it.isNotBlank() } ?: payment.payeeAddress
    val amount = payment.amount?.takeIf { it.isNotBlank() } ?: return payee
    val currency = payment.currency
    val amountText = if (currency == null || currency.equals("INR", ignoreCase = true)) {
        "$RUPEE_SIGN$amount"
    } else {
        "$currency $amount"
    }
    return "$payee$TITLE_SEPARATOR$amountText"
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.5f", value).trimEnd('0').trimEnd('.')

private fun firstLine(text: String): String =
    text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

private fun shorten(value: String): String =
    if (value.length <= MAX_TITLE_LENGTH) value else value.take(MAX_TITLE_LENGTH - 1).trimEnd() + ELLIPSIS
