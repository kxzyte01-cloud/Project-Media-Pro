package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.StarterTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step25ViralVisualizerTemplatesTest {

    private fun sampleLyricDoc(templateId: CaptionTemplateId): CaptionDocument {
        val words = listOf(
            WordTiming(id = "w1", text = "kind", startUs = 0L, endUs = 600_000L),
            WordTiming(id = "w2", text = "misery", startUs = 600_000L, endUs = 1_400_000L),
            WordTiming(id = "w3", text = "dreaming", startUs = 1_400_000L, endUs = 2_400_000L),
        )
        return CaptionDocument(
            projectId = "p-step25",
            rawWords = words,
            lines = listOf(CaptionLine(id = "l1", words = words, startUs = 0L, endUs = 2_400_000L)),
            style = CaptionStyle.default().withTemplate(templateId),
        )
    }

    @Test
    fun knockoutMaskRevealsOneActiveUppercaseWordAtATime() {
        val doc = sampleLyricDoc(CaptionTemplateId.KNOCKOUT_MASK)
        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 900_000L)

        assertTrue(frame.hasVisibleContent)
        val visibleWords = frame.words.filter { it.alpha > 0f && it.visibleCharCount > 0 }
        assertEquals(1, visibleWords.size)
        assertEquals("MISERY", visibleWords.single().displayText)
        assertTrue(visibleWords.single().isActive)
        assertTrue(visibleWords.single().scale >= 0.95f)
    }

    @Test
    fun kineticCollageRendersUppercaseStaggeredPopInWords() {
        val doc = sampleLyricDoc(CaptionTemplateId.KINETIC_COLLAGE)
        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 1_600_000L)

        assertTrue(frame.hasVisibleContent)
        assertEquals(listOf("KIND", "MISERY", "DREAMING"), frame.words.map { it.displayText })
        assertTrue(frame.words[2].isActive)
        assertTrue(frame.words[0].isSpoken)
    }

    @Test
    fun newStarterTemplatesAreRegisteredWithUniqueIds() {
        val misty = StarterTemplates.findById(StarterTemplates.MISTY_KINETIC_LYRICS_ID)
        val sunset = StarterTemplates.findById(StarterTemplates.SUNSET_MASK_MONTAGE_ID)
        assertNotNull(misty)
        assertNotNull(sunset)
        assertEquals(1, requireNotNull(misty).mainVideoSlots().size)
        assertEquals(5, requireNotNull(sunset).mainVideoSlots().size)

        val allIds = StarterTemplates.all().map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
    }
}
