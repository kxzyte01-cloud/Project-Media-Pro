package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.AudioDuckingEnvelope
import com.projectmediapro.core.model.AudioEnhanceConfig
import com.projectmediapro.core.model.SpeechWindowMs
import com.projectmediapro.core.model.BeatEffectKind
import com.projectmediapro.core.model.BeatMarker
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.ChromaKeyConfig
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipCrop
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.KeyframeInterpolator
import com.projectmediapro.core.model.OverlayKeyframe
import com.projectmediapro.core.model.OverlayVisualStyle
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.SpeedRampEvaluator
import com.projectmediapro.core.model.SpeedRampPreset
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.audioClips as modelAudioClips
import com.projectmediapro.core.model.overlayClips as modelOverlayClips
import com.projectmediapro.core.model.textClips as modelTextClips
import com.projectmediapro.core.model.videoClips as modelVideoClips
import com.projectmediapro.feature.captions.CaptionDocument
import java.io.File
import kotlin.math.roundToLong

fun Project.videoClips(): List<Clip> = modelVideoClips()

/**
 * One video clip to export. A [trimEndMs] of 0 means "export the whole file".
 */
data class ExportClip(
    val path: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val durationMs: Long = 0L,
    val visualEffects: ClipVisualEffects? = null,
    val volume: Float = 1.0f,
    val audioBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
    val audioFadeInMs: Long = 0L,
    val audioFadeOutMs: Long = 0L,
    val audioEnhance: AudioEnhanceConfig? = null,
    val playbackSpeed: Float = Clip.DEFAULT_PLAYBACK_SPEED,
    val speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
    val sourceSpanMs: Long = 0L,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: ClipCrop? = null,
    val isStillImage: Boolean = false,
    val muted: Boolean = false,
)

/**
 * One audio clip to export. A [trimEndMs] of 0 means "export the whole file".
 */
data class ExportAudioClip(
    val path: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val durationMs: Long = 0L,
    val timelineStartMs: Long = 0L,
    val volume: Float = 1.0f,
    val audioBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
    val audioFadeInMs: Long = 0L,
    val audioFadeOutMs: Long = 0L,
    val audioEnhance: AudioEnhanceConfig? = null,
    val playbackSpeed: Float = Clip.DEFAULT_PLAYBACK_SPEED,
    val speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
    val sourceSpanMs: Long = 0L,
)

/**
 * One timed image overlay to composite over the exported video frame.
 */
data class ExportOverlay(
    val path: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val centerX: Float,
    val centerY: Float,
    val widthFraction: Float,
    val keyframes: List<OverlayKeyframe> = emptyList(),
    val animationDurationMs: Long = (endTimeMs - startTimeMs).coerceAtLeast(0L),
    val style: OverlayVisualStyle? = null,
    val isVideo: Boolean = false,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val sourceSpanMs: Long = 0L,
    val muted: Boolean = true,
    val volume: Float = 1.0f,
    val chromaKey: ChromaKeyConfig = ChromaKeyConfig(),
)

/**
 * One timed styled text/lyric overlay to composite over the exported video frame.
 */
data class ExportTextOverlay(
    val text: String,
    val style: TextOverlayStyle,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val centerX: Float,
    val centerY: Float,
    val widthFraction: Float,
    val keyframes: List<OverlayKeyframe> = emptyList(),
    val animationDurationMs: Long = (endTimeMs - startTimeMs).coerceAtLeast(0L),
)

data class ExportPlan(
    val clips: List<ExportClip>,
    val totalDurationMs: Long,
    val audioClips: List<ExportAudioClip> = emptyList(),
    val overlays: List<ExportOverlay> = emptyList(),
    val textOverlays: List<ExportTextOverlay> = emptyList(),
    val canvasAspect: CanvasAspect = CanvasAspect.ORIGINAL,
    val exportQuality: ExportQuality = ExportQuality.FULL_HD_1080P,
    val exportFrameRate: ExportFrameRate = ExportFrameRate.AUTO,
    val exportBitrate: ExportBitrate = ExportBitrate.STANDARD,
    val captionDocument: CaptionDocument? = null,
    val duckingWindowsMs: List<SpeechWindowMs> = emptyList(),
    val beatMarkers: List<BeatMarker> = emptyList(),
    val soundReactiveEnabled: Boolean = false,
    val opticalFlowEnabled: Boolean = false,
    val newsOverlays: List<NewsOverlayItem> = emptyList(),
)

