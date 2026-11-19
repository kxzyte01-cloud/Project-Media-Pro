package com.projectmediapro.app.data.media

import java.util.Locale

data class ImportedAudio(
    val relativePath: String,
    val durationMs: Long,
)

interface AudioImporter {
    suspend fun importAudio(
        projectId: String,
        sourceUri: String,
    ): ImportedAudio
}

fun audioExtensionFor(mimeType: String?): String {
    val normalized = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()
    return when (normalized) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
        "audio/ogg", "audio/vorbis", "application/ogg" -> "ogg"
        "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "wav"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/aac", "audio/aacp", "audio/x-aac" -> "aac"
        else -> "m4a"
    }
}
