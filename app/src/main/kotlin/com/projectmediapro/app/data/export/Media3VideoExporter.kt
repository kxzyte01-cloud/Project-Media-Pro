package com.projectmediapro.app.data.export

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.projectmediapro.app.data.export.VideoExportException.Reason
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [VideoExporter] built on Media3 Transformer: one composition, one sequence, H.264 video and AAC audio.
 * Transformer must be created, started and queried on the main thread, so all of that happens there.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Media3VideoExporter(
    private val context: Context,
) : VideoExporter {

    override suspend fun export(plan: ExportPlan, outputFile: File, onProgress: (Int) -> Unit) {
        if (plan.clips.isEmpty()) {
            throw VideoExportException(Reason.FAILED)
        }
        val targetSize = withContext(Dispatchers.IO) {
            prepareOutputFile(outputFile)
            readTargetSize(plan.clips.first().path)
        }
        val composition = buildComposition(plan, targetSize)
        runTransformer(composition, outputFile, onProgress)
    }

    private fun prepareOutputFile(outputFile: File) {
        val parent = outputFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw VideoExportException(Reason.FAILED)
        }
        if (outputFile.exists() && !outputFile.delete()) {
            throw VideoExportException(Reason.FAILED)
        }
    }

    private fun readTargetSize(path: String): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (width == null || height == null) {
                null
            } else if (rotation == 90 || rotation == 270) {
                ExportPlanner.fitExportSize(height, width)
            } else {
                ExportPlanner.fitExportSize(width, height)
            }
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun buildComposition(plan: ExportPlan, targetSize: Pair<Int, Int>?): Composition {
        val videoEffects: List<Effect> = if (targetSize != null) {
            listOf(
                Presentation.createForWidthAndHeight(
                    targetSize.first,
                    targetSize.second,
                    Presentation.LAYOUT_SCALE_TO_FIT,
                ),
            )
        } else {
            emptyList()
        }

        val editedItems = plan.clips.map { clip ->
            val mediaItemBuilder = MediaItem.Builder().setUri(Uri.fromFile(File(clip.path)))
            if (clip.trimEndMs > clip.trimStartMs) {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build(),
                )
            }
            EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
        }

        return Composition.Builder(listOf(EditedMediaItemSequence(editedItems)))
            .experimentalSetForceAudioTrack(true)
            .build()
    }

    private suspend fun runTransformer(
        composition: Composition,
        outputFile: File,
        onProgress: (Int) -> Unit,
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        suspendCancellableCoroutine<Unit> { continuation ->
            mainHandler.post {
                if (!continuation.isActive) return@post

                val holder = ProgressHolder()
                var transformer: Transformer? = null

                val poller = object : Runnable {
                    override fun run() {
                        val active = transformer ?: return
                        if (!continuation.isActive) return
                        if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 100))
                        }
                        mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                    }
                }

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        mainHandler.removeCallbacks(poller)
                        if (continuation.isActive) {
                            onProgress(100)
                            continuation.resume(Unit)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        mainHandler.removeCallbacks(poller)
                        outputFile.delete()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                VideoExportException(mapErrorReason(exportException), exportException),
                            )
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        mainHandler.removeCallbacks(poller)
                        transformer?.let { runCatching { it.cancel() } }
                        outputFile.delete()
                    }
                }

                try {
                    val built = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(listener)
                        .build()
                    transformer = built
                    built.start(composition, outputFile.absolutePath)
                    mainHandler.postDelayed(poller, POLL_INTERVAL_MS)
                } catch (failure: Exception) {
                    mainHandler.removeCallbacks(poller)
                    outputFile.delete()
                    if (continuation.isActive) {
                        continuation.resumeWithException(VideoExportException(Reason.FAILED, failure))
                    }
                }
            }
        }
    }

    private fun mapErrorReason(exception: ExportException): Reason {
        if (isNoSpaceError(exception)) {
            return Reason.NO_SPACE
        }
        return when (exception.errorCode) {
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FAILED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            -> Reason.UNSUPPORTED
            else -> Reason.FAILED
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 250L
    }
}
