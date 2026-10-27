package com.garza.dreamcatcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.garza.dreamcatcher.alarm.AlarmScheduler
import com.garza.dreamcatcher.data.AppDatabase
import com.garza.dreamcatcher.dream.WakeUpShortcut

class DreamcatcherApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val scheduler: AlarmScheduler by lazy { AlarmScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        WakeUpShortcut.refreshNotification(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CHANNEL_ALARM, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while an alarm is ringing"
            // AlarmService plays the sound and vibration itself so it can loop and fade in.
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val reminders = NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Snoozed and missed alarms"
        }
        // Default importance so it is shown on the lock screen (silent notifications often aren't), but without sound.
        val wakeUp = NotificationChannel(CHANNEL_WAKE_UP, "Lock screen shortcut", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A lock screen button for recording a dream when you wake before your alarm"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannels(listOf(alarm, reminders, wakeUp))
    }

    companion object {
        const val CHANNEL_ALARM = "alarm"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_WAKE_UP = "wake_up"
    }
}

val android.content.Context.app: DreamcatcherApp get() = applicationContext as DreamcatcherApp
