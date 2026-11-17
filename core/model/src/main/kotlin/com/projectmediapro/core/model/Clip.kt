package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * A single media segment on a [Track].
 *
 * Conventions:
 * - [mediaUri] is a RELATIVE path inside app-private storage, written "<projectId>/<clipId>.<ext>" and
 *   resolved against the media storage root (filesDir/media). It is never a content:// URI.
 * - [durationMs] is the clip's duration on the timeline in milliseconds (`durationMs = trimEndMs - trimStartMs`).
 * - [startTimeMs] is the clip's start time on the timeline in milliseconds.
 * - At import, [trimStartMs] is 0L, [trimEndMs] equals the source media duration, and [sourceDurationMs]
 *   equals the full length of the imported media file (`0L` means unknown, resolved via `max(trimEndMs, durationMs)`).
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
)
