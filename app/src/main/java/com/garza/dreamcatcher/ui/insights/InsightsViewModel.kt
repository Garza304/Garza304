package com.garza.dreamcatcher.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.insights.Bucket
import com.garza.dreamcatcher.insights.DreamStats
import com.garza.dreamcatcher.insights.Granularity
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.insights.StatsRange
import com.garza.dreamcatcher.insights.Summary
import com.garza.dreamcatcher.insights.TagCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/** Everything the Insights tab shows, worked out together so the numbers always agree. */
data class InsightsState(
    val today: LocalDate,
    val hasEntries: Boolean,
    val nights: Map<LocalDate, NightKind>,
    val streak: Int,
    val bestStreak: Int,
    val month: YearMonth,
    val selectedDay: LocalDate?,
    val dayEntries: List<Dream>,
    val range: StatsRange,
    val summary: Summary,
    val granularity: Granularity,
    val buckets: List<Bucket>,
    val topTags: List<TagCount>,
)

class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = application.app.database.dreamDao()

    val range = MutableStateFlow(StatsRange.MONTH)
    val month = MutableStateFlow(YearMonth.now())
    val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Null until the journal has loaded. */
    val state: StateFlow<InsightsState?> = combine(dao.observeAll(), range, month, selectedDay) { dreams, range, month, day ->
        build(dreams, range, month, day)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showMonth(next: YearMonth) {
        month.value = minOf(next, YearMonth.now())
        selectedDay.value = null
    }

    /** Tapping the selected day again closes it. */
    fun selectDay(day: LocalDate) {
        selectedDay.value = if (selectedDay.value == day) null else day
    }

    private fun build(dreams: List<Dream>, range: StatsRange, month: YearMonth, day: LocalDate?): InsightsState {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nights = DreamStats.nights(dreams, zone)
        val from = DreamStats.rangeStart(range, today, nights.keys.minOrNull())
        val inRange = dreams.filter { DreamStats.dayOf(it.createdAt, zone) in from..today }
        val granularity = DreamStats.granularity(from, today)
        return InsightsState(
            today = today,
            hasEntries = dreams.isNotEmpty(),
            nights = nights,
            streak = DreamStats.currentStreak(nights.keys, today),
            bestStreak = DreamStats.bestStreak(nights.keys),
            month = month,
            selectedDay = day,
            dayEntries = if (day == null) emptyList() else dreams.filter { DreamStats.dayOf(it.createdAt, zone) == day }.sortedBy { it.createdAt },
            range = range,
            summary = DreamStats.summary(nights, from, today),
            granularity = granularity,
            buckets = DreamStats.buckets(nights, from, today, granularity, WeekFields.of(Locale.getDefault()).firstDayOfWeek),
            topTags = DreamStats.topTags(inRange),
        )
    }
}
