package com.projectmediapro.feature.captions

import java.util.Locale
import kotlin.math.max

object SrtCaptionCodec {
    private val TIMECODE_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""",
    )
    private val LRC_TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val LRC_OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val LRC_LENGTH_REGEX = Regex("""\[length:\s*(\d{1,3}):(\d{2})\s*]""", RegexOption.IGNORE_CASE)
    private val LRC_META_TAG_REGEX = Regex("""^\[[a-zA-Z]{2,10}:[^\]]*]$""")
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private const val LRC_DEFAULT_TAIL_DURATION_US = 3_500_000L
    private const val LRC_LINE_GAP_US = 40_000L

    fun isSrtFormat(text: String): Boolean = TIMECODE_REGEX.containsMatchIn(text)

    fun isLrcFormat(text: String): Boolean = LRC_TIMESTAMP_REGEX.containsMatchIn(text)

    fun parseSrt(
        srtText: String,
        projectId: String,
        preset: LyricsStylePreset = LyricsPresetRegistry.SPEECH,
        style: CaptionStyle = CaptionStyle.default(),
    ): CaptionDocument {
        if (!isSrtFormat(srtText) && isLrcFormat(srtText)) {
            return parseLrc(
                lrcText = srtText,
                projectId = projectId,
                preset = preset,
                style = style,
            )
        }

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

            wordCounter = appendTimedCueWords(
                cueText = cueText,
                startUs = startUs,
                endUs = endUs,
                idPrefix = "srt-w-",
                startCounter = wordCounter,
                outWords = rawWords,
            )
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

    /**
     * Parses standard synced LRC lyrics (`[mm:ss.xx]Line text`), stripping metadata headers
     * (`[ar:]`, `[ti:]`, `[al:]`, `[id:]`, `[length:]`), honoring `[offset:]`, and using empty
     * timestamp lines (`[00:34.37]`) as exact end markers for the preceding lyric line.
     */
    fun parseLrc(
        lrcText: String,
        projectId: String,
        preset: LyricsStylePreset = LyricsPresetRegistry.SPEECH,
        style: CaptionStyle = CaptionStyle.default(),
    ): CaptionDocument {
        val cleaned = lrcText.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val offsetMs = LRC_OFFSET_REGEX.find(cleaned)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val shiftUs = -offsetMs * CaptionTime.MICROS_PER_MS
        val lengthUs = LRC_LENGTH_REGEX.find(cleaned)?.let { match ->
            val mins = match.groupValues[1].toLongOrNull() ?: 0L
            val secs = match.groupValues[2].toLongOrNull() ?: 0L
            (mins * 60L + secs) * CaptionTime.MICROS_PER_SEC
        }

        data class TimedEntry(val timeUs: Long, val text: String)
        val entries = mutableListOf<TimedEntry>()

        for (rawLine in cleaned.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || LRC_META_TAG_REGEX.matches(trimmed)) continue

            val matches = LRC_TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val lyricText = LRC_TIMESTAMP_REGEX.replace(trimmed, "")
                .replace(HTML_TAG_REGEX, "")
                .trim()
            for (match in matches) {
                val mins = match.groupValues[1].toLongOrNull() ?: continue
                val secs = match.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = match.groupValues[3]
                val millis = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100L
                    2 -> fracRaw.toLong() * 10L
                    else -> fracRaw.take(3).toLong()
                }
                val rawTimeUs = ((mins * 60L + secs) * 1_000L + millis) * CaptionTime.MICROS_PER_MS
                val shiftedTimeUs = max(0L, rawTimeUs + shiftUs)
                entries.add(TimedEntry(timeUs = shiftedTimeUs, text = lyricText))
            }
        }

        if (entries.isEmpty()) {
            return CaptionDocument(
                projectId = projectId,
                rawWords = emptyList(),
                lines = emptyList(),
                presetId = preset.id,
                globalOffsetUs = 0L,
                style = style,
            )
        }

        val sorted = entries.sortedBy { it.timeUs }
        val rawWords = mutableListOf<WordTiming>()
        var wordCounter = 0

        for (i in sorted.indices) {
            val entry = sorted[i]
            if (entry.text.isEmpty()) continue

            val nextEntry = sorted.drop(i + 1).firstOrNull { it.timeUs > entry.timeUs }
            val endUs = when {
                nextEntry != null -> {
                    if (nextEntry.text.isEmpty()) {
                        nextEntry.timeUs
                    } else {
                        max(entry.timeUs + CaptionTime.MIN_DURATION_US, nextEntry.timeUs - LRC_LINE_GAP_US)
                    }
                }
                lengthUs != null && lengthUs > entry.timeUs -> {
                    minOf(lengthUs, entry.timeUs + LRC_DEFAULT_TAIL_DURATION_US)
                }
                else -> entry.timeUs + LRC_DEFAULT_TAIL_DURATION_US
            }

            wordCounter = appendTimedCueWords(
                cueText = entry.text,
                startUs = entry.timeUs,
                endUs = endUs,
                idPrefix = "lrc-w-",
                startCounter = wordCounter,
                outWords = rawWords,
            )
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

    private fun appendTimedCueWords(
        cueText: String,
        startUs: Long,
        endUs: Long,
        idPrefix: String,
        startCounter: Int,
        outWords: MutableList<WordTiming>,
    ): Int {
        val tokens = cueText.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return startCounter

        val syllables = tokens.map { SyllableEstimator.estimateSyllables(it) }
        val totalWeight = syllables.sum().coerceAtLeast(1)
        val cueDurationUs = max(CaptionTime.MIN_DURATION_US, endUs - startUs)
        var cursorUs = startUs
        var counter = startCounter

        tokens.forEachIndexed { idx, token ->
            val weight = syllables[idx]
            val wordEndUs = if (idx == tokens.lastIndex) {
                max(cursorUs + CaptionTime.MIN_DURATION_US, endUs)
            } else {
                val shareUs = (cueDurationUs * weight) / totalWeight
                max(cursorUs + CaptionTime.MIN_DURATION_US, (cursorUs + shareUs).coerceAtMost(endUs))
            }
            outWords.add(
                WordTiming(
                    id = "$idPrefix${counter++}",
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
        return counter
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
