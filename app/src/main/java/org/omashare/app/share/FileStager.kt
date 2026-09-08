package org.omashare.app.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.omashare.app.service.ShareStore
import java.io.File

object FileStager {
    fun stage(context: Context, uris: List<Uri>): List<ShareStore.StagedFile> {
        val dir = stagingDir(context)
        dir.listFiles()?.forEach { it.delete() }
        val out = mutableListOf<ShareStore.StagedFile>()
        for (uri in uris) {
            val name = (displayName(context, uri) ?: "file").replace('/', '_')
            val size = querySize(context, uri)
            val dest = uniqueFile(dir, name)
            val need = if (size > 0) size else 0L
            val free = dest.parentFile?.usableSpace ?: 0L
            if (need > 0 && free in 1 until need + 32L * 1024 * 1024) {
                ShareStore.setStatus(
                    "Not enough space to stage $name (${humanBytes(need)}; ${humanBytes(free)} free).",
                )
                continue
            }
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    ShareStore.setStatus("Could not open $name")
                    continue
                }
                input.use { stream ->
                    dest.outputStream().use { output -> stream.copyTo(output) }
                }
            } catch (e: Exception) {
                dest.delete()
                val nospace = e.message?.contains("ENOSPC") == true ||
                    e.message?.contains("No space") == true
                ShareStore.setStatus(
                    if (nospace) {
                        "Phone storage is full. Clear Omashare storage and try again."
                    } else {
                        "Could not copy $name: ${e.message}"
                    },
                )
                continue
            }
            out.add(ShareStore.StagedFile(name = dest.name, path = dest.absolutePath, size = dest.length()))
        }
        ShareStore.setStaged(out)
        if (out.isNotEmpty()) {
            val label = out.joinToString(", ") { "${it.name} (${humanBytes(it.size)})" }
            ShareStore.setStatus("Tap a device to send $label")
        }
        return out
    }

    fun stagingDir(context: Context): File {
        return (context.getExternalFilesDir(null) ?: context.filesDir).resolve("staging").apply { mkdirs() }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val base = File(name)
        val stem = base.nameWithoutExtension
        val ext = base.extension
        var dest = dir.resolve(name)
        var i = 1
        while (dest.exists()) {
            dest = dir.resolve(if (ext.isEmpty()) "$stem-$i" else "$stem-$i.$ext")
            i++
        }
        return dest
    }

    fun querySize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx)
            }
        }
        return -1
    }

    fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    fun humanBytes(n: Long): String {
        if (n < 1024) return "$n B"
        val kb = n / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}
