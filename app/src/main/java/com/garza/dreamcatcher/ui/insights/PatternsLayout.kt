package com.garza.dreamcatcher.ui.insights

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.insights.StatsRange
import com.garza.dreamcatcher.ui.charts.ChartSeries
import com.garza.dreamcatcher.ui.charts.Marker
import com.garza.dreamcatcher.ui.charts.MarkerShape
import com.garza.dreamcatcher.ui.charts.PercentLineChart
import com.garza.dreamcatcher.ui.charts.ProportionBar
import com.garza.dreamcatcher.ui.charts.StackedColumn
import com.garza.dreamcatcher.ui.charts.StackedColumnChart
import com.garza.dreamcatcher.ui.charts.StatTile
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Patterns: how recall is changing, what you remember, when you write, and your tags. */
@Composable
internal fun PatternsLayout(state: InsightsState, actions: InsightsActions, contentPadding: PaddingValues) {
    InsightsList(contentPadding) {
        item { RangeSegments(state.range, actions.onRangeChange) }
        if (!state.hasEntries) {
            noEntriesYet()
            return@InsightsList
        }
        item { key(state.range) { RecallCard(state) } }
        item { MixCard(state) }
        item { key(state.range) { HoursCard(state) } }
        item { TagCountTiles(state) }
        item { TagsCard(state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSegments(range: StatsRange, onChange: (StatsRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        StatsRange.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == range,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, StatsRange.entries.size),
                icon = {},
            ) { Text(if (option == StatsRange.ALL) "All" else option.label, maxLines = 1) }
        }
    }
}

@Composable
private fun RecallCard(state: InsightsState) {
    val buckets = state.buckets
    val recall = state.recall
    val latest = recall.indexOfLast { it != null }
    val first = recall.indexOfFirst { it != null }
    var selected by rememberSaveable { mutableStateOf(latest) }
    val shown = selected.takeIf { recall.getOrNull(it) != null } ?: latest
    val spansYears = buckets.isNotEmpty() && buckets.first().start.year != buckets.last().end.year

    InsightCard {
        Text("Dream recall", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (shown < 0) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@InsightCard
        }
        val b = buckets[shown]
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${recall[shown]}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                periodName(b, state.granularity) + " · ${b.dream + b.nightmare} of ${b.total} mornings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }
        if (first >= 0 && first != latest) {
            val then = recall[first]!!
            val now = recall[latest]!!
            Text(
                when {
                    now > then -> "Up from $then% in " + periodName(buckets[first], state.granularity).lowercaseFirst()
                    now < then -> "Down from $then% in " + periodName(buckets[first], state.granularity).lowercaseFirst()
                    else -> "The same as " + periodName(buckets[first], state.granularity).lowercaseFirst()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        PercentLineChart(
            points = recall,
            labels = buckets.map { axisLabel(it, state.granularity, spansYears) },
            selected = shown,
            onSelect = { selected = it },
            description = buckets.indices.joinToString(". ") { i ->
                periodName(buckets[i], state.granularity) + ": " + (recall[i]?.let { "$it% recall" } ?: "nothing recorded")
            },
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun String.lowercaseFirst() = replaceFirstChar { it.lowercase() }

@Composable
private fun MixCard(state: InsightsState) {
    val s = state.summary
    InsightCard {
        Text("What you remember", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(
            if (s.recorded == 1) "1 recorded morning" else "${s.recorded} recorded mornings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val parts = listOf(NightKind.DREAM to s.dream, NightKind.NIGHTMARE to s.nightmare, NightKind.NO_DREAM to s.noDream)
        fun percent(n: Int) = if (s.recorded == 0) 0 else Math.round(n * 100f / s.recorded)
        ProportionBar(
            parts = parts.map { (kind, n) -> n to kind.color() },
            description = parts.joinToString(", ") { (kind, n) -> "${kind.label} ${percent(n)}%" },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { (kind, n) ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MarkerShape(Marker.SQUARE, kind.color())
                        Text(
                            kind.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Text("${percent(n)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (n == 1) "1 morning" else "$n mornings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HoursCard(state: InsightsState) {
    val context = LocalContext.current
    val format = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h a", Locale.getDefault())
    fun hour(h: Int) = LocalTime.of(h % 24, 0).format(format)
    val hours = state.hours.toList()
    val counts = hours.map { state.hourCounts[it] }
    val peak = hours.maxByOrNull { state.hourCounts[it] }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    InsightCard {
        Text("When you record", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        val tapped = selected?.let { hours.getOrNull(it) }
        Text(
            when {
                tapped != null -> {
                    val n = state.hourCounts[tapped]
                    "${hour(tapped)} – ${hour(tapped + 1)}: " + if (n == 1) "1 entry" else "$n entries"
                }
                peak != null && state.hourCounts[peak] > 0 -> "Most entries are written between ${hour(peak)} and ${hour(peak + 1)}"
                else -> "No entries in this period"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        StackedColumnChart(
            columns = hours.mapIndexed { i, h -> StackedColumn(hour(h), listOf(counts[i])) },
            series = listOf(ChartSeries("Entries", MaterialTheme.colorScheme.primary)),
            selected = selected,
            onSelect = { selected = if (selected == it) null else it },
            description = hours.joinToString(". ") { h -> "${hour(h)}: ${state.hourCounts[h]} entries" },
            plotHeight = 110.dp,
        )
    }
}

@Composable
private fun TagCountTiles(state: InsightsState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ratio = if (state.lucid == 0) null else Math.round(state.recalledEntries.toFloat() / state.lucid)
        StatTile(
            label = "Lucid",
            value = state.lucid.toString(),
            detail = ratio?.let { if (it <= 1) "Every dream" else "1 in $it dreams" },
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Recurring",
            value = state.recurring.toString(),
            detail = state.lastRecurring?.let { "Last on " + it.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) },
            modifier = Modifier.weight(1f),
        )
    }
}
