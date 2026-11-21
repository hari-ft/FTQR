package com.hari.ftqr.generator

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

enum class QrContentType(val label: String, val icon: ImageVector) {
    TEXT("Text", Icons.Filled.TextFields),
    LINK("Link", Icons.Filled.Link),
    WIFI("Wi-Fi", Icons.Filled.Wifi),
    CONTACT("Contact", Icons.Filled.Person),
    PHONE("Phone", Icons.Filled.Phone),
    EMAIL("Email", Icons.Filled.Email),
    SMS("SMS", Icons.Filled.Sms),
    UPI("UPI payment", Icons.Filled.CurrencyRupee),
}

private data class PayloadResult(val payload: String, val error: String? = null)

private data class QrPreview(val payload: String, val bitmap: Bitmap)

private val wifiSecurityOptions = listOf(
    QrPayloads.SECURITY_WPA to "WPA/WPA2",
    QrPayloads.SECURITY_WEP to "WEP",
    QrPayloads.SECURITY_NONE to "None",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var type by rememberSaveable { mutableStateOf(QrContentType.TEXT) }

    var text by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }

    var wifiSsid by rememberSaveable { mutableStateOf("") }
    var wifiPassword by rememberSaveable { mutableStateOf("") }
    var wifiSecurity by rememberSaveable { mutableStateOf(QrPayloads.SECURITY_WPA) }
    var wifiHidden by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    var contactName by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("") }
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var contactOrg by rememberSaveable { mutableStateOf("") }
    var contactWebsite by rememberSaveable { mutableStateOf("") }

    var phone by rememberSaveable { mutableStateOf("") }

    var emailTo by rememberSaveable { mutableStateOf("") }
    var emailSubject by rememberSaveable { mutableStateOf("") }
    var emailBody by rememberSaveable { mutableStateOf("") }

    var smsNumber by rememberSaveable { mutableStateOf("") }
    var smsMessage by rememberSaveable { mutableStateOf("") }

    var upiVpa by rememberSaveable { mutableStateOf("") }
    var upiName by rememberSaveable { mutableStateOf("") }
    var upiAmount by rememberSaveable { mutableStateOf("") }
    var upiNote by rememberSaveable { mutableStateOf("") }

    val result = when (type) {
        QrContentType.TEXT -> PayloadResult(if (text.isBlank()) "" else text)
        QrContentType.LINK -> when {
            link.isBlank() -> PayloadResult("", "Enter a link")
            link.trim().any { it.isWhitespace() } -> PayloadResult("", "A link can't contain spaces")
            else -> PayloadResult(QrPayloads.url(link))
        }
        QrContentType.WIFI -> when {
            wifiSsid.isBlank() -> PayloadResult("")
            wifiSecurity != QrPayloads.SECURITY_NONE && wifiPassword.isEmpty() ->
                PayloadResult("", "Enter the network password")
            wifiSecurity == QrPayloads.SECURITY_WPA && wifiPassword.length < 8 ->
                PayloadResult("", "WPA passwords need at least 8 characters")
            else -> PayloadResult(QrPayloads.wifi(wifiSsid, wifiPassword, wifiSecurity, wifiHidden))
        }
        QrContentType.CONTACT -> when {
            listOf(contactName, contactPhone, contactEmail, contactOrg, contactWebsite).all { it.isBlank() } ->
                PayloadResult("")
            contactEmail.isNotBlank() && '@' !in contactEmail -> PayloadResult("", "Email address must contain '@'")
            else -> PayloadResult(
                QrPayloads.contact(contactName, contactPhone, contactEmail, contactOrg, contactWebsite)
            )
        }
        QrContentType.PHONE -> when {
            phone.none { it.isDigit() } -> PayloadResult("")
            else -> PayloadResult(QrPayloads.phone(phone))
        }
        QrContentType.EMAIL -> when {
            emailTo.isBlank() -> PayloadResult("")
            '@' !in emailTo -> PayloadResult("", "Email address must contain '@'")
            else -> PayloadResult(QrPayloads.email(emailTo, emailSubject, emailBody))
        }
        QrContentType.SMS -> when {
            smsNumber.none { it.isDigit() } -> PayloadResult("")
            else -> PayloadResult(QrPayloads.sms(smsNumber, smsMessage))
        }
        QrContentType.UPI -> {
            val amountValue = upiAmount.trim().toBigDecimalOrNull()
            when {
                upiVpa.isBlank() -> PayloadResult("")
                '@' !in upiVpa -> PayloadResult("", "UPI ID must look like name@bank")
                upiAmount.isNotBlank() && (amountValue == null || amountValue <= BigDecimal.ZERO) ->
                    PayloadResult("", "Enter a valid amount")
                else -> PayloadResult(QrPayloads.upi(upiVpa, upiName, upiAmount, upiNote))
            }
        }
    }
    val payload = result.payload

    var preview by remember { mutableStateOf<QrPreview?>(null) }
    var encodeFailed by remember { mutableStateOf(false) }

    LaunchedEffect(payload) {
        if (payload.isBlank()) {
            preview = null
            encodeFailed = false
            return@LaunchedEffect
        }
        delay(200)
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { QrEncoder.encode(payload) }.getOrNull()
        }
        preview = bitmap?.let { QrPreview(payload, it) }
        encodeFailed = bitmap == null
    }

    val current = preview?.takeIf { it.payload == payload && payload.isNotBlank() }
    val isGenerating = payload.isNotBlank() && current == null && !encodeFailed

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun saveCurrent() {
        val target = current ?: return
        scope.launch {
            val uri = ImageExport.saveToGallery(context, target.bitmap)
            showMessage(if (uri != null) "Saved to Pictures/FTQR" else "Couldn't save the image")
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveCurrent() else showMessage("Storage permission is needed to save images")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create QR") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QrContentType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(option.label) },
                        leadingIcon = {
                            Icon(
                                option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }

            when (type) {
                QrContentType.TEXT -> {
                    FormField(
                        value = text,
                        onValueChange = { text = it },
                        label = "Text",
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                        singleLine = false,
                        minLines = 4,
                    )
                }
                QrContentType.LINK -> {
                    FormField(
                        value = link,
                        onValueChange = { link = it },
                        label = "Website link",
                        placeholder = "example.com",
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    )
                }
                QrContentType.WIFI -> {
                    FormField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = "Network name (SSID)",
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        wifiSecurityOptions.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = wifiSecurity == value,
                                onClick = { wifiSecurity = value },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = wifiSecurityOptions.size),
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                    if (wifiSecurity != QrPayloads.SECURITY_NONE) {
                        FormField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = "Password",
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    )
                                }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Hidden network",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(checked = wifiHidden, onCheckedChange = { wifiHidden = it })
                    }
                }
                QrContentType.CONTACT -> {
                    FormField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = "Full name",
                        capitalization = KeyboardCapitalization.Words,
                    )
                    FormField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = "Phone",
                        keyboardType = KeyboardType.Phone,
                    )
                    FormField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        label = "Email",
                        keyboardType = KeyboardType.Email,
                    )
                    FormField(
                        value = contactOrg,
                        onValueChange = { contactOrg = it },
                        label = "Organization",
                        capitalization = KeyboardCapitalization.Words,
                    )
                    FormField(
                        value = contactWebsite,
                        onValueChange = { contactWebsite = it },
                        label = "Website",
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    )
                }
                QrContentType.PHONE -> {
                    FormField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone number",
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    )
                }
                QrContentType.EMAIL -> {
                    FormField(
                        value = emailTo,
                        onValueChange = { emailTo = it },
                        label = "To",
                        keyboardType = KeyboardType.Email,
                    )
                    FormField(
                        value = emailSubject,
                        onValueChange = { emailSubject = it },
                        label = "Subject",
                        capitalization = KeyboardCapitalization.Sentences,
                    )
                    FormField(
                        value = emailBody,
                        onValueChange = { emailBody = it },
                        label = "Message",
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                        singleLine = false,
                        minLines = 3,
                    )
                }
                QrContentType.SMS -> {
                    FormField(
                        value = smsNumber,
                        onValueChange = { smsNumber = it },
                        label = "Phone number",
                        keyboardType = KeyboardType.Phone,
                    )
                    FormField(
                        value = smsMessage,
                        onValueChange = { smsMessage = it },
                        label = "Message",
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                        singleLine = false,
                        minLines = 3,
                    )
                }
                QrContentType.UPI -> {
                    FormField(
                        value = upiVpa,
                        onValueChange = { upiVpa = it },
                        label = "UPI ID",
                        placeholder = "name@bank",
                        keyboardType = KeyboardType.Email,
                    )
                    FormField(
                        value = upiName,
                        onValueChange = { upiName = it },
                        label = "Payee name",
                        capitalization = KeyboardCapitalization.Words,
                    )
                    FormField(
                        value = upiAmount,
                        onValueChange = { value -> upiAmount = value.filter { it.isDigit() || it == '.' } },
                        label = "Amount (INR, optional)",
                        keyboardType = KeyboardType.Decimal,
                    )
                    FormField(
                        value = upiNote,
                        onValueChange = { upiNote = it },
                        label = "Note (optional)",
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    )
                }
            }

            val visibleError = result.error?.takeIf { type != QrContentType.LINK || link.isNotEmpty() }
            if (visibleError != null) {
                Text(
                    visibleError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.size(4.dp))

            Card(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(280.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = current ?: preview
                    when {
                        payload.isBlank() -> Text(
                            "Fill in the details to see your QR code",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                        encodeFailed -> Text(
                            "This content is too long for a QR code",
                            color = Color(0xFFB3261E),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                        shown != null -> {
                            val imageBitmap = remember(shown.bitmap) { shown.bitmap.asImageBitmap() }
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "QR code preview",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        isGenerating -> CircularProgressIndicator(color = Color.Black)
                    }
                }
            }

            val ready = current != null

            Button(
                onClick = {
                    val target = current ?: return@Button
                    scope.launch {
                        if (!ImageExport.shareImage(context, target.bitmap)) {
                            showMessage("Couldn't share the image")
                        }
                    }
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Share")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            saveCurrent()
                        }
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save to gallery", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        val target = current ?: return@OutlinedButton
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (clipboard == null) {
                            showMessage("Clipboard unavailable")
                        } else {
                            clipboard.setPrimaryClip(ClipData.newPlainText("QR content", target.payload))
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                showMessage("Copied to clipboard")
                            }
                        }
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copy text", maxLines = 1)
                }
            }

            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
    )
}
