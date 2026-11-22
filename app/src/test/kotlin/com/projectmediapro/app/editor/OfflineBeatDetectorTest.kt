package com.projectmediapro.app.editor

import com.projectmediapro.feature.captions.PcmAudioData
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class OfflineBeatDetectorTest {

    @Test
    fun `detectBeatsMs finds periodic percussive hits in synthetic 16kHz PCM`() {
        val sampleRate = PcmAudioData.TARGET_SAMPLE_RATE_HZ
        val durationSec = 4
        val totalSamples = sampleRate * durationSec
        val pcm = ShortArray(totalSamples)

        // Quiet background hum + strong 60ms drum hits at 0.5s, 1.5s, 2.5s, 3.5s
        val hitTimesMs = listOf(500L, 1_500L, 2_500L, 3_500L)
        for (i in 0 until totalSamples) {
            val tSec = i.toDouble() / sampleRate.toDouble()
            var value = 0.02 * sin(2.0 * PI * 120.0 * tSec)
            val tMs = (i * 1_000L) / sampleRate
            for (hit in hitTimesMs) {
                if (tMs in hit..(hit + 60L)) {
                    value += 0.75 * sin(2.0 * PI * 80.0 * tSec)
                }
            }
            pcm[i] = (value.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
        }

        val detected = OfflineBeatDetector.detectBeatsMs(
            PcmAudioData(
                samples = pcm,
                sampleRateHz = sampleRate,
                durationUs = durationSec * 1_000_000L,
            ),
        )

        assertTrue("Expected at least 3 detected beats, got $detected", detected.size >= 3)
        // Verify first detected beat is close to 500ms
        assertTrue(
            "Expected a beat near 500ms, got $detected",
            detected.any { kotlin.math.abs(it - 500L) <= 80L },
        )
    }
}
