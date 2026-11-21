package com.hari.ftqr.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6C3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA3F5B4),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF4F6352),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFE6A8),
    onTertiaryContainer = Color(0xFF261A00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D89A),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA3F5B4),
    secondary = Color(0xFFB6CCB7),
    tertiary = Color(0xFFF4C04A),
    tertiaryContainer = Color(0xFF4A3800),
    onTertiaryContainer = Color(0xFFFFE6A8),
)

@Composable
fun FtqrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
