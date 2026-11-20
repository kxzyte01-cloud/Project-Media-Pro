package com.projectmediapro.app.data.media

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline SAF helper for reading and writing `.srt` caption files without storage permissions.
 * Caps reads at [MAX_SRT_BYTES] to avoid OOM on accidentally picked binary files.
 */
object SubtitleFileIo {

    private const val MAX_SRT_BYTES = 2 * 1024 * 1024

    suspend fun readSrtText(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open SRT stream")
            val buffer = ByteArrayOutputStream()
            input.use { stream ->
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = stream.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_SRT_BYTES) {
                        throw IOException("SRT file exceeds 2 MB limit")
                    }
                    buffer.write(chunk, 0, read)
                }
            }
            buffer.toString(Charsets.UTF_8.name())
        }

    suspend fun writeSrtText(context: Context, uri: Uri, srtContent: String) =
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Unable to open output stream for SRT export")
            output.use { stream ->
                stream.write(srtContent.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
        }
}
