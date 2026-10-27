package com.garza.dreamcatcher.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.data.Dream
import com.garza.dreamcatcher.data.DreamTag
import com.garza.dreamcatcher.dream.DreamText
import com.garza.dreamcatcher.ui.ChipRow
import com.garza.dreamcatcher.ui.ChipSpec
import com.garza.dreamcatcher.ui.CustomTagIcon
import com.garza.dreamcatcher.ui.icon

@Composable
fun JournalScreen(
    dreams: List<Dream>?,
    query: String,
    onQueryChange: (String) -> Unit,
    customTags: List<String>,
    tagFilter: TagFilter?,
    onTagFilterChange: (TagFilter?) -> Unit,
    onOpen: (Dream) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            // Leave room for the floating action button.
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search your dreams") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
        }
        item {
            // Tapping the active filter again shows everything.
            fun spec(filter: TagFilter, label: String, icon: ImageVector) =
                ChipSpec(label, icon, filter == tagFilter) { onTagFilterChange(if (filter == tagFilter) null else filter) }
            ChipRow(
                DreamTag.entries.map { spec(TagFilter.BuiltIn(it), it.label, it.icon) } +
                    customTags.map { spec(TagFilter.Custom(it), it, CustomTagIcon) }
            )
        }
        when {
            dreams == null -> Unit
            dreams.isEmpty() && (query.isNotBlank() || tagFilter != null) -> item {
                EmptyState("No entries match.")
            }
            dreams.isEmpty() -> item {
                EmptyState("No dreams yet.\nSet an alarm, and when it rings you'll be asked what you dreamed. Or tap + to write one now.")
            }
            else -> items(dreams, key = { it.id }) { dream ->
                DreamCard(dream, onClick = { onOpen(dream) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DreamCard(dream: Dream, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DreamText.formatDate(dream.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DreamText.formatTime(dream.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (dream.text.isNotBlank()) {
                Text(
                    dream.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "Nothing remembered",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dream.tags.isNotEmpty() || dream.customTags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DreamTag.entries.filter { it in dream.tags }.forEach { tag -> TagLabel(tag.label, tag.icon) }
                    dream.customTags.forEach { TagLabel(it, CustomTagIcon) }
                }
            }
        }
    }
}

@Composable
private fun TagLabel(label: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.NightsStay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
