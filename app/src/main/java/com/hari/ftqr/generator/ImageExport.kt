package com.hari.ftqr.generator

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageExport {

    private const val SHARED_DIR = "shared"
    private const val ALBUM = "FTQR"
    private const val MIME_PNG = "image/png"
    private const val STALE_SHARE_AGE_MS = 60L * 60L * 1000L

    suspend fun writeShareImage(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARED_DIR)
        if (!dir.exists()) dir.mkdirs()
        val now = System.currentTimeMillis()
        dir.listFiles()
            ?.filter { it.name.startsWith("ftqr_") && now - it.lastModified() > STALE_SHARE_AGE_MS }
            ?.forEach { it.delete() }
        val file = File(dir, "ftqr_$now.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    suspend fun shareImage(context: Context, bitmap: Bitmap, chooserTitle: String = "Share QR code"): Boolean {
        val uri = runCatching { writeShareImage(context, bitmap) }.getOrNull() ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("QR code", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val displayName = "ftqr_${System.currentTimeMillis()}.png"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(context, bitmap, displayName)
            } else {
                saveLegacy(context, bitmap, displayName)
            }
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_PNG)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + ALBUM)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            val written = resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: false
            if (!written) {
                resolver.delete(uri, null, null)
                null
            } else {
                val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, publish, null, null)
                uri
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, displayName)
        val written = FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!written) {
            file.delete()
            return null
        }
        val nowSeconds = System.currentTimeMillis() / 1000L
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_PNG)
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DATE_ADDED, nowSeconds)
            put(MediaStore.MediaColumns.DATE_MODIFIED, nowSeconds)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(file)
    }
}
