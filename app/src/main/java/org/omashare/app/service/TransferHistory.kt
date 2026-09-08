package org.omashare.app.service

import android.net.Uri

data class HistoryEntry(
    val id: String,
    val fileName: String,
    val direction: Direction,
    val status: Status,
    val timestamp: Long,
    val openUri: Uri? = null,
    val openPath: String? = null,
    val peerAlias: String? = null,
) {
    enum class Direction { Received, Sent }
    enum class Status { Completed, Failed }
}
