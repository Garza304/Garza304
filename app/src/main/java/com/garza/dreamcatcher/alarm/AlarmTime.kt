package com.garza.dreamcatcher.alarm

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.EnumSet
import java.util.Locale

/** Pure time math for alarms, kept free of Android classes so it can be unit tested. */
object AlarmTime {

    private val WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
    private val WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun daysToMask(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun maskToDays(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filterTo(EnumSet.noneOf(DayOfWeek::class.java)) { mask and (1 shl (it.value - 1)) != 0 }

    /**
     * The first moment strictly after [now] at which an alarm set for [hour]:[minute] should ring.
     * An empty [days] set means a one-time alarm (next occurrence of that time of day).
     * Each candidate day is resolved in [now]'s zone separately, so DST changes are handled.
     */
    fun nextTrigger(hour: Int, minute: Int, days: Set<DayOfWeek>, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        val today = now.toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (days.isNotEmpty() && date.dayOfWeek !in days) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("No trigger found for $hour:$minute on $days")
    }

    fun describeDays(days: Set<DayOfWeek>, locale: Locale = Locale.getDefault()): String = when {
        days.isEmpty() -> "Once"
        days.size == 7 -> "Every day"
        days == WEEKDAYS -> "Weekdays"
        days == WEEKEND -> "Weekends"
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    fun formatTime(hour: Int, minute: Int, is24Hour: Boolean): String =
        if (is24Hour) {
            "%02d:%02d".format(hour, minute)
        } else {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            "%d:%02d %s".format(h12, minute, if (hour < 12) "AM" else "PM")
        }

    /** "Rings in 7 h 5 min" style text for the time between [from] and [to]. */
    fun describeCountdown(from: ZonedDateTime, to: ZonedDateTime): String {
        // Round up so an alarm 30 seconds away reads "1 min", not "0 min".
        val totalMinutes = (Duration.between(from, to).seconds + 59) / 60
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        val parts = buildList {
            if (days > 0) add("$days d")
            if (hours > 0) add("$hours h")
            if (minutes > 0 || isEmpty()) add("$minutes min")
        }
        return "Rings in " + parts.joinToString(" ")
    }
}
