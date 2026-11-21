package com.hari.ftqr.parse

import com.hari.ftqr.model.ContentKind
import com.hari.ftqr.model.ScanContent
import java.util.Locale

private const val MAX_TITLE_LENGTH = 80
private const val ELLIPSIS = "…"
private const val RUPEE_SIGN = "₹"
private const val TITLE_SEPARATOR = " · "
private const val AUTOPAY_PREFIX = "Autopay: "
private const val INVALID_AMOUNT = "invalid amount"
private const val MIN_NAME_LENGTH = 4

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
    val withoutQuery = afterScheme.trimStart('/', '\\').substringBefore('?').substringBefore('#')
    val pathStart = withoutQuery.indexOfAny(charArrayOf('/', '\\'))
    val path = if (pathStart < 0) "" else withoutQuery.substring(pathStart + 1).trimEnd('/')
    val combined = when {
        host.isEmpty() -> withoutQuery.trimEnd('/')
        host.length > MAX_TITLE_LENGTH -> ELLIPSIS + host.takeLast(MAX_TITLE_LENGTH - ELLIPSIS.length)
        path.isEmpty() -> host
        host.length + 1 + path.length > MAX_TITLE_LENGTH -> host
        else -> "$host/$path"
    }
    return combined.ifBlank { url }
}

private fun paymentTitle(payment: ScanContent.Payment): String {
    val prefix = if (payment.isMandate) AUTOPAY_PREFIX else ""
    val address = payment.payeeAddress
    val amount = payment.amount?.takeIf { it.isNotBlank() }
    val amountText = when {
        amount == null -> ""
        !payment.amountValid -> "$TITLE_SEPARATOR$INVALID_AMOUNT"
        payment.currency == null || payment.currency.equals("INR", ignoreCase = true) -> "$TITLE_SEPARATOR$RUPEE_SIGN$amount"
        else -> "$TITLE_SEPARATOR${payment.currency} $amount"
    }
    val name = payment.payeeName?.let(::firstLine)?.takeIf { it.isNotEmpty() && it != address }
        ?: return "$prefix$address$amountText"
    val suffix = " ($address)$amountText"
    val room = MAX_TITLE_LENGTH - prefix.length - suffix.length
    val fittedName = when {
        name.length <= room -> name
        room >= MIN_NAME_LENGTH -> name.take(room - ELLIPSIS.length).trimEnd() + ELLIPSIS
        else -> return "$prefix$address$amountText"
    }
    return "$prefix$fittedName$suffix"
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.5f", value).trimEnd('0').trimEnd('.')

private fun firstLine(text: String): String =
    text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

private fun shorten(value: String): String =
    if (value.length <= MAX_TITLE_LENGTH) value else value.take(MAX_TITLE_LENGTH - 1).trimEnd() + ELLIPSIS
