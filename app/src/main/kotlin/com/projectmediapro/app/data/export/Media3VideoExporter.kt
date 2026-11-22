package com.projectmediapro.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import com.projectmediapro.app.data.export.VideoExportException.Reason
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.KeyframeInterpolator
import com.projectmediapro.core.model.OverlayKeyframe
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.ResolvedOverlayTransform
import com.projectmediapro.feature.captions.CaptionOverlayEffectFactory
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * [VideoExporter] built on Media3 Transformer: H.264 video and AAC audio, mixing the video track's
 * audio with an optional second [EditedMediaItemSequence] for music clips, per-clip transforms,
 * speed changes, volume/mute, GPU filters, color adjustments, and fades via [VideoEffectFactory],
 * plus a single composition-level [OverlayEffect] for timed picture and styled text/lyric overlays.
 * Transformer must be created, started and queried on the main thread.
 * Media3 1.4.1 [Presentation.LAYOUT_SCALE_TO_FIT] renders letterbox/pillarbox bars in black.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Media3VideoExporter(
    private val context: Context,
) : VideoExporter {

    override suspend fun export(plan: ExportPlan, outputFile: File, onProgress: (Int) -> Unit) {
        if (plan.clips.isEmpty()) {
            throw VideoExportException(Reason.FAILED)
        }
        val (targetSize, useTransmuxFastPath) = withContext(Dispatchers.IO) {
            prepareOutputFile(outputFile)
            val firstClip = plan.clips.first()
            val firstSize = if (firstClip.isStillImage) {
                readImageSize(firstClip.path)
            } else {
                readRotatedSize(firstClip.path)
            }
            val firstLongSide = firstSize?.let { maxOf(it.first, it.second) }
            val fastPath = ExportPlanner.canTransmuxVideo(plan, firstLongSide)
            val resolvedSize = if (fastPath) null else resolveTargetSize(plan, firstSize)
            resolvedSize to fastPath
        }
        val composition = buildComposition(plan, targetSize, useTransmuxFastPath)
        runTransformer(composition, plan, useTransmuxFastPath, outputFile, onProgress)
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

    private fun resolveTargetSize(
        plan: ExportPlan,
        preReadFirstSize: Pair<Int, Int>?,
    ): Pair<Int, Int>? {
        if (plan.canvasAspect != CanvasAspect.ORIGINAL) {
            return ExportGeometry.sizeFor(
                aspect = plan.canvasAspect,
                quality = plan.exportQuality,
                firstClipWidth = 0,
                firstClipHeight = 0,
            )
        }
        val firstClip = plan.clips.first()
        val firstSize = preReadFirstSize ?: return null
        val clipRotation = ProjectTimeline.normalizeRotationDegrees(firstClip.rotationDegrees)
        val (effectiveWidth, effectiveHeight) = if (clipRotation == 90 || clipRotation == 270) {
            firstSize.second to firstSize.first
        } else {
            firstSize.first to firstSize.second
        }
        return ExportGeometry.sizeFor(
            aspect = CanvasAspect.ORIGINAL,
            quality = plan.exportQuality,
            firstClipWidth = effectiveWidth,
            firstClipHeight = effectiveHeight,
        )
    }

    private fun readImageSize(path: String): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            Pair(bounds.outWidth, bounds.outHeight)
        } else {
            null
        }
    }

    private fun readRotatedSize(path: String): Pair<Int, Int>? {
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
                Pair(height, width)
            } else {
                Pair(width, height)
            }
        } catch (_: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun buildComposition(
        plan: ExportPlan,
        targetSize: Pair<Int, Int>?,
        useTransmuxFastPath: Boolean,
    ): Composition {
        val presentationEffects: List<Effect> = if (!useTransmuxFastPath && targetSize != null) {
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
        val frameRateEffects: List<Effect> = if (useTransmuxFastPath) {
            emptyList()
        } else {
            when (plan.exportFrameRate) {
                ExportFrameRate.AUTO -> emptyList()
                ExportFrameRate.FPS_30 -> listOf(FrameDropEffect.createDefaultFrameDropEffect(30f))
                ExportFrameRate.FPS_60 -> listOf(FrameDropEffect.createDefaultFrameDropEffect(60f))
            }
        }

        val editedVideoItems = plan.clips.map { clip ->
            val cropEffects = VideoEffectFactory.buildCropEffects(clip.crop)
            val transformEffects = VideoEffectFactory.buildTransformEffects(
                rotationDegrees = clip.rotationDegrees,
                flipHorizontal = clip.flipHorizontal,
                flipVertical = clip.flipVertical,
            )
            val clipEffects = VideoEffectFactory.buildClipEffects(clip.visualEffects, clip.durationMs)
            if (clip.isStillImage) {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(File(clip.path)))
                    .setImageDurationMs(clip.durationMs.coerceAtLeast(1L))
                    .build()
                val allVideoEffects = cropEffects + transformEffects + presentationEffects + clipEffects
                EditedMediaItem.Builder(mediaItem)
                    .setDurationUs(clip.durationMs.coerceAtLeast(1L) * 1_000L)
                    .setFrameRate(FREEZE_FRAME_RATE_FPS)
                    .setEffects(Effects(emptyList(), allVideoEffects))
                    .build()
            } else {
                val mediaItem = buildClippedMediaItem(clip.path, clip.trimStartMs, clip.trimEndMs)
                val speedVideoEffects = VideoEffectFactory.buildSpeedEffects(clip.playbackSpeed)
                val allVideoEffects = cropEffects + transformEffects + presentationEffects + clipEffects + speedVideoEffects + frameRateEffects
                val audioProcessors = buildAudioProcessors(
                    volume = clip.volume,
                    playbackSpeed = clip.playbackSpeed,
                )
                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(audioProcessors, allVideoEffects))
                    .build()
            }
        }

        val sequences = mutableListOf(EditedMediaItemSequence(editedVideoItems))
        if (plan.audioClips.isNotEmpty()) {
            val editedAudioItems = plan.audioClips.map { audioClip ->
                val mediaItem = buildClippedMediaItem(
                    path = audioClip.path,
                    trimStartMs = audioClip.trimStartMs,
                    trimEndMs = audioClip.trimEndMs,
                )
                val audioProcessors = buildAudioProcessors(
                    volume = audioClip.volume,
                    playbackSpeed = audioClip.playbackSpeed,
                )
                EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .setEffects(Effects(audioProcessors, emptyList()))
                    .build()
            }
            sequences.add(EditedMediaItemSequence(editedAudioItems))
        }

        val builder = Composition.Builder(sequences)
            .experimentalSetForceAudioTrack(true)
        val compositionVideoEffects = buildList<Effect> {
            if (plan.overlays.isNotEmpty() || plan.textOverlays.isNotEmpty()) {
                val textureOverlays: List<TextureOverlay> = buildList(plan.overlays.size + plan.textOverlays.size) {
                    plan.overlays.forEach { add(TimedBitmapOverlay(it)) }
                    plan.textOverlays.forEach { add(TimedTextOverlay(it)) }
                }
                add(OverlayEffect(ImmutableList.copyOf(textureOverlays)))
            }
            CaptionOverlayEffectFactory.create(plan.captionDocument)?.let { add(it) }
        }
        if (compositionVideoEffects.isNotEmpty()) {
            builder.setEffects(Effects(emptyList(), compositionVideoEffects))
        }
        return builder.build()
    }

    private fun buildClippedMediaItem(
        path: String,
        trimStartMs: Long,
        trimEndMs: Long,
    ): MediaItem {
        val builder = MediaItem.Builder().setUri(Uri.fromFile(File(path)))
        if (trimEndMs > trimStartMs) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs)
                    .setEndPositionMs(trimEndMs)
                    .build(),
            )
        }
        return builder.build()
    }

    private fun buildAudioProcessors(
        volume: Float,
        playbackSpeed: Float,
    ): List<AudioProcessor> = buildList {
        val safeSpeed = ProjectTimeline.sanitizeSpeed(playbackSpeed)
        if (safeSpeed != Clip.DEFAULT_PLAYBACK_SPEED) {
            add(SonicAudioProcessor().apply { setSpeed(safeSpeed) })
        }
        val clampedVolume = ProjectTimeline.sanitizeVolume(volume)
        if (clampedVolume < 1.0f) {
            // Scaling to 0f via ChannelMixingAudioProcessor mutes cleanly while keeping a valid
            // audio track for Transformer's Composition audio mixer across multi-clip sequences.
            add(
                ChannelMixingAudioProcessor().apply {
                    putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(clampedVolume))
                    putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(clampedVolume))
                },
            )
        }
    }

    private suspend fun runTransformer(
        composition: Composition,
        plan: ExportPlan,
        useTransmuxFastPath: Boolean,
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
                    val transformerBuilder = Transformer.Builder(context)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(listener)
                    if (!useTransmuxFastPath) {
                        val targetBitrateBps = ExportGeometry.targetBitrateBps(
                            quality = plan.exportQuality,
                            frameRate = plan.exportFrameRate,
                            bitrate = plan.exportBitrate,
                        )
                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(targetBitrateBps)
                                    .build(),
                            )
                            .setEnableFallback(true)
                            .build()
                        transformerBuilder
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setEncoderFactory(encoderFactory)
                    }
                    val built = transformerBuilder.build()
                    transformer = built
                    built.start(composition, outputFile.absolutePath)
                    mainHandler.postDelayed(poller, POLL_INTERVAL_MS)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
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
        const val FREEZE_FRAME_RATE_FPS = 30
    }
}

