package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.audioClips as modelAudioClips
import com.projectmediapro.core.model.overlayClips as modelOverlayClips
import com.projectmediapro.core.model.videoClips as modelVideoClips
import java.io.File

fun Project.videoClips(): List<Clip> = modelVideoClips()

/**
 * One video clip to export. A [trimEndMs] of 0 means "export the whole file".
 */
data class ExportClip(
    val path: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
)

/**
 * One audio clip to export. A [trimEndMs] of 0 means "export the whole file".
 */
data class ExportAudioClip(
    val path: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val volume: Float = 1.0f,
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
)

data class ExportPlan(
    val clips: List<ExportClip>,
    val totalDurationMs: Long,
    val audioClips: List<ExportAudioClip> = emptyList(),
    val overlays: List<ExportOverlay> = emptyList(),
    val canvasAspect: CanvasAspect = CanvasAspect.ORIGINAL,
    val exportQuality: ExportQuality = ExportQuality.FULL_HD_1080P,
)

object ExportPlanner {
    const val BYTES_PER_SECOND: Long = 2_500_000L
    const val MIN_ESTIMATED_BYTES: Long = 50L * 1024L * 1024L
    const val MAX_LONG_SIDE_PX: Int = ExportGeometry.FULL_HD_LONG_SIDE_PX

    fun plan(project: Project, resolve: (String) -> File?): ExportPlan {
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
            exportClips.add(
                ExportClip(
                    path = file.absolutePath,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
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

        return ExportPlan(
            clips = exportClips,
            totalDurationMs = totalDurationMs,
            audioClips = exportAudioClips,
            overlays = exportOverlays,
            canvasAspect = project.canvasAspect,
            exportQuality = project.exportQuality,
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
            val exportedDurationMs = minOf(clipDurationMs, remainingMs)
            if (exportedDurationMs <= 0L) break

            val isTrimmed = ProjectTimeline.isClipTrimmed(clip)
            val isCutByVideoEnd = exportedDurationMs < clipDurationMs
            val clipStartMs = if (clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs) {
                clip.trimStartMs
            } else {
                0L
            }
            val shouldSpecifyBounds = isTrimmed || isCutByVideoEnd

            planned.add(
                ExportAudioClip(
                    path = file.absolutePath,
                    trimStartMs = if (shouldSpecifyBounds) clipStartMs else 0L,
                    trimEndMs = if (shouldSpecifyBounds) clipStartMs + exportedDurationMs else 0L,
                    volume = clip.volume.coerceIn(0f, 1f),
                ),
            )
            timelineMs += exportedDurationMs
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

            val durationMs = clip.durationMs.coerceAtLeast(0L)
            if (durationMs <= 0L) continue

            val startMs = clip.startTimeMs.coerceAtLeast(0L)
            if (startMs >= totalVideoDurationMs) continue

            val endMs = minOf(startMs + durationMs, totalVideoDurationMs)
            if (endMs <= startMs) continue

            planned.add(
                ExportOverlay(
                    path = file.absolutePath,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    centerX = clip.overlayCenterX.coerceIn(0f, 1f),
                    centerY = clip.overlayCenterY.coerceIn(0f, 1f),
                    widthFraction = clip.overlayWidthFraction.coerceIn(
                        Clip.MIN_OVERLAY_WIDTH_FRACTION,
                        Clip.MAX_OVERLAY_WIDTH_FRACTION,
                    ),
                ),
            )
        }
        return planned
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
     * Output size for a clip of [width] x [height] (already rotated): the long side is capped at
     * [MAX_LONG_SIDE_PX] and both sides are made even, as H.264 requires. Returns null for bad input.
     */
    fun fitExportSize(width: Int, height: Int): Pair<Int, Int>? =
        ExportGeometry.fitToLongSide(width, height, MAX_LONG_SIDE_PX)
}
