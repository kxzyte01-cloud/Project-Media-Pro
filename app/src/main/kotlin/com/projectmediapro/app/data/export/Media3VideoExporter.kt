package com.projectmediapro.app.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.SpeedChangingAudioProcessor
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
import com.projectmediapro.core.model.AudioEnhanceConfig
import com.projectmediapro.core.model.ChromaKeyMath
import com.projectmediapro.core.model.SpeechWindowMs
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.KeyframeInterpolator
import com.projectmediapro.core.model.OverlayKeyframe
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.SpeedRampPreset
import com.projectmediapro.core.model.ResolvedOverlayTransform
import com.projectmediapro.feature.captions.CaptionOverlayEffectFactory
import com.projectmediapro.feature.news.NewsOverlayEffectFactory
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
                val speedVideoEffects = VideoEffectFactory.buildSpeedEffects(
                    playbackSpeed = clip.playbackSpeed,
                    speedRamp = clip.speedRamp,
                    sourceSpanMs = clip.sourceSpanMs,
                )
                val opticalFlowEffects = if (!useTransmuxFastPath && plan.opticalFlowEnabled) {
                    listOf(OpticalFlowGlEffect())
                } else {
                    emptyList()
                }
                val allVideoEffects = cropEffects + transformEffects + presentationEffects + speedVideoEffects + clipEffects + frameRateEffects + opticalFlowEffects
                val audioProcessors = buildAudioProcessors(
                    volume = clip.volume,
                    audioBoost = clip.audioBoost,
                    fadeInMs = clip.audioFadeInMs,
                    fadeOutMs = clip.audioFadeOutMs,
                    clipDurationMs = clip.durationMs,
                    playbackSpeed = clip.playbackSpeed,
                    speedRamp = clip.speedRamp,
                    sourceSpanMs = clip.sourceSpanMs,
                    audioEnhance = clip.audioEnhance,
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
                    audioBoost = audioClip.audioBoost,
                    fadeInMs = audioClip.audioFadeInMs,
                    fadeOutMs = audioClip.audioFadeOutMs,
                    clipDurationMs = audioClip.durationMs,
                    playbackSpeed = audioClip.playbackSpeed,
                    speedRamp = audioClip.speedRamp,
                    sourceSpanMs = audioClip.sourceSpanMs,
                    audioEnhance = audioClip.audioEnhance,
                    duckingWindowsMs = if (audioClip.audioEnhance?.autoDuckMusicEnabled == true) {
                        plan.duckingWindowsMs
                    } else {
                        emptyList()
                    },
                    timelineOffsetMs = audioClip.timelineStartMs,
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
            if (!useTransmuxFastPath && plan.beatMarkers.isNotEmpty()) {
                addAll(VideoEffectFactory.buildBeatDropEffects(plan.beatMarkers, plan.soundReactiveEnabled))
            }
            val captionOverlay = CaptionOverlayEffectFactory.createOverlay(plan.captionDocument, plan.newsOverlays)
            val newsOverlay = NewsOverlayEffectFactory.createOverlay(plan.newsOverlays)
            if (plan.overlays.isNotEmpty() || plan.textOverlays.isNotEmpty() || captionOverlay != null || newsOverlay != null) {
                val textureOverlays: List<TextureOverlay> = buildList(
                    plan.overlays.size + plan.textOverlays.size +
                        (if (newsOverlay != null) 1 else 0) +
                        (if (captionOverlay != null) 1 else 0),
                ) {
                    plan.overlays.forEach { overlay -> if (overlay.isVideo) add(TimedVideoOverlay(overlay)) else add(TimedBitmapOverlay(overlay)) }
                    plan.textOverlays.forEach { add(TimedTextOverlay(it)) }
                    if (newsOverlay != null) {
                        add(newsOverlay)
                    }
                    if (captionOverlay != null) {
                        add(captionOverlay)
                    }
                }
                add(OverlayEffect(ImmutableList.copyOf(textureOverlays)))
            }
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
        audioBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        clipDurationMs: Long = 0L,
        playbackSpeed: Float,
        speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
        sourceSpanMs: Long = 0L,
        audioEnhance: AudioEnhanceConfig? = null,
        duckingWindowsMs: List<SpeechWindowMs> = emptyList(),
        timelineOffsetMs: Long = 0L,
    ): List<AudioProcessor> = buildList {
        val speedProvider = VideoEffectFactory.buildSpeedProvider(playbackSpeed, speedRamp, sourceSpanMs)
        if (speedRamp != SpeedRampPreset.NONE && speedProvider != null) {
            add(SpeedChangingAudioProcessor(speedProvider))
        } else {
            val safeSpeed = ProjectTimeline.sanitizeSpeed(playbackSpeed)
            if (safeSpeed != Clip.DEFAULT_PLAYBACK_SPEED) {
                add(SonicAudioProcessor().apply { setSpeed(safeSpeed) })
            }
        }
        val clampedVolume = ProjectTimeline.sanitizeVolume(volume)
        val clampedBoost = ProjectTimeline.sanitizeAudioBoost(audioBoost)
        val effectiveGain = (clampedVolume * clampedBoost).coerceIn(0f, Clip.MAX_AUDIO_BOOST)
        val hasFades = fadeInMs > 0L || fadeOutMs > 0L
        val hasEnhance = audioEnhance != null && !audioEnhance.isIdentity
        if (kotlin.math.abs(effectiveGain - 1.0f) >= 0.001f || hasFades || hasEnhance) {
            // SoftClipAudioProcessor handles arbitrary channel counts (mono, stereo, 5.1/7.1)
            // cleanly without throwing UnhandledAudioFormatException on surround tracks.
            add(
                SoftClipAudioProcessor(
                    gain = effectiveGain,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs,
                    clipDurationMs = clipDurationMs,
                    audioEnhance = audioEnhance,
                    duckingWindowsMs = duckingWindowsMs,
                    timelineOffsetMs = timelineOffsetMs,
                ),
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
                        val baseBitrateBps = ExportGeometry.targetBitrateBps(
                            quality = plan.exportQuality,
                            frameRate = plan.exportFrameRate,
                            bitrate = plan.exportBitrate,
                        )
                        val targetBitrateBps = if (plan.opticalFlowEnabled) {
                            (baseBitrateBps * 1.15).toInt()
                        } else {
                            baseBitrateBps
                        }
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
    private val baseOpacity: Float = 1f,
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
            .setAlphaScale((resolved.opacity * baseOpacity).coerceIn(0f, 1f))
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
    baseOpacity = overlay.style?.sanitized()?.opacity ?: 1f,
) {
    override fun buildBitmap(videoSize: Size): Bitmap? {
        val targetWidthPx = (videoSize.width * overlay.widthFraction).roundToInt().coerceAtLeast(1)
        val decoded = decodeScaledOverlayBitmap(overlay.path, targetWidthPx) ?: return null
        val styled = OverlayBitmapStyler.applyStyle(decoded, overlay.style)
        if (styled !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }
        return styled
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


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class TimedVideoOverlay(
    private val overlay: ExportOverlay,
) : BitmapOverlay() {
    private val startTimeUs = overlay.startTimeMs * 1_000L
    private val endTimeUs = overlay.endTimeMs * 1_000L
    private val animationDurationMs = overlay.animationDurationMs.coerceAtLeast(1L)
    private val hiddenSettings = OverlaySettings.Builder().setAlphaScale(0f).build()
    private var retriever: MediaMetadataRetriever? = null
    private var frameW = 1
    private var frameH = 1
    private val cache = object : LinkedHashMap<Long, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean {
            if (size > MAX_CACHED_FRAMES) {
                eldest?.value?.let { if (!it.isRecycled) it.recycle() }
                return true
            }
            return false
        }
    }
    private var blank: Bitmap? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        release()
        frameW = (videoSize.width * overlay.widthFraction).roundToInt().coerceAtLeast(2)
        val r = MediaMetadataRetriever()
        runCatching { r.setDataSource(overlay.path) }
        retriever = r
        val vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: frameW
        val vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: frameW
        frameH = if (vw > 0) (frameW * vh / vw).coerceAtLeast(2) else frameW
        blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (presentationTimeUs !in startTimeUs until endTimeUs) return hiddenSettings
        val elapsedMs = ((presentationTimeUs - startTimeUs) / 1_000L).coerceAtLeast(0L)
        val resolved = KeyframeInterpolator.evaluateAtTime(
            baseCenterX = overlay.centerX,
            baseCenterY = overlay.centerY,
            keyframes = overlay.keyframes,
            elapsedTime = elapsedMs,
            totalDuration = animationDurationMs,
        )
        val styleAlpha = overlay.style?.sanitized()?.opacity ?: 1f
        return OverlaySettings.Builder()
            .setBackgroundFrameAnchor(resolved.centerX * 2f - 1f, 1f - resolved.centerY * 2f)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(resolved.scale, resolved.scale)
            .setRotationDegrees(-resolved.rotationDegrees)
            .setAlphaScale((resolved.opacity * styleAlpha).coerceIn(0f, 1f))
            .build()
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (presentationTimeUs !in startTimeUs until endTimeUs) return blank ?: fallback()
        val localMs = (presentationTimeUs - startTimeUs) / 1_000L
        val bucket = (localMs / FRAME_INTERVAL_MS) * FRAME_INTERVAL_MS
        cache[bucket]?.let { if (!it.isRecycled) return it }
        val r = retriever ?: return blank ?: fallback()
        val sourceMs = overlay.trimStartMs + bucket
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                r.getScaledFrameAtTime(
                    sourceMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    frameW,
                    frameH,
                )
            }.getOrNull()
        } else {
            null
        }
        val bitmap = frame
            ?: runCatching {
                r.getFrameAtTime(sourceMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull()
            ?: return blank ?: fallback()
        val scaled = if (bitmap.width != frameW || bitmap.height != frameH) {
            Bitmap.createScaledBitmap(bitmap, frameW, frameH, true).also {
                if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle()
            }
        } else {
            bitmap
        }
        val mutable = if (scaled.isMutable) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, true).also {
                if (it !== scaled && !scaled.isRecycled) scaled.recycle()
            }
        }
        if (overlay.chromaKey.enabled) {
            val pixels = IntArray(mutable.width * mutable.height)
            mutable.getPixels(pixels, 0, mutable.width, 0, 0, mutable.width, mutable.height)
            val (cb, cr) = overlay.chromaKey.keyChroma()
            ChromaKeyMath.applyToPixels(
                pixels = pixels,
                keyCb = cb,
                keyCr = cr,
                similarity = overlay.chromaKey.similarity,
                smoothness = overlay.chromaKey.smoothness,
                spill = overlay.chromaKey.spillSuppression,
            )
            mutable.setPixels(pixels, 0, mutable.width, 0, 0, mutable.width, mutable.height)
        }
        cache[bucket] = mutable
        return mutable
    }

    override fun release() {
        super.release()
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        runCatching { retriever?.release() }
        retriever = null
        blank?.let { if (!it.isRecycled) it.recycle() }
        blank = null
    }

    private fun fallback(): Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { blank = it }

    companion object {
        const val FRAME_INTERVAL_MS = 83L
        const val MAX_CACHED_FRAMES = 24
    }
}
