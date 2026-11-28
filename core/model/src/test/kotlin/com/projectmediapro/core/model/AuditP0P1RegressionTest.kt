package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditP0P1RegressionTest {

    @Test
    fun updateClipKeyframes_persistsMainTrackVideoKeyframesWithCenteredDefault() {
        val videoClip = Clip(
            id = "v1",
            mediaUri = "video/sample.mp4",
            startTimeMs = 0L,
            durationMs = 4_000L,
            sourceDurationMs = 4_000L,
        )
        val project = Project(
            id = "p1",
            title = "Keyframe Video Test",
            tracks = listOf(Track(id = "t1", type = TrackType.VIDEO, clips = listOf(videoClip))),
        )
        val kenBurns = listOf(
            OverlayKeyframe(timeFraction = 0f, centerX = 0.5f, centerY = 0.5f, scale = 1.0f),
            OverlayKeyframe(timeFraction = 1f, centerX = 0.5f, centerY = 0.5f, scale = 1.16f),
        )
        val updated = ProjectTimeline.updateClipKeyframes(project, "v1", kenBurns)
        val saved = updated.videoClips().first().keyframes
        assertEquals(2, saved.size)
        assertEquals(0.5f, saved.first().centerX, 0.001f)
        assertEquals(1.16f, saved.last().scale, 0.001f)
    }

    @Test
    fun repositionClips_usesEffectiveRampDurationForSpacing() {
        val ramped = Clip(
            id = "v1",
            mediaUri = "video/a.mp4",
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            sourceDurationMs = 4_000L,
            speedRamp = SpeedRampPreset.MONTAGE,
        )
        val second = Clip(
            id = "v2",
            mediaUri = "video/b.mp4",
            startTimeMs = 0L,
            durationMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
            sourceDurationMs = 2_000L,
        )
        val project = Project(
            id = "p2",
            title = "Ramp Spacing Test",
            tracks = listOf(Track(id = "t1", type = TrackType.VIDEO, clips = listOf(ramped, second))),
        )
        val expectedFirstEff = ProjectTimeline.effectiveDurationMs(ramped)
        val updated = ProjectTimeline.setClipSpeed(project, "v1", Clip.DEFAULT_PLAYBACK_SPEED, SpeedRampPreset.MONTAGE)
        val clips = updated.videoClips()
        assertEquals(expectedFirstEff, clips[1].startTimeMs)
        assertTrue(updated.durationMs >= expectedFirstEff + 2_000L)
    }
}
