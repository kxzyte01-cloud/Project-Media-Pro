package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Step37TemplateStyleApplierTest {

    @Test
    fun applyStyleTransfersVisualAudioAndOverlayStylesWithoutMutatingMediaOrDurations() {
        val template = Template(
            id = "tpl-style",
            name = "Misty Cinema",
            soundReactiveEnabled = true,
            opticalFlowEnabled = true,
            slots = listOf(
                Slot(
                    id = "s-v1",
                    label = "Clip 1",
                    requiredType = TrackType.VIDEO,
                    role = SlotRole.MAIN_VIDEO,
                    visualEffects = ClipVisualEffects(
                        filter = VideoFilterPreset.MISTY_MOSS,
                        transitionIn = ClipTransition.ZOOM_IN,
                    ),
                    speedRamp = SpeedRampPreset.HERO,
                ),
                Slot(
                    id = "s-a1",
                    label = "Audio 1",
                    requiredType = TrackType.AUDIO,
                    role = SlotRole.AUDIO,
                    audioEnhance = AudioEnhanceConfig(
                        eqPreset = AudioEqPreset.VOCAL_CLARITY,
                        autoDuckMusicEnabled = true,
                    ),
                ),
                Slot(
                    id = "s-o1",
                    label = "Overlay 1",
                    requiredType = TrackType.OVERLAY,
                    role = SlotRole.OVERLAY,
                    overlayStyle = OverlayVisualStyle(blendMode = OverlayBlendMode.MULTIPLY),
                ),
            ),
        )

        val targetProject = Project(
            id = "proj-target",
            title = "Raw Footage",
            tracks = listOf(
                Track(
                    id = "tv",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(id = "c1", mediaUri = "proj-target/a.mp4", startTimeMs = 0L, durationMs = 2_500L),
                        Clip(id = "c2", mediaUri = "proj-target/b.mp4", startTimeMs = 2_500L, durationMs = 3_500L),
                    ),
                ),
                Track(
                    id = "ta",
                    type = TrackType.AUDIO,
                    clips = listOf(
                        Clip(id = "a1", mediaUri = "proj-target/song.mp3", startTimeMs = 0L, durationMs = 6_000L),
                    ),
                ),
                Track(
                    id = "to",
                    type = TrackType.OVERLAY,
                    clips = listOf(
                        Clip(id = "o1", mediaUri = "proj-target/img.png", startTimeMs = 0L, durationMs = 2_000L),
                    ),
                ),
            ),
        )

        val result = TemplateStyleApplier.applyStyle(targetProject, template)
        assertEquals(2, result.styledVideoClips)
        assertEquals(1, result.styledAudioClips)
        assertEquals(1, result.styledOverlayClips)
        assertTrue(result.project.soundReactiveEnabled)
        assertTrue(result.project.opticalFlowEnabled)

        val videos = result.project.videoClips()
        assertEquals("proj-target/a.mp4", videos[0].mediaUri)
        assertEquals(2_500L, videos[0].durationMs)
        assertEquals(VideoFilterPreset.MISTY_MOSS, videos[0].visualEffects?.filter)
        assertEquals(SpeedRampPreset.HERO, videos[0].speedRamp)
        // Second clip inherits fallback style from first styled video slot
        assertEquals("proj-target/b.mp4", videos[1].mediaUri)
        assertEquals(VideoFilterPreset.MISTY_MOSS, videos[1].visualEffects?.filter)

        val audio = result.project.audioClips().single()
        assertEquals(AudioEqPreset.VOCAL_CLARITY, audio.audioEnhance?.eqPreset)
        assertEquals(true, audio.audioEnhance?.autoDuckMusicEnabled)

        val overlay = result.project.overlayClips().single()
        assertEquals(OverlayBlendMode.MULTIPLY, overlay.overlayStyle?.blendMode)
    }
}
