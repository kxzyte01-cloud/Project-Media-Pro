package com.projectmediapro.core.model

import java.util.Locale

fun Project.videoClips(): List<Clip> =
    tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips.orEmpty()

fun Project.audioClips(): List<Clip> =
    tracks.firstOrNull { it.type == TrackType.AUDIO }?.clips.orEmpty()

object ProjectTimeline {

    const val MIN_CLIP_DURATION_MS: Long = 500L

    fun effectiveSourceDurationMs(clip: Clip): Long {
        return if (clip.sourceDurationMs > 0L) {
            clip.sourceDurationMs
        } else {
            maxOf(clip.trimEndMs, clip.durationMs).coerceAtLeast(0L)
        }
    }

    fun effectiveTrimStartMs(clip: Clip): Long = clip.trimStartMs.coerceAtLeast(0L)

    fun effectiveTrimEndMs(clip: Clip): Long {
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val startMs = effectiveTrimStartMs(clip)
        return if (clip.trimEndMs > startMs) {
            clip.trimEndMs.coerceAtMost(sourceDurationMs)
        } else {
            (startMs + clip.durationMs.coerceAtLeast(0L)).coerceAtMost(sourceDurationMs)
        }
    }

    fun effectiveDurationMs(clip: Clip): Long {
        return if (clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs) {
            clip.trimEndMs - clip.trimStartMs
        } else {
            clip.durationMs.coerceAtLeast(0L)
        }
    }

    fun isClipTrimmed(clip: Clip): Boolean {
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val hasValidTrimRange = clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs
        return hasValidTrimRange &&
            (clip.trimStartMs > 0L || clip.trimEndMs < sourceDurationMs)
    }

    /**
     * Returns `(trimStartMs, trimEndMs)` when [isClipTrimmed] is true, or `(0L, 0L)` when the full
     * file should be played or exported without clipping.
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

    fun appendClip(
        project: Project,
        clip: Clip,
        newTrackId: String,
        trackType: TrackType = TrackType.VIDEO,
    ): Project {
        val trackIndex = project.tracks.indexOfFirst { it.type == trackType }
        if (trackIndex >= 0) {
            val track = project.tracks[trackIndex]
            return updateTrack(project, trackIndex, repositionClips(track.clips + clip))
        }
        val newTrack = Track(
            id = newTrackId,
            type = trackType,
            clips = repositionClips(listOf(clip)),
        )
        val updatedTracks = project.tracks + newTrack
        return project.copy(
            tracks = updatedTracks,
            durationMs = latestClipEndMs(updatedTracks),
        )
    }

    fun removeClip(
        project: Project,
        clipId: String,
    ): Project = modifyClipTrack(project, clipId) { clips, clipIndex ->
        clips.toMutableList().apply { removeAt(clipIndex) }
    }

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

    fun setClipVolume(
        project: Project,
        clipId: String,
        volume: Float,
    ): Project {
        val clampedVolume = if (volume.isNaN()) 1.0f else volume.coerceIn(0f, 1f)
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.volume == clampedVolume) return@modifyClipTrack null
            clips.toMutableList().apply {
                set(clipIndex, clip.copy(volume = clampedVolume))
            }
        }
    }

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
