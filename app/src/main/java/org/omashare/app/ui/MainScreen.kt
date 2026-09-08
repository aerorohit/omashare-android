package org.omashare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import org.omashare.app.service.ShareStore
import org.omashare.app.share.FileStager

@Composable
fun MainScreen(
    state: ShareStore.UiState,
    onSendFiles: () -> Unit,
    onPeer: (ShareStore.Peer) -> Unit,
    onStopSharing: () -> Unit,
    onStartSharing: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBackground)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                sharingOn = state.sharingOn,
                onStopSharing = onStopSharing,
                onStartSharing = onStartSharing,
            )
            Spacer(Modifier.height(8.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Nearby",
                color = MochaMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (state.peers.isEmpty()) {
                Text(
                    text = "Waiting for devices on this network…",
                    color = MochaMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.peers, key = { it.endpointId }) { peer ->
                        PeerRow(peer = peer, onClick = { onPeer(peer) })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.staged.isNotEmpty()) {
                Text(
                    text = state.staged.joinToString(" · ") { it.name },
                    color = MochaMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val transfer = state.transfer
            if (transfer != null) {
                TransferBlock(transfer = transfer, onCancel = onCancel)
                Spacer(Modifier.height(8.dp))
            } else if (state.status.isNotEmpty()) {
                Text(
                    text = state.status,
                    color = MochaMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = onSendFiles,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MochaAccent,
                    contentColor = MochaBackground,
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            ) {
                Text("Send files")
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
private fun PeerRow(peer: ShareStore.Peer, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MochaSurface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = peer.alias.ifEmpty { "device" }, color = MochaText, fontSize = 16.sp)
            if (peer.addrs.isEmpty()) {
                Text(text = "waiting for address…", color = MochaMuted, fontSize = 13.sp)
            }
        }
        Text(
            text = peer.deviceKind.ifEmpty { "unknown" },
            color = MochaMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TransferBlock(transfer: ShareStore.TransferUi, onCancel: () -> Unit) {
    val total = transfer.bytesTotal.coerceAtLeast(1)
    val progress = if (transfer.bytesTotal <= 0L) 0f else (transfer.bytesDone.toFloat() / total).coerceIn(0f, 1f)
    Column {
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
    return if (t.bytesTotal <= 0L) {
        "Transferring…$via"
    } else {
        "${FileStager.humanBytes(t.bytesDone)} / ${FileStager.humanBytes(t.bytesTotal)}$via"
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
