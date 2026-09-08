package org.omashare.app.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import org.omashare.app.service.HistoryEntry
import java.io.File

object FileOpener {
    fun open(context: Context, entry: HistoryEntry) {
        if (entry.status != HistoryEntry.Status.Completed) return
        val opened = when {
            entry.openUri != null -> openUri(context, entry.openUri)
            entry.openPath != null -> openPath(context, entry.openPath)
            else -> false
        }
        if (!opened) {
            Toast.makeText(context, "Can't open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUri(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri) ?: guessMime(uri)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, intent)
    }

    private fun openPath(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.isFile) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mime = guessMime(uri, file.name)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, intent)
    }

    private fun guessMime(uri: Uri, fallbackName: String? = null): String {
        val fromUri = uri.toString().substringAfterLast('.', "")
        val ext = when {
            fromUri.length in 1..5 && !fromUri.contains('/') -> fromUri
            fallbackName != null -> fallbackName.substringAfterLast('.', "")
            else -> ""
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: "application/octet-stream"
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(Intent.createChooser(intent, null))
            true
        }.getOrDefault(false)
    }
}
