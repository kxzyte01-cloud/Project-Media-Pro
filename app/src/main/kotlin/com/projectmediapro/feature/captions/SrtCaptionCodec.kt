package com.projectmediapro.feature.captions

import java.util.Locale
import kotlin.math.max

object SrtCaptionCodec {
    private val TIMECODE_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""",
    )
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun parseSrt(
        srtText: String,
        projectId: String,
        preset: LyricsStylePreset = LyricsPresetRegistry.SPEECH,
        style: CaptionStyle = CaptionStyle.default(),
    ): CaptionDocument {
        val cleaned = srtText.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val blocks = cleaned.split(Regex("""\n\s*\n"""))
        val rawWords = mutableListOf<WordTiming>()
        var wordCounter = 0

        for (block in blocks) {
            val blockLines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (blockLines.isEmpty()) continue

            val timeLineIndex = blockLines.indexOfFirst { TIMECODE_REGEX.containsMatchIn(it) }
            if (timeLineIndex < 0) continue

            val match = TIMECODE_REGEX.find(blockLines[timeLineIndex]) ?: continue
            val startUs = parseTimeUs(
                hours = match.groupValues[1].toLong(),
                minutes = match.groupValues[2].toLong(),
                seconds = match.groupValues[3].toLong(),
                millisRaw = match.groupValues[4],
            )
            val endUs = parseTimeUs(
                hours = match.groupValues[5].toLong(),
                minutes = match.groupValues[6].toLong(),
                seconds = match.groupValues[7].toLong(),
                millisRaw = match.groupValues[8],
            )
            if (endUs <= startUs) continue

            val cueText = blockLines
                .drop(timeLineIndex + 1)
                .joinToString(" ")
                .replace(HTML_TAG_REGEX, "")
                .trim()
            if (cueText.isEmpty()) continue

            val tokens = cueText.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue

            val syllables = tokens.map { SyllableEstimator.estimateSyllables(it) }
            val totalWeight = syllables.sum().coerceAtLeast(1)
            val cueDurationUs = endUs - startUs
            var cursorUs = startUs

            tokens.forEachIndexed { idx, token ->
                val weight = syllables[idx]
                val wordEndUs = if (idx == tokens.lastIndex) {
                    max(cursorUs + CaptionTime.MIN_DURATION_US, endUs)
                } else {
                    val shareUs = (cueDurationUs * weight) / totalWeight
                    max(cursorUs + CaptionTime.MIN_DURATION_US, (cursorUs + shareUs).coerceAtMost(endUs))
                }
                rawWords.add(
                    WordTiming(
                        id = "srt-w-${wordCounter++}",
                        text = token,
                        startUs = cursorUs,
                        endUs = wordEndUs,
                        syllableCount = weight,
                        confidence = 1.0f,
                        isPlaceholder = false,
                    ),
                )
                cursorUs = wordEndUs
            }
        }

        val lines = LyricsPacingEngine.buildLines(rawWords, preset)
        return CaptionDocument(
            projectId = projectId,
            rawWords = rawWords,
            lines = lines,
            presetId = preset.id,
            globalOffsetUs = 0L,
            style = style,
        )
    }

    fun exportSrt(document: CaptionDocument): String {
        if (document.lines.isEmpty()) return ""
        val sb = StringBuilder()
        var index = 1
        for (line in document.lines) {
            val shiftedStartUs = max(0L, line.startUs + document.globalOffsetUs)
            val shiftedEndUs = max(shiftedStartUs + CaptionTime.MIN_DURATION_US, line.endUs + document.globalOffsetUs)
            val text = line.words.joinToString(" ") { it.text }.trim()
            if (text.isEmpty()) continue

            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(index++).append('\n')
            sb.append(formatTimeUs(shiftedStartUs))
                .append(" --> ")
                .append(formatTimeUs(shiftedEndUs))
                .append('\n')
            sb.append(text).append('\n')
        }
        return sb.toString()
    }

    private fun parseTimeUs(
        hours: Long,
        minutes: Long,
        seconds: Long,
        millisRaw: String,
    ): Long {
        val millis = millisRaw.padEnd(3, '0').take(3).toLong()
        val totalMs = (((hours * 60L + minutes) * 60L) + seconds) * 1_000L + millis
        return totalMs * CaptionTime.MICROS_PER_MS
    }

    private fun formatTimeUs(timeUs: Long): String {
        val totalMs = max(0L, timeUs) / CaptionTime.MICROS_PER_MS
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs % 3_600_000L) / 60_000L
        val seconds = (totalMs % 60_000L) / 1_000L
        val millis = totalMs % 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
