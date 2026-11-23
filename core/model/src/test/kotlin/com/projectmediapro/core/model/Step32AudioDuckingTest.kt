package com.projectmediapro.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step32AudioDuckingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun mergeWindowsBridgesShortGapsAndKeepsDistantWindowsSeparate() {
        val raw = listOf(
            SpeechWindowMs(startMs = 2_000L, endMs = 3_000L),
            SpeechWindowMs(startMs = 3_200L, endMs = 4_000L), // 200ms gap <= 300ms -> merged
            SpeechWindowMs(startMs = 6_000L, endMs = 7_500L), // 2000ms gap -> separate
        )
        val merged = AudioDuckingEnvelope.mergeWindows(raw)
        assertEquals(2, merged.size)
        assertEquals(SpeechWindowMs(2_000L, 4_000L), merged[0])
        assertEquals(SpeechWindowMs(6_000L, 7_500L), merged[1])
    }

    @Test
    fun duckingEnvelopeRampsDownSmoothlyHoldsFloorAndReleasesCleanly() {
        val windows = listOf(SpeechWindowMs(startMs = 1_000L, endMs = 2_000L))

        // Far before speech: full music gain
        assertEquals(1.0, AudioDuckingEnvelope.gainAt(500L, windows), 0.0001)

        // Mid-attack (70ms before speech starts): strictly between DUCKED_GAIN and 1.0
        val midAttack = AudioDuckingEnvelope.gainAt(930L, windows)
        assertTrue("Mid-attack should ease smoothly", midAttack in 0.45..0.85)

        // Inside speech window: exact -11 dB ducked gain
        assertEquals(AudioDuckingEnvelope.DUCKED_GAIN, AudioDuckingEnvelope.gainAt(1_500L, windows), 0.0001)

        // Mid-release (140ms after speech ends): strictly between DUCKED_GAIN and 1.0
        val midRelease = AudioDuckingEnvelope.gainAt(2_140L, windows)
        assertTrue("Mid-release should swell smoothly", midRelease in 0.45..0.85)

        // After release completes: back to full 1.0 gain
        assertEquals(1.0, AudioDuckingEnvelope.gainAt(2_500L, windows), 0.0001)
    }

    @Test
    fun autoDuckFlagMarksConfigNonIdentityAndPreservesSchemaV1() {
        val config = AudioEnhanceConfig(autoDuckMusicEnabled = true)
        assertFalse(config.isIdentity)

        val decoded = json.decodeFromString<AudioEnhanceConfig>(json.encodeToString(config))
        assertTrue(decoded.autoDuckMusicEnabled)
        assertEquals(AudioEqPreset.NONE, decoded.eqPreset)
        assertFalse(decoded.noiseGateEnabled)
    }
}
