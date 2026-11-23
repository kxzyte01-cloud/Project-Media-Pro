package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.VideoFilterPreset
import com.projectmediapro.core.model.gradeSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step27RainFontAndViralLutsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun rainFontDefaultsOffAndEnablesAcrossAnyTemplate() {
        val defaultStyle = CaptionStyle.default()
        assertFalse(defaultStyle.rainFontEnabled)

        val rainOnKinetic = defaultStyle
            .withTemplate(CaptionTemplateId.KINETIC_COLLAGE)
            .copy(fontFamily = CaptionFontFamily.RAIN_GLASS, rainFontEnabled = true)

        val encoded = json.encodeToString(rainOnKinetic)
        val decoded = json.decodeFromString<CaptionStyle>(encoded)
        assertTrue(decoded.rainFontEnabled)
        assertEquals(CaptionFontFamily.RAIN_GLASS, decoded.fontFamily)
        assertEquals(CaptionTemplateId.KINETIC_COLLAGE, decoded.templateId)
    }

    @Test
    fun musicPlayerTemplateAutoEnablesRainFont() {
        val styled = CaptionStyle.default().withTemplate(CaptionTemplateId.MUSIC_PLAYER)
        assertTrue(styled.rainFontEnabled)
    }

    @Test
    fun viralColorLutPresetsHaveDistinctGrades() {
        val moss = VideoFilterPreset.MISTY_MOSS.gradeSpec()
        val sunset = VideoFilterPreset.GOLDEN_SUNSET.gradeSpec()
        val crimson = VideoFilterPreset.CRIMSON_MOOD.gradeSpec()

        assertTrue("Misty Moss should lean cool-green", moss.greenScale > moss.redScale)
        assertTrue("Golden Sunset should boost warm amber", sunset.redScale > sunset.blueScale)
        assertTrue("Crimson Mood should punch reds over greens", crimson.redScale > crimson.greenScale)
    }
}
