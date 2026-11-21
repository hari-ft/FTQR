package com.hari.ftqr.scanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface GalleryScanResult {
    data class Found(val rawValue: String, val format: Int) : GalleryScanResult
    data object NotFound : GalleryScanResult
    data class Failed(val error: Throwable) : GalleryScanResult
}

suspend fun decodeBarcodeFromImage(
    context: Context,
    uri: Uri,
    scanner: BarcodeScanner,
): GalleryScanResult = try {
    val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
    val barcode = scanner.awaitBarcodes(image).firstOrNull { !it.rawValue.isNullOrEmpty() }
    val rawValue = barcode?.rawValue
    if (barcode != null && rawValue != null) {
        GalleryScanResult.Found(rawValue, barcode.format)
    } else {
        GalleryScanResult.NotFound
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    GalleryScanResult.Failed(error)
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
                if (continuation.isActive) continuation.cancel()
            }
    }
