package com.projectmediapro.app.data.media

import android.content.Context
import android.graphics.BitmapFactory
import com.projectmediapro.app.data.media.VideoImportException.Reason
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android [OverlayImporter] reusing the shared atomic media import pipeline.
 * Validates dimensions via [BitmapFactory.Options.inJustDecodeBounds] without loading pixels into RAM.
 */
class AndroidOverlayImporter(
    context: Context,
    private val storage: ProjectMediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : OverlayImporter {

    private val appContext = context.applicationContext

    override suspend fun importOverlay(
        projectId: String,
        sourceUri: String,
    ): ImportedOverlay = withContext(ioDispatcher) {
        val (relativePath, bounds) = copyValidatedMediaFromContentUri(
            appContext = appContext,
            storage = storage,
            projectId = projectId,
            sourceUri = sourceUri,
            requiredMimePrefix = "image/",
            clipIdProvider = idProvider,
            extensionForMime = ::imageExtensionFor,
            checkActive = { ensureActive() },
            validateFile = ::readValidImageBounds,
        )
        ImportedOverlay(
            relativePath = relativePath,
            width = bounds.first,
            height = bounds.second,
        )
    }

    private fun readValidImageBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw VideoImportException(Reason.NOT_A_VIDEO)
        }
        return options.outWidth to options.outHeight
    }
}
