package com.garza.dreamcatcher.ui.insights

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.InsightsStyle
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.insights.Bucket
import com.garza.dreamcatcher.insights.Granularity
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.insights.StatsRange
import com.garza.dreamcatcher.insights.TagCount
import com.garza.dreamcatcher.ui.charts.ChartLegend
import com.garza.dreamcatcher.ui.charts.ChartTable
import com.garza.dreamcatcher.ui.charts.HorizontalBarChart
import com.garza.dreamcatcher.ui.charts.StackedColumn
import com.garza.dreamcatcher.ui.charts.StackedColumnChart
import com.garza.dreamcatcher.ui.charts.StatTile
import com.garza.dreamcatcher.ui.journal.DreamCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the Insights layouts can ask for. */
data class InsightsActions(
    val onMonthChange: (YearMonth) -> Unit,
    val onSelectDay: (LocalDate) -> Unit,
    val onRangeChange: (StatsRange) -> Unit,
    val onOpen: (Dream) -> Unit,
    /** Shows the journal filtered to this tag. */
    val onTag: (TagCount) -> Unit,
    val onDigestMonthly: (Boolean) -> Unit,
    /** +1 goes a period back, -1 forward. */
    val onDigestStep: (Int) -> Unit,
)

/** The Insights tab, in the layout picked in Settings. */
@Composable
fun InsightsScreen(style: InsightsStyle, state: InsightsState?, actions: InsightsActions, contentPadding: PaddingValues) {
    if (state == null) return
    when (style) {
        InsightsStyle.CALENDAR -> CalendarLayout(state, actions, contentPadding)
        InsightsStyle.HEATMAP -> HeatmapLayout(state, actions, contentPadding)
        InsightsStyle.DIGEST -> DigestLayout(state, actions, contentPadding)
        InsightsStyle.PATTERNS -> PatternsLayout(state, actions, contentPadding)
    }
}

