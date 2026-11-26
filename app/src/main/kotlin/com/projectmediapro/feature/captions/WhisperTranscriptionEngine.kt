package com.projectmediapro.feature.captions

import com.projectmediapro.core.model.SilenceDetectionConfig
import com.projectmediapro.core.model.SilenceDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * On-device Open Whisper (`whisper.cpp`) [TranscriptionEngine] that listens to 16 kHz mono PCM,
 * transcribes real spoken/sung words completely offline when the optional model is installed,
 * distributes segment timing smoothly into word-level [WordTiming] entries, and falls back
 * cleanly to [OfflineHeuristicTranscriptionEngine] when a custom script is pasted or when the
 * Whisper model is not yet downloaded.
 */
class WhisperTranscriptionEngine(
    private val modelManager: WhisperModelManager,
    private val fallbackEngine: TranscriptionEngine = OfflineHeuristicTranscriptionEngine(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val transcribeInvoker: (modelPath: String, pcmFloat: FloatArray) -> List<WhisperSegment>? =
        { path, pcm -> WhisperNativeBridge.transcribePcm16k(path, pcm) },
) : TranscriptionEngine {

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult {
        // When the user explicitly pasted a script/lyrics, align their exact text directly.
        if (!request.providedScript.isNullOrBlank()) {
            return fallbackEngine.transcribe(request)
        }

        val modelFile = modelManager.installedModelFileOrNull()
            ?: return fallbackEngine.transcribe(request)

        return withContext(defaultDispatcher) {
            try {
                val samples = request.pcmAudio.samples
                if (samples.isEmpty()) {
                    return@withContext TranscriptionResult(emptyList(), 0f, usedPlaceholderTokens = false)
                }
                val sampleRate = request.pcmAudio.sampleRateHz.coerceAtLeast(1)
                val totalDurationMs = (samples.size.toLong() * 1_000L) / sampleRate.toLong()
                // Speech-Only Whisper Fast-Pass: on clips >= 10s with >= 15% silence, compact out
                // dead air before whisper.cpp runs and remap returned timestamps to original time.
                val compacted = if (totalDurationMs >= 10_000L) {
                    val silences = SilenceDetector.detectSilences(
                        samples = samples,
                        sampleRateHz = sampleRate,
                        config = SilenceDetectionConfig(
                            thresholdDb = -36f,
                            minSilenceMs = 700L,
                            paddingMs = 120L,
                        ),
                    )
                    val silenceMs = silences.sumOf { it.durationMs }
                    if (silenceMs * 100L >= totalDurationMs * 15L) {
                        val speechIntervals = SilenceDetector.computeSpeechIntervals(totalDurationMs, silences)
                        SilenceDetector.compactSpeechPcm(samples, sampleRate, speechIntervals)
                            .takeIf { it.floatPcm.isNotEmpty() && it.mappings.isNotEmpty() }
                    } else {
                        null
                    }
                } else {
                    null
                }

                val floatPcm = compacted?.floatPcm ?: ShortArrayToFloatConverter.toNormalizedFloat(samples)
                val rawSegments = transcribeInvoker(modelFile.absolutePath, floatPcm)
                if (rawSegments.isNullOrEmpty()) {
                    return@withContext fallbackEngine.transcribe(request)
                }
                val segments = if (compacted != null) {
                    rawSegments.map { seg ->
                        val remappedStart = SilenceDetector.remapPackedTimeMs(seg.startMs, compacted.mappings)
                        val remappedEnd = SilenceDetector.remapPackedTimeMs(seg.endMs, compacted.mappings)
                            .coerceAtLeast(remappedStart + 80L)
                        seg.copy(startMs = remappedStart, endMs = remappedEnd)
                    }
                } else {
                    rawSegments
                }
                val words = WhisperSegmentMapper.toWordTimings(
                    clipId = request.clipId,
                    segments = segments,
                )
                if (words.isEmpty()) {
                    fallbackEngine.transcribe(request)
                } else {
                    TranscriptionResult(
                        words = words,
                        detectedActivityRatio = 1.0f,
                        usedPlaceholderTokens = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: OutOfMemoryError) {
                fallbackEngine.transcribe(request)
            } catch (_: Exception) {
                fallbackEngine.transcribe(request)
            }
        }
    }
}

internal object ShortArrayToFloatConverter {
    fun toNormalizedFloat(samples: ShortArray): FloatArray {
        val out = FloatArray(samples.size)
        for (i in samples.indices) {
            out[i] = (samples[i].toFloat() / 32768f).coerceIn(-1f, 1f)
        }
        return out
    }
}

/**
 * Pure-JVM mapper converting Whisper time-stamped segments into monotonic word-level [WordTiming]
 * entries ready for [LyricsPacingEngine] and all 8 viral kinetic lyric templates.
 */
object WhisperSegmentMapper {
    private val NON_SPEECH_BRACKETS = Regex("\\[[^\\]]*\\]|\\([^)]*\\)")
    private val WHITESPACE = Regex("\\s+")

    fun toWordTimings(
        clipId: String,
        segments: List<WhisperSegment>,
    ): List<WordTiming> {
        val result = mutableListOf<WordTiming>()
        var wordCounter = 0
        var lastEndUs = 0L

        for (segment in segments) {
            val cleaned = segment.text
                .replace(NON_SPEECH_BRACKETS, " ")
                .trim()
            if (cleaned.isEmpty()) continue

            val tokens = cleaned.split(WHITESPACE).filter { it.isNotBlank() }
            if (tokens.isEmpty()) continue

            val segStartUs = max(lastEndUs, segment.startMs.coerceAtLeast(0L) * CaptionTime.MICROS_PER_MS)
            val rawSegEndUs = segment.endMs.coerceAtLeast(segment.startMs) * CaptionTime.MICROS_PER_MS
            val minSpanUs = tokens.size * CaptionTime.MIN_DURATION_US
            val segEndUs = max(segStartUs + minSpanUs, rawSegEndUs)
            val totalSpanUs = segEndUs - segStartUs

            val weights = tokens.map { token -> max(1, token.length) }
            val totalWeight = weights.sum().coerceAtLeast(1)
            var cursorUs = segStartUs

            tokens.forEachIndexed { idx, token ->
                val wordStartUs = cursorUs
                val wordEndUs = if (idx == tokens.lastIndex) {
                    segEndUs
                } else {
                    val share = ((totalSpanUs.toDouble() * weights[idx].toDouble()) / totalWeight.toDouble())
                        .roundToLong()
                        .coerceAtLeast(CaptionTime.MIN_DURATION_US)
                    minOf(segEndUs - (tokens.lastIndex - idx) * CaptionTime.MIN_DURATION_US, wordStartUs + share)
                }
                result.add(
                    WordTiming(
                        id = "${clipId}_w_${++wordCounter}",
                        text = token,
                        startUs = wordStartUs,
                        endUs = max(wordStartUs + CaptionTime.MIN_DURATION_US, wordEndUs),
                        syllableCount = estimateSyllables(token),
                        confidence = 0.92f,
                        isPlaceholder = false,
                    ),
                )
                cursorUs = max(wordStartUs + CaptionTime.MIN_DURATION_US, wordEndUs)
            }
            lastEndUs = cursorUs
        }
        return result
    }

    private fun estimateSyllables(word: String): Int {
        val letters = word.lowercase().filter { it.isLetter() }
        if (letters.isEmpty()) return 1
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
        var groups = 0
        var inVowel = false
        for (ch in letters) {
            val isVowel = ch in vowels
            if (isVowel && !inVowel) groups++
            inVowel = isVowel
        }
        return max(1, groups)
    }
}
