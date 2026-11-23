package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.StarterTemplates
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.mainVideoSlots
import com.projectmediapro.core.model.overlaySlots
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step26CrimsonZineTemplateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun crimsonZineTemplateEvaluatesPopInAndRoundTripsJson() {
        val words = listOf(
            WordTiming(id = "w1", text = "gimme", startUs = 0L, endUs = 450_000L),
            WordTiming(id = "w2", text = "dope", startUs = 450_000L, endUs = 950_000L),
            WordTiming(id = "w3", text = "diamond", startUs = 950_000L, endUs = 1_500_000L),
        )
        val doc = CaptionDocument(
            projectId = "p-zine",
            rawWords = words,
            lines = listOf(CaptionLine(id = "l1", words = words, startUs = 0L, endUs = 1_500_000L)),
            style = CaptionStyle.default().withTemplate(CaptionTemplateId.CRIMSON_ZINE),
        )

        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 600_000L)
        assertTrue(frame.hasVisibleContent)
        assertTrue(frame.words[0].isSpoken)
        assertTrue(frame.words[1].isActive)
        assertEquals(0f, frame.words[2].alpha, 0.0001f)

        val encoded = json.encodeToString(doc)
        val decoded = json.decodeFromString<CaptionDocument>(encoded)
        assertEquals(CaptionTemplateId.CRIMSON_ZINE, decoded.style.templateId)
    }

    @Test
    fun crimsonZineStarterTemplateIsRegistered() {
        val starter = StarterTemplates.findById(StarterTemplates.CRIMSON_ZINE_EDIT_ID)
        assertNotNull(starter)
        assertEquals(1, requireNotNull(starter).mainVideoSlots().size)
        assertEquals(1, starter.audioSlots().size)
        assertEquals(1, starter.overlaySlots().size)
    }
}
