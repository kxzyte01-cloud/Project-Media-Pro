package com.projectmediapro.app.data.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import com.projectmediapro.app.data.media.VideoImportException.Reason
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android [VideoImporter] using [ContentResolver], [ProjectMediaStorage], and [MediaMetadataRetriever].
 * Never calls takePersistableUriPermission and never retains the source URI.
 */
class AndroidVideoImporter(
    context: Context,
    private val storage: ProjectMediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : VideoImporter {

    private val appContext = context.applicationContext

    override suspend fun importVideo(
        projectId: String,
        sourceUri: String,
    ): ImportedVideo = withContext(ioDispatcher) {
        val uri = Uri.parse(sourceUri)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw VideoImportException(Reason.UNREADABLE)
        }

        val resolver = appContext.contentResolver
        val mimeType = try {
            resolver.getType(uri)
        } catch (denied: SecurityException) {
            throw VideoImportException(Reason.UNREADABLE, denied)
        } catch (missing: FileNotFoundException) {
            throw VideoImportException(Reason.UNREADABLE, missing)
        }
        if (mimeType == null || !mimeType.startsWith("video/")) {
            throw VideoImportException(Reason.NOT_A_VIDEO)
        }

        val sourceSizeBytes = queryKnownSize(resolver, uri)
        if (sourceSizeBytes != null && sourceSizeBytes >= 0L) {
            if (storage.availableBytes() < sourceSizeBytes + HEADROOM_BYTES) {
                throw VideoImportException(Reason.NO_SPACE)
            }
        }

        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.lowercase(Locale.US)
            .orEmpty()
        val relativePath = try {
            storage.clipRelativePath(projectId, idProvider(), extension)
        } catch (invalidId: IllegalArgumentException) {
            throw VideoImportException(Reason.FAILED, invalidId)
        }

        val finalFile = storage.resolve(relativePath)
            ?: throw VideoImportException(Reason.FAILED)
        val partFile = File(finalFile.parentFile, "${finalFile.name}.part")

        var completed = false
        try {
            val checkActive: () -> Unit = { ensureActive() }
            try {
                val inputStream = resolver.openInputStream(uri)
                    ?: throw VideoImportException(Reason.UNREADABLE)
                inputStream.use { input ->
                    copyStreamAtomically(
                        input = input,
                        partFile = partFile,
                        finalFile = finalFile,
                        onChunk = checkActive,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (known: VideoImportException) {
                throw known
            } catch (denied: SecurityException) {
                throw VideoImportException(Reason.UNREADABLE, denied)
            } catch (missing: FileNotFoundException) {
                throw VideoImportException(Reason.UNREADABLE, missing)
            } catch (ioError: IOException) {
                val reason = if (isOutOfSpace(ioError)) Reason.NO_SPACE else Reason.FAILED
                throw VideoImportException(reason, ioError)
            }

            ensureActive()
            val durationMs = readValidVideoDurationMs(finalFile)
            completed = true
            ImportedVideo(
                relativePath = relativePath,
                durationMs = durationMs,
            )
        } finally {
            if (partFile.exists()) {
                partFile.delete()
            }
            if (!completed && finalFile.exists()) {
                finalFile.delete()
            }
        }
    }

    private fun queryKnownSize(
        resolver: ContentResolver,
        uri: Uri,
    ): Long? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
            }
        } catch (denied: SecurityException) {
            throw VideoImportException(Reason.UNREADABLE, denied)
        } catch (missing: FileNotFoundException) {
            throw VideoImportException(Reason.UNREADABLE, missing)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun readValidVideoDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            if (hasVideo != "yes" || durationMs <= 0L) {
                throw VideoImportException(Reason.NOT_A_VIDEO)
            }
            return durationMs
        } catch (known: VideoImportException) {
            throw known
        } catch (error: RuntimeException) {
            throw VideoImportException(Reason.NOT_A_VIDEO, error)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release failures.
            }
        }
    }

    private fun isOutOfSpace(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }.any { cause ->
            (cause is ErrnoException && cause.errno == OsConstants.ENOSPC) ||
                cause.message?.contains("ENOSPC", ignoreCase = true) == true ||
                cause.message?.contains("No space left", ignoreCase = true) == true
        }
    }

    private companion object {
        const val HEADROOM_BYTES = 64L * 1024L * 1024L
    }
}
