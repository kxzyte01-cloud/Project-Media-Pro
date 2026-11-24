package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step38StarterLookTest {

    @Test
    fun everyStarterTemplateHasPreWiredVisualAndAudioLook() {
        val starters = StarterTemplates.all()
        assertEquals(10, starters.size)
        for (starter in starters) {
            val hasFilter = starter.mainVideoSlots().any {
                val fx = it.visualEffects
                fx != null && fx.filter != VideoFilterPreset.NONE
            }
            assertTrue("Starter ${starter.id} should define a visual filter/LUT", hasFilter)

            val hasAudioPreset = starter.audioSlots().any {
                it.audioEnhance != null && !requireNotNull(it.audioEnhance).isIdentity
            }
            assertTrue("Starter ${starter.id} should define an Audio Studio preset", hasAudioPreset)
        }

        val misty = requireNotNull(StarterTemplates.findById(StarterTemplates.MISTY_KINETIC_LYRICS_ID))
        assertNotNull(misty.captionStyleJson)
        assertTrue(misty.captionStyleJson.orEmpty().contains("KINETIC_COLLAGE"))
        assertEquals(VideoFilterPreset.MISTY_MOSS, misty.mainVideoSlots().first().visualEffects?.filter)

        val sunset = requireNotNull(StarterTemplates.findById(StarterTemplates.SUNSET_MASK_MONTAGE_ID))
        assertTrue(sunset.captionStyleJson.orEmpty().contains("KNOCKOUT_MASK"))
        assertEquals(VideoFilterPreset.GOLDEN_SUNSET, sunset.mainVideoSlots().first().visualEffects?.filter)
    }

    @Test
    fun applyingStarterTemplateTransfersLookIntoEditableProject() {
        val montage = requireNotNull(StarterTemplates.findById(StarterTemplates.QUICK_BEAT_MONTAGE_ID))
        val slot1 = montage.mainVideoSlots().first()
        val audio1 = montage.audioSlots().first()

        var seq = 1
        val project = TemplateApplier.buildProjectFromTemplate(
            baseProject = Project(id = "p-38", title = "Beat Edit"),
            template = montage,
            resolvedMediaBySlotId = mapOf(
                slot1.id to ResolvedTemplateMedia("p-38/v1.mp4", 4_000L),
                audio1.id to ResolvedTemplateMedia("p-38/a1.mp3", 10_000L),
            ),
            idProvider = { "id-${seq++}" },
        )

        assertTrue(project.soundReactiveEnabled)
        val clip = project.videoClips().single()
        assertEquals(VideoFilterPreset.VIVID, clip.visualEffects?.filter)
        assertEquals(ClipTransition.ZOOM_IN, clip.visualEffects?.transitionIn)
        assertEquals(SpeedRampPreset.MONTAGE, clip.speedRamp)

        val audio = project.audioClips().single()
        assertFalse(requireNotNull(audio.audioEnhance).isIdentity)
        assertEquals(AudioEqPreset.BASS_BOOST, audio.audioEnhance?.eqPreset)
    }
}
