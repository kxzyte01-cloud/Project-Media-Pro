package com.projectmediapro.feature.captions

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object SyllableEstimator {
    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y')

    fun estimateSyllables(word: String): Int {
        val cleaned = word.lowercase(Locale.US).filter { it.isLetter() }
        if (cleaned.isEmpty()) return 1

        var count = 0
        var prevWasVowel = false
        for (ch in cleaned) {
            val isVowel = ch in VOWELS
            if (isVowel && !prevWasVowel) {
                count++
            }
            prevWasVowel = isVowel
        }
        if (cleaned.endsWith("e") && count > 1 && !cleaned.endsWith("le")) {
            count--
        }
        return max(1, count)
    }
}

object TimelineMapper {
    fun mapClipWordsToTimeline(
        words: List<WordTiming>,
        clipStartMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        playbackSpeed: Float = 1.0f,
    ): List<WordTiming> {
        val safeSpeed = if (playbackSpeed > 0f) playbackSpeed else 1.0f
        val clipStartUs = max(0L, clipStartMs) * CaptionTime.MICROS_PER_MS
        val trimStartUs = max(0L, trimStartMs) * CaptionTime.MICROS_PER_MS
        val hasTrimEnd = trimEndMs > trimStartMs
        val trimEndUs = if (hasTrimEnd) trimEndMs * CaptionTime.MICROS_PER_MS else Long.MAX_VALUE

        return words.mapNotNull { word ->
            if (word.endUs <= trimStartUs || word.startUs >= trimEndUs) {
                return@mapNotNull null
            }
            val clampedStart = max(word.startUs, trimStartUs) - trimStartUs
            val clampedEnd = min(word.endUs, trimEndUs) - trimStartUs
            if (clampedEnd <= clampedStart) return@mapNotNull null

            val timelineStartUs = clipStartUs + (clampedStart / safeSpeed).roundToLong()
            val timelineEndUs = max(
                timelineStartUs + CaptionTime.MIN_DURATION_US,
                clipStartUs + (clampedEnd / safeSpeed).roundToLong(),
            )
            word.copy(
                startUs = timelineStartUs,
                endUs = timelineEndUs,
            )
        }
    }
}

object LyricsPacingEngine {
    private const val MIN_HARD_WORD_US = 50_000L
    private val SENTENCE_END_CHARS = setOf('.', '!', '?')

    fun buildLines(
        rawWords: List<WordTiming>,
        preset: LyricsStylePreset,
    ): List<CaptionLine> {
        val normalized = normalizeAndClampWords(rawWords, preset)
        if (normalized.isEmpty()) return emptyList()

        val groups = mutableListOf<MutableList<WordTiming>>()
        var currentGroup = mutableListOf<WordTiming>()
        var currentChars = 0

        for (word in normalized) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(word)
                currentChars = word.text.length
                continue
            }

            val prevWord = currentGroup.last()
            val gapUs = word.startUs - prevWord.endUs
            val projectedChars = currentChars + 1 + word.text.length
            val prevEndsSentence = prevWord.text.lastOrNull() in SENTENCE_END_CHARS

            val exceedsWords = currentGroup.size >= preset.maxWordsPerLine
            val exceedsChars = projectedChars > preset.maxCharsPerLine
            val exceedsGap = gapUs > preset.maxLineGapUs

