package org.omashare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omashare.app.service.HistoryEntry
import org.omashare.app.service.ShareStore
import org.omashare.app.share.FileStager

@Composable
fun MainScreen(
    state: ShareStore.UiState,
    onSelectFiles: () -> Unit,
    onClearSelection: () -> Unit,
    onPeerSelected: (ShareStore.Peer) -> Unit,
    onSend: () -> Unit,
    onStopSharing: () -> Unit,
    onStartSharing: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenHistoryEntry: (HistoryEntry) -> Unit,
) {
    val canSend = state.staged.isNotEmpty() &&
        state.selectedPeerId != null &&
        state.transfer == null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Header(
                sharingOn = state.sharingOn,
                onStopSharing = onStopSharing,
                onStartSharing = onStartSharing,
            )
            Spacer(Modifier.height(8.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))

            // Top: file selection
            OutlinedButton(
                onClick = onSelectFiles,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = OmashareCardShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MochaAccent,
                ),
            ) {
                Text("Select files")
            }
            if (state.staged.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SelectionCard(staged = state.staged, onClear = onClearSelection)
            }

            val transfer = state.transfer
            if (transfer != null) {
                Spacer(Modifier.height(12.dp))
                TransferBlock(transfer = transfer, onCancel = onCancel)
            } else if (isTransientStatus(state.status)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.status,
                    color = MochaMuted,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Nearby",
                color = MochaMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))

            // Middle: nearby devices (scrollable, takes remaining space above dock)
            if (state.peers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "Waiting for devices on this network…",
                        color = MochaMuted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.peers, key = { it.endpointId }) { peer ->
                        PeerRow(
                            peer = peer,
                            selected = peer.endpointId == state.selectedPeerId,
                            onClick = { onPeerSelected(peer) },
                        )
                    }
                }
            }

            // Bottom dock: fixed Send + scrollable History
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.navigationBarsPadding()) {
                Button(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaAccent,
                        contentColor = MochaBackground,
                        disabledContainerColor = MochaOverlay,
                        disabledContentColor = MochaMuted,
                    ),
                    shape = OmashareCardShape,
                ) {
                    val label = state.selectedPeerId?.let { id ->
                        state.peers.find { it.endpointId == id }?.alias
                    }
                    Text(
                        if (label != null) "Send to $label" else "Send",
                    )
                }
                HistorySection(
                    history = state.history,
                    onOpenEntry = onOpenHistoryEntry,
                )
            }
        }
        state.incoming?.let { offer ->
            OfferSheet(
                offer = offer,
                onAccept = { onAccept(offer.sessionId) },
                onDecline = { onDecline(offer.sessionId) },
            )
        }
    }
}

private fun isTransientStatus(status: String): Boolean {
    if (status.isEmpty()) return false
    if (status.startsWith("Saved ")) return false
    if (status == "Sent") return false
    if (status == "Accepted — transferring") return false
    return true
}

@Composable
private fun Header(
    sharingOn: Boolean,
    onStopSharing: () -> Unit,
    onStartSharing: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Omashare",
            color = MochaText,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { menu = true },
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (sharingOn) MochaGreen else MochaMuted),
            )
            Text(
                text = if (sharingOn) "sharing on" else "sharing off",
                color = if (sharingOn) MochaGreen else MochaMuted,
                fontSize = 13.sp,
            )
            Text(
                text = "  ⋮",
                color = MochaMuted,
                fontSize = 18.sp,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (sharingOn) {
                    DropdownMenuItem(
                        text = { Text("Stop sharing") },
                        onClick = {
                            menu = false
                            onStopSharing()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Start sharing") },
                        onClick = {
                            menu = false
                            onStartSharing()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: ShareStore.Peer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isMobile = peer.deviceKind.contains("mobile", ignoreCase = true)
    val deviceIcon = if (isMobile) "📱" else "💻"
    val kindLabel = peer.deviceKind.ifEmpty { "unknown" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MochaSurface, OmashareCardShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MochaAccent, OmashareCardShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = deviceIcon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.alias.ifEmpty { "device" },
                color = if (selected) MochaAccent else MochaText,
                fontSize = 16.sp,
            )
            if (peer.addrs.isEmpty()) {
                Text(
                    text = "waiting for address…",
                    color = MochaMuted,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = kindLabel,
            color = MochaMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .background(MochaOverlay, OmashareCardShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TransferBlock(transfer: ShareStore.TransferUi, onCancel: () -> Unit) {
    val total = transfer.bytesTotal.coerceAtLeast(1)
    val progress = if (transfer.bytesTotal <= 0L) {
        0f
    } else {
        (transfer.bytesDone.toFloat() / total).coerceIn(0f, 1f)
    }
    Column {
        if (transfer.fileNames.isNotEmpty()) {
            Text(
                text = transfer.fileNames.joinToString(" · "),
                color = MochaText,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MochaAccent,
            trackColor = MochaOverlay,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progressLabel(transfer),
                color = MochaYellow,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MochaRed)
            }
        }
    }
}

private fun progressLabel(t: ShareStore.TransferUi): String {
    val via = when (t.path) {
        "", "unknown" -> ""
        "lan" -> " LAN"
        "direct_wan" -> " WAN"
        "relay" -> " relay"
        else -> " ${t.path}"
    }
    val direction = if (t.inbound) "Receiving" else "Sending"
    return if (t.bytesTotal <= 0L) {
        "$direction…$via"
    } else {
        "$direction ${FileStager.humanBytes(t.bytesDone)} / ${FileStager.humanBytes(t.bytesTotal)}$via"
    }
}

@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MochaOverlay),
    )
}
