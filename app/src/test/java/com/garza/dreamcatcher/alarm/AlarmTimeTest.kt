package com.garza.dreamcatcher.alarm

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.EnumSet
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTimeTest {

    private val zone = ZoneId.of("America/Chicago")

    // 2026-10-26 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int, month: Int = 10) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    @Test
    fun oneTimeAlarmLaterToday() {
        assertEquals(at(26, 7, 30), AlarmTime.nextTrigger(7, 30, emptySet(), at(26, 6, 0)))
    }

    @Test
    fun oneTimeAlarmAlreadyPassedRingsTomorrow() {
        assertEquals(at(27, 7, 30), AlarmTime.nextTrigger(7, 30, emptySet(), at(26, 8, 0)))
    }

    @Test
    fun alarmAtExactlyNowRingsTomorrow() {
        assertEquals(at(27, 7, 30), AlarmTime.nextTrigger(7, 30, emptySet(), at(26, 7, 30)))
    }

    @Test
    fun repeatingAlarmSkipsToNextSelectedDay() {
        val days = EnumSet.of(WEDNESDAY, FRIDAY)
        assertEquals(at(28, 7, 0), AlarmTime.nextTrigger(7, 0, days, at(26, 6, 0)))
    }

    @Test
    fun repeatingAlarmOnTodayAfterTimeWaitsAWeek() {
        val days = EnumSet.of(MONDAY)
        assertEquals(at(2, 7, 0, month = 11), AlarmTime.nextTrigger(7, 0, days, at(26, 8, 0)))
    }

    @Test
    fun springForwardGapShiftsLater() {
        // 2027-03-14 02:30 does not exist in Chicago; it resolves to 03:30 CDT.
        val now = ZonedDateTime.of(2027, 3, 14, 1, 0, 0, 0, zone)
        val next = AlarmTime.nextTrigger(2, 30, emptySet(), now)
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun maskRoundTrips() {
        val days = EnumSet.of(MONDAY, WEDNESDAY, SUNDAY)
        assertEquals(days, AlarmTime.maskToDays(AlarmTime.daysToMask(days)))
        assertEquals(0b1000101, AlarmTime.daysToMask(days))
        assertEquals(emptySet<DayOfWeek>(), AlarmTime.maskToDays(0))
    }

    @Test
    fun describesDays() {
        assertEquals("Once", AlarmTime.describeDays(emptySet()))
        assertEquals("Every day", AlarmTime.describeDays(EnumSet.allOf(DayOfWeek::class.java)))
        assertEquals("Weekdays", AlarmTime.describeDays(EnumSet.range(MONDAY, FRIDAY)))
        assertEquals("Weekends", AlarmTime.describeDays(EnumSet.of(SATURDAY, SUNDAY)))
        assertEquals("Mon, Fri", AlarmTime.describeDays(EnumSet.of(FRIDAY, MONDAY), Locale.US))
    }

    @Test
    fun formatsTime() {
        assertEquals("07:05", AlarmTime.formatTime(7, 5, is24Hour = true))
        assertEquals("7:05 AM", AlarmTime.formatTime(7, 5, is24Hour = false))
        assertEquals("12:00 AM", AlarmTime.formatTime(0, 0, is24Hour = false))
        assertEquals("12:30 PM", AlarmTime.formatTime(12, 30, is24Hour = false))
        assertEquals("11:59 PM", AlarmTime.formatTime(23, 59, is24Hour = false))
    }

    @Test
    fun describesCountdown() {
        val now = at(26, 22, 0)
        assertEquals("Rings in 8 h 30 min", AlarmTime.describeCountdown(now, at(27, 6, 30)))
        assertEquals("Rings in 9 h", AlarmTime.describeCountdown(now, at(27, 7, 0)))
        assertEquals("Rings in 1 min", AlarmTime.describeCountdown(now, now.plusSeconds(20)))
        assertEquals("Rings in 2 d 1 h", AlarmTime.describeCountdown(now, at(28, 23, 0)))
    }
}
