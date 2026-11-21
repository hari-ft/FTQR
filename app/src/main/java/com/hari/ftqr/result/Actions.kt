package com.hari.ftqr.result

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.hari.ftqr.model.ScanContent
import com.hari.ftqr.model.WifiSecurity

data class PaymentApp(val label: String, val packageName: String, val activityName: String, val icon: ImageBitmap)

object Actions {

    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(context, Intent.createChooser(send, "Share"))
    }

    fun openUri(context: Context, uri: String) = launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    fun webSearch(context: Context, query: String) =
        openUri(context, "https://www.google.com/search?q=" + Uri.encode(query))

    fun dial(context: Context, number: String) =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun sms(context: Context, number: String, message: String?) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
        if (!message.isNullOrBlank()) intent.putExtra("sms_body", message)
        launch(context, intent)
    }

    fun email(context: Context, content: ScanContent.Email) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(content.address))
        content.subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        content.body?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        launch(context, intent)
    }

    fun saveContact(context: Context, contact: ScanContent.Contact) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
        contact.name?.let { intent.putExtra(ContactsContract.Intents.Insert.NAME, it) }
        contact.organization?.let { intent.putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        contact.title?.let { intent.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        contact.address?.let { intent.putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
        contact.note?.let { intent.putExtra(ContactsContract.Intents.Insert.NOTES, it) }
        val phoneKeys = listOf(
            ContactsContract.Intents.Insert.PHONE,
            ContactsContract.Intents.Insert.SECONDARY_PHONE,
            ContactsContract.Intents.Insert.TERTIARY_PHONE,
        )
        contact.phones.take(phoneKeys.size).forEachIndexed { index, phone -> intent.putExtra(phoneKeys[index], phone) }
        val emailKeys = listOf(
            ContactsContract.Intents.Insert.EMAIL,
            ContactsContract.Intents.Insert.SECONDARY_EMAIL,
            ContactsContract.Intents.Insert.TERTIARY_EMAIL,
        )
        contact.emails.take(emailKeys.size).forEachIndexed { index, email -> intent.putExtra(emailKeys[index], email) }
        launch(context, intent)
    }

    fun savePhoneAsContact(context: Context, number: String) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
        launch(context, intent)
    }

    fun openMap(context: Context, geo: ScanContent.Geo) {
        val coordinates = "${geo.latitude},${geo.longitude}"
        val label = geo.label?.takeIf { it.isNotBlank() }?.let { "(" + Uri.encode(it) + ")" } ?: ""
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=$coordinates$label")))
    }

    fun addEvent(context: Context, event: ScanContent.Event) {
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        event.summary?.let { intent.putExtra(CalendarContract.Events.TITLE, it) }
        event.location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        event.description?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        event.startMillis?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        event.endMillis?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        if (event.allDay) intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        launch(context, intent)
    }

    fun connectWifi(context: Context, wifi: ScanContent.Wifi) {
        val canSuggest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.security != WifiSecurity.WEP
        if (canSuggest) {
            val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
            if (wifi.security == WifiSecurity.WPA && !wifi.password.isNullOrEmpty()) {
                builder.setWpa2Passphrase(wifi.password)
            }
            val suggestion = runCatching { builder.build() }.getOrNull()
            if (suggestion != null) {
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                if (launch(context, intent, showError = false)) return
            }
        }
        if (!wifi.password.isNullOrEmpty()) copy(context, "Wi-Fi password", wifi.password)
        Toast.makeText(context, "Password copied. Pick \"${wifi.ssid}\" and paste it.", Toast.LENGTH_LONG).show()
        val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        launch(context, settings)
    }

    fun paymentApps(context: Context, upiUri: String): List<PaymentApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return matches
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                PaymentApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun payWith(context: Context, upiUri: String, app: PaymentApp) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)).setClassName(app.packageName, app.activityName)
        launch(context, intent)
    }

    fun payWithChooser(context: Context, upiUri: String) {
        launch(context, Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)), "Pay with"))
    }

    private fun launch(context: Context, intent: Intent, showError: Boolean = true): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (showError) Toast.makeText(context, "No app found to handle this", Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        if (showError) Toast.makeText(context, "This action is not allowed", Toast.LENGTH_SHORT).show()
        false
    }
}
