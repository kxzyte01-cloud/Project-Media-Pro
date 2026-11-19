package com.projectmediapro.app.editor

import com.projectmediapro.core.model.Clip

/**
 * Pure mapping between ExoPlayer's per-item playback position and project timeline milliseconds.
 */
object TimelineClock {

    fun playerToTimelineMs(
        clipDurationsMs: List<Long>,
        mediaItemIndex: Int,
        positionInItemMs: Long,
    ): Long {
        if (clipDurationsMs.isEmpty() || mediaItemIndex < 0) return 0L
        val clampedIndex = mediaItemIndex.coerceAtMost(clipDurationsMs.lastIndex)
        var offsetMs = 0L
        for (i in 0 until clampedIndex) {
            offsetMs += clipDurationsMs[i].coerceAtLeast(0L)
        }
        val currentDurationMs = clipDurationsMs[clampedIndex].coerceAtLeast(0L)
        val clampedPositionMs = positionInItemMs.coerceIn(0L, currentDurationMs)
        return offsetMs + clampedPositionMs
    }

    fun isOverlayVisibleAt(
        clip: Clip,
        timelinePositionMs: Long,
    ): Boolean {
        val durationMs = clip.durationMs.coerceAtLeast(0L)
        if (durationMs <= 0L) return false
        val startMs = clip.startTimeMs.coerceAtLeast(0L)
        return timelinePositionMs >= startMs && timelinePositionMs < startMs + durationMs
    }
}
