package com.garza.dreamcatcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garza.dreamcatcher.EntryStyle
import com.garza.dreamcatcher.Settings
import com.garza.dreamcatcher.ui.theme.Palette
import com.garza.dreamcatcher.ui.theme.Palettes

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(Settings.entryStyle(context)) }
    val palette by Settings.palette.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color scheme", style = MaterialTheme.typography.titleSmall)
                PaletteGrid("Dark", Palettes.all.filter { it.isDark }, palette) { Settings.setPalette(context, it) }
                PaletteGrid("Light", Palettes.all.filterNot { it.isDark }, palette) { Settings.setPalette(context, it) }

                Spacer(Modifier.height(8.dp))
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

                Spacer(Modifier.height(8.dp))
                BackupSection(onRestored = { style = Settings.entryStyle(context) })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun PaletteGrid(title: String, palettes: List<Palette>, current: Palette, onPick: (Palette) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        palettes.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { palette ->
                    PaletteSwatch(palette, palette.id == current.id, { onPick(palette) }, Modifier.weight(1f))
                }
                // Keep a short last row's swatches the same width as the rest.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A miniature of the scheme: "Aa" in its text color on its background, with its two highlight colors. */
@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(palette.background),
            border = if (selected) BorderStroke(3.dp, colors.primary) else BorderStroke(1.dp, colors.outline),
            modifier = Modifier.size(56.dp).clearAndSetSemantics {},
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Aa", color = Color(palette.text), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(12.dp).background(Color(palette.primary), CircleShape))
                    Box(Modifier.size(12.dp).background(Color(palette.accent), CircleShape))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            palette.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) colors.primary else colors.onSurface,
        )
    }
}
