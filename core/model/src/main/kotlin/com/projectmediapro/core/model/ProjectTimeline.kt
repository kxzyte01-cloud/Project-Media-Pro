package com.projectmediapro.core.model

import java.util.Locale

/**
 * Returns the clips of the first [TrackType.VIDEO] track in timeline order, or an empty list.
 */
fun Project.videoClips(): List<Clip> =
    tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips.orEmpty()

/**
 * Pure timeline operations for querying, appending, removing, trimming, splitting, and moving clips,
 * plus formatting durations.
 */
object ProjectTimeline {

    const val MIN_CLIP_DURATION_MS: Long = 500L

    /**
     * Returns the full source duration of [clip] in milliseconds. When [Clip.sourceDurationMs] is `0L`
     * (unknown), falls back to `max(trimEndMs, durationMs)`.
     */
    fun effectiveSourceDurationMs(clip: Clip): Long {
        return if (clip.sourceDurationMs > 0L) {
            clip.sourceDurationMs
        } else {
            maxOf(clip.trimEndMs, clip.durationMs).coerceAtLeast(0L)
        }
    }

    /**
     * Returns the effective trim start of [clip] in milliseconds (`>= 0L`).
     */
    fun effectiveTrimStartMs(clip: Clip): Long = clip.trimStartMs.coerceAtLeast(0L)

    /**
     * Returns the effective trim end of [clip] in milliseconds, falling back to
     * `(effectiveTrimStartMs(clip) + durationMs).coerceAtMost(effectiveSourceDurationMs(clip))`
     * when [Clip.trimEndMs] is not set.
     */
    fun effectiveTrimEndMs(clip: Clip): Long {
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val startMs = effectiveTrimStartMs(clip)
        return if (clip.trimEndMs > startMs) {
            clip.trimEndMs.coerceAtMost(sourceDurationMs)
        } else {
            (startMs + clip.durationMs.coerceAtLeast(0L)).coerceAtMost(sourceDurationMs)
        }
    }

    /**
     * Returns the effective playback duration of [clip] on the timeline in milliseconds.
     */
    fun effectiveDurationMs(clip: Clip): Long {
        return if (clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs) {
            clip.trimEndMs - clip.trimStartMs
        } else {
            clip.durationMs.coerceAtLeast(0L)
        }
    }

    /**
     * Returns true when [clip] has a valid trim range that trims either the start or the end of
     * its [effectiveSourceDurationMs].
     */
    fun isClipTrimmed(clip: Clip): Boolean {
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val hasValidTrimRange = clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs
        return hasValidTrimRange &&
            (clip.trimStartMs > 0L || clip.trimEndMs < sourceDurationMs)
    }

    /**
     * Returns `(trimStartMs, trimEndMs)` when [isClipTrimmed] is true, or `(0L, 0L)` when the full
     * source file should be played/exported without clipping.
     */
    fun resolvedTrimBounds(clip: Clip): Pair<Long, Long> {
        return if (isClipTrimmed(clip)) {
            clip.trimStartMs to clip.trimEndMs
        } else {
            0L to 0L
        }
    }

    fun canTrimClip(clip: Clip): Boolean =
        effectiveSourceDurationMs(clip) >= MIN_CLIP_DURATION_MS

    fun canSplitClip(clip: Clip): Boolean =
        clip.durationMs >= MIN_CLIP_DURATION_MS * 2L

    fun isMediaReferenced(project: Project, mediaUri: String): Boolean =
        project.tracks.asSequence().flatMap { it.clips.asSequence() }.any { it.mediaUri == mediaUri }

    /**
     * Appends [clip] to the first [TrackType.VIDEO] track in [project], creating one with [newTrackId]
     * if none exists. Sets the clip's startTimeMs to the end of that track's last clip and
     * recalculates [Project.durationMs] as the latest clip end across all tracks.
     */
    fun appendClip(
        project: Project,
        clip: Clip,
        newTrackId: String,
    ): Project {
        val videoTrackIndex = project.tracks.indexOfFirst { it.type == TrackType.VIDEO }
        if (videoTrackIndex >= 0) {
            val track = project.tracks[videoTrackIndex]
            return updateTrack(project, videoTrackIndex, repositionClips(track.clips + clip))
        }
        val newTrack = Track(
            id = newTrackId,
            type = TrackType.VIDEO,
            clips = repositionClips(listOf(clip)),
        )
        val updatedTracks = project.tracks + newTrack
        return project.copy(
            tracks = updatedTracks,
            durationMs = latestClipEndMs(updatedTracks),
        )
    }

    /**
     * Removes the clip with [clipId] from whichever track holds it, shifts subsequent clips on that
     * track earlier so there are no gaps, and recalculates [Project.durationMs]. Returns [project]
     * unchanged if [clipId] is not present.
     */
    fun removeClip(
        project: Project,
        clipId: String,
    ): Project = modifyClipTrack(project, clipId) { clips, clipIndex ->
        clips.toMutableList().apply { removeAt(clipIndex) }
    }

