package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.StarterTemplates
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.mainVideoSlots
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step29CodePoemTemplateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun codePoemUsesTypewriterCursorAndRoundTripsJson() {
        val words = listOf(
            WordTiming(id = "w1", text = "burn", startUs = 0L, endUs = 400_000L),
            WordTiming(id = "w2", text = "thePage", startUs = 400_000L, endUs = 900_000L),
        )
        val style = CaptionStyle.default().withTemplate(CaptionTemplateId.CODE_POEM)
        assertEquals(0.5f, style.position.yFraction, 0.0001f)

        val doc = CaptionDocument(
            projectId = "p-code",
            rawWords = words,
            lines = listOf(CaptionLine(id = "l1", words = words, startUs = 0L, endUs = 900_000L)),
            style = style,
        )

        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 600_000L)
        assertTrue(frame.hasVisibleContent)
        assertTrue(frame.showCursor)
        assertTrue(frame.words[0].isSpoken)
        assertTrue(frame.words[1].isActive)
        assertTrue(frame.words[1].visibleCharCount in 1..frame.words[1].displayText.length)

        val decoded = json.decodeFromString<CaptionDocument>(json.encodeToString(doc))
        assertEquals(CaptionTemplateId.CODE_POEM, decoded.style.templateId)
    }

    @Test
    fun codePoemStarterTemplateIsRegistered() {
        val starter = StarterTemplates.findById(StarterTemplates.CODE_POEM_EDIT_ID)
        assertNotNull(starter)
        val nonNull = requireNotNull(starter)
        assertEquals(1, nonNull.mainVideoSlots().size)
        assertEquals(1, nonNull.audioSlots().size)
    }
}
