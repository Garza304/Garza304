package com.garza.dreamcatcher.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.garza.dreamcatcher.DreamcatcherApp
import com.garza.dreamcatcher.R
import com.garza.dreamcatcher.app
import com.garza.dreamcatcher.dream.DreamEntryActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that rings an alarm: loops the alarm sound with a gentle fade-in, vibrates,
 * and posts a full-screen notification that opens [AlarmRingActivity] over the lock screen.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volume = START_VOLUME
    private var current: RingInfo? = null

    private val fadeIn = object : Runnable {
        override fun run() {
            volume = (volume + VOLUME_STEP).coerceAtMost(1f)
            player?.setVolume(volume, volume)
            if (volume < 1f) handler.postDelayed(this, FADE_STEP_MS)
        }
    }

    private val timeout = Runnable {
        postReminder("Alarm stopped after $TIMEOUT_MINUTES minutes", "Tap to record your dream while you still remember it.")
        stopRinging()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> {
                val info = current ?: RingInfo.from(intent)
                app.scheduler.snooze(info, SNOOZE_MINUTES)
                postSnoozedNotification(info)
                stopRinging()
            }
            else -> startRinging(RingInfo.from(intent))
        }
        return START_NOT_STICKY
    }

    private fun startRinging(info: RingInfo) {
        val firstRing = current == null
        current = info
        _ringing.value = info
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_RINGING,
            buildRingingNotification(info),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_SNOOZED)

        if (firstRing) {
            playSound()
            if (info.vibrate) vibrate()
        }
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, TIMEOUT_MINUTES * 60_000L)
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        current = null
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (current != null) stopRinging()
        super.onDestroy()
    }

    private fun playSound() {
        val candidates = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            player = createPlayer(uri) ?: continue
            volume = START_VOLUME
            player?.setVolume(volume, volume)
            player?.start()
            handler.postDelayed(fadeIn, FADE_STEP_MS)
            return
        }
        Log.e(TAG, "No playable alarm sound found")
    }

    private fun createPlayer(uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not play $uri", e)
        null
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0), attributes)
        vibrator = v
    }

    private fun buildRingingNotification(info: RingInfo): Notification {
        val ringScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmRingActivity::class.java).also { info.putInto(it) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val record = PendingIntent.getActivity(
            this, 1,
            DreamEntryActivity.fromAlarmIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getService(
            this, 2,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE).also { info.putInto(it) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, DreamcatcherApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(info.label.ifBlank { "Good morning" })
            .setContentText("What did you dream? Tap to record it.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(ringScreen)
            .setFullScreenIntent(ringScreen, true)
            .addAction(0, "Snooze", snooze)
            .addAction(0, "Record dream", record)
            .build()
    }

    private fun postSnoozedNotification(info: RingInfo) {
        val cancel = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_CANCEL_SNOOZE).also { info.putInto(it) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, DreamcatcherApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Snoozed for $SNOOZE_MINUTES minutes")
            .setContentText("Try to hold on to your dream.")
            .setOngoing(true)
            .addAction(0, "Cancel snooze", cancel)
            .build()
        notifyIfAllowed(NOTIFICATION_SNOOZED, notification)
    }

    private fun postReminder(title: String, text: String) {
        val record = PendingIntent.getActivity(
            this, 3,
            DreamEntryActivity.fromAlarmIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, DreamcatcherApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(record)
            .setAutoCancel(true)
            .build()
        notifyIfAllowed(NOTIFICATION_REMINDER, notification)
    }

    private fun notifyIfAllowed(id: Int, notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_RING = "com.garza.dreamcatcher.RING"
        private const val ACTION_DISMISS = "com.garza.dreamcatcher.DISMISS"
        private const val ACTION_SNOOZE = "com.garza.dreamcatcher.SNOOZE_ALARM"

        const val SNOOZE_MINUTES = 9
        private const val TIMEOUT_MINUTES = 10
        private const val START_VOLUME = 0.1f
        private const val VOLUME_STEP = 0.03f
        private const val FADE_STEP_MS = 1_000L

        private const val NOTIFICATION_RINGING = 1
        const val NOTIFICATION_SNOOZED = 2
        private const val NOTIFICATION_REMINDER = 3

        private val _ringing = MutableStateFlow<RingInfo?>(null)

        /** The alarm currently ringing, or null when silent. */
        val ringing: StateFlow<RingInfo?> = _ringing.asStateFlow()

        fun ring(context: Context, info: RingInfo) {
            val intent = Intent(context, AlarmService::class.java).setAction(ACTION_RING)
            ContextCompat.startForegroundService(context, info.putInto(intent))
        }

        fun dismiss(context: Context) {
            if (_ringing.value == null) return
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS))
        }

        fun snooze(context: Context) {
            if (_ringing.value == null) return
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))
        }
    }
}
