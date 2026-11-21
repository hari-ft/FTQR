package com.hari.ftqr.generator

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrEncoder {

    const val EXPORT_SIZE_PX = 1024
    private const val QUIET_ZONE_MODULES = 4

    fun encode(
        content: String,
        sizePx: Int = EXPORT_SIZE_PX,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
    ): Bitmap = toBitmap(encodeMatrix(content, sizePx), foreground, background)

    fun encodePreview(
        content: String,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
    ): Bitmap = toBitmap(encodeMatrix(content, 0), foreground, background)

    fun encodeMatrix(content: String, sizePx: Int = 0): BitMatrix =
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hintsFor(content))

    private fun hintsFor(content: String): Map<EncodeHintType, Any> = buildMap {
        if (content.any { it.code > 0x7F }) put(EncodeHintType.CHARACTER_SET, "UTF-8")
        put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
        put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES)
    }

    private fun toBitmap(matrix: BitMatrix, foreground: Int, background: Int): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) foreground else background
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
