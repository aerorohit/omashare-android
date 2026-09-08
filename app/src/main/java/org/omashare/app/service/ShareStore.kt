package org.omashare.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ShareStore {
    data class Peer(
        val alias: String,
        val endpointId: String,
        val deviceKind: String,
        val addrs: List<String>,
    )

    data class StagedFile(
        val name: String,
        val path: String,
        val size: Long,
    )

    data class OfferedFile(
        val name: String,
        val size: Long,
    )

    data class IncomingOffer(
        val sessionId: String,
        val fromAlias: String,
        val files: List<OfferedFile>,
    )

    data class TransferUi(
        val sessionId: String,
        val bytesDone: Long,
        val bytesTotal: Long,
        val path: String,
        val inbound: Boolean,
        val fileNames: List<String> = emptyList(),
    )

    data class UiState(
        val sharingOn: Boolean = false,
        val status: String = "",
        val peers: List<Peer> = emptyList(),
        val staged: List<StagedFile> = emptyList(),
        val incoming: IncomingOffer? = null,
        val transfer: TransferUi? = null,
        val fromShareSheet: Boolean = false,
        val history: List<HistoryEntry> = emptyList(),
        val selectedPeerId: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setSharing(on: Boolean) {
        _state.update { it.copy(sharingOn = on) }
    }

    fun setStatus(text: String) {
        _state.update { it.copy(status = text) }
    }

    fun setPeers(peers: List<Peer>) {
        _state.update {
            val selected = it.selectedPeerId
            val stillValid = selected != null && peers.any { p -> p.endpointId == selected }
            it.copy(
                peers = peers,
                selectedPeerId = if (stillValid) selected else null,
            )
        }
    }

    fun setStaged(files: List<StagedFile>, fromShareSheet: Boolean? = null) {
        _state.update {
            it.copy(
                staged = files,
                fromShareSheet = fromShareSheet ?: it.fromShareSheet,
            )
        }
    }

    fun clearStaged() {
        _state.update { it.copy(staged = emptyList()) }
    }

    fun clearAfterOutboundComplete() {
        _state.update { it.copy(staged = emptyList(), status = "") }
    }

    fun setSelectedPeer(endpointId: String?) {
        _state.update { it.copy(selectedPeerId = endpointId) }
    }

    fun setShareSheet(on: Boolean) {
        _state.update { it.copy(fromShareSheet = on) }
    }

    fun setIncoming(offer: IncomingOffer?) {
        _state.update { it.copy(incoming = offer) }
    }

    fun setTransfer(transfer: TransferUi?) {
        _state.update { it.copy(transfer = transfer) }
    }

    fun clearIncomingIf(sessionId: String) {
        _state.update {
            if (it.incoming?.sessionId == sessionId) it.copy(incoming = null) else it
        }
    }

    fun addHistory(entry: HistoryEntry) {
        _state.update {
            it.copy(history = (listOf(entry) + it.history).take(50))
        }
    }

    private var lastSendPaths: List<String> = emptyList()
    private var lastSendPeerAlias: String? = null

    fun trackOutgoing(paths: List<String>, peerAlias: String?) {
        lastSendPaths = paths
        lastSendPeerAlias = peerAlias
    }

    fun lastOutgoingPaths(): List<String> = lastSendPaths
    fun lastOutgoingPeerAlias(): String? = lastSendPeerAlias

    fun clearOutgoingTracking() {
        lastSendPaths = emptyList()
        lastSendPeerAlias = null
    }
}
