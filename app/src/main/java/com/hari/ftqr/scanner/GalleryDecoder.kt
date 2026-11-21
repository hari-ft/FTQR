package com.hari.ftqr.scanner

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface GalleryScanResult {
    data class Found(val rawValue: String, val format: Int) : GalleryScanResult
    data object NotFound : GalleryScanResult
    data class Failed(
        val error: Throwable,
        val message: String = DEFAULT_FAILURE_MESSAGE,
    ) : GalleryScanResult
}

private const val DEFAULT_FAILURE_MESSAGE = "Couldn't read that image"
private const val UNSUPPORTED_FORMAT_MESSAGE = "This image format isn't supported on this Android version"
private const val TOO_LARGE_MESSAGE = "That image is too large to read"
private const val MAX_DECODE_EDGE = 2048

private class UnsupportedImageFormatException : IOException(UNSUPPORTED_FORMAT_MESSAGE)

private class ScanTaskCancelledException : IllegalStateException("Barcode scan was cancelled")

suspend fun decodeBarcodeFromImage(
    context: Context,
    uri: Uri,
    scanner: BarcodeScanner,
): GalleryScanResult = try {
    val resolver = context.contentResolver
    val bitmap = withContext(Dispatchers.IO) { decodeDownsampledBitmap(resolver, uri) }
    val barcode = scanner.awaitBarcodes(InputImage.fromBitmap(bitmap, 0))
        .firstOrNull { !it.rawValue.isNullOrEmpty() }
    val rawValue = barcode?.rawValue
    if (barcode != null && rawValue != null) {
        GalleryScanResult.Found(rawValue, barcode.format)
    } else {
        GalleryScanResult.NotFound
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (unsupported: UnsupportedImageFormatException) {
    GalleryScanResult.Failed(unsupported, UNSUPPORTED_FORMAT_MESSAGE)
} catch (outOfMemory: OutOfMemoryError) {
    GalleryScanResult.Failed(outOfMemory, TOO_LARGE_MESSAGE)
} catch (error: Exception) {
    GalleryScanResult.Failed(error)
}

private fun decodeDownsampledBitmap(resolver: ContentResolver, uri: Uri): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(resolver, uri)
    } else {
        decodeWithBitmapFactory(resolver, uri)
    }

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoder(resolver: ContentResolver, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(resolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longest = max(width, height)
        if (longest > MAX_DECODE_EDGE) {
            val scale = MAX_DECODE_EDGE.toFloat() / longest
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}

private fun decodeWithBitmapFactory(resolver: ContentResolver, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnsupportedImageFormatException()

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_DECODE_EDGE) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = resolver.openStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw UnsupportedImageFormatException()

    val orientation = try {
        resolver.openStream(uri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    } catch (unreadable: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }
    return decoded.applyExifOrientation(orientation)
}

private fun ContentResolver.openStream(uri: Uri) =
    openInputStream(uri) ?: throw FileNotFoundException("Couldn't open $uri")

private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return this
    }
    val oriented = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (oriented !== this) recycle()
    return oriented
}

private suspend fun BarcodeScanner.awaitBarcodes(image: InputImage): List<Barcode> =
    suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { barcodes ->
                if (continuation.isActive) continuation.resume(barcodes.orEmpty())
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resumeWithException(ScanTaskCancelledException())
            }
    }
