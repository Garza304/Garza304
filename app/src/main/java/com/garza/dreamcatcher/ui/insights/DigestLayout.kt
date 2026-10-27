package com.garza.dreamcatcher.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.dream.DreamText
import com.garza.dreamcatcher.insights.DreamStats
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.ui.charts.HorizontalBarChart
import com.garza.dreamcatcher.ui.charts.MarkerShape
import com.garza.dreamcatcher.ui.journal.DreamCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Weekly digest: the week (or month) in words, what helps you remember, its themes and the latest dream. */
@Composable
internal fun DigestLayout(state: InsightsState, actions: InsightsActions, contentPadding: PaddingValues) {
    val digest = state.digest
    InsightsList(contentPadding) {
        item { PeriodToggle(digest.monthly, actions.onDigestMonthly) }
        if (!digest.monthly) item { WeekNavigator(digest, actions.onDigestStep) }
        item { SummaryCard(state, actions) }

        if (digest.monthly) {
            state.selectedDay?.let { day ->
                item {
                    Text(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()).format(day),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(state.dayEntries, key = { "digest-day-" + it.id }) { DreamCard(it, onClick = { actions.onOpen(it) }) }
            }
        }

        if (!state.hasEntries) {
            noEntriesYet()
            return@InsightsList
        }
        item { WakeCard(state) }
        item { ThemesCard(state, actions) }
        state.latest?.let { latest -> item { LatestCard(latest, state.today, actions) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodToggle(monthly: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false to "Week", true to "Month").forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = monthly == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) { Text(label) }
        }
    }
}

@Composable
private fun WeekNavigator(digest: DigestState, onStep: (Int) -> Unit) {
    val format = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val range = digest.from.format(format) + " – " + digest.to.format(format)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onStep(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
        }
        Text(
            if (digest.offset == 0) "Last 7 days · $range" else range,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = { onStep(-1) }, enabled = digest.offset > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(state: InsightsState, actions: InsightsActions) {
    val digest = state.digest
    val s = digest.summary
    val period = if (digest.monthly) "this month" else "these 7 days"
    val sentence = when {
        s.recorded == 0 -> "Nothing recorded in $period."
        s.recalled == s.recorded && s.recorded > 1 ->
            "You wrote down ${s.recorded} of ${s.days} mornings and remembered a dream every time."
        s.recalled == 0 -> "You wrote down ${s.recorded} of ${s.days} mornings but didn't remember a dream."
        else -> "You wrote down ${s.recorded} of ${s.days} mornings and remembered a dream on ${s.recalled} of them."
    }
    InsightCard {
        Text(sentence, fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 31.sp)
        Spacer(Modifier.height(18.dp))
        if (digest.monthly) {
            // The month's own arrows step the digest month.
            MonthCalendar(
                month = digest.month,
                nights = state.nights,
                today = state.today,
                selected = state.selectedDay,
                onSelect = actions.onSelectDay,
                onMonthChange = { m ->
                    actions.onDigestStep(ChronoUnit.MONTHS.between(m, digest.month).toInt())
                },
            )
        } else {
            WeekStrip(digest.from, digest.to, state.nights, state.today)
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(NightKind.DREAM to s.dream, NightKind.NIGHTMARE to s.nightmare, NightKind.NO_DREAM to s.noDream).forEach { (kind, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarkerShape(kind.marker, kind.color())
                    Text(
                        kind.count(n),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(from: LocalDate, to: LocalDate, nights: Map<LocalDate, NightKind>, today: LocalDate) {
    val locale = Locale.getDefault()
    val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    Row(Modifier.fillMaxWidth()) {
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.forEach { day ->
            val kind = nights[day]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = dayFormat.format(day) + ", " + (kind?.label ?: "nothing recorded")
                },
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val ring = if (day == today) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape) else Modifier
                Box(
                    Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).then(ring),
                    contentAlignment = Alignment.Center,
                ) {
                    if (kind != null) MarkerShape(kind.marker, kind.color(), 12.dp)
                }
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WakeCard(state: InsightsState) {
    val w = state.wake
    val own = w.ownPercent
    val alarm = w.alarmPercent
    val enough = w.ownMornings >= MIN_MORNINGS && w.alarmMornings >= MIN_MORNINGS && own != null && alarm != null
    val diff = if (enough) (own ?: 0) - (alarm ?: 0) else 0
    InsightCard {
        Text(
            when {
                !enough -> "Waking on your own vs. the alarm"
                diff > 0 -> "You remember more when you wake on your own"
                diff < 0 -> "You remember more when the alarm wakes you"
                else -> "Waking on your own or to the alarm makes no difference so far"
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Mornings with a remembered dream, last 30 days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (!enough) {
            Text(
                "Once you have a few mornings of each, this shows which way you remember more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            fun mornings(n: Int) = if (n == 1) "1 morning" else "$n mornings"
            HorizontalBarChart(
                rows = listOf(
                    "Woke on your own (${mornings(w.ownMornings)})" to (own ?: 0),
                    "Woke to the alarm (${mornings(w.alarmMornings)})" to (alarm ?: 0),
                ),
                color = MaterialTheme.colorScheme.primary,
                unit = { "$it%" },
                max = 100,
                valueText = { "$it%" },
                labelAbove = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemesCard(state: InsightsState, actions: InsightsActions) {
    val digest = state.digest
    InsightCard {
        Text(
            if (digest.monthly) "Themes this month" else "Themes this week",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        if (digest.themes.isEmpty()) {
            Text(
                "No tags in this period. Tag a dream with a feeling or topic to see themes here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digest.themes.forEach { tag ->
                    AssistChip(
                        onClick = { actions.onTag(tag) },
                        label = { Text(tag.label) },
                        trailingIcon = {
                            Text(tag.count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        },
                    )
                }
            }
            digest.recurringTheme?.let {
                Text(
                    "${it.label} keeps coming up: it was in ${it.count} dreams in the last 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LatestCard(dream: Dream, today: LocalDate, actions: InsightsActions) {
    val day = DreamStats.dayOf(dream.createdAt)
    val title = when (day) {
        today -> "This morning"
        today.minusDays(1) -> "Yesterday"
        else -> "Latest entry"
    }
    InsightCard {
        Text(
            title + " · " + DreamText.formatDate(dream.createdAt) + " · " + DreamText.formatTime(dream.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            dream.text.ifBlank { "Nothing remembered" },
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        val labels = DreamText.tagLabels(dream)
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(labels.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = { actions.onOpen(dream) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Open entry")
        }
    }
}

private const val MIN_MORNINGS = 3
