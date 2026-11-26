package com.projectmediapro.core.model

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One detected silent pause inside a clip's active audio span (relative to clip start, in ms).
 */
data class SilenceSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val selected: Boolean = true,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Tunable parameters for offline RMS silence detection.
 */
data class SilenceDetectionConfig(
    val thresholdDb: Float = DEFAULT_THRESHOLD_DB,
    val minSilenceMs: Long = DEFAULT_MIN_SILENCE_MS,
    val paddingMs: Long = DEFAULT_PADDING_MS,
    val windowMs: Long = DEFAULT_WINDOW_MS,
) {
    fun sanitized(): SilenceDetectionConfig = copy(
        thresholdDb = if (thresholdDb.isNaN() || thresholdDb.isInfinite()) {
            DEFAULT_THRESHOLD_DB
        } else {
            thresholdDb.coerceIn(MIN_THRESHOLD_DB, MAX_THRESHOLD_DB)
        },
        minSilenceMs = minSilenceMs.coerceIn(MIN_SILENCE_MS_FLOOR, MAX_SILENCE_MS_CEILING),
        paddingMs = paddingMs.coerceIn(0L, MAX_PADDING_MS),
        windowMs = windowMs.coerceIn(10L, 100L),
    )

    companion object {
        const val DEFAULT_THRESHOLD_DB: Float = -35f
        const val MIN_THRESHOLD_DB: Float = -50f
        const val MAX_THRESHOLD_DB: Float = -18f
        const val DEFAULT_MIN_SILENCE_MS: Long = 600L
        const val MIN_SILENCE_MS_FLOOR: Long = 250L
        const val MAX_SILENCE_MS_CEILING: Long = 3_000L
        const val DEFAULT_PADDING_MS: Long = 80L
        const val MAX_PADDING_MS: Long = 250L
        const val DEFAULT_WINDOW_MS: Long = 30L
    }
}

/**
 * Maps a contiguous slice inside compacted speech-only PCM back to its original timeline offset.
 */
data class SpeechChunkMapping(
    val packedStartMs: Long,
    val packedEndMs: Long,
    val originalStartMs: Long,
)

/**
 * Speech-only normalized 16 kHz mono PCM ready for `whisper.cpp`, plus timestamp remapping table.
 */
data class CompactedSpeechAudio(
    val floatPcm: FloatArray,
    val mappings: List<SpeechChunkMapping>,
    val originalDurationMs: Long,
    val compactedDurationMs: Long,
) {
    val savedMs: Long
        get() = (originalDurationMs - compactedDurationMs).coerceAtLeast(0L)
}

/**
 * Pure-JVM offline silence detector and speech-only PCM compactor.
 *
 * Scans 16-bit mono PCM in 30 ms sliding RMS windows, converts to dBFS, groups consecutive
 * below-threshold spans longer than `minSilenceMs`, and shrinks each silence inward by `paddingMs`
 * (default 80 ms) so spoken word attacks and tails are never clipped.
 */
object SilenceDetector {

    private const val MIN_SHRUNK_SILENCE_MS: Long = 120L
    private const val MIN_GAP_TO_MERGE_SPEECH_MS: Long = 100L
    private const val FLOOR_DB: Float = -96f
    private const val PCM_FULL_SCALE: Double = 32768.0

    fun detectSilences(
        samples: ShortArray,
        sampleRateHz: Int = 16_000,
        config: SilenceDetectionConfig = SilenceDetectionConfig(),
    ): List<SilenceSegment> {
        if (samples.isEmpty() || sampleRateHz <= 0) return emptyList()
        val cfg = config.sanitized()
        val windowSamples = ((sampleRateHz.toLong() * cfg.windowMs) / 1_000L).toInt().coerceAtLeast(1)
        val totalDurationMs = (samples.size.toLong() * 1_000L) / sampleRateHz.toLong()
        if (totalDurationMs < cfg.minSilenceMs) return emptyList()

        val results = mutableListOf<SilenceSegment>()
        var silentStartMs = -1L
        var offset = 0
        var segmentCounter = 0

        fun flushSilence(rawEndMs: Long) {
            if (silentStartMs < 0L) return
            val rawSpanMs = rawEndMs - silentStartMs
            if (rawSpanMs >= cfg.minSilenceMs) {
                val paddedStart = (silentStartMs + cfg.paddingMs).coerceAtMost(rawEndMs)
                val paddedEnd = (rawEndMs - cfg.paddingMs).coerceAtLeast(paddedStart)
                if (paddedEnd - paddedStart >= MIN_SHRUNK_SILENCE_MS) {
                    segmentCounter += 1
                    results.add(
                        SilenceSegment(
                            id = "sil_$segmentCounter",
                            startMs = paddedStart,
                            endMs = paddedEnd,
                            selected = true,
                        ),
                    )
                }
            }
            silentStartMs = -1L
        }

        while (offset < samples.size) {
            val end = min(offset + windowSamples, samples.size)
            val count = end - offset
            var sumSq = 0.0
            for (i in offset until end) {
                val v = samples[i].toDouble()
                sumSq += v * v
            }
            val rms = sqrt(sumSq / count.toDouble())
            val db = if (rms <= 1e-6) FLOOR_DB else (20.0 * log10(rms / PCM_FULL_SCALE)).toFloat().coerceAtLeast(FLOOR_DB)
            val frameStartMs = (offset.toLong() * 1_000L) / sampleRateHz.toLong()

            if (db < cfg.thresholdDb) {
                if (silentStartMs < 0L) silentStartMs = frameStartMs
            } else {
                flushSilence(frameStartMs)
            }
            offset = end
        }
        flushSilence(totalDurationMs)
        return results
    }

