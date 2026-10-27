package com.garza.dreamcatcher.ui.theme

import com.garza.dreamcatcher.ui.theme.Palette.Companion.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every color scheme must keep text readable (WCAG AA: 4.5:1 for text, 3:1 for large text and borders). */
class PaletteTest {

    private fun check(failures: MutableList<String>, palette: Palette, what: String, fg: Int, bg: Int, min: Double) {
        val ratio = contrast(fg, bg)
        if (ratio < min) failures += "${palette.name}: $what is %.2f:1, needs %.1f:1".format(ratio, min)
    }

    @Test
    fun everyPaletteIsReadable() {
        val failures = mutableListOf<String>()
        for (p in Palettes.all) {
            for ((surfaceName, surface) in listOf(
                "background" to p.background,
                "card" to p.surfaceContainerHigh,
                "panel" to p.surfaceContainerHighest,
            )) {
                check(failures, p, "text on $surfaceName", p.text, surface, 4.5)
                check(failures, p, "secondary text on $surfaceName", p.onSurfaceVariant, surface, 4.5)
                check(failures, p, "primary on $surfaceName", p.primary, surface, 4.5)
                check(failures, p, "accent on $surfaceName", p.accent, surface, 4.5)
                check(failures, p, "error on $surfaceName", p.error, surface, 4.5)
            }
            check(failures, p, "outline on background", p.outline, p.background, 3.0)
            check(failures, p, "text on primary", p.onPrimary, p.primary, 4.5)
            check(failures, p, "text on accent", p.onAccent, p.accent, 4.5)
            check(failures, p, "text on primary container", p.onPrimaryContainer, p.primaryContainer, 4.5)
            check(failures, p, "text on accent container", p.onAccentContainer, p.accentContainer, 4.5)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun idsAreUniqueAndUnknownIdsFallBack() {
        assertEquals(Palettes.all.size, Palettes.all.map { it.id }.toSet().size)
        assertEquals(Palettes.default, Palettes.byId("no-such-scheme"))
        assertEquals("ocean", Palettes.byId("ocean").id)
    }
}
