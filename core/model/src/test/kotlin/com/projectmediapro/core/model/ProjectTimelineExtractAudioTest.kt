package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProjectTimelineExtractAudioTest {

    @Test
    fun `extractAudioFromVideoClip appends audio clip and mutes source video clip`() {
        val videoClip = Clip(
            id = "v1",
            mediaUri = "proj-1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 8_000L,
            sourceDurationMs = 8_000L,
            trimStartMs = 0L,
            trimEndMs = 8_000L,
            volume = 0.85f,
            playbackSpeed = 1.5f,
        )
        val project = Project(
            id = "proj-1",
            title = "Edit",
            tracks = listOf(
                Track(id = "t-video", type = TrackType.VIDEO, clips = listOf(videoClip)),
            ),
            durationMs = 5_333L,
        )

        val updated = ProjectTimeline.extractAudioFromVideoClip(
            project = project,
            sourceClipId = "v1",
            newAudioClipId = "a1",
            audioMediaUri = "proj-1/a1.m4a",
            audioDurationMs = 8_000L,
            newAudioTrackId = "t-audio",
        )

        val updatedVideo = updated.videoClips().single()
        assertEquals(0f, updatedVideo.volume, 0.0001f)

        val extractedAudio = updated.audioClips().single()
        assertEquals("a1", extractedAudio.id)
        assertEquals("proj-1/a1.m4a", extractedAudio.mediaUri)
        assertEquals(8_000L, extractedAudio.durationMs)
        assertEquals(0.85f, extractedAudio.volume, 0.0001f)
        assertEquals(1.5f, extractedAudio.playbackSpeed, 0.0001f)
    }

    @Test
    fun `extractAudioFromVideoClip ignores still-image freeze frames`() {
        val stillClip = Clip(
            id = "freeze-1",
            mediaUri = "proj-1/freeze.png",
            startTimeMs = 0L,
            durationMs = 3_000L,
            sourceDurationMs = 3_000L,
            isStillImage = true,
        )
        val project = Project(
            id = "proj-1",
            title = "Edit",
            tracks = listOf(
                Track(id = "t-video", type = TrackType.VIDEO, clips = listOf(stillClip)),
            ),
            durationMs = 3_000L,
        )

        val updated = ProjectTimeline.extractAudioFromVideoClip(
            project = project,
            sourceClipId = "freeze-1",
            newAudioClipId = "a1",
            audioMediaUri = "proj-1/a1.m4a",
            audioDurationMs = 3_000L,
            newAudioTrackId = "t-audio",
        )

        assertSame(project, updated)
    }
}
