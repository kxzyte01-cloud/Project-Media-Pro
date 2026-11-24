package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step39ProjectRemixEngineTest {

    private fun sampleProject(): Project = Project(
        id = "p-orig",
        title = "Summer Trip",
        durationMs = 7_000L,
        tracks = listOf(
            Track(
                id = "tv",
                type = TrackType.VIDEO,
                clips = listOf(
                    Clip(id = "v1", mediaUri = "p-orig/1.mp4", startTimeMs = 0L, durationMs = 5_000L, trimEndMs = 5_000L, sourceDurationMs = 5_000L),
                    Clip(id = "v2", mediaUri = "p-orig/2.mp4", startTimeMs = 5_000L, durationMs = 2_000L, trimEndMs = 2_000L, sourceDurationMs = 2_000L),
                ),
            ),
            Track(
                id = "ta",
                type = TrackType.AUDIO,
                clips = listOf(
                    Clip(id = "a1", mediaUri = "p-orig/song.mp3", startTimeMs = 0L, durationMs = 7_000L),
                ),
            ),
        ),
    )

    @Test
    fun rhythmCutTrimsLongClipsNeverStretchesShortClipsAndEnablesReactive() {
        val original = sampleProject()
        val remix = ProjectRemixEngine.buildRemix(original, RemixPreset.RHYTHM_CUT)

        assertTrue(remix.soundReactiveEnabled)
        assertFalse("Original project must stay untouched", original.soundReactiveEnabled)

        val clips = remix.videoClips()
        assertEquals(2_400L, clips[0].durationMs)
        assertEquals(0L, clips[0].startTimeMs)
        assertEquals(2_000L, clips[1].durationMs)
        assertEquals(2_400L, clips[1].startTimeMs)
        assertEquals(4_400L, remix.durationMs)
        assertEquals(ClipTransition.ZOOM_IN, clips[0].visualEffects?.transitionIn)
        assertEquals(SpeedRampPreset.MONTAGE, clips[0].speedRamp)
        assertEquals(AudioEqPreset.BASS_BOOST, remix.audioClips().single().audioEnhance?.eqPreset)
    }

    @Test
    fun storyCutAppliesCinemaGradeSmoothFadesAndAutoDucking() {
        val remix = ProjectRemixEngine.buildRemix(sampleProject(), RemixPreset.STORY_CUT)
        val clip = remix.videoClips().first()
        assertEquals(VideoFilterPreset.CINEMA, clip.visualEffects?.filter)
        assertEquals(ClipTransition.FADE_BLACK, clip.visualEffects?.transitionIn)
        assertEquals(SpeedRampPreset.NONE, clip.speedRamp)
        val audio = remix.audioClips().single()
        assertEquals(AudioEqPreset.STUDIO_WARMTH, audio.audioEnhance?.eqPreset)
        assertEquals(true, audio.audioEnhance?.autoDuckMusicEnabled)
    }

    @Test
    fun minimalCutStripsTransitionsAndSpeedRampsWithCleanVocalClarity() {
        val remix = ProjectRemixEngine.buildRemix(sampleProject(), RemixPreset.MINIMAL_CUT)
        assertFalse(remix.soundReactiveEnabled)
        val clip = remix.videoClips().first()
        assertEquals(VideoFilterPreset.FADE_FILM, clip.visualEffects?.filter)
        assertEquals(ClipTransition.NONE, clip.visualEffects?.transitionIn)
        assertEquals(SpeedRampPreset.NONE, clip.speedRamp)
        assertEquals(7_000L, remix.durationMs)
        assertEquals(AudioEqPreset.VOCAL_CLARITY, remix.audioClips().single().audioEnhance?.eqPreset)
    }

    @Test
    fun rhythmCutClampsAndDropsOutOfBoundsOverlays() {
        val base = sampleProject().copy(
            tracks = sampleProject().tracks + listOf(
                Track(
                    id = "tt",
                    type = TrackType.TEXT,
                    clips = listOf(
                        Clip(id = "t-keep", mediaUri = "", startTimeMs = 1_000L, durationMs = 5_000L, textContent = "Keep"),
                        Clip(id = "t-drop", mediaUri = "", startTimeMs = 6_000L, durationMs = 1_000L, textContent = "Drop"),
                    ),
                ),
            ),
        )
        val remix = ProjectRemixEngine.buildRemix(base, RemixPreset.RHYTHM_CUT)
        assertEquals(4_400L, remix.durationMs)
        val texts = remix.textClips()
        assertEquals(1, texts.size)
        assertEquals("Keep", texts.single().textContent)
        assertEquals(3_400L, texts.single().durationMs)
    }
}
