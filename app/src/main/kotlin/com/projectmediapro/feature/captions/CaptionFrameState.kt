package com.projectmediapro.feature.captions

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class WordRenderState(
    val wordId: String,
    val displayText: String,
    val visibleCharCount: Int,
    val fillProgress: Float,
    val scale: Float,
    val alpha: Float,
    val isActive: Boolean,
    val isSpoken: Boolean,
)

data class CaptionFrameState(
    val presentationTimeUs: Long,
    val adjustedTimeUs: Long,
    val activeLineId: String?,
    val words: List<WordRenderState>,
    val lineAlpha: Float,
    val showCursor: Boolean,
    val style: CaptionStyle,
    val activeLineIndex: Int = 0,
    val totalLines: Int = 0,
    val previousLineText: String? = null,
    val nextLineText: String? = null,
    val songProgressFraction: Float = 0f,
) {
    val hasVisibleContent: Boolean
        get() = activeLineId != null && words.isNotEmpty() && lineAlpha > 0.001f

    companion object {
        fun empty(presentationTimeUs: Long, adjustedTimeUs: Long, style: CaptionStyle): CaptionFrameState =
            CaptionFrameState(
                presentationTimeUs = presentationTimeUs,
                adjustedTimeUs = adjustedTimeUs,
                activeLineId = null,
                words = emptyList(),
                lineAlpha = 0f,
                showCursor = false,
                style = style,
            )
    }
}

object CaptionFrameEvaluator {
    private const val FADE_WINDOW_US = 65_000L

    fun evaluate(
        document: CaptionDocument?,
        presentationTimeUs: Long,
    ): CaptionFrameState {
        if (document == null || document.lines.isEmpty()) {
            return CaptionFrameState.empty(
                presentationTimeUs = presentationTimeUs,
                adjustedTimeUs = presentationTimeUs,
                style = document?.style ?: CaptionStyle.default(),
            )
        }

        // Positive globalOffsetUs shifts captions later in timeline time.
        val adjustedTimeUs = presentationTimeUs - document.globalOffsetUs
        val activeIndex = findActiveLineIndex(document.lines, adjustedTimeUs)
        if (activeIndex < 0) {
            return CaptionFrameState.empty(presentationTimeUs, adjustedTimeUs, document.style)
        }
        val activeLine = document.lines[activeIndex]

        val template = CaptionTemplateRegistry.byId(document.style.templateId)
        val lineAlpha = computeLineAlpha(activeLine, adjustedTimeUs)
        var anyActiveWord = false

        val forceUppercase = document.style.uppercase ||
            document.style.templateId == CaptionTemplateId.KINETIC_GRUNGE

        val wordStates = activeLine.words.map { word ->
            val state = evaluateWord(
                word = word,
                adjustedTimeUs = adjustedTimeUs,
                template = template,
                uppercase = forceUppercase,
            )
            if (state.isActive) {
                anyActiveWord = true
            }
            state
        }

        val showCursor = document.style.templateId == CaptionTemplateId.TYPEWRITER && anyActiveWord
        val prevText = document.lines.getOrNull(activeIndex - 1)?.let { line ->
            formatLinePreview(line, forceUppercase)
        }
        val nextText = document.lines.getOrNull(activeIndex + 1)?.let { line ->
            formatLinePreview(line, forceUppercase)
        }
        val songStartUs = document.lines.first().startUs
        val songEndUs = document.lines.last().endUs
        val songSpanUs = max(1L, songEndUs - songStartUs)
        val songProgress = ((adjustedTimeUs - songStartUs).toFloat() / songSpanUs.toFloat())
            .coerceIn(0f, 1f)

        return CaptionFrameState(
            presentationTimeUs = presentationTimeUs,
            adjustedTimeUs = adjustedTimeUs,
            activeLineId = activeLine.id,
            words = wordStates,
            lineAlpha = lineAlpha,
            showCursor = showCursor,
            style = document.style,
            activeLineIndex = activeIndex,
            totalLines = document.lines.size,
            previousLineText = prevText,
            nextLineText = nextText,
            songProgressFraction = songProgress,
        )
    }

    internal fun findActiveLine(lines: List<CaptionLine>, timeUs: Long): CaptionLine? {
        val index = findActiveLineIndex(lines, timeUs)
        return if (index >= 0) lines[index] else null
    }

