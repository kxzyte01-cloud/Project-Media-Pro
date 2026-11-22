package com.projectmediapro.feature.captions

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TimelineAndAudioMappingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun twoBurstPcm(): PcmAudioData {
        val sampleRate = 16_000
        val totalSamples = sampleRate * 2
        val pcm = ShortArray(totalSamples) { i ->
            val inFirstBurst = i in (sampleRate / 4) until (sampleRate * 3 / 4)
            val inSecondBurst = i in (sampleRate + 2_000) until (sampleRate + 10_000)
            if (inFirstBurst || inSecondBurst) {
                (sin(2.0 * PI * 440.0 * i / sampleRate) * 18_000.0).toInt().toShort()
            } else {
                0
            }
        }
        return PcmAudioData(
            samples = pcm,
            sampleRateHz = sampleRate,
            channelCount = 1,
            durationUs = 2_000_000L,
        )
    }

    @Test
    fun mapClipWordsToTimeline_appliesTrimAndSpeedInMicroseconds() {
        val clipWords = listOf(
            WordTiming("w-0", "skip", 0L, 400_000L),
            WordTiming("w-1", "keep", 1_000_000L, 2_000_000L),
            WordTiming("w-2", "late", 3_500_000L, 4_000_000L),
        )

        val mapped = TimelineMapper.mapClipWordsToTimeline(
            words = clipWords,
            clipStartMs = 5_000L,
            trimStartMs = 1_000L,
            trimEndMs = 3_000L,
            playbackSpeed = 2.0f,
        )

        assertEquals(1, mapped.size)
        assertEquals("w-1", mapped.single().id)
        assertEquals(5_000_000L, mapped.single().startUs)
        assertEquals(5_500_000L, mapped.single().endUs)
    }

    @Test
    fun continuousResampler_chunked44100HzTo16000Hz_hasZeroCumulativeDrift() {
        val sourceRate = 44_100
        val chunkSize = 1_024
        val chunks = 100
        val totalSourceSamples = chunkSize * chunks
        val resampler = ContinuousMono16kResampler(maxSamples = 100_000)

        repeat(chunks) {
            resampler.appendMono(FloatArray(chunkSize) { 0.25f }, sourceRate)
        }

        val out = resampler.finish()
        val expectedSamples = (totalSourceSamples.toDouble() * 16_000.0 / sourceRate.toDouble()).toInt()
        assertTrue(
            "Expected ~$expectedSamples samples, got ${out.size}",
            abs(out.size - expectedSamples) <= 2,
        )
    }

    @Test
    fun heuristicEngine_withoutScript_emitsHonestNumberedPlaceholders() = runTest {
        val engine = OfflineHeuristicTranscriptionEngine()
        val result = engine.transcribe(
            TranscriptionRequest(
                pcmAudio = twoBurstPcm(),
                clipId = "clip-1",
            ),
        )

        assertTrue(result.words.size >= 2)
        assertTrue(result.usedPlaceholderTokens)
        assertTrue(result.words.all { it.isPlaceholder && it.text.startsWith("[") })
        assertTrue(result.detectedActivityRatio > 0.2f)
    }

    @Test
    fun heuristicEngine_withProvidedScript_alignsRealWordsToActiveAudio() = runTest {
        val engine = OfflineHeuristicTranscriptionEngine()
        val result = engine.transcribe(
            TranscriptionRequest(
                pcmAudio = twoBurstPcm(),
                clipId = "clip-1",
                providedScript = "Hello world from Media Pro",
            ),
        )

        assertFalse(result.usedPlaceholderTokens)
        assertEquals(listOf("Hello", "world", "from", "Media", "Pro"), result.words.map { it.text })
        assertTrue(result.words.none { it.isPlaceholder })
        assertTrue(result.words.first().startUs >= 200_000L)
    }

    @Test
    fun fileCaptionRepository_atomicSaveAndLoadRoundtripWithUnderscoreId() = runTest {
        val repo = FileCaptionRepository(tempFolder.newFolder("captions"))
        val words = listOf(WordTiming("w-1", "test", 0L, 350_000L, 1))
        val doc = CaptionDocument(
            projectId = "proj_underscore_1",
            rawWords = words,
            lines = LyricsPacingEngine.buildLines(words, LyricsPresetRegistry.FAST_FLOW),
            presetId = LyricsPresetRegistry.FAST_FLOW_ID,
            globalOffsetUs = -120_000L,
        )

        repo.saveCaptions(doc)
        val loaded = repo.loadCaptions("proj_underscore_1")
        assertNotNull(loaded)
        assertEquals(doc, loaded)

        repo.deleteCaptions("proj_underscore_1")
        assertEquals(null, repo.loadCaptions("proj_underscore_1"))
    }

    @Test
    fun scriptAlignment_spansQuietVerseAndLoudChorusWithoutCramming() = runTest {
        val sampleRate = 16_000
        val totalSeconds = 10
        val samples = ShortArray(sampleRate * totalSeconds) { idx ->
            val sec = idx / sampleRate
            // 0..8s quiet verse (amplitude 5_000), 8..10s loud chorus (amplitude 28_000)
            val amp = if (sec < 8) 5_000.0 else 28_000.0
            (sin(2.0 * PI * 440.0 * idx / sampleRate) * amp).toInt().toShort()
        }
        val pcm = PcmAudioData(
            samples = samples,
            sampleRateHz = sampleRate,
            channelCount = 1,
            durationUs = totalSeconds * 1_000_000L,
        )
        val lyrics = """
            First line in the quiet verse
            Second line keeps moving slow

            Chorus hits right at the end
        """.trimIndent()

        val engine = OfflineHeuristicTranscriptionEngine()
        val result = engine.transcribe(
            TranscriptionRequest(
                pcmAudio = pcm,
                clipId = "song-1",
                providedScript = lyrics,
            ),
        )

        assertTrue(result.words.size >= 15)
        // Must start in the quiet verse (not wait until the 8s chorus) and span most of the 10s track.
        assertTrue(result.words.first().startUs < 1_000_000L)
        assertTrue(result.words.last().endUs > 8_500_000L)
    }
}