object ExportPlanner {
    const val BYTES_PER_SECOND: Long = 2_500_000L
    const val MIN_ESTIMATED_BYTES: Long = 50L * 1024L * 1024L
    const val MAX_LONG_SIDE_PX: Int = ExportGeometry.FULL_HD_LONG_SIDE_PX

    fun plan(
        project: Project,
        captionDocument: CaptionDocument? = null,
        resolve: (String) -> File?,
    ): ExportPlan {
        val clips = project.modelVideoClips()
        if (clips.isEmpty()) {
            throw IllegalStateException("No video clips to export")
        }

        val exportClips = mutableListOf<ExportClip>()
        var totalDurationMs = 0L
        for (clip in clips) {
            val file = resolve(clip.mediaUri)
            if (file == null || !file.isFile) continue

            val clipDurationMs = ProjectTimeline.effectiveDurationMs(clip)
            if (clipDurationMs <= 0L) continue

            val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
            val sanitizedEffects = clip.visualEffects
                ?.sanitized(clipDurationMs)
                ?.takeUnless { it.isIdentity }
            exportClips.add(
                ExportClip(
                    path = file.absolutePath,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    durationMs = clipDurationMs,
                    visualEffects = sanitizedEffects,
                    volume = if (clip.muted) 0f else ProjectTimeline.sanitizeVolume(clip.volume),
                    audioBoost = ProjectTimeline.sanitizeAudioBoost(clip.audioBoost),
                    audioFadeInMs = ProjectTimeline.sanitizeAudioFadeMs(clip.audioFadeInMs, clipDurationMs),
                    audioFadeOutMs = ProjectTimeline.sanitizeAudioFadeMs(clip.audioFadeOutMs, clipDurationMs),
                    audioEnhance = clip.audioEnhance?.takeUnless { it.isIdentity },
                    playbackSpeed = ProjectTimeline.effectiveSpeed(clip),
                    speedRamp = clip.speedRamp,
                    sourceSpanMs = ProjectTimeline.effectiveSourceSpanMs(clip),
                    rotationDegrees = ProjectTimeline.normalizeRotationDegrees(clip.rotationDegrees),
                    flipHorizontal = clip.flipHorizontal,
                    flipVertical = clip.flipVertical,
                    crop = clip.crop?.sanitized()?.takeUnless { it.isIdentity },
                    isStillImage = clip.isStillImage,
                    muted = clip.muted,
                ),
            )
            totalDurationMs += clipDurationMs
        }

        if (exportClips.isEmpty()) {
            throw IllegalStateException("No usable clips to export")
        }

        val exportAudioClips = planAudioClips(
            audioClips = project.modelAudioClips(),
            totalVideoDurationMs = totalDurationMs,
            resolve = resolve,
        )
        val exportOverlays = planOverlays(
            overlayClips = project.modelOverlayClips(),
            totalVideoDurationMs = totalDurationMs,
            resolve = resolve,
        )
        val exportTextOverlays = planTextOverlays(
            textClips = project.modelTextClips(),
            totalVideoDurationMs = totalDurationMs,
        )

        return ExportPlan(
            clips = exportClips,
            totalDurationMs = totalDurationMs,
            audioClips = exportAudioClips,
            overlays = exportOverlays,
            textOverlays = exportTextOverlays,
            canvasAspect = project.canvasAspect,
            exportQuality = project.exportQuality,
            exportFrameRate = project.exportFrameRate,
            exportBitrate = project.exportBitrate,
            captionDocument = captionDocument?.takeIf { it.lines.isNotEmpty() },
            duckingWindowsMs = extractSpeechWindows(captionDocument),
            beatMarkers = project.beatMarkers.map { it.sanitized() },
            soundReactiveEnabled = project.soundReactiveEnabled,
            opticalFlowEnabled = project.opticalFlowEnabled,
            newsOverlays = project.newsOverlays,
        )
    }

