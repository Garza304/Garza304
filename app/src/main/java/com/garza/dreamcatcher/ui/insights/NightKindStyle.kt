package com.garza.dreamcatcher.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.ui.charts.ChartSeries
import com.garza.dreamcatcher.ui.charts.Marker
import com.garza.dreamcatcher.ui.theme.LocalPalette

val NightKind.label: String
    get() = when (this) {
        NightKind.DREAM -> "Dream"
        NightKind.NIGHTMARE -> "Nightmare"
        NightKind.NO_DREAM -> "No dream"
    }

fun NightKind.count(n: Int): String = when (this) {
    NightKind.DREAM -> if (n == 1) "1 dream" else "$n dreams"
    NightKind.NIGHTMARE -> if (n == 1) "1 nightmare" else "$n nightmares"
    NightKind.NO_DREAM -> "$n with no dream"
}

/** Each kind has its own shape as well as color, so the calendar never relies on color alone. */
val NightKind.marker: Marker
    get() = when (this) {
        NightKind.DREAM -> Marker.DOT
        NightKind.NIGHTMARE -> Marker.DIAMOND
        NightKind.NO_DREAM -> Marker.RING
    }

@Composable
@ReadOnlyComposable
fun NightKind.color(): Color {
    val palette = LocalPalette.current
    return Color(
        when (this) {
            NightKind.DREAM -> palette.chartDream
            NightKind.NIGHTMARE -> palette.chartNightmare
            NightKind.NO_DREAM -> palette.chartNone
        }
    )
}

/** Stacking order for charts, bottom to top: remembered dreams sit on the baseline where they're easiest to compare. */
val ChartKinds = listOf(NightKind.DREAM, NightKind.NIGHTMARE, NightKind.NO_DREAM)

@Composable
@ReadOnlyComposable
fun nightSeries(marker: (NightKind) -> Marker = { Marker.SQUARE }): List<ChartSeries> =
    ChartKinds.map { ChartSeries(it.label, it.color(), marker(it)) }
