package com.hari.ftqr.scanner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

class ScanHaptics(
    private val context: Context,
    private val fallback: HapticFeedback,
) {
    fun perform() {
        val vibrator = findVibrator()
        if (vibrator == null || !vibrator.hasVibrator()) {
            fallback.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(SCAN_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (denied: SecurityException) {
            fallback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun findVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    private companion object {
        const val SCAN_VIBRATION_MS = 40L
    }
}

@Composable
fun rememberScanHaptics(): ScanHaptics {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    return remember(context, hapticFeedback) {
        ScanHaptics(context.applicationContext, hapticFeedback)
    }
}
