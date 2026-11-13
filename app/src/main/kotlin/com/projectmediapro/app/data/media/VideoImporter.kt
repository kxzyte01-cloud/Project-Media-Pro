package com.projectmediapro.app.data.media

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Result of copying and validating a video in app-private storage.
 */
data class ImportedVideo(
    val relativePath: String,
    val durationMs: Long,
)

/**
 * Categorized failure thrown by [VideoImporter.importVideo].
 */
class VideoImportException(
    val reason: Reason,
    cause: Throwable? = null,
) : Exception(reason.name, cause) {
    enum class Reason {
        UNREADABLE,
        NOT_A_VIDEO,
        NO_SPACE,
        FAILED,
    }
}

/**
 * Copies a user-selected video into app-private storage and reads its duration.
 */
interface VideoImporter {
    suspend fun importVideo(
        projectId: String,
        sourceUri: String,
    ): ImportedVideo
}

private const val BUFFER_SIZE_BYTES = 64 * 1024

/**
 * Copies [input] with a 64 KB buffer to [partFile] and atomically moves it to [finalFile].
 * Cleans up [partFile] on any failure and never overwrites [finalFile] if the stream fails midway.
 */
fun copyStreamAtomically(
    input: InputStream,
    partFile: File,
    finalFile: File,
    onChunk: () -> Unit = {},
) {
    val finalParent = finalFile.parentFile
    if (finalParent != null && !finalParent.exists() && !finalParent.mkdirs() && !finalParent.isDirectory) {
        throw IOException("Could not create parent directory: ${finalParent.absolutePath}")
    }
    val partParent = partFile.parentFile
    if (partParent != null && !partParent.exists() && !partParent.mkdirs() && !partParent.isDirectory) {
        throw IOException("Could not create staging directory: ${partParent.absolutePath}")
    }

    var moved = false
    try {
        FileOutputStream(partFile).use { output ->
            val buffer = ByteArray(BUFFER_SIZE_BYTES)
            while (true) {
                onChunk()
                val read = input.read(buffer)
                if (read < 0) break
                onChunk()
                output.write(buffer, 0, read)
            }
            output.flush()
            output.fd.sync()
        }
        onChunk()
        Files.move(
            partFile.toPath(),
            finalFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
        moved = true
    } finally {
        if (!moved && partFile.exists()) {
            partFile.delete()
        }
    }
}
