package com.garza.dreamcatcher.dream

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garza.dreamcatcher.data.CustomTags
import com.garza.dreamcatcher.data.DreamTag
import com.garza.dreamcatcher.ui.ChipFor
import com.garza.dreamcatcher.ui.ChipSpec
import com.garza.dreamcatcher.ui.CustomTagIcon
import com.garza.dreamcatcher.ui.NewTagDialog
import com.garza.dreamcatcher.ui.icon

private val KINDS = listOf(DreamTag.DREAM, DreamTag.NIGHTMARE, DreamTag.NO_DREAM)
private val EXTRAS = DreamTag.entries.filterNot { it.isKind }

// ---------------------------------------------------------------------------------------------
// Voice first: listening

/** Nearly empty screen for talking with your eyes half closed; words appear in large type. */
@Composable
fun ListeningScreen(
    text: String,
    partial: String,
    listening: Boolean,
    message: String?,
    onToggleMic: () -> Unit,
    onTypeInstead: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (listening) colors.primary else colors.outline, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    listening -> "Listening"
                    text.isEmpty() -> "Ready"
                    else -> "Paused"
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            CloseButton(onClose)
        }

        // Newest words stay in view at the bottom; scroll up to see the start.
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.BottomStart) {
            Column(Modifier.verticalScroll(rememberScrollState(), reverseScrolling = true)) {
                Text(
                    buildAnnotatedString {
                        if (text.isEmpty() && partial.isEmpty()) {
                            withStyle(SpanStyle(color = colors.outline)) { append("Tell me everything you remember…") }
                        }
                        withStyle(SpanStyle(color = colors.onBackground)) { append(text) }
                        if (partial.isNotEmpty()) {
                            if (text.isNotEmpty()) append(' ')
                            withStyle(SpanStyle(color = colors.primary, fontStyle = FontStyle.Italic)) { append(partial) }
                        }
                    },
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    lineHeight = 35.sp,
                )
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicOrb(listening = listening, onClick = onToggleMic)
            Spacer(Modifier.height(20.dp))
            Text(
                if (listening) "Keep your eyes closed. Just talk." else "Tap the mic to start talking.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onTypeInstead, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Type instead")
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.onBackground, contentColor = colors.background),
                ) { Text("I'm done") }
            }
        }
    }
}