/** The scrolling column every layout is built in. */
@Composable
internal fun InsightsList(contentPadding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

internal fun LazyListScope.noEntriesYet() = item {
    Text(
        "Your trends will show up here once you've recorded a few mornings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Calendar first: a calendar of mornings, then trends for a chosen period. The period chips sit
 * above everything they scope; the calendar above them has its own month controls.
 */
@Composable
private fun CalendarLayout(state: InsightsState, actions: InsightsActions, contentPadding: PaddingValues) {
    InsightsList(contentPadding) {
        item { CalendarCard(state, actions.onMonthChange, actions.onSelectDay) }

        state.selectedDay?.let { day ->
            item {
                Text(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()).format(day),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(state.dayEntries, key = { "day-" + it.id }) { DreamCard(it, onClick = { actions.onOpen(it) }) }
        }

        item {
            Text(
                "Trends",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() },
            )
        }
        item { RangeChips(state.range, actions.onRangeChange) }

        if (!state.hasEntries) {
            noEntriesYet()
            return@InsightsList
        }

        item { SummaryTiles(state) }
        item { key(state.range) { MorningsCard(state) } }
        item { TagsCard(state) }
    }
}

@Composable
private fun CalendarCard(state: InsightsState, onMonthChange: (YearMonth) -> Unit, onSelectDay: (LocalDate) -> Unit) {
    InsightCard {
        Text(
            when {
                state.streak == 0 -> "No current streak"
                state.streak == 1 -> "1-morning streak"
                else -> "${state.streak}-morning streak"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Mornings in a row with an entry. Best: ${state.bestStreak}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        MonthCalendar(
            month = state.month,
            nights = state.nights,
            today = state.today,
            selected = state.selectedDay,
            onSelect = onSelectDay,
            onMonthChange = onMonthChange,
        )
        Spacer(Modifier.height(8.dp))
        ChartLegend(nightSeries { it.marker })
    }
}

@Composable
internal fun RangeChips(range: StatsRange, onRangeChange: (StatsRange) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { option ->
            FilterChip(
                selected = option == range,
                onClick = { onRangeChange(option) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun SummaryTiles(state: InsightsState) {
    val s = state.summary
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            label = "Recorded",
            value = "${s.recorded}/${s.days}",
            detail = "mornings",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Recall",
            value = s.recallPercent?.let { "$it%" } ?: "–",
            detail = "remembered a dream",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Nightmares",
            value = s.nightmare.toString(),
            detail = s.nightmarePercent?.let { "$it% of dreams" },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MorningsCard(state: InsightsState) {
    val buckets = state.buckets
    var selected by rememberSaveable { mutableStateOf(buckets.lastIndex) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    val chosen = buckets.getOrNull(selected) ?: buckets.lastOrNull()
    val series = nightSeries()
    val spansYears = buckets.isNotEmpty() && buckets.first().start.year != buckets.last().end.year

    InsightCard {
        Text(
            when (state.granularity) {
                Granularity.WEEK -> "Mornings by week"
                Granularity.MONTH -> "Mornings by month"
                Granularity.YEAR -> "Mornings by year"
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        // The readout for the tapped column, so exact numbers never need a tooltip.
        if (chosen != null) {
            Text(
                periodName(chosen, state.granularity) + ": " + breakdown(chosen),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        StackedColumnChart(
            columns = buckets.map { b ->
                StackedColumn(axisLabel(b, state.granularity, spansYears), listOf(b.dream, b.nightmare, b.noDream))
            },
            series = series,
            selected = selected,
            onSelect = { selected = it },
            description = buckets.joinToString(". ") { periodName(it, state.granularity) + ": " + breakdown(it) },
        )
        Spacer(Modifier.height(8.dp))
        ChartLegend(series)
        TextButton(onClick = { showTable = !showTable }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(if (showTable) "Hide table" else "Show as table")
        }
        if (showTable) {
            ChartTable(
                header = listOf("Period") + ChartKinds.map { it.label },
                rows = buckets.reversed().map { b ->
                    listOf(periodName(b, state.granularity), b.dream.toString(), b.nightmare.toString(), b.noDream.toString())
                },
            )
        }
    }
}

@Composable
internal fun TagsCard(state: InsightsState) {
    InsightCard {
        Text("Most-used tags", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(
            "Lucid, recurring and your own tags in this period",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (state.topTags.isEmpty()) {
            Text(
                "No tags yet. Add tags like Lucid or a feeling when you save a dream to see patterns here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HorizontalBarChart(
                rows = state.topTags.map { it.label to it.count },
                color = MaterialTheme.colorScheme.primary,
                unit = { if (it == 1) "1 entry" else "$it entries" },
            )
        }
    }
}

@Composable
internal fun InsightCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

internal fun breakdown(b: Bucket): String =
    if (b.total == 0) {
        "nothing recorded"
    } else {
        listOf(NightKind.DREAM to b.dream, NightKind.NIGHTMARE to b.nightmare, NightKind.NO_DREAM to b.noDream)
            .filter { it.second > 0 }
            .joinToString(", ") { (kind, n) -> kind.count(n) }
    }

internal fun periodName(b: Bucket, granularity: Granularity): String {
    val locale = Locale.getDefault()
    return when (granularity) {
        Granularity.WEEK -> "Week of " + b.start.format(DateTimeFormatter.ofPattern("MMM d", locale))
        Granularity.MONTH -> b.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        Granularity.YEAR -> b.start.year.toString()
    }
}

internal fun axisLabel(b: Bucket, granularity: Granularity, spansYears: Boolean): String {
    val locale = Locale.getDefault()
    return when (granularity) {
        Granularity.WEEK -> b.start.format(DateTimeFormatter.ofPattern("MMM d", locale))
        Granularity.MONTH -> b.start.format(DateTimeFormatter.ofPattern(if (spansYears) "MMM yy" else "MMM", locale))
        Granularity.YEAR -> b.start.year.toString()
    }
}