    private fun planAudioClips(
        audioClips: List<Clip>,
        totalVideoDurationMs: Long,
        resolve: (String) -> File?,
    ): List<ExportAudioClip> {
        if (audioClips.isEmpty() || totalVideoDurationMs <= 0L) return emptyList()
        val planned = mutableListOf<ExportAudioClip>()
        var timelineMs = 0L

        for (clip in audioClips) {
            if (timelineMs >= totalVideoDurationMs) break
            val file = resolve(clip.mediaUri)
            if (file == null || !file.isFile) continue

            val clipDurationMs = ProjectTimeline.effectiveDurationMs(clip)
            if (clipDurationMs <= 0L) continue

            val remainingMs = totalVideoDurationMs - timelineMs
            val exportedTimelineMs = minOf(clipDurationMs, remainingMs)
            if (exportedTimelineMs <= 0L) break

            val speed = ProjectTimeline.effectiveSpeed(clip)
            val sourceSpanMs = ProjectTimeline.effectiveSourceSpanMs(clip)
            val isTrimmed = ProjectTimeline.isClipTrimmed(clip)
            val isCutByVideoEnd = exportedTimelineMs < clipDurationMs
            val clipStartMs = if (clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs) {
                clip.trimStartMs
            } else {
                0L
            }
            val exportedSourceMs = if (isCutByVideoEnd) {
                SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
                    timelineOffsetMs = exportedTimelineMs,
                    sourceSpanMs = sourceSpanMs,
                    baseSpeed = clip.playbackSpeed,
                    preset = clip.speedRamp,
                ).coerceIn(1L, sourceSpanMs.coerceAtLeast(1L))
            } else {
                sourceSpanMs
            }
            val shouldSpecifyBounds = isTrimmed || isCutByVideoEnd

            planned.add(
                ExportAudioClip(
                    path = file.absolutePath,
                    trimStartMs = if (shouldSpecifyBounds) clipStartMs else 0L,
                    trimEndMs = if (shouldSpecifyBounds) clipStartMs + exportedSourceMs else 0L,
                    durationMs = exportedTimelineMs,
                    timelineStartMs = timelineMs,
                    volume = if (clip.muted) 0f else ProjectTimeline.sanitizeVolume(clip.volume),
                    audioBoost = ProjectTimeline.sanitizeAudioBoost(clip.audioBoost),
                    audioFadeInMs = ProjectTimeline.sanitizeAudioFadeMs(clip.audioFadeInMs, exportedTimelineMs),
                    audioFadeOutMs = ProjectTimeline.sanitizeAudioFadeMs(clip.audioFadeOutMs, exportedTimelineMs),
                    audioEnhance = clip.audioEnhance?.takeUnless { it.isIdentity },
                    playbackSpeed = speed,
                    speedRamp = clip.speedRamp,
                    sourceSpanMs = exportedSourceMs,
                ),
            )
            timelineMs += exportedTimelineMs
        }