@Composable
private fun MicOrb(listening: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "orb")
    val ring by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.1f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "orbRing",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(200.dp).scale(ring).background(colors.primary.copy(alpha = 0.08f), CircleShape))
        Box(Modifier.size(160.dp).background(colors.primary.copy(alpha = 0.14f), CircleShape))
        Surface(onClick = onClick, shape = CircleShape, color = colors.primary, modifier = Modifier.size(120.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (listening) "Pause listening" else "Start talking",
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Guided questions

/** Short prompts that jog your memory; each answer has its own mic. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidedScreen(
    createdAt: Long,
    what: String,
    onWhatChange: (String) -> Unit,
    who: String,
    onWhoChange: (String) -> Unit,
    where: String,
    onWhereChange: (String) -> Unit,
    activeField: GuidedField,
    listening: Boolean,
    partial: String,
    voiceAvailable: Boolean,
    customTags: Set<String>,
    onToggleFeeling: (String) -> Unit,
    message: String?,
    onMic: (GuidedField) -> Unit,
    onDontRemember: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("What did you dream?", style = MaterialTheme.typography.titleLarge)
                DateLine(createdAt)
            }
            CloseButton(onClose)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuestionCard(
                label = "What happened?",
                value = what,
                onValueChange = onWhatChange,
                placeholder = "The story, in any order",
                multiLine = true,
                showMic = voiceAvailable,
                micActive = listening && activeField == GuidedField.WHAT,
                partial = if (listening && activeField == GuidedField.WHAT) partial else "",
                onMic = { onMic(GuidedField.WHAT) },
            )
            QuestionCard(
                label = "Who was there?",
                value = who,
                onValueChange = onWhoChange,
                placeholder = "People, animals, strangers",
                multiLine = false,
                showMic = voiceAvailable,
                micActive = listening && activeField == GuidedField.WHO,
                partial = if (listening && activeField == GuidedField.WHO) partial else "",
                onMic = { onMic(GuidedField.WHO) },
            )
            QuestionCard(
                label = "Where were you?",
                value = where,
                onValueChange = onWhereChange,
                placeholder = "A place, real or not",
                multiLine = false,
                showMic = voiceAvailable,
                micActive = listening && activeField == GuidedField.WHERE,
                partial = if (listening && activeField == GuidedField.WHERE) partial else "",
                onMic = { onMic(GuidedField.WHERE) },
            )
            QuestionSurface {
                Text("How did it feel?", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomTags.FEELINGS.forEach { feeling ->
                        FilterChip(
                            selected = customTags.any { it.equals(feeling, ignoreCase = true) },
                            onClick = { onToggleFeeling(feeling) },
                            label = { Text(feeling) },
                        )
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
        }

        Surface(color = colors.surfaceContainer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onDontRemember, modifier = Modifier.height(48.dp)) { Text("I don't remember") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onNext, modifier = Modifier.height(48.dp)) { Text("Next") }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    multiLine: Boolean,
    showMic: Boolean,
    micActive: Boolean,
    partial: String,
    onMic: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    QuestionSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.weight(1f))
            if (showMic) {
                Surface(
                    onClick = onMic,
                    shape = CircleShape,
                    color = if (micActive) colors.secondary else Color.Transparent,
                    contentColor = if (micActive) colors.onSecondary else colors.onSurface,
                    border = if (micActive) null else BorderStroke(1.dp, colors.outline),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (micActive) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (micActive) "Stop listening" else "Speak your answer to $label",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = !multiLine,
            minLines = if (multiLine) 4 else 1,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = transparentFieldColors(),
        )
        if (partial.isNotEmpty()) {
            Text(partial, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = colors.primary)
        }
    }
}

@Composable
private fun QuestionSurface(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) { content() }
    }
}

// ---------------------------------------------------------------------------------------------
// Review and tag (also the editor for saved dreams)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    isNew: Boolean,
    createdAt: Long,
    text: String,
    onTextChange: (String) -> Unit,
    focusText: Boolean,
    onFocusHandled: () -> Unit,
    tags: Set<DreamTag>,
    onToggleTag: (DreamTag) -> Unit,
    customTags: Set<String>,
    availableCustomTags: List<String>,
    onToggleCustomTag: (String) -> Unit,
    onAddCustomTag: (String) -> String?,
    voiceAvailable: Boolean,
    onKeepTalking: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTag by rememberSaveable { mutableStateOf(false) }
    val textFocus = remember { FocusRequester() }
    LaunchedEffect(focusText) {
        if (focusText) {
            textFocus.requestFocus()
            onFocusHandled()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier.statusBarsPadding().padding(start = 24.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    !isNew -> "Your dream"
                    text.isBlank() -> "How was the night?"
                    else -> "Got it."
                },
                fontFamily = FontFamily.Serif,
                fontSize = 32.sp,
                color = colors.secondary,
                modifier = Modifier.weight(1f),
            )
            if (!isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete dream")
                }
            }
            CloseButton(onClose)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DateLine(createdAt)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (DreamTag.NO_DREAM in tags) "NOTES · OPTIONAL" else "YOUR DREAM · TAP TO EDIT",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(textFocus),
                    placeholder = {
                        Text(
                            if (DreamTag.NO_DREAM in tags) "How you slept, or any fragments" else "Everything you remember"
                        )
                    },
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainer,
                        unfocusedContainerColor = colors.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What kind of night was it?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KINDS.forEach { kind ->
                        KindButton(kind, selected = kind in tags, onClick = { onToggleTag(kind) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Anything else?", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EXTRAS.forEach { tag ->
                        ChipFor(ChipSpec(tag.label, tag.icon, tag in tags) { onToggleTag(tag) })
                    }
                    availableCustomTags.forEach { name ->
                        val selected = customTags.any { it.equals(name, ignoreCase = true) }
                        ChipFor(ChipSpec(name, CustomTagIcon, selected) { onToggleCustomTag(name) })
                    }
                    AssistChip(
                        onClick = { addingTag = true },
                        label = { Text("New tag") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (isNew) "Save dream" else "Save changes")
            }
            if (voiceAvailable) {
                TextButton(onClick = onKeepTalking, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (text.isBlank()) "Talk instead" else "Keep talking")
                }
            }
        }
    }

    if (addingTag) {
        NewTagDialog(onAdd = onAddCustomTag, onDismiss = { addingTag = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this dream?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun KindButton(kind: DreamTag, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.surfaceVariant),
        modifier = modifier.height(76.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(kind.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(kind.label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared bits

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Save and close") }
}

@Composable
private fun DateLine(createdAt: Long) {
    Text(
        "${DreamText.formatDate(createdAt)} · ${DreamText.formatTime(createdAt)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
