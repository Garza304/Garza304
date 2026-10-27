package com.garza.dreamcatcher.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.insights.StatsRange
import com.garza.dreamcatcher.insights.Summary
import com.garza.dreamcatcher.ui.charts.ChartLegend
import com.garza.dreamcatcher.ui.charts.ChartSeries
import com.garza.dreamcatcher.ui.charts.Marker
import com.garza.dreamcatcher.ui.charts.StatTile
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Streak and heatmap: the habit up front, 20 weeks of mornings, then figures compared with the period before. */
@Composable
internal fun HeatmapLayout(state: InsightsState, actions: InsightsActions, contentPadding: PaddingValues) {
    InsightsList(contentPadding) {
        item { StreakHero(state) }
        item { HeatmapCard(state) }
        item { RangeChips(state.range, actions.onRangeChange) }
        if (!state.hasEntries) {
            noEntriesYet()
            return@InsightsList
        }
        item { ComparisonTiles(state) }
        item { TagChipsCard(state, actions) }
    }
}

@Composable
private fun StreakHero(state: InsightsState) {
    InsightCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Current streak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(state.streak.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.streak == 1) "morning" else "mornings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Best", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.bestStreak.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeatmapCard(state: InsightsState) {
    val weeks = state.heatWeeks
    val days = weeks.flatten().filterNotNull()
    val recorded = days.count { it in state.nights }
    val empty = MaterialTheme.colorScheme.surfaceVariant
    InsightCard {
        Text("Last ${weeks.size} weeks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(
            "$recorded of ${days.size} mornings recorded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = "Recorded $recorded of ${days.size} mornings in the last ${weeks.size} weeks"
            }
        ) {
            val labelWidth = 16.dp
            val gap = 3.dp
            val cell: Dp = ((maxWidth - labelWidth - gap * weeks.size) / weeks.size).coerceAtMost(16.dp)
            val locale = Locale.getDefault()
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                // Every other weekday initial, from the first day of the week.
                Column(Modifier.width(labelWidth).padding(top = cell + gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                    weeks.first().indices.forEach { row ->
                        val day = weeks.firstNotNullOfOrNull { it[row] }?.dayOfWeek
                        Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                            if (row % 2 == 0 && day != null) {
                                Text(
                                    day.getDisplayName(TextStyle.NARROW, locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                weeks.forEachIndexed { index, week ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        // Month initial above the first week of each month.
                        val first = week.firstOrNull()
                        val newMonth = first != null && (index == 0 || weeks[index - 1].firstOrNull()?.month != first.month)
                        Box(Modifier.size(cell), contentAlignment = Alignment.BottomStart) {
                            if (newMonth && first != null) {
                                Text(
                                    first.month.getDisplayName(TextStyle.SHORT, locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = false,
                                    maxLines = 1,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true),
                                )
                            }
                        }
                        week.forEach { day ->
                            HeatCell(if (day == null) null else state.nights[day], visible = day != null, size = cell, empty = empty)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ChartLegend(
            nightSeries { if (it == NightKind.NO_DREAM) Marker.RING else Marker.SQUARE } +
                ChartSeries("Not recorded", empty, Marker.SQUARE)
        )
    }
}

@Composable
private fun HeatCell(kind: NightKind?, visible: Boolean, size: Dp, empty: Color) {
    val shape = RoundedCornerShape(3.dp)
    val modifier = Modifier.size(size)
    when {
        !visible -> Spacer(modifier)
        kind == null -> Box(modifier.background(empty, shape))
        // "No dream" is a ring, so it differs from the filled kinds by shape as well as color.
        kind == NightKind.NO_DREAM -> Box(modifier.border(BorderStroke(2.dp, kind.color()), shape))
        else -> Box(modifier.background(kind.color(), shape))
    }
}

@Composable
private fun ComparisonTiles(state: InsightsState) {
    val s = state.summary
    val p = state.previous
    val before = when (state.range) {
        StatsRange.MONTH -> "the 30 days before"
        StatsRange.QUARTER -> "the 90 days before"
        StatsRange.YEAR -> "the year before"
        StatsRange.ALL -> ""
    }
    // Only compare with a period that had something in it.
    val compare = p != null && p.recorded > 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Dream recall",
                value = s.recallPercent?.let { "$it%" } ?: "–",
                detail = if (compare) delta(s.recallPercent, p!!.recallPercent, "points", before) else "remembered a dream",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Mornings recorded",
                value = "${s.recorded} of ${s.days}",
                detail = if (compare) countDelta(s.recorded, p!!.recorded) else null,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Nightmares",
                value = s.nightmare.toString(),
                detail = if (compare) nightmareDelta(s, p!!) else s.nightmarePercent?.let { "$it% of dreams" },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Average entry",
                value = state.averageWords?.let { "$it words" } ?: "–",
                detail = if (state.longestWords > 0) "Longest: ${state.longestWords}" else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun delta(now: Int?, then: Int?, unit: String, before: String): String? {
    if (now == null || then == null) return null
    val d = now - then
    return when {
        d > 0 -> "Up $d $unit on $before"
        d < 0 -> "Down ${abs(d)} $unit on $before"
        else -> "Same as $before"
    }
}

private fun countDelta(now: Int, then: Int): String = when {
    now > then -> "${now - then} more than before"
    now < then -> "${then - now} fewer than before"
    else -> "Same as before"
}

private fun nightmareDelta(now: Summary, then: Summary): String = when {
    now.nightmare < then.nightmare -> "Down from ${then.nightmare}"
    now.nightmare > then.nightmare -> "Up from ${then.nightmare}"
    else -> "Same as before"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipsCard(state: InsightsState, actions: InsightsActions) {
    InsightCard {
        Text("Your tags", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        if (state.topTags.isEmpty()) {
            Text(
                "No tags in this period yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.topTags.forEach { tag ->
                    AssistChip(
                        onClick = { actions.onTag(tag) },
                        label = { Text(tag.label) },
                        trailingIcon = {
                            Text(
                                tag.count.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
            Text(
                "Tap a tag to see those dreams in the journal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
