package com.garza.dreamcatcher.ui.theme

import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garza.dreamcatcher.Settings

fun Palette.toColorScheme(): ColorScheme {
    fun c(argb: Int) = Color(argb)
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(primary),
        onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer),
        onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(accent),
        onSecondary = c(onAccent),
        secondaryContainer = c(accentContainer),
        onSecondaryContainer = c(onAccentContainer),
        tertiary = c(primary),
        onTertiary = c(onPrimary),
        background = c(background),
        onBackground = c(text),
        surface = c(background),
        onSurface = c(text),
        surfaceVariant = c(surfaceVariant),
        onSurfaceVariant = c(onSurfaceVariant),
        surfaceContainerLowest = c(background),
        surfaceContainerLow = c(surfaceContainer),
        surfaceContainer = c(surfaceContainer),
        surfaceContainerHigh = c(surfaceContainerHigh),
        surfaceContainerHighest = c(surfaceContainerHighest),
        surfaceBright = c(surfaceContainerHighest),
        surfaceDim = c(background),
        outline = c(outline),
        outlineVariant = c(surfaceVariant),
        inverseSurface = c(text),
        inverseOnSurface = c(background),
        inversePrimary = c(primaryContainer),
        error = c(error),
    )
}

/** Background for the full-screen wake-up surfaces: the scheme's background fading toward its primary color. */
@Composable
fun nightSkyBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        listOf(colors.background, lerp(colors.background, colors.primary, 0.10f), lerp(colors.background, colors.primary, 0.24f))
    )
}

/** Applies the color scheme picked in Settings; changes take effect immediately. */
@Composable
fun DreamcatcherTheme(content: @Composable () -> Unit) {
    val palette by Settings.palette.collectAsStateWithLifecycle()
    val scheme = remember(palette) { palette.toColorScheme() }

    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(activity, palette) {
        activity ?: return@LaunchedEffect
        // Light schemes need dark status bar icons and vice versa, whatever the phone's own theme is.
        val bars = if (palette.isDark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        // Matches what shows behind the keyboard and during transitions.
        activity.window.setBackgroundDrawable(ColorDrawable(palette.background))
    }

    MaterialTheme(colorScheme = scheme) {
        // Text outside a Surface or Scaffold would otherwise fall back to black.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
