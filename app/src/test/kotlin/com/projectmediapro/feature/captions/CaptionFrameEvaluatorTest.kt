package com.projectmediapro.feature.captions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionFrameEvaluatorTest {

    private fun sampleDocument(
        templateId: CaptionTemplateId,
        globalOffsetUs: Long = 0L,
    ): CaptionDocument {
        val words = listOf(
            WordTiming("w-1", "First", 1_000_000L, 1_500_000L, 1),
            WordTiming("w-2", "Second", 1_500_000L, 2_000_000L, 2),
        )
        return CaptionDocument(
            projectId = "proj-1",
            rawWords = words,
            lines = listOf(CaptionLine("line-0", words, 1_000_000L, 2_000_000L)),
            presetId = LyricsPresetRegistry.SPEECH_ID,
            globalOffsetUs = globalOffsetUs,
            style = CaptionStyle.default().withTemplate(templateId),
        )
    }

    @Test
    fun evaluate_outsideActiveRange_returnsEmptyState() {
        val doc = sampleDocument(CaptionTemplateId.KARAOKE_HIGHLIGHT)
        val before = CaptionFrameEvaluator.evaluate(doc, 500_000L)
        assertFalse(before.hasVisibleContent)
        assertNull(before.activeLineId)

        val exactEnd = CaptionFrameEvaluator.evaluate(doc, 2_000_000L)
        assertFalse(exactEnd.hasVisibleContent)
    }

    @Test
    fun evaluate_karaokeHighlight_computesExactProgressAtMidpoint() {
        val doc = sampleDocument(CaptionTemplateId.KARAOKE_HIGHLIGHT)
        val state = CaptionFrameEvaluator.evaluate(doc, 1_750_000L)

        assertTrue(state.hasVisibleContent)
        assertEquals(1f, state.words[0].fillProgress, 0.001f)
        assertTrue(state.words[0].isSpoken)
        assertEquals(0.5f, state.words[1].fillProgress, 0.001f)
        assertTrue(state.words[1].isActive)
    }

    @Test
    fun evaluate_popInAndTypewriter_hideUnstartedWordsAndScaleSmoothly() {
        val popDoc = sampleDocument(CaptionTemplateId.POP_IN)
        val popStart = CaptionFrameEvaluator.evaluate(popDoc, 1_000_000L)
        assertTrue(popStart.words[0].scale >= 0.65f)
        assertEquals(0f, popStart.words[1].alpha, 0.001f)

        val popEndOfFirstWord = CaptionFrameEvaluator.evaluate(popDoc, 1_499_999L)
        val popAfterFirstWord = CaptionFrameEvaluator.evaluate(popDoc, 1_500_000L)
        assertEquals(popEndOfFirstWord.words[0].scale, popAfterFirstWord.words[0].scale, 0.01f)

        val typeDoc = sampleDocument(CaptionTemplateId.TYPEWRITER)
        assertTrue(typeDoc.style.backgroundBoxEnabled)
        val typeMid = CaptionFrameEvaluator.evaluate(typeDoc, 1_250_000L)
        assertTrue(typeMid.showCursor)
        assertEquals(3, typeMid.words[0].visibleCharCount)
        assertEquals(0, typeMid.words[1].visibleCharCount)
    }

    @Test
    fun evaluate_positiveGlobalOffsetUs_delaysCaptionActivation() {
        val doc = sampleDocument(
            templateId = CaptionTemplateId.CLEAN_SUBTITLE,
            globalOffsetUs = 250_000L,
        )
        assertTrue(doc.style.backgroundBoxEnabled)
        assertFalse(CaptionFrameEvaluator.evaluate(doc, 1_100_000L).hasVisibleContent)
        assertTrue(CaptionFrameEvaluator.evaluate(doc, 1_300_000L).hasVisibleContent)
    }
}
