package com.projectmediapro.app.data.media

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedVideo(
    val sourceUri: String,
    val displayName: String,
    val durationMs: Long,
)

interface VideoImportDataSource {
    suspend fun importVideo(uri: Uri): ImportedVideo
}

/**
 * Retains a persistable Photo Picker grant when available; otherwise copies the stream
 * into app-private storage so the project remains usable after the picker grant expires.
 */
class AndroidVideoImportDataSource(
    context: Context,
    private val mediaDirectory: File,
) : VideoImportDataSource {
    private val appContext = context.applicationContext

    override suspend fun importVideo(uri: Uri): ImportedVideo = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Video"
        val duration = readDuration(uri)
        val hasPersistedGrant = runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess

        val stableUri = if (hasPersistedGrant) {
            uri.toString()
        } else {
            copyIntoPrivateStorage(uri, resolver.getType(uri))
        }
        ImportedVideo(stableUri, displayName, duration)
    }

    private fun readDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L) ?: 0L
        } catch (_: RuntimeException) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun copyIntoPrivateStorage(uri: Uri, mimeType: String?): String {
        if (!mediaDirectory.exists() && !mediaDirectory.mkdirs()) {
            throw IOException("Could not create the media directory.")
        }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "media"
        val destination = File(mediaDirectory, "${UUID.randomUUID()}.$extension")
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected video could not be opened.")
        try {
            input.use { source ->
                FileOutputStream(destination).use { output ->
                    source.copyTo(output, DEFAULT_BUFFER_SIZE)
                    output.fd.sync()
                }
            }
        } catch (exception: Exception) {
            destination.delete()
            throw exception
        }
        if (destination.length() == 0L) {
            destination.delete()
            throw IOException("The selected video is empty.")
        }
        return Uri.fromFile(destination).toString()
    }
}
