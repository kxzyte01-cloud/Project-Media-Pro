package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.StarterTemplates
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.mainVideoSlots
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step30BlackoutKineticTemplateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun blackoutKineticPopRevealsWordsAndRoundTripsJson() {
        val words = listOf(
            WordTiming(id = "w1", text = "never", startUs = 0L, endUs = 350_000L),
            WordTiming(id = "w2", text = "ever", startUs = 350_000L, endUs = 700_000L),
            WordTiming(id = "w3", text = "bored", startUs = 700_000L, endUs = 1_200_000L),
        )
        val style = CaptionStyle.default().withTemplate(CaptionTemplateId.BLACKOUT_KINETIC)
        assertEquals(0.5f, style.position.yFraction, 0.0001f)

        val doc = CaptionDocument(
            projectId = "p-blackout",
            rawWords = words,
            lines = listOf(CaptionLine(id = "l1", words = words, startUs = 0L, endUs = 1_200_000L)),
            style = style,
        )

        val frame = CaptionFrameEvaluator.evaluate(doc, presentationTimeUs = 800_000L)
        assertTrue(frame.hasVisibleContent)
        assertTrue(frame.words[0].isSpoken)
        assertTrue(frame.words[1].isSpoken)
        assertTrue(frame.words[2].isActive)

        val decoded = json.decodeFromString<CaptionDocument>(json.encodeToString(doc))
        assertEquals(CaptionTemplateId.BLACKOUT_KINETIC, decoded.style.templateId)
    }

    @Test
    fun blackoutStarterTemplateIs16x9AndRegistered() {
        val starter = StarterTemplates.findById(StarterTemplates.BLACKOUT_LYRICS_16_9_ID)
        assertNotNull(starter)
        val nonNull = requireNotNull(starter)
        assertEquals(CanvasAspect.RATIO_16_9, nonNull.canvasAspect)
        assertEquals(1, nonNull.mainVideoSlots().size)
        assertEquals(1, nonNull.audioSlots().size)
    }
}
