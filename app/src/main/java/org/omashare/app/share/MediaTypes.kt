package org.omashare.app.share

import org.omashare.app.service.ShareStore

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif",
)

fun isImageFileName(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

fun imageFiles(staged: List<ShareStore.StagedFile>): List<ShareStore.StagedFile> {
    return staged.filter { isImageFileName(it.name) }
}