    /**
     * Returns sorted, non-overlapping speech intervals `[startMs, endMs]` after subtracting all
     * `selected == true` silences from `[0, totalDurationMs]`.
     */
    fun computeSpeechIntervals(
        totalDurationMs: Long,
        silences: List<SilenceSegment>,
    ): List<LongRange> {
        if (totalDurationMs <= 0L) return emptyList()
        val activeSilences = silences
            .filter { it.selected && it.endMs > it.startMs }
            .map { it.startMs.coerceIn(0L, totalDurationMs)..it.endMs.coerceIn(0L, totalDurationMs) }
            .filter { it.last > it.first }
            .sortedBy { it.first }

        if (activeSilences.isEmpty()) return listOf(0L..totalDurationMs)

        // Merge overlapping silences first
        val mergedSilences = mutableListOf<LongRange>()
        for (range in activeSilences) {
            val prev = mergedSilences.lastOrNull()
            if (prev != null && range.first <= prev.last) {
                mergedSilences[mergedSilences.lastIndex] = prev.first..max(prev.last, range.last)
            } else {
                mergedSilences.add(range)
            }
        }

        val kept = mutableListOf<LongRange>()
        var cursor = 0L
        for (sil in mergedSilences) {
            if (sil.first > cursor) {
                kept.add(cursor..sil.first)
            }
            cursor = max(cursor, sil.last)
        }
        if (cursor < totalDurationMs) {
            kept.add(cursor..totalDurationMs)
        }

        // Merge tiny gaps (< 100ms) so speech flows naturally
        if (kept.size <= 1) return kept
        val mergedKept = mutableListOf(kept.first())
        for (i in 1 until kept.size) {
            val prev = mergedKept.last()
            val next = kept[i]
            if (next.first - prev.last < MIN_GAP_TO_MERGE_SPEECH_MS) {
                mergedKept[mergedKept.lastIndex] = prev.first..next.last
            } else {
                mergedKept.add(next)
            }
        }
        return mergedKept
    }

    /**
     * Packs only [speechIntervalsMs] into a contiguous normalized FloatArray (`[-1f, 1f]`) for
     * faster `whisper.cpp` inference, returning the exact timestamp-remapping table.
     */
    fun compactSpeechPcm(
        samples: ShortArray,
        sampleRateHz: Int = 16_000,
        speechIntervalsMs: List<LongRange>,
    ): CompactedSpeechAudio {
        if (samples.isEmpty() || sampleRateHz <= 0 || speechIntervalsMs.isEmpty()) {
            return CompactedSpeechAudio(FloatArray(0), emptyList(), 0L, 0L)
        }
        val originalDurationMs = (samples.size.toLong() * 1_000L) / sampleRateHz.toLong()
        val sampleSlices = mutableListOf<Triple<Int, Int, Long>>()
        var totalPackedSamples = 0

        for (interval in speechIntervalsMs) {
            val startIdx = ((interval.first * sampleRateHz.toLong()) / 1_000L).toInt().coerceIn(0, samples.size)
            val endIdx = ((interval.last * sampleRateHz.toLong()) / 1_000L).toInt().coerceIn(startIdx, samples.size)
            val len = endIdx - startIdx
            if (len > 0) {
                sampleSlices.add(Triple(startIdx, endIdx, interval.first))
                totalPackedSamples += len
            }
        }

        val packed = FloatArray(totalPackedSamples)
        val mappings = mutableListOf<SpeechChunkMapping>()
        var writeCursor = 0

        for ((startIdx, endIdx, origStartMs) in sampleSlices) {
            val packedStartMs = (writeCursor.toLong() * 1_000L) / sampleRateHz.toLong()
            for (i in startIdx until endIdx) {
                packed[writeCursor++] = (samples[i].toFloat() / 32768f).coerceIn(-1f, 1f)
            }
            val packedEndMs = (writeCursor.toLong() * 1_000L) / sampleRateHz.toLong()
            mappings.add(
                SpeechChunkMapping(
                    packedStartMs = packedStartMs,
                    packedEndMs = packedEndMs,
                    originalStartMs = origStartMs,
                ),
            )
        }

        val compactedDurationMs = (writeCursor.toLong() * 1_000L) / sampleRateHz.toLong()
        return CompactedSpeechAudio(
            floatPcm = packed,
            mappings = mappings,
            originalDurationMs = originalDurationMs,
            compactedDurationMs = compactedDurationMs,
        )
    }

    /**
     * Remaps a timestamp returned by Whisper on compacted speech-only PCM back to the original
     * uncompacted audio timeline in milliseconds.
     */
    fun remapPackedTimeMs(
        packedMs: Long,
        mappings: List<SpeechChunkMapping>,
    ): Long {
        if (mappings.isEmpty()) return packedMs.coerceAtLeast(0L)
        val clamped = packedMs.coerceAtLeast(0L)
        for (m in mappings) {
            if (clamped <= m.packedEndMs) {
                val offsetInChunk = (clamped - m.packedStartMs).coerceAtLeast(0L)
                return (m.originalStartMs + offsetInChunk).coerceAtLeast(0L)
            }
        }
        val last = mappings.last()
        val lastSpan = (last.packedEndMs - last.packedStartMs).coerceAtLeast(0L)
        return (last.originalStartMs + lastSpan + (clamped - last.packedEndMs).coerceAtLeast(0L)).coerceAtLeast(0L)
    }
}
