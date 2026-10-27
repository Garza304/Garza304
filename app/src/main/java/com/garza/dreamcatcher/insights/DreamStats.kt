package com.garza.dreamcatcher.insights

import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** What a morning's entries add up to. Declared from most to least notable: a nightmare outweighs a dream. */
enum class NightKind { NIGHTMARE, DREAM, NO_DREAM }

/** The period the trend charts and totals cover; [days] is null for everything since the first entry. */
enum class StatsRange(val label: String, val days: Long?) {
    MONTH("30 days", 30),
    QUARTER("90 days", 90),
    YEAR("Year", 365),
    ALL("All time", null),
}

/** How the trend chart groups mornings. */
enum class Granularity { WEEK, MONTH, YEAR }

/** Mornings per kind from [start] to [end], both inclusive. */
data class Bucket(val start: LocalDate, val end: LocalDate, val dream: Int, val nightmare: Int, val noDream: Int) {
    val total: Int get() = dream + nightmare + noDream
}

/** Totals for a range of mornings. */
data class Summary(val days: Int, val recorded: Int, val dream: Int, val nightmare: Int, val noDream: Int) {
    /** Mornings when a dream (good or bad) was remembered. */
    val recalled: Int get() = dream + nightmare

    /** Share of recorded mornings with a remembered dream, or null with nothing recorded. */
    val recallPercent: Int? get() = if (recorded == 0) null else Math.round(recalled * 100f / recorded)

    /** Share of remembered dreams that were nightmares, or null with none remembered. */
    val nightmarePercent: Int? get() = if (recalled == 0) null else Math.round(nightmare * 100f / recalled)
}

/** Recall split by how the morning started: [alarm] mornings had an entry started from a ringing alarm. */
data class WakeComparison(val ownMornings: Int, val ownRecalled: Int, val alarmMornings: Int, val alarmRecalled: Int) {
    val ownPercent: Int? get() = if (ownMornings == 0) null else Math.round(ownRecalled * 100f / ownMornings)
    val alarmPercent: Int? get() = if (alarmMornings == 0) null else Math.round(alarmRecalled * 100f / alarmMornings)
}

/** A tag and how many entries carry it. [builtIn] is set for Lucid and Recurring. */
data class TagCount(val label: String, val count: Int, val builtIn: DreamTag? = null)

/**
 * Numbers behind the Insights tab, kept free of Android classes so they can be unit tested.
 * Entries belong to the calendar day they were written on, so waking at 3am and again at 7am
 * both count toward the same morning.
 */
object DreamStats {

    fun dayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun kindOf(dream: Dream): NightKind = when {
        DreamTag.NIGHTMARE in dream.tags -> NightKind.NIGHTMARE
        DreamTag.DREAM in dream.tags -> NightKind.DREAM
        DreamTag.NO_DREAM in dream.tags -> NightKind.NO_DREAM
        // Untagged entries with words in them are dreams; empty ones mean nothing was remembered.
        dream.text.isNotBlank() -> NightKind.DREAM
        else -> NightKind.NO_DREAM
    }

    /** Each recorded morning and its most notable kind. */
    fun nights(dreams: List<Dream>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, NightKind> =
        dreams.groupBy { dayOf(it.createdAt, zone) }.mapValues { (_, entries) -> entries.minOf(::kindOf) }

    /**
     * Consecutive recorded mornings ending today, or ending yesterday when today isn't recorded
     * yet, so the streak doesn't look broken before you've had a chance to write.
     */
    fun currentStreak(mornings: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in mornings) today else today.minusDays(1)
        var count = 0
        while (day in mornings) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun bestStreak(mornings: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in mornings.sorted()) {
            run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /**
     * First morning [range] covers. Ranges never reach back before the first entry, so someone
     * who started last week isn't shown 23 "missed" mornings.
     */
    fun rangeStart(range: StatsRange, today: LocalDate, firstMorning: LocalDate?): LocalDate {
        val first = firstMorning ?: today
        val start = range.days?.let { today.minusDays(it - 1) } ?: first
        return maxOf(start, minOf(first, today))
    }

    fun summary(nights: Map<LocalDate, NightKind>, from: LocalDate, to: LocalDate): Summary {
        val kinds = nights.filterKeys { it in from..to }.values
        return Summary(
            days = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(0),
            recorded = kinds.size,
            dream = kinds.count { it == NightKind.DREAM },
            nightmare = kinds.count { it == NightKind.NIGHTMARE },
            noDream = kinds.count { it == NightKind.NO_DREAM },
        )
    }

    /** Weeks for up to about four months, months for up to three years, years beyond that. */
    fun granularity(from: LocalDate, to: LocalDate): Granularity {
        val days = ChronoUnit.DAYS.between(from, to) + 1
        return when {
            days <= 120 -> Granularity.WEEK
            days <= 3 * 366 -> Granularity.MONTH
            else -> Granularity.YEAR
        }
    }

    /** Consecutive buckets covering [from]..[to]; the first and last are trimmed to the range. */
    fun buckets(
        nights: Map<LocalDate, NightKind>,
        from: LocalDate,
        to: LocalDate,
        granularity: Granularity,
        firstDayOfWeek: DayOfWeek,
    ): List<Bucket> {
        if (to < from) return emptyList()
        var start = when (granularity) {
            Granularity.WEEK -> from.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            Granularity.MONTH -> from.withDayOfMonth(1)
            Granularity.YEAR -> from.withDayOfYear(1)
        }
        val result = mutableListOf<Bucket>()
        while (start <= to) {
            val next = when (granularity) {
                Granularity.WEEK -> start.plusWeeks(1)
                Granularity.MONTH -> start.plusMonths(1)
                Granularity.YEAR -> start.plusYears(1)
            }
            val s = maxOf(start, from)
            val e = minOf(next.minusDays(1), to)
            val kinds = nights.filterKeys { it in s..e }.values
            result += Bucket(
                start = s,
                end = e,
                dream = kinds.count { it == NightKind.DREAM },
                nightmare = kinds.count { it == NightKind.NIGHTMARE },
                noDream = kinds.count { it == NightKind.NO_DREAM },
            )
            start = next
        }
        return result
    }

    /**
     * Lucid, Recurring and custom tags by how many entries carry them, most used first, then A to Z.
     * Custom tags match case-insensitively and show the spelling used first.
     */
    fun topTags(dreams: List<Dream>, limit: Int = 8): List<TagCount> {
        val counts = LinkedHashMap<String, TagCount>()
        for (dream in dreams.sortedBy { it.createdAt }) {
            DreamTag.entries.filter { !it.isKind && it in dream.tags }.forEach { tag ->
                val key = "builtin:" + tag.key
                counts[key] = counts[key]?.let { it.copy(count = it.count + 1) } ?: TagCount(tag.label, 1, tag)
            }
            dream.customTags.forEach { name ->
                val key = "custom:" + name.lowercase()
                counts[key] = counts[key]?.let { it.copy(count = it.count + 1) } ?: TagCount(name, 1)
            }
        }
        return counts.values
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.label.lowercase() })
            .take(limit)
    }

