package com.garza.dreamcatcher.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.data.CustomTags
import com.garza.dreamcatcher.data.DreamTag

val DreamTag.icon: ImageVector
    get() = when (this) {
        DreamTag.DREAM -> Icons.Filled.Cloud
        DreamTag.NIGHTMARE -> Icons.Filled.MoodBad
        DreamTag.NO_DREAM -> Icons.Filled.Bedtime
        DreamTag.LUCID -> Icons.Filled.AutoAwesome
        DreamTag.RECURRING -> Icons.Filled.Repeat
    }

/** Icon shown beside tags the user made. */
val CustomTagIcon: ImageVector get() = Icons.Filled.Tag

/** One chip in a [ChipRow]. */
data class ChipSpec(
    val label: String,
    val icon: ImageVector?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** A horizontally scrolling row of filter chips. */
@Composable
fun ChipRow(chips: List<ChipSpec>, modifier: Modifier = Modifier, horizontalPadding: Dp = 0.dp) {
    Row(
        // Padding inside the scroll keeps the edges clear while letting chips scroll to the screen edge.
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { ChipFor(it) }
    }
}

@Composable
fun ChipFor(chip: ChipSpec) {
    FilterChip(
        selected = chip.selected,
        onClick = chip.onClick,
        label = { Text(chip.label) },
        leadingIcon = if (chip.icon != null) {
            { Icon(chip.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

/** Asks for a new tag's name. [onAdd] returns an error to show, or null when the tag was added. */
@Composable
fun NewTagDialog(onAdd: (String) -> String?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        error = onAdd(name)
        if (error == null) onDismiss()
    }

    val message = error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CustomTags.MAX_LENGTH); error = null },
                    modifier = Modifier.focusRequester(focus),
                    placeholder = { Text("Flying, Falling, Melatonin…") },
                    singleLine = true,
                    isError = message != null,
                    supportingText = if (message != null) {
                        { Text(message) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Text(
                    "Your tags show up on every new entry and in the journal filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = ::submit) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
