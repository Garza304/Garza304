package com.garza.dreamcatcher.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.garza.dreamcatcher.app
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired by AlarmManager when an alarm is due. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val info = RingInfo.from(intent)
        if (intent.action == ACTION_CANCEL_SNOOZE) {
            context.app.scheduler.cancelSnooze(info.alarmId)
            context.getSystemService(NotificationManager::class.java).cancel(AlarmService.NOTIFICATION_SNOOZED)
            return
        }
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)

        // Start ringing right away; bookkeeping can follow.
        AlarmService.ring(context, info)

        if (snooze) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = context.app.database.alarmDao()
                val alarm = dao.get(info.alarmId) ?: return@launch
                if (alarm.repeatDays == 0) {
                    dao.update(alarm.copy(enabled = false))
                } else {
                    // Look from a minute ahead so an alarm that fires a hair early can't re-arm for the same minute.
                    context.app.scheduler.schedule(alarm, ZonedDateTime.now().plusMinutes(1))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_SNOOZE = "com.garza.dreamcatcher.CANCEL_SNOOZE"
        const val EXTRA_SNOOZE = "com.garza.dreamcatcher.SNOOZE"
    }
}
