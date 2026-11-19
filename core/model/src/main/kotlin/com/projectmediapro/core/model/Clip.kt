package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * A single media segment on a [Track].
 *
 * Conventions:
 * - [mediaUri] is a RELATIVE path inside app-private storage, written "<projectId>/<clipId>.<ext>" and
 *   resolved against the media storage root (filesDir/media). It is never a content:// URI.
 * - [durationMs] is the clip's duration on the timeline in milliseconds (`durationMs = trimEndMs - trimStartMs`
 *   for sequential video/audio clips, or free display duration for overlay clips).
 * - [startTimeMs] is the clip's start time on the timeline in milliseconds.
 * - At import, [trimStartMs] is 0L, [trimEndMs] equals the source media duration, and [sourceDurationMs]
 *   equals the full length of the imported media file (`0L` means unknown, resolved via `max(trimEndMs, durationMs)`).
 * - For [TrackType.OVERLAY] clips, [overlayCenterX], [overlayCenterY], and [overlayWidthFraction]
 *   define placement as fractions of the project canvas frame.
 */
@Serializable
data class Clip(
    val id: String,
    val mediaUri: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val sourceDurationMs: Long = 0L,
    val overlayCenterX: Float = DEFAULT_OVERLAY_CENTER_X,
    val overlayCenterY: Float = DEFAULT_OVERLAY_CENTER_Y,
    val overlayWidthFraction: Float = DEFAULT_OVERLAY_WIDTH_FRACTION,
) {
    companion object {
        const val DEFAULT_OVERLAY_CENTER_X: Float = 0.8f
        const val DEFAULT_OVERLAY_CENTER_Y: Float = 0.2f
        const val DEFAULT_OVERLAY_WIDTH_FRACTION: Float = 0.3f
        const val MIN_OVERLAY_WIDTH_FRACTION: Float = 0.1f
        const val MAX_OVERLAY_WIDTH_FRACTION: Float = 1.0f
    }
}
