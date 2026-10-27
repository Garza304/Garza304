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