        return planned
    }

    private fun planOverlays(
        overlayClips: List<Clip>,
        totalVideoDurationMs: Long,
        resolve: (String) -> File?,
    ): List<ExportOverlay> {
        if (overlayClips.isEmpty() || totalVideoDurationMs <= 0L) return emptyList()
        val planned = mutableListOf<ExportOverlay>()
        for (clip in overlayClips) {
            val file = resolve(clip.mediaUri)
            if (file == null || !file.isFile) continue

            val (startMs, endMs) = resolveTimedBounds(clip, totalVideoDurationMs) ?: continue
            val (centerX, centerY, widthFraction) = resolvePlacement(
                clip = clip,
                defaultX = Clip.DEFAULT_OVERLAY_CENTER_X,
                defaultY = Clip.DEFAULT_OVERLAY_CENTER_Y,
                defaultWidth = Clip.DEFAULT_OVERLAY_WIDTH_FRACTION,
            )

            planned.add(
                ExportOverlay(
                    path = file.absolutePath,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    centerX = centerX,
                    centerY = centerY,
                    widthFraction = widthFraction,
                    keyframes = KeyframeInterpolator.sanitizeList(clip.keyframes, centerX, centerY),
                    animationDurationMs = clip.durationMs.coerceAtLeast(endMs - startMs),
                    style = clip.overlayStyle?.sanitized()?.takeUnless { it.isIdentity },
                    isVideo = clip.isVideo,
                    trimStartMs = ProjectTimeline.effectiveTrimStartMs(clip),
                    trimEndMs = ProjectTimeline.effectiveTrimEndMs(clip),
                    sourceSpanMs = ProjectTimeline.effectiveSourceSpanMs(clip),
                    muted = clip.muted,
                    volume = clip.effectiveVolume,
                    chromaKey = clip.chromaKey.sanitized(),
                ),
            )
        }
        return planned
    }

    private fun planTextOverlays(
        textClips: List<Clip>,
        totalVideoDurationMs: Long,
    ): List<ExportTextOverlay> {
        if (textClips.isEmpty() || totalVideoDurationMs <= 0L) return emptyList()
        val planned = mutableListOf<ExportTextOverlay>()
        for (clip in textClips) {
            val cleanedText = clip.textContent.trim()
            if (cleanedText.isEmpty()) continue

            val (startMs, endMs) = resolveTimedBounds(clip, totalVideoDurationMs) ?: continue
            val (centerX, centerY, widthFraction) = resolvePlacement(
                clip = clip,
                defaultX = Clip.DEFAULT_TEXT_CENTER_X,
                defaultY = Clip.DEFAULT_TEXT_CENTER_Y,
                defaultWidth = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
            )

            planned.add(
                ExportTextOverlay(
                    text = cleanedText,
                    style = (clip.textStyle ?: TextOverlayStyle()).sanitized(),
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    centerX = centerX,
                    centerY = centerY,
                    widthFraction = widthFraction,
                    keyframes = KeyframeInterpolator.sanitizeList(clip.keyframes, centerX, centerY),
                    animationDurationMs = clip.durationMs.coerceAtLeast(endMs - startMs),
                ),
            )
        }
        return planned
    }

    private fun resolveTimedBounds(
        clip: Clip,
        totalVideoDurationMs: Long,
    ): Pair<Long, Long>? {
        val durationMs = clip.durationMs.coerceAtLeast(0L)
        if (durationMs <= 0L) return null
        val startMs = clip.startTimeMs.coerceAtLeast(0L)
        if (startMs >= totalVideoDurationMs) return null
        val endMs = minOf(startMs + durationMs, totalVideoDurationMs)
        return if (endMs > startMs) startMs to endMs else null
    }

    private fun resolvePlacement(
        clip: Clip,
        defaultX: Float,
        defaultY: Float,
        defaultWidth: Float,
    ): Triple<Float, Float, Float> {
        val centerX = OverlayKeyframe.sanitizeCoordinate(clip.overlayCenterX, defaultX)
        val centerY = OverlayKeyframe.sanitizeCoordinate(clip.overlayCenterY, defaultY)
        val widthFraction = OverlayKeyframe.sanitizeFinite(clip.overlayWidthFraction, defaultWidth)
            .coerceIn(Clip.MIN_OVERLAY_WIDTH_FRACTION, Clip.MAX_OVERLAY_WIDTH_FRACTION)
        return Triple(centerX, centerY, widthFraction)
    }

    /**
     * Rough size of the exported file (about 20 Mbps) with a 50 percent safety margin, never below 50 MB.
     */
    fun estimateExportBytes(totalDurationMs: Long): Long {
        if (totalDurationMs <= 0L) return MIN_ESTIMATED_BYTES
        val rawBytes = (totalDurationMs / 1000L) * BYTES_PER_SECOND
        return maxOf(MIN_ESTIMATED_BYTES, rawBytes * 3L / 2L)
    }

    /**
     * Plan-aware export size estimate incorporating target resolution, FPS, bitrate tier,
     * audio (~192 kbps), optional optical-flow overhead (+15%), and a 50% safety margin.
     */
    fun estimateExportBytes(plan: ExportPlan): Long {
        if (plan.totalDurationMs <= 0L) return MIN_ESTIMATED_BYTES
        val videoBps = ExportGeometry.targetBitrateBps(
            quality = plan.exportQuality,
            frameRate = plan.exportFrameRate,
            bitrate = plan.exportBitrate,
        ).toDouble()
        val effectiveVideoBps = if (plan.opticalFlowEnabled) videoBps * 1.15 else videoBps
        val totalBytesPerSec = (effectiveVideoBps + 192_000.0) / 8.0
        val durationSec = plan.totalDurationMs / 1_000.0
        val estimated = (totalBytesPerSec * durationSec * 1.5).roundToLong()
        return maxOf(MIN_ESTIMATED_BYTES, estimated)
    }

    /**
     * Output size for a clip of [width] x [height] (already rotated): the long side is capped at
     * [MAX_LONG_SIDE_PX] and both sides are made even, as H.264 requires. Returns null for bad input.
     */
    /**
     * Returns true when the video stream can safely transmux (direct sample copy without re-encoding)
     * in Media3 Transformer for dramatically faster exports.
     */
    fun canTransmuxVideo(plan: ExportPlan, sourceLongSidePx: Int?): Boolean {
        if (plan.clips.size != 1) return false
        if (plan.canvasAspect != CanvasAspect.ORIGINAL) return false
        if (plan.overlays.isNotEmpty() || plan.textOverlays.isNotEmpty() || plan.captionDocument != null || plan.newsOverlays.isNotEmpty()) return false
        if (plan.beatMarkers.any { it.effect != BeatEffectKind.NONE }) return false
        if (plan.soundReactiveEnabled && plan.beatMarkers.isNotEmpty()) return false
        if (plan.opticalFlowEnabled) return false
        if (plan.exportFrameRate != ExportFrameRate.AUTO || plan.exportBitrate != ExportBitrate.STANDARD) return false
        val longSide = sourceLongSidePx ?: return false
        if (longSide <= 0 || longSide > ExportGeometry.longSideCapFor(plan.exportQuality)) return false
        val clip = plan.clips.single()
        return !clip.isStillImage &&
            clip.visualEffects == null &&
            clip.crop == null &&
            clip.rotationDegrees == 0 &&
            !clip.flipHorizontal &&
            !clip.flipVertical &&
            clip.playbackSpeed == Clip.DEFAULT_PLAYBACK_SPEED &&
            clip.speedRamp == SpeedRampPreset.NONE
    }

    fun extractSpeechWindows(captionDocument: CaptionDocument?): List<SpeechWindowMs> {
        val doc = captionDocument?.takeIf { it.lines.isNotEmpty() } ?: return emptyList()
        val offsetMs = doc.globalOffsetUs / 1_000L
        val raw = doc.lines.mapNotNull { line ->
            val startMs = (line.startUs / 1_000L + offsetMs).coerceAtLeast(0L)
            val endMs = (line.endUs / 1_000L + offsetMs).coerceAtLeast(0L)
            if (endMs > startMs) SpeechWindowMs(startMs, endMs) else null
        }
        return AudioDuckingEnvelope.mergeWindows(raw)
    }

    fun fitExportSize(width: Int, height: Int): Pair<Int, Int>? =
        ExportGeometry.fitToLongSide(width, height, MAX_LONG_SIDE_PX)
}
