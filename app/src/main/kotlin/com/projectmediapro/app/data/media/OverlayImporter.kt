package com.projectmediapro.app.data.media

import java.util.Locale

data class ImportedOverlay(
    val relativePath: String,
    val width: Int,
    val height: Int,
)

interface OverlayImporter {
    suspend fun importOverlay(
        projectId: String,
        sourceUri: String,
    ): ImportedOverlay
}

fun imageExtensionFor(mimeType: String?): String {
    val normalized = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()
    return when (normalized) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "png"
    }
}
