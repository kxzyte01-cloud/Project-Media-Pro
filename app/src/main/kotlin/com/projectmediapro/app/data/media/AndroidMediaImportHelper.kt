package com.projectmediapro.app.data.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.projectmediapro.app.data.media.VideoImportException.Reason
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException

private const val HEADROOM_BYTES = 64L * 1024L * 1024L

internal data class ImportedMediaFile(
    val relativePath: String,
    val durationMs: Long,
)

internal fun importMediaFromContentUri(
    appContext: Context,
    storage: ProjectMediaStorage,
    projectId: String,
    sourceUri: String,
    requiredMimePrefix: String,
    requiredMetadataKey: Int,
    clipIdProvider: () -> String,
    extensionForMime: (String) -> String,
    checkActive: () -> Unit,
): ImportedMediaFile {
    val (relativePath, durationMs) = copyValidatedMediaFromContentUri(
        appContext = appContext,
        storage = storage,
        projectId = projectId,
        sourceUri = sourceUri,
        requiredMimePrefix = requiredMimePrefix,
        clipIdProvider = clipIdProvider,
        extensionForMime = extensionForMime,
        checkActive = checkActive,
        validateFile = { file -> readValidMediaDurationMs(file, requiredMetadataKey) },
    )
    return ImportedMediaFile(relativePath = relativePath, durationMs = durationMs)
}

internal fun <T> copyValidatedMediaFromContentUri(
    appContext: Context,
    storage: ProjectMediaStorage,
    projectId: String,
    sourceUri: String,
    requiredMimePrefix: String,
    clipIdProvider: () -> String,
    extensionForMime: (String) -> String,
    checkActive: () -> Unit,
    validateFile: (File) -> T,
): Pair<String, T> {
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
    val resolvedMimeType = mimeType?.takeIf {
        it.startsWith(requiredMimePrefix) ||
            (requiredMimePrefix == "audio/" && it == "application/ogg")
    } ?: throw VideoImportException(Reason.NOT_A_VIDEO)

    val sourceSizeBytes = queryKnownSize(resolver, uri)
    if (sourceSizeBytes != null && sourceSizeBytes >= 0L) {
        val availableForFile = (storage.availableBytes() - HEADROOM_BYTES).coerceAtLeast(0L)
        if (sourceSizeBytes > availableForFile) {
            throw VideoImportException(Reason.NO_SPACE)
        }
    }

    val extension = extensionForMime(resolvedMimeType)
    val relativePath = try {
        storage.clipRelativePath(projectId, clipIdProvider(), extension)
    } catch (invalidId: IllegalArgumentException) {
        throw VideoImportException(Reason.FAILED, invalidId)
    }

    val finalFile = storage.resolve(relativePath)
        ?: throw VideoImportException(Reason.FAILED)
    val partFile = File(finalFile.parentFile, "${finalFile.name}.part")

    var completed = false
    try {
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

        checkActive()
        val validated = validateFile(finalFile)
        completed = true
        return relativePath to validated
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

private fun readValidMediaDurationMs(
    file: File,
    requiredMetadataKey: Int,
): Long {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val hasRequiredTrack = retriever.extractMetadata(requiredMetadataKey)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
        if (hasRequiredTrack != "yes" || durationMs <= 0L) {
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
            // Some OEM MediaMetadataRetriever implementations throw on release.
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
