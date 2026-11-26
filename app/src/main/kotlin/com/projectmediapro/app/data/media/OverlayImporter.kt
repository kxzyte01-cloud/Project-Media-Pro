package com.projectmediapro.app.data.media

import java.util.Locale

data class ImportedOverlay(
    val relativePath: String,
    val width: Int,
    val height: Int,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
)

interface OverlayImporter {
    suspend fun importOverlay(projectId: String, sourceUri: String): ImportedOverlay
}

fun imageExtensionFor(mimeType: String?): String {
    val normalized = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
    return when (normalized) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }
}

fun videoExtensionFor(mimeType: String?): String {
    val normalized = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US).orEmpty()
    return when (normalized) {
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        "video/x-matroska" -> "mkv"
        else -> "mp4"
    }
}