/**
 * Shared timed [BitmapOverlay] lifecycle for Media3 1.4.1.
 * Builds a single output-resolution bitmap inside [configure], hides outside
 * `[startTimeUs, endTimeUs)` via alpha 0, and recycles deterministically in [configure] & [release].
 * Media3 OverlaySettings.setRotationDegrees is counter-clockwise, so clockwise model degrees are negated.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private abstract class TimedCanvasBitmapOverlay(
    startTimeMs: Long,
    endTimeMs: Long,
    animationDurationMs: Long,
    private val baseCenterX: Float,
    private val baseCenterY: Float,
    keyframes: List<OverlayKeyframe>,
) : BitmapOverlay() {

    private val startTimeUs: Long = startTimeMs * 1_000L
    private val endTimeUs: Long = endTimeMs * 1_000L
    private val animationDurationUs: Long = animationDurationMs
        .coerceAtLeast(endTimeMs - startTimeMs)
        .coerceAtLeast(1L) * 1_000L
    private val sanitizedKeyframes: List<OverlayKeyframe> =
        KeyframeInterpolator.sanitizeList(keyframes, baseCenterX, baseCenterY)

    private val staticVisibleSettings: OverlaySettings = buildOverlaySettings(
        KeyframeInterpolator.evaluateSanitized(
            baseCenterX = baseCenterX,
            baseCenterY = baseCenterY,
            sanitizedKeyframes = sanitizedKeyframes,
            progress = 0f,
        ),
    )

    private val hiddenSettings: OverlaySettings = OverlaySettings.Builder()
        .setAlphaScale(0f)
        .build()

    private var cachedBitmap: Bitmap? = null

    protected abstract fun buildBitmap(videoSize: Size): Bitmap?

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedBitmap = buildBitmap(videoSize)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val current = cachedBitmap
        if (current != null && !current.isRecycled) {
            return current
        }
        val fallback = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        cachedBitmap = fallback
        return fallback
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (presentationTimeUs !in startTimeUs until endTimeUs) {
            return hiddenSettings
        }
        if (sanitizedKeyframes.size <= 1) {
            return staticVisibleSettings
        }
        val resolved = KeyframeInterpolator.evaluateSanitizedAtTime(
            baseCenterX = baseCenterX,
            baseCenterY = baseCenterY,
            sanitizedKeyframes = sanitizedKeyframes,
            elapsedTime = presentationTimeUs - startTimeUs,
            totalDuration = animationDurationUs,
        )
        return buildOverlaySettings(resolved)
    }

    override fun release() {
        super.release()
        cachedBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedBitmap = null
    }

    private fun buildOverlaySettings(resolved: ResolvedOverlayTransform): OverlaySettings =
        OverlaySettings.Builder()
            .setAlphaScale(resolved.opacity)
            .setBackgroundFrameAnchor(
                resolved.centerX * 2f - 1f,
                1f - resolved.centerY * 2f,
            )
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(resolved.scale, resolved.scale)
            .setRotationDegrees(-resolved.rotationDegrees)
            .build()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TimedBitmapOverlay(
    private val overlay: ExportOverlay,
) : TimedCanvasBitmapOverlay(
    startTimeMs = overlay.startTimeMs,
    endTimeMs = overlay.endTimeMs,
    animationDurationMs = overlay.animationDurationMs,
    baseCenterX = overlay.centerX,
    baseCenterY = overlay.centerY,
    keyframes = overlay.keyframes,
) {
    override fun buildBitmap(videoSize: Size): Bitmap? {
        val targetWidthPx = (videoSize.width * overlay.widthFraction).roundToInt().coerceAtLeast(1)
        return decodeScaledOverlayBitmap(overlay.path, targetWidthPx)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TimedTextOverlay(
    private val textOverlay: ExportTextOverlay,
) : TimedCanvasBitmapOverlay(
    startTimeMs = textOverlay.startTimeMs,
    endTimeMs = textOverlay.endTimeMs,
    animationDurationMs = textOverlay.animationDurationMs,
    baseCenterX = textOverlay.centerX,
    baseCenterY = textOverlay.centerY,
    keyframes = textOverlay.keyframes,
) {
    override fun buildBitmap(videoSize: Size): Bitmap? {
        return TextOverlayBitmapRenderer.render(
            text = textOverlay.text,
            style = textOverlay.style,
            canvasWidthPx = videoSize.width,
            canvasHeightPx = videoSize.height,
            maxWidthFraction = textOverlay.widthFraction,
        )
    }
}

private fun decodeScaledOverlayBitmap(path: String, targetWidthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= targetWidthPx) {
        sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
    val aspectRatio = bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
    val targetHeightPx = (targetWidthPx / aspectRatio).roundToInt().coerceAtLeast(1)
    if (decoded.width == targetWidthPx && decoded.height == targetHeightPx) {
        return decoded
    }
    val scaled = Bitmap.createScaledBitmap(decoded, targetWidthPx, targetHeightPx, true)
    if (scaled !== decoded) {
        decoded.recycle()
    }
    return scaled
}
