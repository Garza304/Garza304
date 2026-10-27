package com.garza.dreamcatcher.ui.theme

import com.garza.dreamcatcher.ui.theme.Palette.Companion.contrast
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Chart colors must stay tellable apart for everyone, in every scheme. Distances are OKLab ΔE×100;
 * color blindness is simulated with Machado, Oliveira & Fernandes (2009) at full severity.
 * Floors: 8 under protanopia/deuteranopia, 15 with normal vision, 3:1 against the chart surface.
 */
class ChartColorsTest {

    private val protan = arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998),
    )
    private val deutan = arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047413),
        doubleArrayOf(-0.011820, 0.042940, 0.968881),
    )

    private fun linear(color: Int): DoubleArray = intArrayOf(16, 8, 0).map { shift ->
        val c = ((color shr shift) and 0xFF) / 255.0
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }.toDoubleArray()

    private fun simulate(rgb: DoubleArray, m: Array<DoubleArray>) = DoubleArray(3) { i ->
        (m[i][0] * rgb[0] + m[i][1] * rgb[1] + m[i][2] * rgb[2]).coerceIn(0.0, 1.0)
    }

    private fun oklab(rgb: DoubleArray): DoubleArray {
        val (r, g, b) = rgb.toList()
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun deltaE(a: Int, b: Int, cvd: Array<DoubleArray>? = null): Double {
        val la = oklab(cvd?.let { simulate(linear(a), it) } ?: linear(a))
        val lb = oklab(cvd?.let { simulate(linear(b), it) } ?: linear(b))
        return 100 * hypot(hypot(la[0] - lb[0], la[1] - lb[1]), la[2] - lb[2])
    }

    @Test
    fun chartColorsAreDistinctInEveryPalette() {
        val failures = mutableListOf<String>()
        for (p in Palettes.all) {
            val series = listOf("dream" to p.chartDream, "nightmare" to p.chartNightmare, "no dream" to p.chartNone)
            for (i in series.indices) for (j in i + 1 until series.size) {
                val (na, a) = series[i]
                val (nb, b) = series[j]
                val normal = deltaE(a, b)
                val cvd = minOf(deltaE(a, b, protan), deltaE(a, b, deutan))
                if (normal < 15) failures += "${p.name}: $na vs $nb ΔE %.1f with normal vision, needs 15".format(normal)
                if (cvd < 8) failures += "${p.name}: $na vs $nb ΔE %.1f with color blindness, needs 8".format(cvd)
            }
            for (surface in listOf(p.background, p.surfaceContainer, p.surfaceContainerHigh, p.surfaceContainerHighest)) {
                for ((name, color) in series.take(2)) {
                    val ratio = contrast(color, surface)
                    if (ratio < 3.0) failures += "${p.name}: $name is %.2f:1 on a surface, needs 3:1".format(ratio)
                }
                // The gray is meant to recede; it always comes with a label, legend or table, so 2.5:1 is enough.
                val none = contrast(p.chartNone, surface)
                if (none < 2.5) failures += "${p.name}: no-dream gray is %.2f:1 on a surface, needs 2.5:1".format(none)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
