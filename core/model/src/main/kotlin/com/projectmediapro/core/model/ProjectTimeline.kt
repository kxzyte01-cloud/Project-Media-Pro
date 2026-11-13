package com.projectmediapro.core.model

import java.util.Locale

/**
 * Pure timeline operations for appending and removing clips and formatting durations.
 */
object ProjectTimeline {

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
        val updatedTracks = if (videoTrackIndex >= 0) {
            val track = project.tracks[videoTrackIndex]
            val startTimeMs = track.clips.lastOrNull()?.let { last ->
                last.startTimeMs + last.durationMs
            } ?: 0L
            val positionedClip = clip.copy(startTimeMs = startTimeMs)
            val updatedTrack = track.copy(clips = track.clips + positionedClip)
            project.tracks.toMutableList().apply {
                set(videoTrackIndex, updatedTrack)
            }
        } else {
            val positionedClip = clip.copy(startTimeMs = 0L)
            val newTrack = Track(
                id = newTrackId,
                type = TrackType.VIDEO,
                clips = listOf(positionedClip),
            )
            project.tracks + newTrack
        }

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
    ): Project {
        val trackIndex = project.tracks.indexOfFirst { track ->
            track.clips.any { it.id == clipId }
        }
        if (trackIndex < 0) return project

        val track = project.tracks[trackIndex]
        var nextStartMs = 0L
        val shiftedClips = track.clips
            .filterNot { it.id == clipId }
            .map { remaining ->
                val updated = remaining.copy(startTimeMs = nextStartMs)
                nextStartMs += remaining.durationMs
                updated
            }

        val updatedTracks = project.tracks.toMutableList().apply {
            set(trackIndex, track.copy(clips = shiftedClips))
        }

        return project.copy(
            tracks = updatedTracks,
            durationMs = latestClipEndMs(updatedTracks),
        )
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

    private fun latestClipEndMs(tracks: List<Track>): Long {
        return tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .maxOfOrNull { it.startTimeMs + it.durationMs }
            ?: 0L
    }
}
