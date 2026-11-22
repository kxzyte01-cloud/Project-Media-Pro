package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step19AudioAndSoundReactiveTest {

    @Test
    fun `soundReactiveEnabled pulses scale and glow on plain beat markers`() {
        val plainBeat = listOf(BeatMarker(id = "b1", timeMs = 1_000L, effect = BeatEffectKind.NONE))

        val offSample = BeatEffectEvaluator.evaluate(plainBeat, timeMs = 1_020L, soundReactiveEnabled = false)
        assertTrue(offSample.isIdentity)

        val onSample = BeatEffectEvaluator.evaluate(plainBeat, timeMs = 1_020L, soundReactiveEnabled = true)
        assertFalse(onSample.isIdentity)
        assertTrue(onSample.scale > 1.01f)
        assertTrue(onSample.reactiveGlow > 0.2f)
    }

    @Test
    fun `setClipAudioMix stores 200 percent boost and clamps fades to half clip length`() {
        val clip = Clip(
            id = "a1",
            mediaUri = "p1/a1.m4a",
            startTimeMs = 0L,
            durationMs = 2_000L,
            sourceDurationMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
        )
        val project = Project(
            id = "p1",
            title = "Audio Studio",
            tracks = listOf(Track(id = "ta", type = TrackType.AUDIO, clips = listOf(clip))),
            durationMs = 2_000L,
        )

        val updated = ProjectTimeline.setClipAudioMix(
            project = project,
            clipId = "a1",
            volume = 1.0f,
            audioBoost = 1.85f,
            fadeInMs = 1_500L,
            fadeOutMs = 500L,
        )
        val result = updated.audioClips().single()
        assertEquals(1.0f, result.volume, 0.001f)
        assertEquals(1.85f, result.audioBoost, 0.001f)
        assertEquals(1_000L, result.audioFadeInMs) // clamped to half of 2000ms
        assertEquals(500L, result.audioFadeOutMs)
        assertTrue(result.hasCustomAudioMix)
    }

    @Test
    fun `vita aesthetic filter presets produce non-identity grade specs`() {
        val newPresets = listOf(
            VideoFilterPreset.DREAMY_GLOW,
            VideoFilterPreset.RETRO_FILM,
            VideoFilterPreset.CYBER_NEON,
            VideoFilterPreset.NOIR_PUNCH,
        )
        for (preset in newPresets) {
            val spec = preset.gradeSpec()
            assertTrue("Expected non-identity spec for $preset", spec != FilterGradeSpec())
        }
        assertTrue(VideoFilterPreset.NOIR_PUNCH.gradeSpec().isGrayscale)
    }
}
