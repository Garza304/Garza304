package com.garza.dreamcatcher.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.garza.dreamcatcher.data.Alarm
import com.garza.dreamcatcher.data.days
import com.garza.dreamcatcher.ui.MainActivity
import java.time.ZonedDateTime

/** Registers alarms with the system [AlarmManager] so they fire even if the app is not running. */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Schedules the next occurrence of [alarm] after [now] and returns its time in epoch millis. */
    fun schedule(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val trigger = AlarmTime.nextTrigger(alarm.hour, alarm.minute, alarm.days, now)
            .toInstant().toEpochMilli()
        setAlarmClock(trigger, ringIntent(alarm.id, alarm.label, alarm.vibrate, snooze = false))
        return trigger
    }

    fun snooze(info: RingInfo, minutes: Int): Long {
        val trigger = System.currentTimeMillis() + minutes * 60_000L
        setAlarmClock(trigger, ringIntent(info.alarmId, info.label, info.vibrate, snooze = true))
        return trigger
    }

    /** Cancels both the regular occurrence and any pending snooze of an alarm. */
    fun cancel(alarmId: Long) {
        cancelPending(alarmId, snooze = false)
        cancelPending(alarmId, snooze = true)
    }

    fun cancelSnooze(alarmId: Long) = cancelPending(alarmId, snooze = true)

    private fun cancelPending(alarmId: Long, snooze: Boolean) {
        val intent = ringIntent(alarmId, "", vibrate = false, snooze = snooze)
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { alarmManager.cancel(it); it.cancel() }
    }

    fun rescheduleAll(alarms: List<Alarm>) {
        alarms.filter { it.enabled }.forEach { schedule(it) }
    }

    private fun setAlarmClock(triggerAtMillis: Long, intent: Intent) {
        val operation = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            // setAlarmClock is exact, survives Doze, and shows the alarm icon in the status bar.
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), operation)
        } catch (e: SecurityException) {
            // Exact alarms were revoked; ring as close to on time as the system allows.
            Log.w(TAG, "Exact alarm permission missing, falling back to inexact alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun ringIntent(alarmId: Long, label: String, vibrate: Boolean, snooze: Boolean): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            // A distinct data URI per alarm (and per snooze) keeps their PendingIntents separate.
            data = Uri.parse("dreamcatcher://${if (snooze) "snooze" else "alarm"}/$alarmId")
            RingInfo(alarmId, label, vibrate).putInto(this)
            putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        }

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
