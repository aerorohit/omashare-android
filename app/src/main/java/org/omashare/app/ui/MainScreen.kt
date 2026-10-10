package org.omashare.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
    onOpenSettings: () -> Unit,
) {
    val canSend = state.staged.isNotEmpty() &&
        state.selectedPeerId != null &&
        state.transfer == null
    val quietStatus = state.status.takeIf { isQuietStatus(it) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Header(
                    sharingOn = state.sharingOn,
                    onStopSharing = onStopSharing,
                    onStartSharing = onStartSharing,
                    onOpenSettings = onOpenSettings,
                )
                if (quietStatus != null) {
                    Text(
                        text = quietStatus,
                        color = if (isFailedStatus(quietStatus)) MochaRed else MochaMuted,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Hairline()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onSelectFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = OmashareCardShape,
                    border = BorderStroke(1.dp, MochaOverlay),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MochaText,
                    ),
                ) {
                    Text("Select files")
                }
                if (state.staged.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SelectionCard(staged = state.staged, onClear = onClearSelection)
                }

                val transfer = state.transfer
                if (transfer != null) {
                    Spacer(Modifier.height(16.dp))
                    TransferBlock(transfer = transfer, onCancel = onCancel)
                }

                Spacer(Modifier.height(16.dp))
                SectionLabel("Nearby")
                Spacer(Modifier.height(8.dp))
                if (state.peers.isEmpty()) {
                    Text(
                        text = "Waiting for devices on this network…",
                        color = MochaMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.peers.forEach { peer ->
                            PeerRow(
                                peer = peer,
                                selected = peer.endpointId == state.selectedPeerId,
                                onClick = { onPeerSelected(peer) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (canSend) MochaAccent else MochaOverlay,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaAccent,
                        contentColor = MochaBackground,
                        disabledContainerColor = MochaBackground,
                        disabledContentColor = MochaMuted,
                    ),
                    shape = OmashareCardShape,
                ) {
                    val label = state.selectedPeerId?.let { id ->
                        state.peers.find { it.endpointId == id }?.alias
                    }
                    Text(
                        text = if (label != null) "Send to $label" else "Send",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HistorySection(
                    history = state.history,
                    onOpenEntry = onOpenHistoryEntry,
                )
                Spacer(Modifier.height(16.dp))
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

private fun isFailedStatus(status: String): Boolean {
    val lower = status.lowercase()
    return lower.contains("fail") || lower.contains("could not")
}

private fun isQuietStatus(status: String): Boolean {
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
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Omashare",
            color = MochaText,
            fontSize = 22.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SharingPill(
            sharingOn = sharingOn,
            onClick = { if (sharingOn) onStopSharing() else onStartSharing() },
        )
        Spacer(Modifier.width(16.dp))
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MochaMuted,
            )
        }
    }
}

@Composable
private fun SharingPill(
    sharingOn: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .border(1.dp, MochaOverlay, OmasharePillShape)
            .clip(OmasharePillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (sharingOn) MochaGreen else MochaMuted,
                    CircleShape,
                ),
        )
        Text(
            text = if (sharingOn) "sharing" else "paused",
            color = if (sharingOn) MochaText else MochaMuted,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
        )
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
    val sourceLabel = when (peer.source) {
        "" -> ""
        "bluetooth" -> "Bluetooth"
        else -> "LAN"
    }
    val badge = if (sourceLabel.isEmpty()) kindLabel else "$kindLabel · $sourceLabel"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MochaSurface, OmashareCardShape)
            .then(
                if (selected) {
                    Modifier.border(1.dp, MochaAccent, OmashareCardShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (peer.addrs.isEmpty() && peer.source != "bluetooth") {
                Text(
                    text = "waiting for address…",
                    color = MochaMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = badge,
            color = MochaMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MochaSurface, OmashareCardShape)
            .padding(16.dp),
    ) {
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MochaOverlay),
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(MochaAccent),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progressLabel(transfer),
                color = MochaYellow,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            QuietAction(
                text = "Cancel",
                color = MochaRed,
                onClick = onCancel,
            )
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
