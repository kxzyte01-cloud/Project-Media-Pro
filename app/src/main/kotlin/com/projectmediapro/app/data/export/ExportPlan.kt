package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.videoClips as modelVideoClips
import java.io.File
import kotlin.math.roundToInt

/**
 * Returns the clips of the first video track in timeline order, or an empty list.
 */
fun Project.videoClips(): List<Clip> = modelVideoClips()

/**
 * One clip to export. A [trimEndMs] of 0 means "export the whole file".
 */
data class ExportClip(
    val path: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
)

/**
 * The ordered clips to join into one video and their combined duration.
 */
data class ExportPlan(
    val clips: List<ExportClip>,
    val totalDurationMs: Long,
)

/**
 * Pure helpers that turn a project into an [ExportPlan] and estimate the space it needs.
 */
object ExportPlanner {
    const val BYTES_PER_SECOND: Long = 2_500_000L
    const val MIN_ESTIMATED_BYTES: Long = 50L * 1024L * 1024L
    const val MAX_LONG_SIDE_PX: Int = 1920

    /**
     * Uses the clips of the first video track in timeline order. Clips whose file does not exist are
     * skipped. Throws [IllegalStateException] when no usable clip remains.
     */
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
        return ExportPlan(clips = exportClips, totalDurationMs = totalDurationMs)
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
    fun fitExportSize(width: Int, height: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        val longSide = maxOf(width, height)
        val scale = if (longSide > MAX_LONG_SIDE_PX) {
            MAX_LONG_SIDE_PX.toDouble() / longSide
        } else {
            1.0
        }
        return Pair(toEven((width * scale).roundToInt()), toEven((height * scale).roundToInt()))
    }

    private fun toEven(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)
}
