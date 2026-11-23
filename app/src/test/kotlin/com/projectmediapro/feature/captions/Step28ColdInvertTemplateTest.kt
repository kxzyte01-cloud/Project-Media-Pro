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

class Step28ColdInvertTemplateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun coldInvertShowsOneUppercaseWordAtCenterAndRoundTripsJson() {
        val words = listOf(
            WordTiming(id = "w1", text = "hold", startUs = 0L, endUs = 300_000L),
            WordTiming(id = "w2", text = "cold", startUs = 300_000L, endUs = 650_000L),
        )
        val style = CaptionStyle.default().withTemplate(CaptionTemplateId.COLD_INVERT)
        assertEquals(0.5f, style.position.yFraction, 0.0001f)

        val doc = CaptionDocument(
            projectId = "p-cold",
            rawWords = words,
            lines = listOf(CaptionLine(id = "l1", words = words, startUs = 0L, endUs = 650_000L)),
            style = style,
        )

        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 400_000L)
        assertTrue(frame.hasVisibleContent)
        val visible = frame.words.filter { it.alpha > 0f && it.visibleCharCount > 0 }
        assertEquals(1, visible.size)
        assertEquals("COLD", visible.single().displayText)

        val decoded = json.decodeFromString<CaptionDocument>(json.encodeToString(doc))
        assertEquals(CaptionTemplateId.COLD_INVERT, decoded.style.templateId)
    }

    @Test
    fun coldInvertStarterTemplateIsRegistered() {
        val starter = StarterTemplates.findById(StarterTemplates.COLD_INVERT_EDIT_ID)
        assertNotNull(starter)
        val nonNull = requireNotNull(starter)
        assertEquals(1, nonNull.mainVideoSlots().size)
        assertEquals(1, nonNull.audioSlots().size)
    }
}