            if (exceedsWords || exceedsChars || exceedsGap || prevEndsSentence) {
                groups.add(currentGroup)
                currentGroup = mutableListOf(word)
                currentChars = word.text.length
            } else {
                currentGroup.add(word)
                currentChars = projectedChars
            }
        }
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }

        if (preset.joinShortOrphans && groups.size >= 2) {
            var i = 1
            while (i < groups.size) {
                val tail = groups[i]
                val prev = groups[i - 1]
                if (tail.size == 1 && prev.size < preset.maxWordsPerLine + 1) {
                    val gapUs = tail.first().startUs - prev.last().endUs
                    val combinedChars = prev.sumOf { it.text.length + 1 } + tail.first().text.length
                    if (gapUs <= preset.maxLineGapUs * 2 && combinedChars <= preset.maxCharsPerLine + 6) {
                        prev.add(tail.first())
                        groups.removeAt(i)
                        continue
                    }
                }
                i++
            }
        }

        return enforceNonOverlappingLines(groups, preset)
    }

    fun rebuildWithUpdatedWord(
        document: CaptionDocument,
        wordId: String,
        newText: String,
    ): CaptionDocument {
        val trimmed = newText.trim()
        val updatedRaw = if (trimmed.isEmpty()) {
            document.rawWords.filterNot { it.id == wordId }
        } else {
            document.rawWords.map { word ->
                if (word.id == wordId) {
                    word.copy(
                        text = trimmed,
                        syllableCount = SyllableEstimator.estimateSyllables(trimmed),
                        isPlaceholder = false,
                    )
                } else {
                    word
                }
            }
        }
        val preset = LyricsPresetRegistry.byId(document.presetId)
        return document.copy(
            rawWords = updatedRaw,
            lines = buildLines(updatedRaw, preset),
        )
    }

    private fun normalizeAndClampWords(
        rawWords: List<WordTiming>,
        preset: LyricsStylePreset,
    ): List<WordTiming> {
        val sorted = rawWords
            .asSequence()
            .mapNotNull { word ->
                val text = word.text.trim()
                if (text.isEmpty()) return@mapNotNull null
                val startUs = max(0L, word.startUs)
                val endUs = max(startUs + CaptionTime.MIN_DURATION_US, word.endUs)
                val syllables = max(1, word.syllableCount)
                word.copy(
                    text = text,
                    startUs = startUs,
                    endUs = endUs,
                    syllableCount = syllables,
                )
            }
            .sortedWith(compareBy<WordTiming>({ it.startUs }, { it.endUs }))
            .toList()

        if (sorted.isEmpty()) return emptyList()

        val result = ArrayList<WordTiming>(sorted.size)
        var prevEndUs = 0L

        for (index in sorted.indices) {
            val current = sorted[index]
            val startUs = max(current.startUs, prevEndUs)
            val densityFloorUs = current.syllableCount.toLong() * preset.syllableDensityFloorUsPerSyllable
            val minRequiredUs = max(preset.minWordDurationUs, densityFloorUs)
            val desiredEndUs = max(current.endUs, startUs + minRequiredUs)

            val nextRawStartUs = if (index + 1 < sorted.size) sorted[index + 1].startUs else Long.MAX_VALUE
            val hardFloorEndUs = startUs + min(MIN_HARD_WORD_US, preset.minWordDurationUs)
            val cappedEndUs = if (nextRawStartUs < Long.MAX_VALUE) {
                val availableBudget = max(hardFloorEndUs, nextRawStartUs)
                min(desiredEndUs, availableBudget)
            } else {
                desiredEndUs
            }

            val finalEndUs = max(startUs + CaptionTime.MIN_DURATION_US, cappedEndUs)
            result.add(current.copy(startUs = startUs, endUs = finalEndUs))
            prevEndUs = finalEndUs
        }

        return result
    }

    private fun enforceNonOverlappingLines(
        groups: List<List<WordTiming>>,
        preset: LyricsStylePreset,
    ): List<CaptionLine> {
        val lines = ArrayList<CaptionLine>(groups.size)
        for (index in groups.indices) {
            val words = groups[index]
            val lineStartUs = words.first().startUs
            val naturalEndUs = words.last().endUs
            val minEndUs = lineStartUs + preset.minLineDurationUs
            val desiredEndUs = max(naturalEndUs, minEndUs)

            val nextLineStartUs = if (index + 1 < groups.size) {
                groups[index + 1].first().startUs
            } else {
                Long.MAX_VALUE
            }
            val clampedEndUs = if (nextLineStartUs < Long.MAX_VALUE) {
                max(naturalEndUs, min(desiredEndUs, nextLineStartUs))
            } else {
                desiredEndUs
            }

            lines.add(
                CaptionLine(
                    id = "line-$index-${words.first().id}",
                    words = words,
                    startUs = lineStartUs,
                    endUs = max(lineStartUs + CaptionTime.MIN_DURATION_US, clampedEndUs),
                ),
            )
        }
        return lines
    }
}
