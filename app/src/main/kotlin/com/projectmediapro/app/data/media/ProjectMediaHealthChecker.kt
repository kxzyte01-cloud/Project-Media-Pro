package com.projectmediapro.app.data.media

import com.projectmediapro.core.model.Project
import java.io.File
import com.projectmediapro.core.model.TrackType

/**
 * Describes a clip in a [Project] whose backing media file on disk is missing or empty (0 bytes).
 */
data class MissingMediaIssue(
    val clipId: String,
    val trackType: TrackType,
    val relativePath: String,
)

/**
 * Pure JVM helper that inspects a [Project] against [ProjectMediaStorage] to detect missing or
 * zero-byte corrupt media files before playback or export fails.
 */
object ProjectMediaHealthChecker {

    fun isHealthyMediaFile(file: File?): Boolean =
        file != null && file.isFile && file.length() > 0L

    fun findMissingMedia(
        project: Project,
        mediaStorage: ProjectMediaStorage,
    ): List<MissingMediaIssue> {
        val issues = mutableListOf<MissingMediaIssue>()
        for (track in project.tracks) {
            if (track.type == TrackType.TEXT) continue
            for (clip in track.clips) {
                val resolved = mediaStorage.resolve(clip.mediaUri)
                val isHealthy = isHealthyMediaFile(resolved)
                if (!isHealthy) {
                    issues.add(
                        MissingMediaIssue(
                            clipId = clip.id,
                            trackType = track.type,
                            relativePath = clip.mediaUri,
                        ),
                    )
                }
            }
        }
        return issues
    }

    /**
     * Returns a copy of [project] with all clips in [missingClipIds] removed and video timeline
     * positions/durations recalculated cleanly.
     */
    fun removeMissingClips(
        project: Project,
        missingClipIds: Set<String>,
    ): Project {
        if (missingClipIds.isEmpty()) return project
        val cleanedTracks = project.tracks.map { track ->
            val remaining = track.clips.filterNot { it.id in missingClipIds }
            if (track.type == TrackType.VIDEO) {
                var cursorMs = 0L
                val relaid = remaining.map { clip ->
                    val positioned = clip.copy(startTimeMs = cursorMs)
                    cursorMs += clip.durationMs.coerceAtLeast(0L)
                    positioned
                }
                track.copy(clips = relaid)
            } else {
                track.copy(clips = remaining)
            }
        }
        val newDurationMs = cleanedTracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
            .sumOf { it.durationMs.coerceAtLeast(0L) }
        return project.copy(
            tracks = cleanedTracks,
            durationMs = newDurationMs,
        )
    }
}
