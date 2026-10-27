package com.garza.dreamcatcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garza.dreamcatcher.backup.AutoBackup
import com.garza.dreamcatcher.backup.Backup
import com.garza.dreamcatcher.backup.BackupException
import com.garza.dreamcatcher.backup.BackupFormat
import com.garza.dreamcatcher.backup.RestorePlan
import com.garza.dreamcatcher.dream.DreamText
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Settings rows for saving the journal to a file, restoring it, and backing up daily. */
@Composable
fun BackupSection(onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auto by AutoBackup.status.collectAsStateWithLifecycle()
    var plan by remember { mutableStateOf<RestorePlan?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val count = Backup.writeTo(context, uri)
                toast(if (count == 1) "Backed up 1 dream" else "Backed up $count dreams")
            } catch (e: Exception) {
                error = "Couldn't save the backup there. Try another folder."
            }
        }
    }
    // Any type: cloud drives often label JSON files as plain text or binary.
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                plan = Backup.plan(context, uri)
            } catch (e: BackupException) {
                error = e.message
            } catch (e: Exception) {
                error = "Couldn't read that file."
            }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                AutoBackup.enable(context, uri)
            } catch (e: Exception) {
                error = "That folder can't be used for backups. Try a folder on your phone."
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Backup", style = MaterialTheme.typography.titleSmall)
        Text(
            "Your dreams are only stored on this phone. Save a backup to keep them safe if you lose it or switch phones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { save.launch(BackupFormat.fileName(LocalDate.now())) },
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Back up")
            }
            OutlinedButton(
                onClick = { open.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Restore")
            }
        }

        val enabled = auto.folder != null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = { on -> if (on) pickFolder.launch(null) else AutoBackup.disable(context) },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Back up every day", style = MaterialTheme.typography.bodyLarge)
                val detail = when {
                    !enabled -> "Saves a copy to a folder you choose. The last 7 days are kept."
                    else -> buildString {
                        append("To ").append(auto.folderName ?: "your chosen folder")
                        auto.lastBackupAt?.let {
                            append(" · Last saved ").append(DreamText.formatDate(it)).append(", ").append(DreamText.formatTime(it))
                        }
                    }
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (enabled) {
                    auto.lastError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // The whole row handles the click, so the switch itself doesn't.
            Switch(checked = enabled, onCheckedChange = null)
        }
    }

    plan?.let { current ->
        RestoreDialog(
            plan = current,
            onDismiss = { plan = null },
            onConfirm = {
                plan = null
                scope.launch {
                    try {
                        Backup.restore(context, current)
                        val count = current.newDreams.size
                        toast(
                            when (count) {
                                0 -> "Backup restored"
                                1 -> "Restored 1 dream"
                                else -> "Restored $count dreams"
                            }
                        )
                        onRestored()
                    } catch (e: Exception) {
                        error = "Restoring failed, so nothing was changed."
                    }
                }
            },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Backup") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun RestoreDialog(plan: RestorePlan, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val backup = plan.backup
    fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"
    val nothingNew = plan.newDreams.isEmpty() && plan.newAlarms.isEmpty() && !plan.restoreSettings

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (nothingNew) "Already up to date" else "Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Saved ${DreamText.formatDate(backup.exportedAt)}, with " +
                        count(backup.dreams.size, "dream", "dreams") + " and " +
                        count(backup.alarms.size, "alarm", "alarms") + "."
                )
                if (nothingNew) {
                    Text("Everything in it is already in the app.")
                } else {
                    Text(
                        "This adds " + count(plan.newDreams.size, "dream", "dreams") + " and " +
                            count(plan.newAlarms.size, "alarm", "alarms") + " you don't have yet. " +
                            "Nothing already in the app is changed or deleted."
                    )
                    if (plan.restoreSettings) Text("Your color scheme and entry style will be restored too.")
                }
            }
        },
        confirmButton = {
            if (nothingNew) {
                TextButton(onClick = onDismiss) { Text("OK") }
            } else {
                TextButton(onClick = onConfirm) { Text("Restore") }
            }
        },
        dismissButton = if (nothingNew) null else {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        },
    )
}
