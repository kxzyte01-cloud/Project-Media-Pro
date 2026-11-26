package com.projectmediapro.app.editor

import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.SpeedRampPreset
import kotlin.math.abs
import kotlin.math.roundToLong

enum class TimelineTrimEdge {
    START,
    END,
}

/**
 * Pure JVM math for timeline zoom scaling, ruler tick spacing, drag-to-trim bounds, and
 * audio waveform peak bucketing.
 */
object TimelineScale {

    const val MIN_DP_PER_SECOND: Float = 12f
    const val DEFAULT_DP_PER_SECOND: Float = 32f
    const val MAX_DP_PER_SECOND: Float = 120f
    const val MIN_BLOCK_WIDTH_DP: Float = 48f
    private const val MAX_RULER_LABELS: Long = 120L

    fun clampZoom(dpPerSecond: Float): Float {
        if (dpPerSecond.isNaN() || dpPerSecond.isInfinite()) return DEFAULT_DP_PER_SECOND
        return dpPerSecond.coerceIn(MIN_DP_PER_SECOND, MAX_DP_PER_SECOND)
    }

    fun zoomPercent(dpPerSecond: Float): Int {
        val safe = clampZoom(dpPerSecond)
        return ((safe / DEFAULT_DP_PER_SECOND) * 100f).roundToLong().toInt()
    }

    fun msToDp(ms: Long, dpPerSecond: Float): Float {
        if (ms <= 0L) return 0f
        return (ms / 1_000f) * clampZoom(dpPerSecond)
    }

    fun dpToMs(dp: Float, dpPerSecond: Float): Long {
        if (dp.isNaN() || dp.isInfinite() || dp <= 0f) return 0L
        return ((dp / clampZoom(dpPerSecond)) * 1_000f).roundToLong().coerceAtLeast(0L)
    }

    fun signedDpToMs(deltaDp: Float, dpPerSecond: Float): Long {
        if (deltaDp.isNaN() || deltaDp.isInfinite() || deltaDp == 0f) return 0L
        val magnitudeMs = dpToMs(abs(deltaDp), dpPerSecond)
        return if (deltaDp < 0f) -magnitudeMs else magnitudeMs
    }

    /**
     * Picks a clean tick interval in milliseconds so adjacent ruler labels stay at least ~56 dp
     * apart and total label count stays bounded on long timelines.
     */
    fun rulerStepMs(dpPerSecond: Float, totalDurationMs: Long = 0L): Long {
        val safe = clampZoom(dpPerSecond)
        val candidates = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L)
        var chosen = candidates.last()
        for (stepMs in candidates) {
            if (msToDp(stepMs, safe) >= 56f) {
                chosen = stepMs
                break
            }
        }
        if (totalDurationMs > 0L) {
            while (totalDurationMs / chosen > MAX_RULER_LABELS && chosen < candidates.last()) {
                val next = candidates.firstOrNull { it > chosen } ?: break
                chosen = next
            }
        }
        return chosen
    }

    fun desiredThumbnailCount(blockWidthDp: Float, maxFrames: Int = 6): Int {
        if (blockWidthDp.isNaN() || blockWidthDp <= 0f) return 1
        return (blockWidthDp / 42f).roundToLong().toInt().coerceIn(1, maxFrames.coerceAtLeast(1))
    }

    /**
     * Computes clamped `(newTrimStartMs, newTrimEndMs)` in source media coordinates when dragging
     * [edge] by [deltaTimelineMs] on the timeline.
     */
    fun computeDragTrimBounds(
        clip: Clip,
        edge: TimelineTrimEdge,
        deltaTimelineMs: Long,
    ): Pair<Long, Long> {
        val sourceDurationMs = ProjectTimeline.effectiveSourceDurationMs(clip)
        val currentStartMs = ProjectTimeline.effectiveTrimStartMs(clip)
        val currentEndMs = ProjectTimeline.effectiveTrimEndMs(clip)
        val currentSourceSpanMs = (currentEndMs - currentStartMs).coerceAtLeast(1L)
        val currentTimelineMs = ProjectTimeline.effectiveDurationMs(clip).coerceAtLeast(1L)
        val deltaSourceMs = if (clip.speedRamp != SpeedRampPreset.NONE) {
            val effectiveAvgSpeed = currentSourceSpanMs.toDouble() / currentTimelineMs.toDouble()
            (deltaTimelineMs * effectiveAvgSpeed).roundToLong()
        } else {
            val speed = ProjectTimeline.effectiveSpeed(clip)
            (deltaTimelineMs * speed).roundToLong()
        }
        val minSpanMs = ProjectTimeline.MIN_CLIP_DURATION_MS

        return when (edge) {
            TimelineTrimEdge.START -> {
                val maxStartMs = (currentEndMs - minSpanMs).coerceAtLeast(0L)
                val newStartMs = (currentStartMs + deltaSourceMs).coerceIn(0L, maxStartMs)
                newStartMs to currentEndMs
            }
            TimelineTrimEdge.END -> {
                val minEndMs = (currentStartMs + minSpanMs).coerceAtMost(sourceDurationMs)
                val newEndMs = (currentEndMs + deltaSourceMs).coerceIn(minEndMs, sourceDurationMs)
                currentStartMs to newEndMs
            }
        }
    }

    /**
     * Downsamples raw 16-bit mono PCM [samples] into [bucketCount] normalized peak bars in `0f..1f`.
     */
    fun downsamplePcmToPeaks(
        samples: ShortArray,
        bucketCount: Int,
    ): List<Float> {
        val safeBuckets = bucketCount.coerceIn(1, 256)
        if (samples.isEmpty()) return List(safeBuckets) { 0f }

        val rawPeaks = FloatArray(safeBuckets)
        var maxPeak = 0f
        for (bucket in 0 until safeBuckets) {
            val start = ((bucket.toLong() * samples.size) / safeBuckets).toInt()
            val end = (((bucket + 1L) * samples.size) / safeBuckets).toInt().coerceAtLeast(start + 1)
            var bucketMax = 0
            for (i in start until end.coerceAtMost(samples.size)) {
                val v = abs(samples[i].toInt())
                if (v > bucketMax) bucketMax = v
            }
            val norm = (bucketMax / 32768f).coerceIn(0f, 1f)
            rawPeaks[bucket] = norm
            if (norm > maxPeak) maxPeak = norm
        }

        if (maxPeak <= 0.0001f) return List(safeBuckets) { 0f }
        return List(safeBuckets) { idx ->
            val scaled = (rawPeaks[idx] / maxPeak).coerceIn(0f, 1f)
            if (scaled > 0f) scaled.coerceAtLeast(0.08f) else 0f
        }
    }
}
