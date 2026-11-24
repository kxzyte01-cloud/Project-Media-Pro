package com.projectmediapro.feature.captions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionOverlayVisualEqualityTest {

    private fun sampleDocument(): CaptionDocument {
        val word1 = WordTiming(id = "w1", text = "Hello", startUs = 1_000_000L, endUs = 1_500_000L)
        val word2 = WordTiming(id = "w2", text = "world", startUs = 1_500_000L, endUs = 2_000_000L)
        val line = CaptionLine(
            id = "line-1",
            words = listOf(word1, word2),
            startUs = 1_000_000L,
            endUs = 2_000_000L,
        )
        return CaptionDocument(
            projectId = "p1",
            rawWords = listOf(word1, word2),
            lines = listOf(line),
            presetId = LyricsPresetRegistry.SPEECH_ID,
            style = CaptionStyle.default(),
        )
    }

    @Test
    fun firstFrame_isNeverConsideredIdentical() {
        val doc = sampleDocument()
        val frame0 = CaptionFrameEvaluator.evaluate(doc, 0L)
        assertFalse(CaptionFrameVisualEquality.isVisuallyIdentical(null, frame0))
    }

    @Test
    fun consecutiveEmptyFrames_reuseCachedTexture() {
        val doc = sampleDocument()
        val emptyA = CaptionFrameEvaluator.evaluate(doc, 0L)
        val emptyB = CaptionFrameEvaluator.evaluate(doc, 500_000L)
        assertTrue(CaptionFrameVisualEquality.isVisuallyIdentical(emptyA, emptyB))
    }

    @Test
    fun transitionFromEmptyToVisible_triggersBufferSwap() {
        val doc = sampleDocument()
        val empty = CaptionFrameEvaluator.evaluate(doc, 0L)
        val visible = CaptionFrameEvaluator.evaluate(doc, 1_100_000L)
        assertFalse(CaptionFrameVisualEquality.isVisuallyIdentical(empty, visible))
    }

    @Test
    fun animatedWordProgressChange_triggersBufferSwap() {
        val doc = sampleDocument()
        val early = CaptionFrameEvaluator.evaluate(doc, 1_100_000L)
        val mid = CaptionFrameEvaluator.evaluate(doc, 1_300_000L)
        assertFalse(CaptionFrameVisualEquality.isVisuallyIdentical(early, mid))
    }

    @Test
    fun transitionFromVisibleToEmpty_triggersBufferSwap() {
        val doc = sampleDocument()
        val visible = CaptionFrameEvaluator.evaluate(doc, 1_800_000L)
        val cleared = CaptionFrameEvaluator.evaluate(doc, 2_500_000L)
        assertFalse(CaptionFrameVisualEquality.isVisuallyIdentical(visible, cleared))
    }
}
