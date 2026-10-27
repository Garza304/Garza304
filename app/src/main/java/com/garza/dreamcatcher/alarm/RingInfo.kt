package com.garza.dreamcatcher.alarm

import android.content.Intent

/** What a ringing alarm needs to know, carried through intents so ringing never waits on the database. */
data class RingInfo(val alarmId: Long, val label: String, val vibrate: Boolean) {

    fun putInto(intent: Intent): Intent = intent
        .putExtra(EXTRA_ALARM_ID, alarmId)
        .putExtra(EXTRA_LABEL, label)
        .putExtra(EXTRA_VIBRATE, vibrate)

    companion object {
        private const val EXTRA_ALARM_ID = "com.garza.dreamcatcher.ALARM_ID"
        private const val EXTRA_LABEL = "com.garza.dreamcatcher.LABEL"
        private const val EXTRA_VIBRATE = "com.garza.dreamcatcher.VIBRATE"

        fun from(intent: Intent?): RingInfo = RingInfo(
            alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L,
            label = intent?.getStringExtra(EXTRA_LABEL).orEmpty(),
            vibrate = intent?.getBooleanExtra(EXTRA_VIBRATE, true) ?: true,
        )
    }
}
