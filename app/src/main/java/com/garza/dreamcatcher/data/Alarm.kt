package com.garza.dreamcatcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.garza.dreamcatcher.alarm.AlarmTime
import java.time.DayOfWeek

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Bitmask of repeat days, see [AlarmTime.daysToMask]. Zero means ring once. */
    val repeatDays: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val vibrate: Boolean = true,
)

/** The repeat days as a set; empty for a one-time alarm. */
val Alarm.days: Set<DayOfWeek> get() = AlarmTime.maskToDays(repeatDays)
