package com.projectmediapro.app.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.projectmediapro.app.data.media.VideoImportException.Reason
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidOverlayImporter(
    context: Context,
    private val storage: ProjectMediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : OverlayImporter {
    private val appContext = context.applicationContext

    override suspend fun importOverlay(projectId: String, sourceUri: String): ImportedOverlay =
        withContext(ioDispatcher) {
            val mime = runCatching {
                appContext.contentResolver.getType(Uri.parse(sourceUri))
            }.getOrNull().orEmpty()
            val isVideo = mime.startsWith("video/")
            if (!isVideo && !mime.startsWith("image/") && mime.isNotEmpty() && mime != "application/octet-stream") {
                throw VideoImportException(Reason.NOT_A_VIDEO)
            }
            val (relativePath, bounds) = copyValidatedMediaFromContentUri(
                appContext = appContext,
                storage = storage,
                projectId = projectId,
                sourceUri = sourceUri,
                requiredMimePrefix = if (isVideo) "video/" else "image/",
                clipIdProvider = idProvider,
                extensionForMime = if (isVideo) ::videoExtensionFor else ::imageExtensionFor,
                checkActive = { ensureActive() },
                validateFile = { file -> if (isVideo) readValidVideoBounds(file) else readValidImageBounds(file) },
            )
            if (isVideo) {
                val durationMs = probeVideoDurationMs(File(bounds.third))
                ImportedOverlay(relativePath, bounds.first, bounds.second, isVideo = true, durationMs = durationMs)
            } else {
                ImportedOverlay(relativePath, bounds.first, bounds.second, isVideo = false, durationMs = 0L)
            }
        }

    private fun readValidImageBounds(file: File): Triple<Int, Int, String> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw VideoImportException(Reason.NOT_A_VIDEO)
        }
        return Triple(options.outWidth, options.outHeight, file.absolutePath)
    }

    private fun readValidVideoBounds(file: File): Triple<Int, Int, String> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0) throw VideoImportException(Reason.NOT_A_VIDEO)
            return Triple(w, h, file.absolutePath)
        } catch (_: RuntimeException) {
            throw VideoImportException(Reason.NOT_A_VIDEO)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun probeVideoDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: RuntimeException) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