    /**
     * Trims the clip with [clipId] to `[newStartMs, newEndMs]` within its source duration.
     * Requires `0 <= newStartMs < newEndMs <= effectiveSourceDurationMs(clip)` and
     * `newEndMs - newStartMs >= 500`, otherwise returns [project] unchanged.
     */
    fun trimClip(
        project: Project,
        clipId: String,
        newStartMs: Long,
        newEndMs: Long,
    ): Project = modifyClipTrack(project, clipId) { clips, clipIndex ->
        val clip = clips[clipIndex]
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val trimmedDurationMs = newEndMs - newStartMs
        if (newStartMs < 0L ||
            newEndMs <= newStartMs ||
            newEndMs > sourceDurationMs ||
            trimmedDurationMs < MIN_CLIP_DURATION_MS
        ) {
            return@modifyClipTrack null
        }

        val updatedClip = clip.copy(
            durationMs = trimmedDurationMs,
            trimStartMs = newStartMs,
            trimEndMs = newEndMs,
            sourceDurationMs = sourceDurationMs,
        )
        clips.toMutableList().apply { set(clipIndex, updatedClip) }
    }

    /**
     * Splits the clip with [clipId] at [offsetMs] measured from the start of the clip's current
     * trimmed range. Both resulting parts must be at least 500 ms, and [newClipId] must be non-blank
     * and unique in [project], otherwise returns [project] unchanged. The first part keeps [clipId];
     * the second gets [newClipId] and sits right after the first, sharing [Clip.mediaUri].
     */
    fun splitClip(
        project: Project,
        clipId: String,
        offsetMs: Long,
        newClipId: String,
    ): Project {
        if (newClipId.isBlank() || newClipId == clipId) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val clip = clips[clipIndex]
            val sourceDurationMs = effectiveSourceDurationMs(clip)
            val currentStartMs = effectiveTrimStartMs(clip)
            val currentEndMs = effectiveTrimEndMs(clip)
            val currentDurationMs = currentEndMs - currentStartMs
            val secondDurationMs = currentDurationMs - offsetMs

            if (offsetMs < MIN_CLIP_DURATION_MS || secondDurationMs < MIN_CLIP_DURATION_MS) {
                return@modifyClipTrack null
            }

            val splitPointMs = currentStartMs + offsetMs
            val firstPart = clip.copy(
                durationMs = offsetMs,
                trimStartMs = currentStartMs,
                trimEndMs = splitPointMs,
                sourceDurationMs = sourceDurationMs,
            )
            val secondPart = clip.copy(
                id = newClipId,
                durationMs = secondDurationMs,
                trimStartMs = splitPointMs,
                trimEndMs = currentEndMs,
                sourceDurationMs = sourceDurationMs,
            )

            buildList(clips.size + 1) {
                clips.forEachIndexed { index, existing ->
                    if (index == clipIndex) {
                        add(firstPart)
                        add(secondPart)
                    } else {
                        add(existing)
                    }
                }
            }
        }
    }

    /**
     * Moves the clip with [clipId] by [delta] (`-1` for earlier, `+1` for later) within its track.
     * Returns [project] unchanged when [delta] is not `-1` or `+1`, when [clipId] is not found,
     * or when the clip is already at the boundary of its track.
     */
    fun moveClip(
        project: Project,
        clipId: String,
        delta: Int,
    ): Project {
        if (delta != -1 && delta != 1) return project
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val targetIndex = clipIndex + delta
            if (targetIndex !in clips.indices) return@modifyClipTrack null
            clips.toMutableList().apply {
                val moved = removeAt(clipIndex)
                add(targetIndex, moved)
            }
        }
    }

    /**
     * Formats [ms] as "M:SS" (for example "0:07", "1:05") or "H:MM:SS" (for example "1:02:03").
     * Negative values return "0:00".
     */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSeconds = ms / 1_000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3_600L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private inline fun modifyClipTrack(
        project: Project,
        clipId: String,
        transform: (clips: List<Clip>, clipIndex: Int) -> List<Clip>?,
    ): Project {
        for (trackIndex in project.tracks.indices) {
            val clips = project.tracks[trackIndex].clips
            val clipIndex = clips.indexOfFirst { it.id == clipId }
            if (clipIndex >= 0) {
                val updatedClips = transform(clips, clipIndex) ?: return project
                return updateTrack(project, trackIndex, repositionClips(updatedClips))
            }
        }
        return project
    }

    private fun repositionClips(clips: List<Clip>): List<Clip> {
        var nextStartMs = 0L
        return clips.map { clip ->
            val positioned = clip.copy(startTimeMs = nextStartMs)
            nextStartMs += clip.durationMs
            positioned
        }
    }

    private fun updateTrack(
        project: Project,
        trackIndex: Int,
        clips: List<Clip>,
    ): Project {
        val track = project.tracks[trackIndex]
        val updatedTracks = project.tracks.toMutableList().apply {
            set(trackIndex, track.copy(clips = clips))
        }
        return project.copy(
            tracks = updatedTracks,
            durationMs = latestClipEndMs(updatedTracks),
        )
    }

    private fun latestClipEndMs(tracks: List<Track>): Long {
        return tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .maxOfOrNull { it.startTimeMs + it.durationMs }
            ?: 0L
    }
}

