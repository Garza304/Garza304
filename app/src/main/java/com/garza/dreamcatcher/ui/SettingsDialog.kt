package com.garza.dreamcatcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.EntryStyle
import com.garza.dreamcatcher.Settings

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(Settings.entryStyle(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When you start a new dream", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    EntryStyle.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == style,
                                    onClick = {
                                        style = option
                                        Settings.setEntryStyle(context, option)
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // The whole row handles the click, so the button itself doesn't.
                            RadioButton(selected = option == style, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(option.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
