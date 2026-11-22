package com.projectmediapro.feature.captions

import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class TranscriptionRequest(
    val pcmAudio: PcmAudioData,
    val clipId: String,
    val languageTag: String = "en-US",
    val providedScript: String? = null,
)

data class TranscriptionResult(
    val words: List<WordTiming>,
    val detectedActivityRatio: Float,
    val usedPlaceholderTokens: Boolean = false,
)

interface TranscriptionEngine {
    suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult
}

private data class ScriptToken(
    val text: String,
    val syllables: Int,
    val wordWeight: Float,
    val trailingPauseWeight: Float,
)

class OfflineHeuristicTranscriptionEngine(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TranscriptionEngine {

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult =
        withContext(defaultDispatcher) {
            try {
                val samples = request.pcmAudio.samples
                val sampleRate = request.pcmAudio.sampleRateHz.coerceAtLeast(1)
                if (samples.isEmpty()) {
                    return@withContext TranscriptionResult(emptyList(), 0f, usedPlaceholderTokens = false)
                }

                val hopSamples = max(1, (sampleRate * HOP_MS) / 1_000)
                val frameSamples = max(1, (sampleRate * FRAME_MS) / 1_000)
                val totalFrames = max(1, (samples.size - frameSamples) / hopSamples + 1)

                val energies = FloatArray(totalFrames)
                var maxEnergy = 0f
                for (frameIdx in 0 until totalFrames) {
                    if (frameIdx % 256 == 0) {
                        coroutineContext.ensureActive()
                    }
                    val startSample = frameIdx * hopSamples
                    val endSample = min(samples.size, startSample + frameSamples)
                    var sumSq = 0.0
                    for (s in startSample until endSample) {
                        val norm = samples[s].toDouble() / 32768.0
                        sumSq += norm * norm
                    }
                    val rms = sqrt(sumSq / max(1, endSample - startSample)).toFloat()
                    energies[frameIdx] = rms
                    if (rms > maxEnergy) maxEnergy = rms
                }

                if (maxEnergy < SILENCE_FLOOR) {
                    return@withContext TranscriptionResult(emptyList(), 0f, usedPlaceholderTokens = false)
                }

                val parsedScript = parseScriptTokens(request.providedScript)
                if (parsedScript.isNotEmpty()) {
                    // Use a gentle audible floor for pasted lyrics so quiet verses are never
                    // dropped in favor of loud chorus peaks (which crams words too fast).
                    val scriptThreshold = max(SILENCE_FLOOR, maxEnergy * SCRIPT_AUDIBLE_RATIO)
                    val scriptSegments = detectActiveRegions(
                        energies = energies,
                        threshold = scriptThreshold,
                        bridgeGapMs = SCRIPT_BRIDGE_GAP_MS,
                    ).ifEmpty { listOf(0 until totalFrames) }
                    val activeFrames = energies.count { it >= scriptThreshold }
                    val activityRatio = (activeFrames.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
                    return@withContext TranscriptionResult(
                        words = alignScriptToActiveRegions(
                            clipId = request.clipId,
                            tokens = parsedScript,
                            segments = scriptSegments,
                            energies = energies,
                            maxEnergy = maxEnergy,
                        ),
                        detectedActivityRatio = activityRatio,
                        usedPlaceholderTokens = false,
                    )
                }

                val sortedEnergies = energies.copyOf().apply { sort() }
                val noiseIdx = (sortedEnergies.size * 0.25f).toInt().coerceIn(0, sortedEnergies.lastIndex)
                val noiseFloor = sortedEnergies[noiseIdx]
                val adaptiveThreshold = min(maxEnergy * 0.55f, max(noiseFloor * 2.2f, maxEnergy * 0.14f))
                val activityThreshold = max(SILENCE_FLOOR, adaptiveThreshold)

                val segments = detectActiveRegions(
                    energies = energies,
                    threshold = activityThreshold,
                    bridgeGapMs = BRIDGE_GAP_MS,
                )
                val activeFrames = energies.count { it >= activityThreshold }
                val activityRatio = (activeFrames.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
                if (segments.isEmpty()) {
                    return@withContext TranscriptionResult(emptyList(), activityRatio, usedPlaceholderTokens = false)
                }

                val words = mutableListOf<WordTiming>()
                var wordCounter = 0

                for (segment in segments) {
                    coroutineContext.ensureActive()
                    val onsets = detectWordOnsetsInRegion(energies, segment.first, segment.last + 1)
                    for (i in onsets.indices) {
                        val wStartFrame = onsets[i]
                        val wEndFrame = if (i + 1 < onsets.size) onsets[i + 1] else segment.last + 1
                        val startUs = wStartFrame.toLong() * HOP_MS * CaptionTime.MICROS_PER_MS
                        val endUs = max(
                            startUs + MIN_ALIGNED_WORD_US,
                            wEndFrame.toLong() * HOP_MS * CaptionTime.MICROS_PER_MS,
                        )
                        val durationMs = (endUs - startUs) / CaptionTime.MICROS_PER_MS
                        val markerNumber = wordCounter + 1

                        words.add(
                            WordTiming(
                                id = "${request.clipId}-w-${wordCounter++}",
                                text = "[$markerNumber]",
                                startUs = startUs,
                                endUs = endUs,
                                syllableCount = max(1, (durationMs / 160L).toInt().coerceIn(1, 3)),
                                confidence = computeConfidence(energies, wStartFrame, wEndFrame, maxEnergy),
                                isPlaceholder = true,
                            ),
                        )
                    }
                }

                TranscriptionResult(
                    words = words,
                    detectedActivityRatio = activityRatio,
                    usedPlaceholderTokens = words.isNotEmpty(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }

    private fun parseScriptTokens(providedScript: String?): List<ScriptToken> {
        val raw = providedScript?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()

        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val tokens = mutableListOf<ScriptToken>()
        var pendingBlankLines = 0

        for (lineIndex in lines.indices) {
            val words = lines[lineIndex]
                .trim()
                .split(WHITESPACE_REGEX)
                .filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                pendingBlankLines++
                continue
            }
            if (tokens.isNotEmpty()) {
                val prev = tokens[tokens.lastIndex]
                val extraPause = if (pendingBlankLines > 0) BLANK_LINE_PAUSE_WEIGHT else LINE_BREAK_PAUSE_WEIGHT
                tokens[tokens.lastIndex] = prev.copy(
                    trailingPauseWeight = prev.trailingPauseWeight + extraPause,
                )
            }
            pendingBlankLines = 0

            for (word in words) {
                val syllables = SyllableEstimator.estimateSyllables(word)
                val lastChar = word.lastOrNull()
                val punctuationPause = when (lastChar) {
                    '.', '!', '?' -> SENTENCE_PAUSE_WEIGHT
                    ',', ';', ':' -> CLAUSE_PAUSE_WEIGHT
                    else -> 0f
                }
                tokens.add(
                    ScriptToken(
                        text = word,
                        syllables = syllables,
                        wordWeight = syllables.toFloat(),
                        trailingPauseWeight = punctuationPause,
                    ),
                )
            }
        }
        return tokens
    }

    private fun alignScriptToActiveRegions(
        clipId: String,
        tokens: List<ScriptToken>,
        segments: List<IntRange>,
        energies: FloatArray,
        maxEnergy: Float,
    ): List<WordTiming> {
        val segmentDurationsUs = segments.map { seg ->
            max(MIN_ALIGNED_WORD_US, (seg.last - seg.first + 1).toLong() * HOP_MS * CaptionTime.MICROS_PER_MS)
        }
        val totalActiveUs = segmentDurationsUs.sum().coerceAtLeast(1L)
        val totalWeight = tokens.fold(0.0) { acc, token ->
            acc + token.wordWeight.toDouble() + token.trailingPauseWeight.toDouble()
        }.coerceAtLeast(1.0)

        val result = ArrayList<WordTiming>(tokens.size)
        var cumulativeWeight = 0.0

        for (i in tokens.indices) {
            val token = tokens[i]
            val startActiveOffsetUs = ((cumulativeWeight / totalWeight) * totalActiveUs).roundToLong()
            val wordEndWeight = cumulativeWeight + token.wordWeight.toDouble()
            val endActiveOffsetUs = ((wordEndWeight / totalWeight) * totalActiveUs).roundToLong()
            cumulativeWeight = wordEndWeight + token.trailingPauseWeight.toDouble()

            val startUs = mapActiveOffsetToTimelineUs(startActiveOffsetUs, segments, segmentDurationsUs)
            val rawEndUs = mapActiveOffsetToTimelineUs(endActiveOffsetUs, segments, segmentDurationsUs)
            val endUs = max(startUs + MIN_ALIGNED_WORD_US, rawEndUs)

            val startFrame = (startUs / (HOP_MS * CaptionTime.MICROS_PER_MS)).toInt()
            val endFrame = (endUs / (HOP_MS * CaptionTime.MICROS_PER_MS)).toInt().coerceAtLeast(startFrame + 1)

            result.add(
                WordTiming(
                    id = "$clipId-w-$i",
                    text = token.text,
                    startUs = startUs,
                    endUs = endUs,
                    syllableCount = token.syllables,
                    confidence = computeConfidence(energies, startFrame, endFrame, maxEnergy),
                    isPlaceholder = false,
                ),
            )
        }
        return result
    }

    private fun mapActiveOffsetToTimelineUs(
        activeOffsetUs: Long,
        segments: List<IntRange>,
        segmentDurationsUs: List<Long>,
    ): Long {
        var remaining = activeOffsetUs.coerceAtLeast(0L)
        for (idx in segments.indices) {
            val segDur = segmentDurationsUs[idx]
            val segStartUs = segments[idx].first.toLong() * HOP_MS * CaptionTime.MICROS_PER_MS
            if (remaining <= segDur || idx == segments.lastIndex) {
                return segStartUs + remaining.coerceAtMost(segDur)
            }
            remaining -= segDur
        }
        return 0L
    }

    private fun detectActiveRegions(
        energies: FloatArray,
        threshold: Float,
        bridgeGapMs: Int,
    ): List<IntRange> {
        val rawActive = BooleanArray(energies.size) { idx -> energies[idx] >= threshold }
        val minBridgeFrames = bridgeGapMs / HOP_MS
        var lastTrue = -1
        for (i in rawActive.indices) {
            if (rawActive[i]) {
                if (lastTrue >= 0 && i - lastTrue <= minBridgeFrames) {
                    for (fill in lastTrue + 1 until i) {
                        rawActive[fill] = true
                    }
                }
                lastTrue = i
            }
        }

        val regions = mutableListOf<IntRange>()
        var start = -1
        for (i in rawActive.indices) {
            if (rawActive[i] && start < 0) {
                start = i
            } else if (!rawActive[i] && start >= 0) {
                if ((i - start) * HOP_MS >= MIN_REGION_MS) {
                    regions.add(start until i)
                }
                start = -1
            }
        }
        if (start >= 0 && (rawActive.size - start) * HOP_MS >= MIN_REGION_MS) {
            regions.add(start until rawActive.size)
        }
        return regions
    }

    private fun detectWordOnsetsInRegion(
        energies: FloatArray,
        startFrame: Int,
        endFrameExclusive: Int,
    ): List<Int> {
        val onsets = mutableListOf(startFrame)
        val minWordFrames = MIN_ONSET_SPACING_MS / HOP_MS
        val maxWordFrames = MAX_WORD_SPAN_MS / HOP_MS

        var lastOnset = startFrame
        for (f in (startFrame + 1) until endFrameExclusive) {
            val framesSinceLast = f - lastOnset
            if (framesSinceLast < minWordFrames) continue

            val prevEnergy = energies[f - 1]
            val currEnergy = energies[f]
            val delta = currEnergy - prevEnergy
            val isPeakOnset = delta > max(0.015f, prevEnergy * 0.45f) &&
                (f + 1 >= endFrameExclusive || currEnergy >= energies[f + 1] * 0.9f)

            if (isPeakOnset || framesSinceLast >= maxWordFrames) {
                onsets.add(f)
                lastOnset = f
            }
        }
        return onsets
    }

    private fun computeConfidence(
        energies: FloatArray,
        startFrame: Int,
        endFrame: Int,
        maxEnergy: Float,
    ): Float {
        if (maxEnergy <= 0f) return 0.5f
        var sum = 0f
        var count = 0
        val safeEnd = min(energies.size, endFrame)
        for (i in startFrame.coerceAtLeast(0) until safeEnd) {
            sum += abs(energies[i])
            count++
        }
        val avg = if (count > 0) sum / count else 0f
        return (avg / maxEnergy).coerceIn(0.25f, 0.99f)
    }

    private companion object {
        const val FRAME_MS = 20
        const val HOP_MS = 10
        const val SILENCE_FLOOR = 0.004f
        const val SCRIPT_AUDIBLE_RATIO = 0.08f
        const val BRIDGE_GAP_MS = 140
        const val SCRIPT_BRIDGE_GAP_MS = 650
        const val MIN_REGION_MS = 120
        const val MIN_ONSET_SPACING_MS = 170
        const val MAX_WORD_SPAN_MS = 480
        const val MIN_ALIGNED_WORD_US = 120_000L
        const val CLAUSE_PAUSE_WEIGHT = 0.5f
        const val SENTENCE_PAUSE_WEIGHT = 1.0f
        const val LINE_BREAK_PAUSE_WEIGHT = 1.2f
        const val BLANK_LINE_PAUSE_WEIGHT = 2.5f
        val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
