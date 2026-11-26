package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3AudioAndMuteTest {

    private fun videoClip(id: String, volume: Float = 0.7f) = Clip(
        id = id, mediaUri = "v.mp4", startTimeMs = 0L, durationMs = 4_000L,
        trimStartMs = 0L, trimEndMs = 4_000L, sourceDurationMs = 4_000L, volume = volume,
    )

    private fun audioClip(id: String, speed: Float = 1f) = Clip(
        id = id, mediaUri = "a.m4a", startTimeMs = 0L, durationMs = (4_000L / speed).toLong(),
        trimStartMs = 0L, trimEndMs = 4_000L, sourceDurationMs = 4_000L,
        playbackSpeed = speed, volume = 0.8f, audioFadeInMs = 300L, audioFadeOutMs = 300L,
    )

    private fun project(
        video: List<Clip> = listOf(videoClip("v1")),
        audio: List<Clip> = listOf(audioClip("a1")),
    ) = Project(
        id = "p",
        title = "t",
        tracks = listOf(Track("tv", TrackType.VIDEO, video), Track("ta", TrackType.AUDIO, audio)),
    )

    @Test
    fun mute_preserves_and_restores_exact_volume() {
        val p = project(video = listOf(videoClip("v1", volume = 0.42f)))
        val muted = ProjectTimeline.toggleVideoClipMute(p, "v1")
        val clip = muted.videoClips().single()
        assertTrue(clip.muted)
        assertEquals(0f, clip.volume, 0f)
        assertEquals(0.42f, clip.volumeBeforeMute, 0.0001f)
        val restored = ProjectTimeline.toggleVideoClipMute(muted, "v1")
        val back = restored.videoClips().single()
        assertFalse(back.muted)
        assertEquals(0.42f, back.volume, 0.0001f)
    }

    @Test
    fun toggle_clip_mute_works_on_audio_clips_too() {
        val p = project()
        val muted = ProjectTimeline.toggleClipMute(p, "a1")
        assertTrue(muted.audioClips().single().muted)
        assertEquals(0f, muted.audioClips().single().volume, 0f)
        val restored = ProjectTimeline.toggleClipMute(muted, "a1")
        assertFalse(restored.audioClips().single().muted)
        assertEquals(0.8f, restored.audioClips().single().volume, 0.0001f)
    }

    @Test
    fun mute_all_then_unmute_all_restores_each_volume() {
        val p = project(video = listOf(videoClip("v1", 0.3f), videoClip("v2", 0.9f)))
        val muted = ProjectTimeline.toggleMuteAllVideoClips(p)
        assertTrue(muted.videoClips().all { it.muted })
        val restored = ProjectTimeline.toggleMuteAllVideoClips(muted)
        assertEquals(0.3f, restored.videoClips()[0].volume, 0.0001f)
        assertEquals(0.9f, restored.videoClips()[1].volume, 0.0001f)
    }

    @Test
    fun raising_volume_clears_muted_flag() {
        val muted = ProjectTimeline.toggleVideoClipMute(project(), "v1")
        val raised = ProjectTimeline.setClipVolume(muted, "v1", 0.5f)
        assertFalse(raised.videoClips().single().muted)
        assertEquals(0.5f, raised.videoClips().single().volume, 0.0001f)
    }

    @Test
    fun split_valid_cut_produces_two_halves_and_zeroes_seam_fades() {
        val p = project()
        val split = ProjectTimeline.splitAudioClipAt(p, "a1", 2_000L, "a2")
        val clips = split.audioClips()
        assertEquals(2, clips.size)
        assertEquals("a1", clips[0].id)
        assertEquals("a2", clips[1].id)
        assertEquals(0L, clips[0].audioFadeOutMs)
        assertEquals(0L, clips[1].audioFadeInMs)
        assertEquals(300L, clips[0].audioFadeInMs)
        assertEquals(300L, clips[1].audioFadeOutMs)
        assertEquals(2_000L, clips[0].trimEndMs)
        assertEquals(2_000L, clips[1].trimStartMs)
        assertEquals(0.8f, clips[1].volume, 0.0001f)
    }

    @Test
    fun split_is_speed_aware() {
        val p = project(audio = listOf(audioClip("a1", speed = 2f)))
        val split = ProjectTimeline.splitAudioClipAt(p, "a1", 1_000L, "a2")
        val clips = split.audioClips()
        assertEquals(2, clips.size)
        assertEquals(2_000L, clips[0].trimEndMs)
    }

    @Test
    fun split_outside_clip_returns_same_project() {
        val p = project()
        assertSame(p, ProjectTimeline.splitAudioClipAt(p, "a1", 9_000L, "a2"))
    }

    @Test
    fun split_too_short_half_returns_same_project() {
        val p = project()
        assertSame(p, ProjectTimeline.splitAudioClipAt(p, "a1", 50L, "a2"))
    }

    @Test
    fun can_split_reflects_min_half_validation() {
        val p = project()
        assertTrue(ProjectTimeline.canSplitAudioClipAt(p, "a1", 2_000L))
        assertFalse(ProjectTimeline.canSplitAudioClipAt(p, "a1", 50L))
        assertFalse(ProjectTimeline.canSplitAudioClipAt(p, "a1", 3_990L))
    }

    @Test
    fun source_project_unchanged_supports_undo_snapshot() {
        val p = project()
        val before = p.tracks.toString()
        ProjectTimeline.splitAudioClipAt(p, "a1", 2_000L, "a2")
        ProjectTimeline.toggleVideoClipMute(p, "v1")
        assertEquals(before, p.tracks.toString())
        assertNotEquals(before, ProjectTimeline.splitAudioClipAt(p, "a1", 2_000L, "a2").tracks.toString())
    }
}
