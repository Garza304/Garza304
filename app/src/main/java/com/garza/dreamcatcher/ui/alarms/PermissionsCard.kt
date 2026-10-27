package com.garza.dreamcatcher.ui.alarms

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.garza.dreamcatcher.app

private class Requirement(val title: String, val detail: String, val fix: () -> Unit)

/** Lists anything that would stop an alarm from ringing or a dream from being dictated, with a button to fix it. */
@Composable
fun PermissionsCard() {
    val context = LocalContext.current
    // Bumped on resume and after permission prompts so the checks re-run when the user comes back from Settings.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks++ }

    // Once a runtime prompt has been refused, the system may stop showing it, so send the user to Settings instead.
    var notificationsRefused by rememberSaveable { mutableStateOf(false) }
    var micRefused by rememberSaveable { mutableStateOf(false) }
    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsRefused = !granted
        checks++
    }
    val micPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micRefused = !granted
        checks++
    }

    val requirements = remember(checks, notificationsRefused, micRefused) {
        buildList {
            val notifications = context.getSystemService(NotificationManager::class.java)
            if (!notifications.areNotificationsEnabled()) {
                add(Requirement("Notifications", "Needed to show the alarm screen when it rings.") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsRefused) {
                        notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openSettings(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                })
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !notifications.canUseFullScreenIntent()) {
                add(Requirement("Full-screen alarms", "Lets the alarm open over the lock screen.") {
                    context.openSettings(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, context.packageUri())
                    )
                })
            }
            if (!context.app.scheduler.canScheduleExactAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Requirement("Alarms & reminders", "Needed to ring at exactly the right time.") {
                    context.openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, context.packageUri()))
                })
            }
            val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!micGranted) {
                add(Requirement("Microphone", "So you can speak your dream instead of typing it.") {
                    if (!micRefused) {
                        micPrompt.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        context.openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.packageUri()))
                    }
                })
            }
        }
    }
    if (requirements.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A few things to set up", style = MaterialTheme.typography.titleMedium)
            requirements.forEach { requirement ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(requirement.title, style = MaterialTheme.typography.bodyLarge)
                        Text(requirement.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = requirement.fix) { Text("Allow") }
                }
            }
        }
    }
}

private fun Context.packageUri(): Uri = Uri.parse("package:$packageName")

private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))
    }
}
