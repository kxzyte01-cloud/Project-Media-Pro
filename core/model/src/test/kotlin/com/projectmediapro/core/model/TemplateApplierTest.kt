package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateApplierTest {

    @Test
    fun starterTemplates_provideMusicLyricsAndCreatorPresets() {
        val starters = StarterTemplates.all()
        assertEquals(7, starters.size)
        assertTrue(starters.all { it.isStarterTemplate() })
        val reel = StarterTemplates.findById(StarterTemplates.MUSIC_LYRICS_REEL_ID)
        assertEquals(CanvasAspect.RATIO_9_16, reel?.canvasAspect)
        assertEquals(3, reel?.mainVideoSlots()?.size)
        assertEquals(1, reel?.audioSlots()?.size)
    }

    @Test
    fun buildProjectFromTemplate_ordersIntroMainOutroAudioAndClampsOverlay() {
        var nextId = 0
        val template = Template(
            id = "tpl-1",
            name = "My Format",
            canvasAspect = CanvasAspect.RATIO_9_16,
            canvasBackground = CanvasBackground.GREY,
            slots = listOf(
                Slot(id = "in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
                Slot(id = "v1", label = "Video 1", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
                Slot(id = "out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
                Slot(id = "a1", label = "Audio 1", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
                Slot(id = "a2", label = "Audio 2", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
                Slot(
                    id = "ov1",
                    label = "Logo",
                    requiredType = TrackType.OVERLAY,
                    role = SlotRole.OVERLAY,
                    isReplaceable = false,
                    durationMs = 15_000L,
                    startTimeMs = 0L,
                    overlayCenterX = 0.2f,
                    overlayCenterY = 0.8f,
                    overlayWidthFraction = 0.4f,
                ),
            ),
        )
        val base = Project(id = "p-1", title = "Episode 1")
        val resolved = mapOf(
            "in" to ResolvedTemplateMedia("p-1/in.mp4", 2_000L),
            "v1" to ResolvedTemplateMedia("p-1/v1.mp4", 4_000L),
            "out" to ResolvedTemplateMedia("p-1/out.mp4", 1_500L),
            "a1" to ResolvedTemplateMedia("p-1/a1.m4a", 3_000L),
            "a2" to ResolvedTemplateMedia("p-1/a2.m4a", 3_000L),
            "ov1" to ResolvedTemplateMedia("p-1/ov1.png", 0L),
        )

        val project = TemplateApplier.buildProjectFromTemplate(
            baseProject = base,
            template = template,
            resolvedMediaBySlotId = resolved,
            idProvider = { "id-${++nextId}" },
        )

        assertEquals(CanvasAspect.RATIO_9_16, project.canvasAspect)
        assertEquals(CanvasBackground.GREY, project.canvasBackground)
        assertEquals(
            listOf("p-1/in.mp4", "p-1/v1.mp4", "p-1/out.mp4"),
            project.videoClips().map { it.mediaUri },
        )
        assertEquals(listOf(0L, 2_000L, 6_000L), project.videoClips().map { it.startTimeMs })
        assertEquals(
            listOf("p-1/a1.m4a", "p-1/a2.m4a"),
            project.audioClips().map { it.mediaUri },
        )
        assertEquals(1, project.overlayClips().size)
        assertEquals(7_500L, project.overlayClips()[0].durationMs)
        assertEquals(0.2f, project.overlayClips()[0].overlayCenterX, 0.0001f)
    }
}
