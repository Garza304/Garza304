package com.garza.dreamcatcher.ui.alarms

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.garza.dreamcatcher.alarm.AlarmTime
import com.garza.dreamcatcher.data.Alarm
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Create or edit an alarm. [alarm] is null for a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditDialog(
    alarm: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = alarm?.hour ?: 7,
        initialMinute = alarm?.minute ?: 0,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    var repeatMask by rememberSaveable { mutableStateOf(alarm?.repeatDays ?: 0) }
    var label by rememberSaveable { mutableStateOf(alarm?.label.orEmpty()) }
    var vibrate by rememberSaveable { mutableStateOf(alarm?.vibrate ?: true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(16.dp).widthIn(max = 420.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(if (alarm == null) "New alarm" else "Edit alarm", style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }

                Text("Repeat", style = MaterialTheme.typography.titleSmall)
                DayPicker(
                    selected = AlarmTime.maskToDays(repeatMask),
                    onToggle = { day -> repeatMask = repeatMask xor AlarmTime.daysToMask(setOf(day)) },
                )
                Text(
                    AlarmTime.describeDays(AlarmTime.maskToDays(repeatMask)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text("Good morning") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alarm != null) {
                        TextButton(onClick = { onDelete(alarm) }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        val edited = (alarm ?: Alarm(hour = 0, minute = 0)).copy(
                            hour = timeState.hour,
                            minute = timeState.minute,
                            repeatDays = repeatMask,
                            label = label.trim(),
                            vibrate = vibrate,
                            // Saving an alarm means you want it to ring.
                            enabled = true,
                        )
                        onSave(edited)
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun DayPicker(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = Locale.getDefault()
    val days = remember(locale) {
        val first = WeekFields.of(locale).firstDayOfWeek
        (0L until 7L).map { first.plus(it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            val isSelected = day in selected
            Surface(
                onClick = { onToggle(day) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
