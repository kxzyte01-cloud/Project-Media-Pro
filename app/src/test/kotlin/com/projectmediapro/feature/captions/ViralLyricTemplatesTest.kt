package com.projectmediapro.feature.captions

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViralLyricTemplatesTest {

    private fun multiLineDocument(templateId: CaptionTemplateId): CaptionDocument {
        val line1Words = listOf(
            WordTiming(id = "w1", text = "first", startUs = 0L, endUs = 500_000L),
            WordTiming(id = "w2", text = "line", startUs = 500_000L, endUs = 1_000_000L),
        )
        val line2Words = listOf(
            WordTiming(id = "w3", text = "second", startUs = 1_000_000L, endUs = 1_500_000L),
            WordTiming(id = "w4", text = "verse", startUs = 1_500_000L, endUs = 2_000_000L),
        )
        val line3Words = listOf(
            WordTiming(id = "w5", text = "final", startUs = 2_000_000L, endUs = 2_500_000L),
            WordTiming(id = "w6", text = "drop", startUs = 2_500_000L, endUs = 3_000_000L),
        )
        return CaptionDocument(
            projectId = "proj-viral",
            rawWords = line1Words + line2Words + line3Words,
            lines = listOf(
                CaptionLine(id = "l1", words = line1Words, startUs = 0L, endUs = 1_000_000L),
                CaptionLine(id = "l2", words = line2Words, startUs = 1_000_000L, endUs = 2_000_000L),
                CaptionLine(id = "l3", words = line3Words, startUs = 2_000_000L, endUs = 3_000_000L),
            ),
            style = CaptionStyle.default().withTemplate(templateId),
        )
    }

    @Test
    fun `registry exposes all 8 templates including 4 viral kinetic styles`() {
        val ids = CaptionTemplateRegistry.all.map { it.id }.toSet()
        assertTrue(CaptionTemplateId.KINETIC_GRUNGE in ids)
        assertTrue(CaptionTemplateId.EDITORIAL_SCRATCH in ids)
        assertTrue(CaptionTemplateId.NEON_GLOW_STACK in ids)
        assertTrue(CaptionTemplateId.MUSIC_PLAYER in ids)
        assertTrue(CaptionTemplateId.KINETIC_COLLAGE in ids)
        assertTrue(CaptionTemplateId.KNOCKOUT_MASK in ids)
        assertTrue(CaptionTemplateId.CRIMSON_ZINE in ids)
        assertTrue(CaptionTemplateId.COLD_INVERT in ids)
        assertTrue(CaptionTemplateId.CODE_POEM in ids)
        assertTrue(CaptionTemplateId.BLACKOUT_KINETIC in ids)
        assertEquals(14, ids.size)
    }

    @Test
    fun `music player frame state exposes previous, next, and song progress`() {
        val doc = multiLineDocument(CaptionTemplateId.MUSIC_PLAYER)
        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 1_500_000L)

        assertTrue(frame.hasVisibleContent)
        assertEquals(1, frame.activeLineIndex)
        assertEquals(3, frame.totalLines)
        assertEquals("first line", frame.previousLineText)
        assertEquals("final drop", frame.nextLineText)
        assertEquals(0.5f, frame.songProgressFraction, 0.01f)
    }

    @Test
    fun `kinetic grunge uppercases words and pops active word`() {
        val doc = multiLineDocument(CaptionTemplateId.KINETIC_GRUNGE)
        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 1_250_000L)

        assertEquals("SECOND", frame.words.first().displayText)
        assertTrue(frame.words.first().isActive)
        assertTrue(frame.words.first().scale > 1.0f)
    }

    @Test
    fun `all viral templates round-trip through kotlinx serialization`() {
        val json = Json { encodeDefaults = true }
        val viralIds = listOf(
            CaptionTemplateId.KINETIC_GRUNGE,
            CaptionTemplateId.EDITORIAL_SCRATCH,
            CaptionTemplateId.NEON_GLOW_STACK,
            CaptionTemplateId.MUSIC_PLAYER,
        )
        for (id in viralIds) {
            val doc = multiLineDocument(id)
            val encoded = json.encodeToString(doc)
            val decoded = json.decodeFromString<CaptionDocument>(encoded)
            assertNotNull(decoded)
            assertEquals(id, decoded.style.templateId)
        }
    }
}
