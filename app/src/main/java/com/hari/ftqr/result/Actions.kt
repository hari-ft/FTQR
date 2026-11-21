package com.hari.ftqr.result

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.hari.ftqr.model.ScanContent
import com.hari.ftqr.model.WifiSecurity
import java.math.BigDecimal

data class PaymentApp(val label: String, val packageName: String, val activityName: String, val icon: ImageBitmap)

object Actions {

    fun copy(context: Context, label: String, text: String, sensitive: Boolean = false) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
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
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(content.address)))
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
        contact.website?.takeIf { it.isNotBlank() }?.let { site ->
            val row = ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Website.URL, site)
                put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_WORK)
            }
            intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(row))
        }
        launch(context, intent)
    }

    fun savePhoneAsContact(context: Context, number: String) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number)
        launch(context, intent)
    }

    fun plainNumber(value: Double): String =
        if (value.isFinite()) BigDecimal.valueOf(value).toPlainString() else value.toString()

    fun openMap(context: Context, geo: ScanContent.Geo) {
        val address = geo.addressQuery
        val uri = if (address != null) {
            "geo:0,0?q=" + Uri.encode(address)
        } else {
            val coordinates = plainNumber(geo.latitude) + "," + plainNumber(geo.longitude)
            val label = geo.label?.takeIf { it.isNotBlank() }?.let { "(" + Uri.encode(it) + ")" } ?: ""
            "geo:$coordinates?q=$coordinates$label"
        }
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
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
        val password = wifi.password?.takeIf { it.isNotEmpty() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val suggestion = buildSuggestion(wifi, password)
            if (suggestion != null) {
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                if (launch(context, intent, showError = false)) return
            }
        }
        val message = if (password != null) {
            copy(context, "Wi-Fi password", password, sensitive = true)
            "Password copied. Pick \"${wifi.ssid}\" and paste it."
        } else {
            "Pick \"${wifi.ssid}\" from the list."
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        launch(context, settings)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildSuggestion(wifi: ScanContent.Wifi, password: String?): WifiNetworkSuggestion? {
        val needsPassword = when (wifi.security) {
            WifiSecurity.OPEN -> false
            WifiSecurity.WEP -> return null
            WifiSecurity.WPA, WifiSecurity.SAE -> true
        }
        if (needsPassword && password == null) return null
        return runCatching {
            val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
            if (password != null) {
                when (wifi.security) {
                    WifiSecurity.WPA -> builder.setWpa2Passphrase(password)
                    WifiSecurity.SAE -> builder.setWpa3Passphrase(password)
                    WifiSecurity.OPEN, WifiSecurity.WEP -> builder
                }
            }
            builder.build()
        }.getOrNull()
    }

    fun paymentApps(context: Context, upiUri: String, iconSizePx: Int): List<PaymentApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val size = iconSizePx.coerceAtLeast(1)
        return matches
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                PaymentApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    icon = it.loadIcon(pm).toBitmap(size, size).asImageBitmap(),
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
    } catch (e: RuntimeException) {
        if (showError) Toast.makeText(context, "This can't be opened", Toast.LENGTH_SHORT).show()
        false
    }
}
