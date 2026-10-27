package com.garza.dreamcatcher.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import com.garza.dreamcatcher.insights.Bucket
import com.garza.dreamcatcher.insights.DreamStats
import com.garza.dreamcatcher.insights.Granularity
import com.garza.dreamcatcher.insights.NightKind
import com.garza.dreamcatcher.insights.StatsRange
import com.garza.dreamcatcher.insights.Summary
import com.garza.dreamcatcher.insights.TagCount
import com.garza.dreamcatcher.insights.WakeComparison
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/** The weekly digest's period: the last 7 days or a calendar month, [offset] periods back from now. */
data class DigestState(
    val monthly: Boolean,
    val offset: Int,
    val from: LocalDate,
    val to: LocalDate,
    val month: YearMonth,
    val summary: Summary,
    val themes: List<TagCount>,
    /** The top theme and how many entries had it in the 30 days up to [to], when that's more than this period. */
    val recurringTheme: TagCount?,
)

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
    /** The same-length period before, or null for all time. */
    val previous: Summary?,
    val granularity: Granularity,
    val buckets: List<Bucket>,
    val recall: List<Int?>,
    val topTags: List<TagCount>,
    val averageWords: Int?,
    val longestWords: Int,
    val heatWeeks: List<List<LocalDate?>>,
    /** Always the last 30 days, so it has enough mornings to say something. */
    val wake: WakeComparison,
    val hourCounts: List<Int>,
    val hours: IntRange,
    val lucid: Int,
    val recurring: Int,
    val lastRecurring: LocalDate?,
    /** Remembered dreams (not "no dream" entries) in the range, for "1 in N dreams". */
    val recalledEntries: Int,
    val latest: Dream?,
    val digest: DigestState,
)

class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private data class Controls(
        val range: StatsRange = StatsRange.MONTH,
        val month: YearMonth = YearMonth.now(),
        val selectedDay: LocalDate? = null,
        val digestMonthly: Boolean = false,
        val digestOffset: Int = 0,
    )

    private val dao = application.app.database.dreamDao()
    private val controls = MutableStateFlow(Controls())

    /** Null until the journal has loaded. */
    val state: StateFlow<InsightsState?> = combine(dao.observeAll(), controls, ::build)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setRange(range: StatsRange) = controls.update { it.copy(range = range) }

    fun showMonth(next: YearMonth) = controls.update { it.copy(month = minOf(next, YearMonth.now()), selectedDay = null) }

    /** Tapping the selected day again closes it. */
    fun selectDay(day: LocalDate) = controls.update { it.copy(selectedDay = if (it.selectedDay == day) null else day) }

    fun setDigestMonthly(monthly: Boolean) = controls.update { it.copy(digestMonthly = monthly, digestOffset = 0) }

    /** Moves the digest back (+1) or forward (-1) a period; never past the current one. */
    fun stepDigest(back: Int) = controls.update { it.copy(digestOffset = (it.digestOffset + back).coerceAtLeast(0)) }

    private fun build(dreams: List<Dream>, c: Controls): InsightsState {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        fun between(from: LocalDate, to: LocalDate) = dreams.filter { DreamStats.dayOf(it.createdAt, zone) in from..to }

        val nights = DreamStats.nights(dreams, zone)
        val from = DreamStats.rangeStart(c.range, today, nights.keys.minOrNull())
        val inRange = between(from, today)
        val granularity = DreamStats.granularity(from, today)
        val buckets = DreamStats.buckets(nights, from, today, granularity, firstDay)
        val previous = if (c.range == StatsRange.ALL) {
            null
        } else {
            val (pFrom, pTo) = DreamStats.previousRange(from, today)
            DreamStats.summary(nights, pFrom, pTo)
        }
        val hourCounts = DreamStats.byHour(inRange, zone)
        val recurringEntries = inRange.filter { DreamTag.RECURRING in it.tags }

        return InsightsState(
            today = today,
            hasEntries = dreams.isNotEmpty(),
            nights = nights,
            streak = DreamStats.currentStreak(nights.keys, today),
            bestStreak = DreamStats.bestStreak(nights.keys),
            month = c.month,
            selectedDay = c.selectedDay,
            dayEntries = c.selectedDay?.let { day -> between(day, day).sortedBy { it.createdAt } } ?: emptyList(),
            range = c.range,
            summary = DreamStats.summary(nights, from, today),
            previous = previous,
            granularity = granularity,
            buckets = buckets,
            recall = DreamStats.recallPercents(buckets),
            topTags = DreamStats.topTags(inRange),
            averageWords = DreamStats.averageWords(inRange),
            longestWords = DreamStats.longestWords(inRange),
            heatWeeks = DreamStats.weekColumns(today, HEAT_WEEKS, firstDay),
            wake = DreamStats.wakeComparison(dreams, today.minusDays(29), today, zone),
            hourCounts = hourCounts,
            hours = DreamStats.activeHours(hourCounts),
            lucid = inRange.count { DreamTag.LUCID in it.tags },
            recurring = recurringEntries.size,
            lastRecurring = recurringEntries.maxOfOrNull { it.createdAt }?.let { DreamStats.dayOf(it, zone) },
            recalledEntries = inRange.count { DreamStats.kindOf(it) != NightKind.NO_DREAM },
            latest = dreams.maxByOrNull { it.createdAt },
            digest = digest(c, today, nights, ::between),
        )
    }

    private fun digest(
        c: Controls,
        today: LocalDate,
        nights: Map<LocalDate, NightKind>,
        between: (LocalDate, LocalDate) -> List<Dream>,
    ): DigestState {
        val month = YearMonth.from(today).minusMonths(c.digestOffset.toLong())
        val (from, to) = if (c.digestMonthly) {
            month.atDay(1) to minOf(month.atEndOfMonth(), today)
        } else {
            val end = today.minusWeeks(c.digestOffset.toLong())
            end.minusDays(6) to end
        }
        val themes = DreamStats.topTags(between(from, to), limit = 6)
        val recurringTheme = themes.firstOrNull()?.let { top ->
            val lastMonth = DreamStats.topTags(between(to.minusDays(29), to), limit = Int.MAX_VALUE)
                .firstOrNull { it.label.equals(top.label, ignoreCase = true) }
            lastMonth?.takeIf { it.count > top.count && !c.digestMonthly }
        }
        return DigestState(
            monthly = c.digestMonthly,
            offset = c.digestOffset,
            from = from,
            to = to,
            month = month,
            summary = DreamStats.summary(nights, from, to),
            themes = themes,
            recurringTheme = recurringTheme,
        )
    }

    private companion object {
        const val HEAT_WEEKS = 20
    }
}
