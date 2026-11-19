package com.projectmediapro.app.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.webkit.MimeTypeMap
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android [VideoImporter] using [ProjectMediaStorage] and [MediaMetadataRetriever].
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
        val imported = importMediaFromContentUri(
            appContext = appContext,
            storage = storage,
            projectId = projectId,
            sourceUri = sourceUri,
            requiredMimePrefix = "video/",
            requiredMetadataKey = MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO,
            clipIdProvider = idProvider,
            extensionForMime = { mimeType ->
                MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType)
                    ?.lowercase(Locale.US)
                    .orEmpty()
            },
            checkActive = { ensureActive() },
        )
        ImportedVideo(
            relativePath = imported.relativePath,
            durationMs = imported.durationMs,
        )
    }
}
