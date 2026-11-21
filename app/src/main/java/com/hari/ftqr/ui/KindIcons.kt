package com.hari.ftqr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.hari.ftqr.model.ContentKind

val ContentKind.icon: ImageVector
    get() = when (this) {
        ContentKind.LINK -> Icons.Outlined.Link
        ContentKind.WIFI -> Icons.Outlined.Wifi
        ContentKind.CONTACT -> Icons.Outlined.Person
        ContentKind.PHONE -> Icons.Outlined.Phone
        ContentKind.EMAIL -> Icons.Outlined.Email
        ContentKind.SMS -> Icons.AutoMirrored.Outlined.Message
        ContentKind.GEO -> Icons.Outlined.Place
        ContentKind.EVENT -> Icons.Outlined.CalendarMonth
        ContentKind.PAYMENT -> Icons.Outlined.CurrencyRupee
        ContentKind.PRODUCT -> Icons.Outlined.QrCodeScanner
        ContentKind.TEXT -> Icons.AutoMirrored.Outlined.Notes
    }

fun contentKindOf(name: String): ContentKind = ContentKind.entries.firstOrNull { it.name == name } ?: ContentKind.TEXT

fun barcodeFormatName(format: Int): String = when (format) {
    1 -> "Code 128"
    2 -> "Code 39"
    4 -> "Code 93"
    8 -> "Codabar"
    16 -> "Data Matrix"
    32 -> "EAN-13"
    64 -> "EAN-8"
    128 -> "ITF"
    256 -> "QR code"
    512 -> "UPC-A"
    1024 -> "UPC-E"
    2048 -> "PDF417"
    4096 -> "Aztec"
    else -> "Barcode"
}
