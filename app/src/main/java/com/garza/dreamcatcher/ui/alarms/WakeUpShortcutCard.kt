package com.garza.dreamcatcher.ui.alarms

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.dream.WakeUpShortcut

/** Settings for recording a dream from the lock screen when you wake before the alarm. */
@Composable
fun WakeUpShortcutCard() {
    val context = LocalContext.current
    var notificationOn by remember { mutableStateOf(WakeUpShortcut.isNotificationEnabled(context)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Woke up before your alarm?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Start a dream recording straight from the lock screen, without unlocking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Lock screen shortcut", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A silent \"Record your dream\" notification that stays on your lock screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = notificationOn,
                    onCheckedChange = {
                        notificationOn = it
                        WakeUpShortcut.setNotificationEnabled(context, it)
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Quick Settings tile", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (WakeUpShortcut.canRequestTile) {
                            "Swipe down from the top of the lock screen and tap \"Record dream\"."
                        } else {
                            "Swipe down twice, tap the pencil to edit your tiles, and drag in \"Record dream\"."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (WakeUpShortcut.canRequestTile) {
                    FilledTonalButton(onClick = {
                        WakeUpShortcut.requestTile(context) { message ->
                            message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text("Add tile") }
                }
            }
        }
    }
}
