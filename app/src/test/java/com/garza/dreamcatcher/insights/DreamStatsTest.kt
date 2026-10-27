package com.garza.dreamcatcher.insights

import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

class DreamStatsTest {

    private val zone = ZoneOffset.UTC

    private fun dream(date: String, time: String = "07:00", text: String = "A dream", vararg tags: DreamTag, custom: Set<String> = emptySet()) =
        Dream(
            text = text,
            createdAt = LocalDate.parse(date).atTime(LocalTime.parse(time)).toInstant(zone).toEpochMilli(),
            tags = tags.toSet(),
            customTags = custom,
        )

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun aNightmareOutweighsADreamOnTheSameMorning() {
        val nights = DreamStats.nights(
            listOf(
                dream("2026-10-01", "03:00", "Falling", DreamTag.NIGHTMARE),
                dream("2026-10-01", "07:00", "Flying", DreamTag.DREAM),
                dream("2026-10-02", "07:00", "", DreamTag.NO_DREAM),
                dream("2026-10-03", text = "Untagged but written"),
            ),
            zone,
        )
        assertEquals(
            mapOf(d("2026-10-01") to NightKind.NIGHTMARE, d("2026-10-02") to NightKind.NO_DREAM, d("2026-10-03") to NightKind.DREAM),
            nights,
        )
    }

    @Test
    fun streakSurvivesUntilAMorningIsMissed() {
        val mornings = setOf(d("2026-10-20"), d("2026-10-21"), d("2026-10-22"), d("2026-10-25"), d("2026-10-26"))
        // Today not written yet: the streak still counts through yesterday.
        assertEquals(2, DreamStats.currentStreak(mornings, d("2026-10-27")))
        assertEquals(3, DreamStats.currentStreak(mornings + d("2026-10-27"), d("2026-10-27")))
        // Missed yesterday and today: broken.
        assertEquals(0, DreamStats.currentStreak(mornings, d("2026-10-28")))
        assertEquals(3, DreamStats.bestStreak(mornings))
        assertEquals(0, DreamStats.bestStreak(emptySet()))
    }

    @Test
    fun rangesStartNoEarlierThanTheFirstEntry() {
        val today = d("2026-10-27")
        assertEquals(d("2026-09-28"), DreamStats.rangeStart(StatsRange.MONTH, today, d("2026-01-01")))
        assertEquals(d("2026-10-20"), DreamStats.rangeStart(StatsRange.MONTH, today, d("2026-10-20")))
        assertEquals(d("2026-01-01"), DreamStats.rangeStart(StatsRange.ALL, today, d("2026-01-01")))
        assertEquals(today, DreamStats.rangeStart(StatsRange.ALL, today, null))
    }

    @Test
    fun summaryCountsMorningsAndShares() {
        val nights = mapOf(
            d("2026-10-01") to NightKind.DREAM,
            d("2026-10-02") to NightKind.DREAM,
            d("2026-10-03") to NightKind.NIGHTMARE,
            d("2026-10-04") to NightKind.NO_DREAM,
            d("2026-09-30") to NightKind.DREAM, // outside the range
        )
        val s = DreamStats.summary(nights, d("2026-10-01"), d("2026-10-10"))
        assertEquals(Summary(days = 10, recorded = 4, dream = 2, nightmare = 1, noDream = 1), s)
        assertEquals(75, s.recallPercent)
        assertEquals(33, s.nightmarePercent)
        assertNull(DreamStats.summary(emptyMap(), d("2026-10-01"), d("2026-10-01")).recallPercent)
    }

    @Test
    fun weeklyBucketsAreTrimmedToTheRange() {
        val nights = mapOf(d("2026-10-01") to NightKind.DREAM, d("2026-10-05") to NightKind.NIGHTMARE, d("2026-10-06") to NightKind.DREAM)
        // 1 Oct 2026 is a Thursday.
        val buckets = DreamStats.buckets(nights, d("2026-10-01"), d("2026-10-14"), Granularity.WEEK, DayOfWeek.MONDAY)
        assertEquals(
            listOf(
                Bucket(d("2026-10-01"), d("2026-10-04"), dream = 1, nightmare = 0, noDream = 0),
                Bucket(d("2026-10-05"), d("2026-10-11"), dream = 1, nightmare = 1, noDream = 0),
                Bucket(d("2026-10-12"), d("2026-10-14"), dream = 0, nightmare = 0, noDream = 0),
            ),
            buckets,
        )
    }

    @Test
    fun longerRangesUseMonthsThenYears() {
        assertEquals(Granularity.WEEK, DreamStats.granularity(d("2026-07-30"), d("2026-10-27")))
        assertEquals(Granularity.MONTH, DreamStats.granularity(d("2025-10-28"), d("2026-10-27")))
        assertEquals(Granularity.YEAR, DreamStats.granularity(d("2021-01-01"), d("2026-10-27")))
        val months = DreamStats.buckets(emptyMap(), d("2025-10-28"), d("2026-10-27"), Granularity.MONTH, DayOfWeek.MONDAY)
        assertEquals(13, months.size)
        assertEquals(d("2025-10-28"), months.first().start)
        assertEquals(d("2026-10-27"), months.last().end)
    }

    @Test
    fun topTagsMergeCaseAndRankByUse() {
        val tags = DreamStats.topTags(
            listOf(
                dream("2026-10-01", "07:00", "a", DreamTag.DREAM, DreamTag.LUCID, custom = setOf("Anxious")),
                dream("2026-10-02", "07:00", "b", DreamTag.DREAM, DreamTag.LUCID, custom = setOf("anxious", "Flying")),
                dream("2026-10-03", "07:00", "c", DreamTag.NIGHTMARE, DreamTag.RECURRING, custom = setOf("Anxious")),
            ),
        )
        assertEquals(
            listOf(TagCount("Anxious", 3), TagCount("Lucid", 2, DreamTag.LUCID), TagCount("Flying", 1), TagCount("Recurring", 1, DreamTag.RECURRING)),
            tags,
        )
    }

    @Test
    fun monthGridPadsToWholeWeeks() {
        // October 2026 starts on a Thursday and has 31 days.
        val monday = DreamStats.monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(35, monday.size)
        assertEquals(listOf(null, null, null), monday.take(3))
        assertEquals(d("2026-10-01"), monday[3])
        val sunday = DreamStats.monthGrid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(d("2026-10-01"), sunday[4])
        assertEquals(35, sunday.size)
    }

    @Test
    fun axisTicksAreRound() {
        assertEquals(listOf(0, 1), DreamStats.axisTicks(0))
        assertEquals(listOf(0, 1, 2, 3), DreamStats.axisTicks(3))
        assertEquals(listOf(0, 2, 4, 6, 8), DreamStats.axisTicks(7))
        assertEquals(listOf(0, 5, 10, 15), DreamStats.axisTicks(12))
        assertEquals(listOf(0, 10, 20, 30, 40), DreamStats.axisTicks(31))
    }
}
