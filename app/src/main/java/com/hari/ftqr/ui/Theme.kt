package com.hari.ftqr.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6C3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA3F5B4),
    onPrimaryContainer = Color(0xFF00210C),
    inversePrimary = Color(0xFF88D89A),
    secondary = Color(0xFF4F6352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E8D3),
    onSecondaryContainer = Color(0xFF0D1F12),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE6A8),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF6FBF3),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF3),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceTint = Color(0xFF1B6C3A),
    inverseSurface = Color(0xFF2D322D),
    inverseOnSurface = Color(0xFFEEF2EA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FBF3),
    surfaceContainer = Color(0xFFEAEFE7),
    surfaceContainerHigh = Color(0xFFE5E9E2),
    surfaceContainerHighest = Color(0xFFDFE4DC),
    surfaceContainerLow = Color(0xFFF0F5ED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD7DBD4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D89A),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFA3F5B4),
    inversePrimary = Color(0xFF1B6C3A),
    secondary = Color(0xFFB6CCB7),
    onSecondary = Color(0xFF223526),
    secondaryContainer = Color(0xFF384B3C),
    onSecondaryContainer = Color(0xFFD2E8D3),
    tertiary = Color(0xFFF4C04A),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF4A3800),
    onTertiaryContainer = Color(0xFFFFE6A8),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceTint = Color(0xFF88D89A),
    inverseSurface = Color(0xFFDFE4DC),
    inverseOnSurface = Color(0xFF2D322D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A35),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceDim = Color(0xFF101510),
)

@Composable
fun FtqrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
