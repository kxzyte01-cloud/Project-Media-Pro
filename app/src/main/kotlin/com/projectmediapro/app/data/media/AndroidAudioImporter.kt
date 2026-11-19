package com.projectmediapro.app.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Android [AudioImporter] reusing the shared atomic media import pipeline.
 * Never calls takePersistableUriPermission and never retains the source URI.
 */
class AndroidAudioImporter(
    context: Context,
    private val storage: ProjectMediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : AudioImporter {

    private val appContext = context.applicationContext

    override suspend fun importAudio(
        projectId: String,
        sourceUri: String,
    ): ImportedAudio = withContext(ioDispatcher) {
        val imported = importMediaFromContentUri(
            appContext = appContext,
            storage = storage,
            projectId = projectId,
            sourceUri = sourceUri,
            requiredMimePrefix = "audio/",
            requiredMetadataKey = MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO,
            clipIdProvider = idProvider,
            extensionForMime = ::audioExtensionFor,
            checkActive = { ensureActive() },
        )
        ImportedAudio(
            relativePath = imported.relativePath,
            durationMs = imported.durationMs,
        )
    }
}
