package org.omashare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omashare.app.service.HistoryEntry
import org.omashare.app.service.ShareStore
import org.omashare.app.share.FileStager

@Composable
fun SelectionCard(
    staged: List<ShareStore.StagedFile>,
    onClear: () -> Unit,
) {
    val totalSize = staged.sumOf { it.size }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MochaSurface, OmashareCardShape)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Selection",
                color = MochaMuted,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text("Clear", color = MochaRed, fontSize = 13.sp)
            }
        }
        Text(
            text = "Files: ${staged.size}  ·  ${FileStager.humanBytes(totalSize)}",
            color = MochaText,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = staged.joinToString("\n") { it.name },
            color = MochaMuted,
            fontSize = 13.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HistorySection(
    history: List<HistoryEntry>,
    onOpenEntry: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "History",
            color = MochaMuted,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(MochaSurface, OmashareCardShape),
        ) {
            if (history.isEmpty()) {
                Text(
                    text = "No transfers yet",
                    color = MochaMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    items(history, key = { it.id }) { entry ->
                        HistoryRow(entry = entry, onClick = { onOpenEntry(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val iconColor = when {
        entry.status == HistoryEntry.Status.Failed -> MochaRed
        entry.direction == HistoryEntry.Direction.Received -> MochaGreen
        else -> MochaAccent
    }
    val icon = when (entry.direction) {
        HistoryEntry.Direction.Received -> "↓"
        HistoryEntry.Direction.Sent -> "↑"
    }
    val canOpen = entry.status == HistoryEntry.Status.Completed &&
        (entry.openUri != null || entry.openPath != null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canOpen) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            color = iconColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.fileName,
                color = if (entry.status == HistoryEntry.Status.Failed) MochaRed else MochaText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.peerAlias?.let { alias ->
                val label = when (entry.direction) {
                    HistoryEntry.Direction.Received -> "from $alias"
                    HistoryEntry.Direction.Sent -> "to $alias"
                }
                Text(
                    text = label,
                    color = MochaMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = relativeTime(entry.timestamp),
            color = MochaMuted,
            fontSize = 12.sp,
        )
    }
}

private fun relativeTime(timestamp: Long): String {
    val seconds = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
