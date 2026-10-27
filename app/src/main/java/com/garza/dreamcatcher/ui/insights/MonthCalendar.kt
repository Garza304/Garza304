package com.garza.dreamcatcher.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.insights.DreamStats
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.ui.charts.MarkerShape
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** A month of mornings, each marked with what was recorded. Recorded days can be tapped to see their entries. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    nights: Map<LocalDate, NightKind>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val colors = MaterialTheme.colorScheme
    val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = month < YearMonth.from(today)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        Row(Modifier.clearAndSetSemantics {}) {
            (0 until 7).forEach { i ->
                Text(
                    firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        DreamStats.monthGrid(month, firstDay).chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                kind = nights[day],
                                isToday = day == today,
                                isFuture = day > today,
                                isSelected = day == selected,
                                description = dayFormat.format(day) + ", " + (nights[day]?.label ?: "nothing recorded"),
                                onClick = { onSelect(day) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    kind: NightKind?,
    isToday: Boolean,
    isFuture: Boolean,
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    var modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    if (isSelected) modifier = modifier.background(colors.surfaceVariant, shape)
    if (kind != null) modifier = modifier.clickable(onClick = onClick, role = Role.Button)
    modifier = modifier.clearAndSetSemantics {
        contentDescription = description
        if (kind != null) {
            role = Role.Button
            selected = isSelected
        }
    }
    if (isFuture) modifier = modifier.alpha(0.4f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) colors.primary else colors.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(9.dp), contentAlignment = Alignment.Center) {
            if (kind != null) MarkerShape(kind.marker, kind.color(), 9.dp)
        }
    }
}
