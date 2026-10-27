package com.garza.dreamcatcher.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.garza.dreamcatcher.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager forgets everything on reboot, and clock changes shift trigger times; re-arm all alarms. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.app.scheduler.rescheduleAll(context.app.database.alarmDao().getEnabled())
            } finally {
                pending.finish()
            }
        }
    }
}
