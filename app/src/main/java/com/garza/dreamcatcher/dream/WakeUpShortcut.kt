package com.garza.dreamcatcher.dream

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.garza.dreamcatcher.DreamcatcherApp
import com.garza.dreamcatcher.R

/**
 * Ways to start recording a dream straight from the lock screen when you wake before the alarm:
 * an optional always-there notification, and the Quick Settings tile ([DreamTileService]).
 */
object WakeUpShortcut {

    private const val PREFS = "wake_up_shortcut"
    private const val KEY_NOTIFICATION = "notification_enabled"
    private const val NOTIFICATION_ID = 10

    fun isNotificationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NOTIFICATION, false)

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NOTIFICATION, enabled).apply()
        refreshNotification(context)
    }

    /** Posts or removes the lock screen notification to match the setting. Safe to call any time. */
    fun refreshNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!isNotificationEnabled(context)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 0,
            DreamEntryActivity.wakeUpIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, DreamcatcherApp.CHANNEL_WAKE_UP)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Woke up? Record your dream")
            .setContentText("Tap to start speaking. No need to unlock.")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Whether the app can ask the system to add its tile (Android 13+); otherwise users add it by editing Quick Settings. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val canRequestTile: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestTile(context: Context, onResult: (message: String?) -> Unit) {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, DreamTileService::class.java),
            "Record dream",
            Icon.createWithResource(context, R.drawable.ic_stat_alarm),
            context.mainExecutor,
        ) { result ->
            onResult(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added to Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "The tile is already in Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
                    else -> "Couldn't add the tile. Edit Quick Settings and drag in \"Record dream\"."
                }
            )
        }
    }
}