    internal fun findActiveLineIndex(lines: List<CaptionLine>, timeUs: Long): Int {
        if (lines.isEmpty() || timeUs < lines.first().startUs || timeUs >= lines.last().endUs) {
            return -1
        }
        var low = 0
        var high = lines.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val candidate = lines[mid]
            when {
                timeUs < candidate.startUs -> high = mid - 1
                timeUs >= candidate.endUs -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    private fun formatLinePreview(line: CaptionLine, uppercase: Boolean): String =
        line.words.joinToString(" ") { word ->
            if (uppercase) word.text.uppercase(Locale.US) else word.text
        }

    private fun evaluateWord(
        word: WordTiming,
        adjustedTimeUs: Long,
        template: CaptionTemplate,
        uppercase: Boolean,
    ): WordRenderState {
        val displayText = if (uppercase) word.text.uppercase(Locale.US) else word.text
        val durationUs = max(1L, word.endUs - word.startUs)
        val rawProgress = ((adjustedTimeUs - word.startUs).toFloat() / durationUs.toFloat())
            .coerceIn(0f, 1f)
        val isSpoken = adjustedTimeUs >= word.endUs
        val isActive = adjustedTimeUs in word.startUs until word.endUs

        return when (template.id) {
            CaptionTemplateId.KARAOKE_HIGHLIGHT,
            CaptionTemplateId.MUSIC_PLAYER,
            -> WordRenderState(
                wordId = word.id,
                displayText = displayText,
                visibleCharCount = displayText.length,
                fillProgress = if (isSpoken) 1f else if (isActive) rawProgress else 0f,
                scale = 1f + activePulse(isActive, rawProgress, template.activeWordScaleBoost),
                alpha = 1f,
                isActive = isActive,
                isSpoken = isSpoken,
            )

            CaptionTemplateId.POP_IN,
            CaptionTemplateId.KINETIC_GRUNGE,
            CaptionTemplateId.NEON_GLOW_STACK,
            -> {
                if (adjustedTimeUs < word.startUs) {
                    WordRenderState(
                        wordId = word.id,
                        displayText = displayText,
                        visibleCharCount = 0,
                        fillProgress = 0f,
                        scale = 0.65f,
                        alpha = 0f,
                        isActive = false,
                        isSpoken = false,
                    )
                } else {
                    val entranceUs = min(template.entranceDurationUs, durationUs)
                    val t = ((adjustedTimeUs - word.startUs).toFloat() / max(1L, entranceUs).toFloat())
                        .coerceIn(0f, 1f)
                    val popScale = 0.65f + 0.35f * easeOutBack(t, overshoot = template.popScaleFactor)
                    WordRenderState(
                        wordId = word.id,
                        displayText = displayText,
                        visibleCharCount = displayText.length,
                        fillProgress = if (isActive || isSpoken) 1f else 0f,
                        scale = popScale + activePulse(isActive, rawProgress, template.activeWordScaleBoost),
                        alpha = min(1f, t * 2.4f),
                        isActive = isActive,
                        isSpoken = isSpoken,
                    )
                }
            }

            CaptionTemplateId.EDITORIAL_SCRATCH -> {
                val entranceUs = min(template.entranceDurationUs, durationUs)
                val t = if (adjustedTimeUs < word.startUs) {
                    0f
                } else {
                    ((adjustedTimeUs - word.startUs).toFloat() / max(1L, entranceUs).toFloat())
                        .coerceIn(0f, 1f)
                }
                val settleScale = if (adjustedTimeUs < word.startUs) {
                    0.94f
                } else {
                    0.94f + 0.06f * easeOutBack(t, overshoot = template.popScaleFactor)
                }
                WordRenderState(
                    wordId = word.id,
                    displayText = displayText,
                    visibleCharCount = displayText.length,
                    fillProgress = if (isActive) 1f else 0f,
                    scale = settleScale + activePulse(isActive, rawProgress, template.activeWordScaleBoost),
                    alpha = if (adjustedTimeUs < word.startUs) 0.58f else 1f,
                    isActive = isActive,
                    isSpoken = isSpoken,
                )
            }

            CaptionTemplateId.TYPEWRITER -> {
                val hasStarted = adjustedTimeUs >= word.startUs
                val visibleChars = when {
                    !hasStarted -> 0
                    isSpoken -> displayText.length
                    else -> max(1, ceil(displayText.length * rawProgress).toInt())
                        .coerceAtMost(displayText.length)
                }
                WordRenderState(
                    wordId = word.id,
                    displayText = displayText,
                    visibleCharCount = visibleChars,
                    fillProgress = if (isActive) 1f else 0f,
                    scale = 1f,
                    alpha = if (hasStarted) 1f else 0f,
                    isActive = isActive,
                    isSpoken = isSpoken,
                )
            }

            CaptionTemplateId.CLEAN_SUBTITLE -> WordRenderState(
                wordId = word.id,
                displayText = displayText,
                visibleCharCount = displayText.length,
                fillProgress = 0f,
                scale = 1f,
                alpha = 1f,
                isActive = isActive,
                isSpoken = isSpoken,
            )
        }
    }

    private fun activePulse(isActive: Boolean, rawProgress: Float, boost: Float): Float =
        if (isActive && boost > 0f) boost * (1f - abs(rawProgress * 2f - 1f)) else 0f

    private fun computeLineAlpha(line: CaptionLine, adjustedTimeUs: Long): Float {
        val lineDurationUs = max(1L, line.endUs - line.startUs)
        val fadeUs = min(FADE_WINDOW_US, lineDurationUs / 4L)
        if (fadeUs <= 0L) return 1f
        val fadeIn = ((adjustedTimeUs - line.startUs).toFloat() / fadeUs.toFloat()).coerceIn(0f, 1f)
        val fadeOut = ((line.endUs - adjustedTimeUs).toFloat() / fadeUs.toFloat()).coerceIn(0f, 1f)
        return min(fadeIn, fadeOut)
    }

    private fun easeOutBack(t: Float, overshoot: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val c1 = (overshoot - 1f) * 6.5f
        val c3 = c1 + 1f
        val shifted = clamped - 1f
        return 1f + c3 * shifted * shifted * shifted + c1 * shifted * shifted
    }
}
