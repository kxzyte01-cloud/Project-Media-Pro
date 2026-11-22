package com.projectmediapro.feature.captions

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WhisperTranscriptionEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `segmentMapper splits whisper segments into monotonic word timings and strips bracketed tags`() {
        val segments = listOf(
            WhisperSegment(text = "[Music] hello world", startMs = 1_000L, endMs = 2_000L),
            WhisperSegment(text = "(applause) drop the beat", startMs = 2_200L, endMs = 3_400L),
        )

        val words = WhisperSegmentMapper.toWordTimings("clip-1", segments)
        assertEquals(listOf("hello", "world", "drop", "the", "beat"), words.map { it.text })
        assertTrue(words.all { !it.isPlaceholder })
        for (i in 1 until words.size) {
            assertTrue(
                "Expected monotonic timing between ${words[i - 1]} and ${words[i]}",
                words[i].startUs >= words[i - 1].endUs,
            )
        }
    }

    @Test
    fun `engine uses whisper invoker when model is installed and falls back when absent`() = runTest {
        val modelsDir = tempFolder.newFolder("whisper_models")
        val manager = WhisperModelManager(modelsDir)
        val pcm = PcmAudioData(
            samples = ShortArray(16_000) { 1000 },
            sampleRateHz = 16_000,
            durationUs = 1_000_000L,
        )

        var invoked = false
        val engine = WhisperTranscriptionEngine(
            modelManager = manager,
            transcribeInvoker = { _, _ ->
                invoked = true
                listOf(WhisperSegment(text = "real whisper words", startMs = 0L, endMs = 900L))
            },
        )

        // Not installed -> falls back without invoking native bridge
        engine.transcribe(TranscriptionRequest(pcmAudio = pcm, clipId = "c1"))
        assertFalse(invoked)

        // Create a valid-sized dummy model file -> invokes Whisper
        val fakeModel = File(modelsDir, WhisperModelManager.MODEL_FILE_NAME)
        fakeModel.writeBytes(ByteArray(WhisperModelManager.MIN_VALID_MODEL_BYTES.toInt() + 16))
        manager.refreshState()
        assertTrue(manager.state.value is WhisperModelState.Installed)

        val result = engine.transcribe(TranscriptionRequest(pcmAudio = pcm, clipId = "c1"))
        assertTrue(invoked)
        assertEquals(listOf("real", "whisper", "words"), result.words.map { it.text })
        assertFalse(result.usedPlaceholderTokens)

        // Delete removes file and resets state
        manager.deleteModel()
        assertEquals(WhisperModelState.NotInstalled, manager.state.value)
        assertFalse(fakeModel.exists())
    }
}
