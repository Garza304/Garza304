package com.garza.dreamcatcher.ui.theme

import kotlin.math.pow

/**
 * A color scheme the user can pick. Each is defined by four base colors; every other role is
 * derived from them so all schemes stay consistent. Colors are ARGB ints so this file has no
 * Android dependencies and contrast can be unit tested.
 */
class Palette(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Int,
    /** Buttons, links, highlights; also used as text, so it must read well on [background]. */
    val primary: Int,
    /** A second highlight for tags, the alarm clock and the active mic. */
    val accent: Int,
    val text: Int,
    /** Chart color for nights with a dream. Charts never use [primary]/[accent]: those are too alike to tell apart in a chart. */
    val chartDream: Int,
    /** Chart color for nightmares; must stay distinct from [chartDream] under color blindness. */
    val chartNightmare: Int,
    /** How far [chartNone] is mixed from [text] toward [background]. */
    chartNoneMix: Float,
) {
    private val surfaceMix = if (isDark) floatArrayOf(0.07f, 0.11f, 0.15f, 0.20f) else floatArrayOf(0.05f, 0.08f, 0.11f, 0.15f)

    val surfaceContainer = blend(background, primary, surfaceMix[0])
    val surfaceContainerHigh = blend(background, primary, surfaceMix[1])
    val surfaceContainerHighest = blend(background, primary, surfaceMix[2])
    val surfaceVariant = blend(background, primary, surfaceMix[3])

    val onSurfaceVariant = blend(text, background, 0.22f)
    val outline = blend(text, background, 0.45f)

    val onPrimary = bestOn(primary)
    val primaryContainer = blend(background, primary, if (isDark) 0.30f else 0.16f)
    val onPrimaryContainer = readableOn(primaryContainer, text)

    val onAccent = bestOn(accent)
    val accentContainer = blend(background, accent, if (isDark) 0.22f else 0.14f)
    val onAccentContainer = readableOn(accentContainer, accent)

    /** Recessive gray for nights with nothing remembered. */
    val chartNone = blend(text, background, chartNoneMix)

    val error = if (isDark) 0xFFFFB4AB.toInt() else 0xFFBA1A1A.toInt()

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** Mixes [to] into [from] by [amount] (0 = all [from], 1 = all [to]). */
        fun blend(from: Int, to: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val a = (from shr shift) and 0xFF
                val b = (to shr shift) and 0xFF
                return (a + (b - a) * amount).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        /** WCAG relative luminance. */
        fun luminance(color: Int): Double {
            fun linear(shift: Int): Double {
                val c = ((color shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
        }

        /** WCAG contrast ratio between two colors, from 1 to 21. */
        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** Near-black or white, whichever reads better on [background]. */
        private fun bestOn(background: Int): Int {
            val dark = 0xFF14121C.toInt()
            return if (contrast(dark, background) >= contrast(WHITE, background)) dark else WHITE
        }

        /** [preferred] if it reads well on [background], otherwise black or white. */
        private fun readableOn(background: Int, preferred: Int): Int =
            if (contrast(preferred, background) >= 4.5) {
                preferred
            } else if (contrast(BLACK, background) >= contrast(WHITE, background)) {
                BLACK
            } else {
                WHITE
            }
    }
}

object Palettes {
    private fun c(hex: Long) = hex.toInt()

    /**
     * Chart hues, one step per hue for light and one for dark backgrounds. Each palette below
     * pairs two of them that pass the colorblind checks in ChartColorsTest against its surfaces.
     */
    private object Light {
        val blue = c(0xFF2A78D6); val orange = c(0xFFEB6834); val green = c(0xFF008300)
        val violet = c(0xFF6250D6); val red = c(0xFFE34948)
    }

    private object Dark {
        val blue = c(0xFF3987E5); val orange = c(0xFFD95926); val aqua = c(0xFF199E70)
        val yellow = c(0xFFC98500); val magenta = c(0xFFD55181); val violet = c(0xFF9085E9)
    }

    val all: List<Palette> = listOf(
        // Dark
        Palette("midnight", "Midnight", true, c(0xFF0F1028), c(0xFFB9A7FF), c(0xFFF3E9C6), c(0xFFE5E2F5), Dark.violet, Dark.yellow, 0.55f),
        Palette("black", "True black", true, c(0xFF000000), c(0xFFB9A7FF), c(0xFFF3E9C6), c(0xFFE8E6F0), Dark.violet, Dark.yellow, 0.45f),
        Palette("ocean", "Deep ocean", true, c(0xFF06141B), c(0xFF6FD3E0), c(0xFF9FE8C9), c(0xFFDDEFF2), Dark.blue, Dark.yellow, 0.40f),
        Palette("aurora", "Aurora", true, c(0xFF0B1712), c(0xFF7EE3A8), c(0xFFC6A7FF), c(0xFFE0F2E8), Dark.blue, Dark.orange, 0.40f),
        Palette("forest", "Forest", true, c(0xFF0F1A12), c(0xFFA8D58A), c(0xFFE8D9A0), c(0xFFE6EEDF), Dark.blue, Dark.yellow, 0.50f),
        Palette("nebula", "Nebula", true, c(0xFF140B24), c(0xFFE18CFF), c(0xFF7FD6FF), c(0xFFEEE3F7), Dark.violet, Dark.aqua, 0.55f),
        Palette("rose", "Rose night", true, c(0xFF1A0E16), c(0xFFF4A3C8), c(0xFFFFD5A8), c(0xFFF5E4EE), Dark.violet, Dark.orange, 0.50f),
        Palette("ember", "Ember", true, c(0xFF1A0F0C), c(0xFFFF9E6B), c(0xFFFFD27A), c(0xFFF6E6DD), Dark.orange, Dark.violet, 0.40f),
        Palette("amber", "Amber glow", true, c(0xFF060504), c(0xFFFFB547), c(0xFFFFD08A), c(0xFFF5DDB5), Dark.yellow, Dark.violet, 0.50f),
        // Warm hues only, so charts don't break the point of a red night-light scheme.
        Palette("redlight", "Red light", true, c(0xFF0A0000), c(0xFFFF6B5E), c(0xFFFF9A7A), c(0xFFFFB8AC), Dark.magenta, Dark.yellow, 0.55f),
        Palette("graphite", "Graphite", true, c(0xFF141414), c(0xFF9ECBFF), c(0xFFE6E6E6), c(0xFFE8E8E8), Dark.blue, Dark.orange, 0.40f),
        // Light
        Palette("dawn", "Dawn", false, c(0xFFFFF7F0), c(0xFF9A3E1D), c(0xFF6B4BA6), c(0xFF2B1D17), Light.red, Light.violet, 0.45f),
        Palette("lavender", "Lavender mist", false, c(0xFFF6F3FF), c(0xFF5B45B0), c(0xFF8A4B00), c(0xFF1E1A33), Light.violet, Light.red, 0.40f),
        Palette("sky", "Clear sky", false, c(0xFFF2F8FC), c(0xFF00658A), c(0xFF5B4BC4), c(0xFF0E1E26), Light.blue, Light.red, 0.50f),
        Palette("mint", "Mint", false, c(0xFFF0FAF5), c(0xFF1F6E4A), c(0xFF8A3B6E), c(0xFF13261C), Light.green, Light.violet, 0.40f),
        Palette("paper", "Paper", false, c(0xFFF5EEDC), c(0xFF6B4F2A), c(0xFF2E5E4E), c(0xFF2A241B), Light.green, Light.blue, 0.40f),
        Palette("blush", "Blush", false, c(0xFFFFF4F6), c(0xFFA3285A), c(0xFF5A4FB0), c(0xFF2B1620), Light.red, Light.violet, 0.45f),
    )

    val default: Palette = all.first()

    fun byId(id: String?): Palette = all.firstOrNull { it.id == id } ?: default
}