    /** The month's days laid out in weeks starting on [firstDayOfWeek]; nulls pad the first and last week. */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = MutableList<LocalDate?>(leading) { null }
        for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** The period of the same length just before [from]..[to], for "compared with before" figures. */
    fun previousRange(from: LocalDate, to: LocalDate): Pair<LocalDate, LocalDate> {
        val days = ChronoUnit.DAYS.between(from, to) + 1
        return from.minusDays(days) to from.minusDays(1)
    }

    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

    /** Average words per written entry (entries with no words are left out), or null with none. */
    fun averageWords(dreams: List<Dream>): Int? {
        val counts = dreams.map { wordCount(it.text) }.filter { it > 0 }
        return if (counts.isEmpty()) null else Math.round(counts.average()).toInt()
    }

    fun longestWords(dreams: List<Dream>): Int = dreams.maxOfOrNull { wordCount(it.text) } ?: 0

    /**
     * Columns of [weeks] weeks ending with the week holding [today], each starting on
     * [firstDayOfWeek]. Days after today are null.
     */
    fun weekColumns(today: LocalDate, weeks: Int, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
        val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return (weeks - 1 downTo 0).map { back ->
            val start = lastWeekStart.minusWeeks(back.toLong())
            (0L until 7L).map { start.plusDays(it).takeIf { day -> day <= today } }
        }
    }

    /** Recall on mornings you woke by yourself vs. mornings an alarm woke you, within [from]..[to]. */
    fun wakeComparison(dreams: List<Dream>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): WakeComparison {
        var own = 0; var ownRecalled = 0; var alarm = 0; var alarmRecalled = 0
        dreams.groupBy { dayOf(it.createdAt, zone) }
            .filterKeys { it in from..to }
            .values
            .forEach { entries ->
                val recalled = entries.minOf(::kindOf) != NightKind.NO_DREAM
                if (entries.any { it.fromAlarm }) {
                    alarm++
                    if (recalled) alarmRecalled++
                } else {
                    own++
                    if (recalled) ownRecalled++
                }
            }
        return WakeComparison(own, ownRecalled, alarm, alarmRecalled)
    }

    /** Recall percentage per bucket, or null for a bucket with nothing recorded. */
    fun recallPercents(buckets: List<Bucket>): List<Int?> =
        buckets.map { if (it.total == 0) null else Math.round((it.dream + it.nightmare) * 100f / it.total) }

    /** How many entries were written in each hour of the day, index 0 to 23. */
    fun byHour(dreams: List<Dream>, zone: ZoneId = ZoneId.systemDefault()): List<Int> {
        val counts = IntArray(24)
        dreams.forEach { counts[Instant.ofEpochMilli(it.createdAt).atZone(zone).hour]++ }
        return counts.toList()
    }

    /** The hours worth charting: the first to the last hour with entries, widened evenly to at least [minSpan]. */
    fun activeHours(counts: List<Int>, minSpan: Int = 6): IntRange {
        val first = counts.indexOfFirst { it > 0 }
        if (first < 0) return 5..10
        var start = first
        var end = counts.indexOfLast { it > 0 }
        while (end - start + 1 < minSpan) {
            if (end < 23) end++
            if (end - start + 1 < minSpan && start > 0) start--
        }
        return start..end
    }

    /** Up to five evenly spaced, round axis values from 0 that cover [max]. */
    fun axisTicks(max: Int): List<Int> {
        if (max <= 0) return listOf(0, 1)
        val step = generateSequence(1) { it * 10 }
            .flatMap { sequenceOf(it, it * 2, it * 5) }
            .first { (max + it - 1) / it <= 4 }
        val top = (max + step - 1) / step * step
        return (0..top step step).toList()
    }
}
