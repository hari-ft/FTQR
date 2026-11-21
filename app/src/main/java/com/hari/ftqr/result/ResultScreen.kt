package com.hari.ftqr.result

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.hari.ftqr.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hari.ftqr.data.ScanEntity
import com.hari.ftqr.data.ScanRepository
import com.hari.ftqr.model.LinkAssessment
import com.hari.ftqr.model.ScanContent
import com.hari.ftqr.model.WifiSecurity
import com.hari.ftqr.parse.ContentParser
import com.hari.ftqr.parse.LinkSafety
import com.hari.ftqr.parse.kind
import com.hari.ftqr.ui.barcodeFormatName
import com.hari.ftqr.ui.icon
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(scanId: Long, repository: ScanRepository, onBack: () -> Unit, onDelete: (ScanEntity) -> Unit) {
    val scan by remember(scanId) { repository.observeById(scanId) }.collectAsStateWithLifecycle(initialValue = null)
    val current = scan
    val content = remember(current?.rawValue, current?.format) {
        current?.let { ContentParser.parse(it.rawValue, it.format) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(content?.kind()?.label ?: "Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { onDelete(current) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete from history")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (current == null || content == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(current, content)
            ContentDetails(content)
            RawValueCard(current.rawValue)
        }
    }
}

@Composable
private fun Header(scan: ScanEntity, content: ScanContent) {
    val kind = content.kind()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                kind.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp).size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                scan.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                barcodeFormatName(scan.format) + " · " + formatTimestamp(scan.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContentDetails(content: ScanContent) {
    when (content) {
        is ScanContent.Link -> LinkDetails(content)
        is ScanContent.Payment -> PaymentDetails(content)
        is ScanContent.Wifi -> WifiDetails(content)
        is ScanContent.Contact -> ContactDetails(content)
        is ScanContent.Phone -> PhoneDetails(content)
        is ScanContent.Email -> EmailDetails(content)
        is ScanContent.Sms -> SmsDetails(content)
        is ScanContent.Geo -> GeoDetails(content)
        is ScanContent.Event -> EventDetails(content)
        is ScanContent.Product -> ProductDetails(content)
        is ScanContent.Text -> TextDetails(content)
    }
}

@Composable
private fun LinkDetails(link: ScanContent.Link) {
    val context = LocalContext.current
    val assessment = remember(link.url) { LinkSafety.assess(link.url) }
    var confirmOpen by remember { mutableStateOf(false) }
    DetailCard {
        if (assessment.host.isNotBlank()) {
            Text("Website", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(assessment.host, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.padding(4.dp))
        }
        Text("Full link", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(link.url, style = MaterialTheme.typography.bodyMedium) }
    }
    SafetyCard(assessment)
    ActionRow {
        Button(onClick = {
            if (assessment.warnings.isEmpty()) Actions.openUri(context, link.url) else confirmOpen = true
        }) { ButtonContent(Icons.AutoMirrored.Outlined.OpenInNew, "Open link") }
        CommonTextActions(context, link.url)
    }
    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("Open this link?") },
            text = { Text("This link has ${assessment.warnings.size} warning(s). Only continue if you trust ${assessment.host.ifBlank { "it" }}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    Actions.openUri(context, link.url)
                }) { Text("Open anyway") }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SafetyCard(assessment: LinkAssessment) {
    val warn = assessment.warnings.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!warn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("No obvious warning signs. Still check the website name before entering details.")
                }
            } else {
                assessment.warnings.forEach { warning ->
                    Row {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(warning.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentDetails(payment: ScanContent.Payment) {
    val context = LocalContext.current
    val apps = remember(payment.upiUri) { Actions.paymentApps(context, payment.upiUri) }
    DetailCard {
        Field("Pay to", payment.payeeName)
        Field("UPI ID", payment.payeeAddress)
        payment.amount?.let { Field("Amount", currencySymbol(payment.currency) + it) }
        Field("Note", payment.note)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.illus_payment_safety),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Check the payee name in your payment app before you enter your PIN. Scanning a QR never requires a PIN to receive money.",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (apps.isEmpty()) {
        Text(
            "No UPI payment apps are installed on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        Text("Pay with", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            apps.forEach { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(76.dp)
                        .clickable { Actions.payWith(context, payment.upiUri, app) }
                        .padding(4.dp),
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    ActionRow {
        if (apps.isNotEmpty()) {
            FilledTonalButton(onClick = { Actions.payWithChooser(context, payment.upiUri) }) {
                ButtonContent(Icons.Outlined.Payments, "Choose app")
            }
        }
        OutlinedButton(onClick = { Actions.copy(context, "UPI ID", payment.payeeAddress) }) {
            ButtonContent(Icons.Outlined.ContentCopy, "Copy UPI ID")
        }
    }
}

@Composable
private fun WifiDetails(wifi: ScanContent.Wifi) {
    val context = LocalContext.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    DetailCard {
        Field("Network", wifi.ssid)
        Field(
            "Security",
            when (wifi.security) {
                WifiSecurity.OPEN -> "Open (no password)"
                WifiSecurity.WEP -> "WEP"
                WifiSecurity.WPA -> "WPA/WPA2/WPA3"
            },
        )
        if (wifi.hidden) Field("Hidden network", "Yes")
        if (!wifi.password.isNullOrEmpty()) {
            Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        if (showPassword) wifi.password else "•".repeat(wifi.password.length.coerceAtMost(16)),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            }
        }
    }
    ActionRow {
        Button(onClick = { Actions.connectWifi(context, wifi) }) { ButtonContent(Icons.Outlined.Wifi, "Connect") }
        if (!wifi.password.isNullOrEmpty()) {
            OutlinedButton(onClick = { Actions.copy(context, "Wi-Fi password", wifi.password) }) {
                ButtonContent(Icons.Outlined.ContentCopy, "Copy password")
            }
        }
    }
}

@Composable
private fun ContactDetails(contact: ScanContent.Contact) {
    val context = LocalContext.current
    DetailCard {
        Field("Name", contact.name)
        Field("Organization", contact.organization)
        Field("Title", contact.title)
        contact.phones.forEach { Field("Phone", it) }
        contact.emails.forEach { Field("Email", it) }
        Field("Address", contact.address)
        Field("Website", contact.website)
        Field("Note", contact.note)
    }
    ActionRow {
        Button(onClick = { Actions.saveContact(context, contact) }) { ButtonContent(Icons.Outlined.PersonAdd, "Save contact") }
        contact.phones.firstOrNull()?.let { phone ->
            OutlinedButton(onClick = { Actions.dial(context, phone) }) { ButtonContent(Icons.Outlined.Phone, "Call") }
        }
        contact.emails.firstOrNull()?.let { address ->
            OutlinedButton(onClick = { Actions.email(context, ScanContent.Email(address, null, null)) }) {
                ButtonContent(Icons.Outlined.Email, "Email")
            }
        }
    }
}

@Composable
private fun PhoneDetails(phone: ScanContent.Phone) {
    val context = LocalContext.current
    DetailCard { Field("Phone number", phone.number) }
    ActionRow {
        Button(onClick = { Actions.dial(context, phone.number) }) { ButtonContent(Icons.Outlined.Phone, "Call") }
        OutlinedButton(onClick = { Actions.sms(context, phone.number, null) }) {
            ButtonContent(Icons.AutoMirrored.Outlined.Send, "Message")
        }
        OutlinedButton(onClick = { Actions.savePhoneAsContact(context, phone.number) }) {
            ButtonContent(Icons.Outlined.PersonAdd, "Save")
        }
    }
}

@Composable
private fun EmailDetails(email: ScanContent.Email) {
    val context = LocalContext.current
    DetailCard {
        Field("To", email.address)
        Field("Subject", email.subject)
        Field("Message", email.body)
    }
    ActionRow {
        Button(onClick = { Actions.email(context, email) }) { ButtonContent(Icons.Outlined.Email, "Write email") }
        OutlinedButton(onClick = { Actions.copy(context, "Email", email.address) }) {
            ButtonContent(Icons.Outlined.ContentCopy, "Copy address")
        }
    }
}

@Composable
private fun SmsDetails(sms: ScanContent.Sms) {
    val context = LocalContext.current
    DetailCard {
        Field("To", sms.number)
        Field("Message", sms.message)
    }
    ActionRow {
        Button(onClick = { Actions.sms(context, sms.number, sms.message) }) {
            ButtonContent(Icons.AutoMirrored.Outlined.Send, "Send SMS")
        }
    }
}

@Composable
private fun GeoDetails(geo: ScanContent.Geo) {
    val context = LocalContext.current
    DetailCard {
        Field("Place", geo.label)
        Field("Coordinates", "${geo.latitude}, ${geo.longitude}")
    }
    ActionRow {
        Button(onClick = { Actions.openMap(context, geo) }) { ButtonContent(Icons.Outlined.Map, "Open in maps") }
    }
}

@Composable
private fun EventDetails(event: ScanContent.Event) {
    val context = LocalContext.current
    DetailCard {
        Field("Event", event.summary)
        event.startMillis?.let { Field("Starts", formatEventTime(it, event.allDay)) }
        event.endMillis?.let { Field("Ends", formatEventTime(it, event.allDay)) }
        Field("Location", event.location)
        Field("Details", event.description)
    }
    ActionRow {
        Button(onClick = { Actions.addEvent(context, event) }) { ButtonContent(Icons.Outlined.CalendarMonth, "Add to calendar") }
    }
}

@Composable
private fun ProductDetails(product: ScanContent.Product) {
    val context = LocalContext.current
    DetailCard {
        Field("Product code", product.code)
        Field("Type", product.formatName)
    }
    ActionRow {
        Button(onClick = { Actions.webSearch(context, product.code) }) { ButtonContent(Icons.Outlined.Search, "Search product") }
        OutlinedButton(onClick = { Actions.copy(context, "Code", product.code) }) {
            ButtonContent(Icons.Outlined.ContentCopy, "Copy")
        }
    }
}

@Composable
private fun TextDetails(text: ScanContent.Text) {
    val context = LocalContext.current
    DetailCard {
        SelectionContainer { Text(text.text, style = MaterialTheme.typography.bodyLarge) }
    }
    ActionRow {
        CommonTextActions(context, text.text)
        OutlinedButton(onClick = { Actions.webSearch(context, text.text) }) { ButtonContent(Icons.Outlined.Search, "Search") }
    }
}

@Composable
private fun RawValueCard(raw: String) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Raw content", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Show") }
        IconButton(onClick = { Actions.copy(context, "Scanned content", raw) }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy raw content")
        }
        IconButton(onClick = { Actions.shareText(context, raw) }) {
            Icon(Icons.Outlined.Share, contentDescription = "Share raw content")
        }
    }
    if (expanded) {
        SelectionContainer {
            Text(raw, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CommonTextActions(context: Context, text: String) {
    OutlinedButton(onClick = { Actions.copy(context, "Scanned content", text) }) {
        ButtonContent(Icons.Outlined.ContentCopy, "Copy")
    }
    OutlinedButton(onClick = { Actions.shareText(context, text) }) { ButtonContent(Icons.Outlined.Share, "Share") }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun ButtonContent(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}

private fun currencySymbol(currency: String?): String = when (currency?.uppercase()) {
    null, "", "INR", "356" -> "₹"
    else -> "$currency "
}

private fun formatTimestamp(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun formatEventTime(millis: Long, allDay: Boolean): String {
    val format = if (allDay) DateFormat.getDateInstance(DateFormat.MEDIUM) else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return format.format(Date(millis))
}
