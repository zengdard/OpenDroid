package com.opendroid.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palette inspirée d'iOS : neutres, un seul accent bleu, séparateurs fins.
val IosBlueLight = Color(0xFF007AFF)
val IosBlueDark = Color(0xFF0A84FF)
val IosGreen = Color(0xFF34C759)
val IosRedLight = Color(0xFFFF3B30)
val IosRedDark = Color(0xFFFF453A)
val IosOrange = Color(0xFFFF9500)

val LightColors = lightColorScheme(
    primary = IosBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F0FF),
    onPrimaryContainer = Color(0xFF003A75),
    secondary = Color(0xFF5E5CE6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDECFF),
    onSecondaryContainer = Color(0xFF1B1A4B),
    tertiary = IosGreen,
    onTertiary = Color.White,
    error = IosRedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF5A0B06),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
)

val DarkColors = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3A6B),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF7D7AFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2A2A4A),
    onSecondaryContainer = Color(0xFFE4E3FF),
    tertiary = IosGreen,
    onTertiary = Color.Black,
    error = IosRedDark,
    onError = Color.White,
    errorContainer = Color(0xFF4A0D0A),
    onErrorContainer = Color(0xFFFFD9D6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E),
)
