package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateBuilderTest {

    @Test
    fun defaultSlots_andEditingVideoOverlayAudioOrder_behaveAsExpected() {
        var template = Template(
            id = "tpl-1",
            name = "Daily Vlog",
            slots = TemplateBuilder.createDefaultSlots("in", "v1", "out"),
        )

        assertEquals(SlotRole.INTRO, template.introSlot()?.role)
        assertEquals(1, template.mainVideoSlots().size)
        assertEquals(SlotRole.OUTRO, template.outroSlot()?.role)
        assertFalse(template.introSlot()?.isReplaceable ?: true)
        assertTrue(template.mainVideoSlots()[0].isReplaceable)
        assertFalse(template.outroSlot()?.isReplaceable ?: true)

        template = TemplateBuilder.setSlotMedia(template, "in", "tpl-1/intro.mp4", 3_000L)
        assertEquals("tpl-1/intro.mp4", template.introSlot()?.filledMediaUri)
        assertEquals(3_000L, template.introSlot()?.durationMs)

        template = TemplateBuilder.addVideoSlot(template, "v2", "Video 2")
        assertEquals(listOf("in", "v1", "v2", "out"), template.slots.map { it.id })

        template = TemplateBuilder.addOverlaySlot(template, "ov1", "Overlay 1", "tpl-1/logo.png")
        assertEquals(5_000L, template.overlaySlots()[0].durationMs)
        template = TemplateBuilder.updateOverlayTiming(template, "ov1", startTimeMs = -100L, durationMs = 200L)
        assertEquals(0L, template.overlaySlots()[0].startTimeMs)
        assertEquals(500L, template.overlaySlots()[0].durationMs)

        template = TemplateBuilder.addAudioSlot(template, "a1", "Audio 1")
        template = TemplateBuilder.addAudioSlot(template, "a2", "Audio 2")
        template = TemplateBuilder.moveAudioSlot(template, "a2", -1)
        assertEquals(listOf("a2", "a1"), template.audioSlots().map { it.id })

        // Removing intro clears media instead of deleting the fixed intro slot.
        template = TemplateBuilder.removeSlot(template, "in")
        assertEquals("in", template.introSlot()?.id)
        assertNull(template.introSlot()?.filledMediaUri)

        // Removing extra video slot deletes it, while keeping at least one video slot.
        template = TemplateBuilder.removeSlot(template, "v2")
        assertEquals(1, template.mainVideoSlots().size)
        template = TemplateBuilder.removeSlot(template, "v1")
        assertEquals(1, template.mainVideoSlots().size)
    }
}
