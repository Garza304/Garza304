package com.garza.dreamcatcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Night = Color(0xFF0F1028)
val Dusk = Color(0xFF1C1B45)
val Moon = Color(0xFFF3E9C6)
val Lavender = Color(0xFFB9A7FF)

private val colors = darkColorScheme(
    primary = Lavender,
    onPrimary = Color(0xFF231A4F),
    primaryContainer = Color(0xFF3A2F7A),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Moon,
    onSecondary = Color(0xFF3A3220),
    secondaryContainer = Color(0xFF4A4330),
    onSecondaryContainer = Moon,
    tertiary = Color(0xFF8FD3E8),
    background = Night,
    onBackground = Color(0xFFE5E2F5),
    surface = Night,
    onSurface = Color(0xFFE5E2F5),
    surfaceVariant = Color(0xFF2A2950),
    onSurfaceVariant = Color(0xFFC7C4DC),
    surfaceContainer = Color(0xFF191A3A),
    surfaceContainerHigh = Color(0xFF222247),
    surfaceContainerHighest = Color(0xFF2B2B52),
    outline = Color(0xFF8E8BA8),
)

/** Background gradient used on the full-screen wake-up surfaces. */
val NightSky = Brush.verticalGradient(listOf(Night, Dusk, Color(0xFF3B2E6E)))

/** Dreamcatcher is always dark: it is mostly used at night and first thing in the morning. */
@Composable
fun DreamcatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
