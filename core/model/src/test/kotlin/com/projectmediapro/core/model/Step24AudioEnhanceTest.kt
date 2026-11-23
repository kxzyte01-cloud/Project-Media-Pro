package com.projectmediapro.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class Step24AudioEnhanceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun measureSteadyStateRms(
        preset: AudioEqPreset,
        freqHz: Double,
        sampleRate: Int = 48_000,
    ): Double {
        val filters = AudioBiquadCoefficients.forPreset(preset, sampleRate).map { AudioBiquadFilter(it) }
        val totalSamples = 4_800 // 100 ms
        val measureStart = 2_400 // skip initial transient
        var sumSq = 0.0
        var count = 0
        for (n in 0 until totalSamples) {
            var x = sin(2.0 * PI * freqHz * n / sampleRate)
            for (f in filters) {
                x = f.process(x)
            }
            assertTrue("Filter output must remain finite", x.isFinite())
            if (n >= measureStart) {
                sumSq += x * x
                count++
            }
        }
        return sqrt(sumSq / count)
    }

    @Test
    fun vocalClarityCutsLowRumbleAndBoostsSpeechPresence() {
        val flatLow = measureSteadyStateRms(AudioEqPreset.NONE, 40.0)
        val vocalLow = measureSteadyStateRms(AudioEqPreset.VOCAL_CLARITY, 40.0)
        val flatVoice = measureSteadyStateRms(AudioEqPreset.NONE, 3_200.0)
        val vocalVoice = measureSteadyStateRms(AudioEqPreset.VOCAL_CLARITY, 3_200.0)

        assertTrue("Vocal Clarity should attenuate 40 Hz rumble", vocalLow < flatLow * 0.5)
        assertTrue("Vocal Clarity should boost 3.2 kHz presence", vocalVoice > flatVoice * 1.4)
    }

    @Test
    fun bassBoostAmplifiesLowEndAndLofiCutsHighs() {
        val flatBass = measureSteadyStateRms(AudioEqPreset.NONE, 90.0)
        val boostedBass = measureSteadyStateRms(AudioEqPreset.BASS_BOOST, 90.0)
        assertTrue("Bass Boost should amplify 90 Hz", boostedBass > flatBass * 1.4)

        val flatHigh = measureSteadyStateRms(AudioEqPreset.NONE, 10_000.0)
        val lofiHigh = measureSteadyStateRms(AudioEqPreset.LOFI_RADIO, 10_000.0)
        assertTrue("Lo-Fi Radio should roll off 10 kHz highs", lofiHigh < flatHigh * 0.35)
    }

    @Test
    fun noiseGateAttenuatesQuietFloorAndOpensOnSpeech() {
        val gate = AudioNoiseGateEnvelope(sampleRate = 48_000)
        var quietGain = 1.0
        repeat(4_800) {
            quietGain = gate.nextGain(0.004) // ~ -48 dBFS room hum
        }
        assertTrue("Noise gate should hold near floor on quiet hum", quietGain < 0.15)

        var speechGain = 0.0
        repeat(2_400) {
            speechGain = gate.nextGain(0.35) // loud voice
        }
        assertTrue("Noise gate should open smoothly on speech", speechGain > 0.90)
    }

    @Test
    fun projectTimelineSetClipAudioMixPersistsAndResetsAudioEnhanceWithSchemaV1() {
        val base = Project(
            id = "p-audio",
            title = "Audio Studio Test",
            createdAt = 1L,
            updatedAt = 1L,
            tracks = listOf(
                Track(
                    id = "v1",
                    type = TrackType.VIDEO,
                    clips = listOf(Clip(id = "c1", mediaUri = "talk.mp4", startTimeMs = 0L, durationMs = 6_000L)),
                ),
            ),
        )

        val enhance = AudioEnhanceConfig(
            eqPreset = AudioEqPreset.VOCAL_CLARITY,
            noiseGateEnabled = true,
        )
        val updated = ProjectTimeline.setClipAudioMix(
            project = base,
            clipId = "c1",
            volume = 1.0f,
            audioBoost = 1.0f,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            audioEnhance = enhance,
        )
        val clip = updated.videoClips().single()
        assertEquals(enhance, clip.audioEnhance)
        assertTrue(clip.hasCustomAudioMix)

        // Schema v1 JSON round-trip
        val encoded = json.encodeToString(updated)
        val decoded = json.decodeFromString<Project>(encoded)
        assertEquals(Project.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(enhance, decoded.videoClips().single().audioEnhance)

        // Resetting to identity clears audioEnhance to null
        val reset = ProjectTimeline.setClipAudioMix(
            project = updated,
            clipId = "c1",
            volume = 1.0f,
            audioBoost = 1.0f,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            audioEnhance = AudioEnhanceConfig(),
        )
        assertNull(reset.videoClips().single().audioEnhance)
        assertFalse(reset.videoClips().single().hasCustomAudioMix)
    }
}
