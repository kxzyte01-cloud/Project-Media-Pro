package com.projectmediapro.app.editor

import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline

data class ActiveClipPosition(
    val clip: Clip,
    val positionInClipMs: Long,
    val clipDurationMs: Long,
)

/**
 * Pure bidirectional mapping between ExoPlayer's per-item playback position and project timeline ms.
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
            offsetMs = ProjectTimeline.saturatingAdd(offsetMs, clipDurationsMs[i])
        }
        val currentDurationMs = clipDurationsMs[clampedIndex].coerceAtLeast(0L)
        val clampedPositionMs = positionInItemMs.coerceIn(0L, currentDurationMs)
        return ProjectTimeline.saturatingAdd(offsetMs, clampedPositionMs)
    }

    fun timelineToPlayerPosition(
        clipDurationsMs: List<Long>,
        timelinePositionMs: Long,
    ): Pair<Int, Long>? {
        if (clipDurationsMs.isEmpty()) return null
        val targetMs = timelinePositionMs.coerceAtLeast(0L)
        var offsetMs = 0L
        for (index in clipDurationsMs.indices) {
            val durationMs = clipDurationsMs[index].coerceAtLeast(0L)
            val endMs = ProjectTimeline.saturatingAdd(offsetMs, durationMs)
            val isLast = index == clipDurationsMs.lastIndex
            if (targetMs < endMs || isLast) {
                return index to (targetMs - offsetMs).coerceIn(0L, durationMs)
            }
            offsetMs = endMs
        }
        return null
    }

    fun activeClipAt(
        clips: List<Clip>,
        timelinePositionMs: Long,
    ): ActiveClipPosition? {
        if (clips.isEmpty()) return null
        var offsetMs = 0L
        val clampedPos = timelinePositionMs.coerceAtLeast(0L)
        for (index in clips.indices) {
            val clip = clips[index]
            val durationMs = ProjectTimeline.effectiveDurationMs(clip)
            if (durationMs <= 0L) continue
            val endMs = ProjectTimeline.saturatingAdd(offsetMs, durationMs)
            val isLast = index == clips.lastIndex
            if (clampedPos < endMs || isLast) {
                val posInClip = (clampedPos - offsetMs).coerceIn(0L, durationMs)
                return ActiveClipPosition(
                    clip = clip,
                    positionInClipMs = posInClip,
                    clipDurationMs = durationMs,
                )
            }
            offsetMs = endMs
        }
        return null
    }

    fun isOverlayVisibleAt(
        clip: Clip,
        timelinePositionMs: Long,
    ): Boolean {
        val durationMs = clip.durationMs.coerceAtLeast(0L)
        if (durationMs <= 0L) return false
        val startMs = clip.startTimeMs.coerceAtLeast(0L)
        val endMs = ProjectTimeline.saturatingAdd(startMs, durationMs)
        return timelinePositionMs >= startMs && timelinePositionMs < endMs
    }
}
