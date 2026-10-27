package com.garza.dreamcatcher.ui.alarms

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garza.dreamcatcher.alarm.AlarmTime
import com.garza.dreamcatcher.data.Alarm
import com.garza.dreamcatcher.data.days
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
fun AlarmsScreen(
    alarms: List<Alarm>?,
    onEdit: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    // Keeps the "rings in" countdowns current.
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            delay(30_000)
            value = ZonedDateTime.now()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PermissionsCard() }
        when {
            alarms == null -> Unit
            alarms.isEmpty() -> item { EmptyAlarms() }
            else -> items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    is24Hour = is24Hour,
                    now = now,
                    onClick = { onEdit(alarm) },
                    onToggle = { onToggle(alarm, it) },
                )
            }
        }
        item { WakeUpShortcutCard() }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    is24Hour: Boolean,
    now: ZonedDateTime,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val contentAlpha = if (alarm.enabled) 1f else 0.5f
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    AlarmTime.formatTime(alarm.hour, alarm.minute, is24Hour),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
                val details = listOf(alarm.label, AlarmTime.describeDays(alarm.days)).filter { it.isNotBlank() }
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
                if (alarm.enabled) {
                    val next = AlarmTime.nextTrigger(alarm.hour, alarm.minute, alarm.days, now)
                    Text(
                        AlarmTime.describeCountdown(now, next),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EmptyAlarms() {
    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "No alarms yet.\nAdd one and Dreamcatcher will ask about your dreams the moment you wake.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
