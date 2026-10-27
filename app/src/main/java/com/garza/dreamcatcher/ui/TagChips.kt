package com.garza.dreamcatcher.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garza.dreamcatcher.data.DreamTag

val DreamTag.icon: ImageVector
    get() = when (this) {
        DreamTag.DREAM -> Icons.Filled.Cloud
        DreamTag.NIGHTMARE -> Icons.Filled.MoodBad
        DreamTag.NO_DREAM -> Icons.Filled.Bedtime
        DreamTag.LUCID -> Icons.Filled.AutoAwesome
        DreamTag.RECURRING -> Icons.Filled.Repeat
    }

/** A horizontally scrolling row with one chip per tag. */
@Composable
fun TagChipRow(
    isSelected: (DreamTag) -> Boolean,
    onClick: (DreamTag) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
) {
    Row(
        // Padding inside the scroll keeps the edges clear while letting chips scroll to the screen edge.
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DreamTag.entries.forEach { tag ->
            FilterChip(
                selected = isSelected(tag),
                onClick = { onClick(tag) },
                label = { Text(tag.label) },
                leadingIcon = { Icon(tag.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}
