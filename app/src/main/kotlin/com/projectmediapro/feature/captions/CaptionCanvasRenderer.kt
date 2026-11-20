package com.projectmediapro.feature.captions

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min

object CaptionCanvasRenderer {
    private const val BASE_FONT_RATIO = 0.062f
    private const val MAX_LINE_WIDTH_RATIO = 0.86f
    private const val LINE_SPACING_MULTIPLIER = 1.24f
    private const val EDGE_MARGIN_RATIO = 0.05f

    private val typefaceCache = EnumMap<CaptionFontFamily, Typeface>(CaptionFontFamily::class.java)

    // Media3 CanvasOverlay draws on a GL thread while Compose draws on the UI thread;
    // ThreadLocal scratch paints avoid both per-frame allocations and cross-thread contention.
    private class RenderScratch {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        val boxRect = RectF()
    }

    private val scratchLocal = object : ThreadLocal<RenderScratch>() {
        override fun initialValue(): RenderScratch = RenderScratch()
    }

    private data class MeasuredWord(
        val state: WordRenderState,
        val fullWidth: Float,
        val visibleText: String,
    )

    private data class WrappedLine(
        val words: List<MeasuredWord>,
        val totalWidth: Float,
    )

    fun render(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        frameState: CaptionFrameState,
    ) {
        if (!frameState.hasVisibleContent || widthPx <= 1f || heightPx <= 1f) return

        val scratch = scratchLocal.get() ?: RenderScratch()
        val style = frameState.style
        val minDim = min(widthPx, heightPx)
        val fontPx = minDim * BASE_FONT_RATIO * style.fontSizeScale.coerceIn(CaptionStyleBounds.FONT_SCALE_RANGE)
        val typeface = resolveTypeface(style.fontFamily)

        val fillPaint = scratch.fillPaint.apply {
            textSize = fontPx
            this.typeface = typeface
            clearShadowLayer()
        }
        val strokePaint = scratch.strokePaint.apply {
            textSize = fontPx
            this.typeface = typeface
            strokeWidth = fontPx * style.strokeWidthFraction.coerceIn(CaptionStyleBounds.STROKE_WIDTH_RANGE)
        }

        val spaceWidth = fillPaint.measureText(" ")
        val maxContentWidth = widthPx * MAX_LINE_WIDTH_RATIO

        val measuredWords = frameState.words.map { word ->
            MeasuredWord(
                state = word,
                fullWidth = fillPaint.measureText(word.displayText),
                visibleText = word.displayText.take(word.visibleCharCount),
            )
        }

        val wrappedLines = wrapWords(measuredWords, spaceWidth, maxContentWidth)
        if (wrappedLines.isEmpty()) return

        val fontMetrics = fillPaint.fontMetrics
        val lineHeight = (fontMetrics.descent - fontMetrics.ascent) * LINE_SPACING_MULTIPLIER
        val totalBlockHeight = lineHeight * wrappedLines.size
        val maxBlockWidth = wrappedLines.maxOf { it.totalWidth }

        val edgeMargin = minDim * EDGE_MARGIN_RATIO
        val halfBound = maxBlockWidth / 2f + edgeMargin
        val centerX = (widthPx * style.position.xFraction.coerceIn(CaptionStyleBounds.POSITION_X_RANGE))
            .coerceIn(halfBound, max(halfBound, widthPx - halfBound))
        val desiredCenterY = heightPx * style.position.yFraction.coerceIn(CaptionStyleBounds.POSITION_Y_RANGE)
        val minCenterY = totalBlockHeight / 2f + edgeMargin
        val maxCenterY = max(minCenterY, heightPx - totalBlockHeight / 2f - edgeMargin)
        val centerY = desiredCenterY.coerceIn(minCenterY, maxCenterY)
        val blockTop = centerY - totalBlockHeight / 2f

        if (style.backgroundBoxEnabled) {
            drawBackgroundBox(
                canvas = canvas,
                scratch = scratch,
                centerX = centerX,
                blockTop = blockTop,
                blockWidth = maxBlockWidth,
                blockHeight = totalBlockHeight,
                fontPx = fontPx,
                style = style,
                lineAlpha = frameState.lineAlpha,
            )
        }

        val baselineOffset = -fontMetrics.ascent + (lineHeight - (fontMetrics.descent - fontMetrics.ascent)) / 2f

        wrappedLines.forEachIndexed { lineIdx, line ->
            val lineBaselineY = blockTop + lineIdx * lineHeight + baselineOffset
            var cursorX = centerX - line.totalWidth / 2f

            line.words.forEachIndexed { wordIdx, measured ->
                val wordState = measured.state
                if (wordState.alpha > 0.001f && measured.visibleText.isNotEmpty()) {
                    val wordCenterX = cursorX + measured.fullWidth / 2f
                    val wordCenterY = lineBaselineY + (fontMetrics.ascent + fontMetrics.descent) / 2f
                    val combinedAlpha = (frameState.lineAlpha * wordState.alpha).coerceIn(0f, 1f)

                    canvas.save()
                    if (wordState.scale != 1f) {
                        canvas.scale(wordState.scale, wordState.scale, wordCenterX, wordCenterY)
                    }

                    val textToDraw = if (frameState.showCursor && wordState.isActive) {
                        "${measured.visibleText}|"
                    } else {
                        measured.visibleText
                    }

                    drawWord(
                        canvas = canvas,
                        text = textToDraw,
                        drawX = cursorX,
                        baselineY = lineBaselineY,
                        wordWidth = measured.fullWidth,
                        fontMetrics = fontMetrics,
                        wordState = wordState,
                        style = style,
                        alpha = combinedAlpha,
                        fillPaint = fillPaint,
                        strokePaint = strokePaint,
                    )

                    canvas.restore()
                }
                cursorX += measured.fullWidth
                if (wordIdx < line.words.lastIndex) {
                    cursorX += spaceWidth
                }
            }
        }
    }

    private fun drawWord(
        canvas: Canvas,
        text: String,
        drawX: Float,
        baselineY: Float,
        wordWidth: Float,
        fontMetrics: Paint.FontMetrics,
        wordState: WordRenderState,
        style: CaptionStyle,
        alpha: Float,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val primaryArgb = applyAlpha(style.primaryColorArgb.toInt(), alpha)
        val highlightArgb = applyAlpha(style.highlightColorArgb.toInt(), alpha)
        val strokeArgb = applyAlpha(style.strokeColorArgb.toInt(), alpha)

        if (style.shadowEnabled) {
            val shadowArgb = applyAlpha(style.shadowColorArgb.toInt(), alpha)
            val radius = max(2f, fillPaint.textSize * 0.09f)
            val offset = max(1f, fillPaint.textSize * 0.05f)
            fillPaint.setShadowLayer(radius, offset, offset, shadowArgb)
        } else {
            fillPaint.clearShadowLayer()
        }

        if (style.strokeWidthFraction > 0.005f) {
            strokePaint.color = strokeArgb
            canvas.drawText(text, drawX, baselineY, strokePaint)
        }

        when {
            wordState.fillProgress <= 0.001f -> {
                fillPaint.color = primaryArgb
                canvas.drawText(text, drawX, baselineY, fillPaint)
            }

            wordState.fillProgress >= 0.999f -> {
                fillPaint.color = highlightArgb
                canvas.drawText(text, drawX, baselineY, fillPaint)
            }

            else -> {
                fillPaint.color = primaryArgb
                canvas.drawText(text, drawX, baselineY, fillPaint)

                val splitX = drawX + wordWidth * wordState.fillProgress
                val topY = baselineY + fontMetrics.top - fillPaint.textSize * 0.2f
                val bottomY = baselineY + fontMetrics.bottom + fillPaint.textSize * 0.2f

                // Clear shadow on the highlight pass so the split seam does not double-render shadow.
                fillPaint.clearShadowLayer()
                canvas.save()
                canvas.clipRect(drawX - fillPaint.textSize * 0.2f, topY, splitX, bottomY)
                fillPaint.color = highlightArgb
                canvas.drawText(text, drawX, baselineY, fillPaint)
                canvas.restore()
            }
        }
    }

    private fun drawBackgroundBox(
        canvas: Canvas,
        scratch: RenderScratch,
        centerX: Float,
        blockTop: Float,
        blockWidth: Float,
        blockHeight: Float,
        fontPx: Float,
        style: CaptionStyle,
        lineAlpha: Float,
    ) {
        val padH = fontPx * 0.45f
        val padV = fontPx * 0.22f
        val radius = fontPx * 0.28f
        scratch.boxRect.set(
            centerX - blockWidth / 2f - padH,
            blockTop - padV,
            centerX + blockWidth / 2f + padH,
            blockTop + blockHeight + padV,
        )
        scratch.boxPaint.color = applyAlpha(style.backgroundBoxColorArgb.toInt(), lineAlpha)
        canvas.drawRoundRect(scratch.boxRect, radius, radius, scratch.boxPaint)
    }

    private fun wrapWords(
        words: List<MeasuredWord>,
        spaceWidth: Float,
        maxWidth: Float,
    ): List<WrappedLine> {
        val lines = mutableListOf<WrappedLine>()
        var current = mutableListOf<MeasuredWord>()
        var currentWidth = 0f

        for (word in words) {
            if (current.isEmpty()) {
                current.add(word)
                currentWidth = word.fullWidth
            } else if (currentWidth + spaceWidth + word.fullWidth <= maxWidth) {
                current.add(word)
                currentWidth += spaceWidth + word.fullWidth
            } else {
                lines.add(WrappedLine(current, currentWidth))
                current = mutableListOf(word)
                currentWidth = word.fullWidth
            }
        }
        if (current.isNotEmpty()) {
            lines.add(WrappedLine(current, currentWidth))
        }
        return lines
    }

    private fun resolveTypeface(family: CaptionFontFamily): Typeface =
        synchronized(typefaceCache) {
            typefaceCache.getOrPut(family) {
                when (family) {
                    CaptionFontFamily.SANS_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    CaptionFontFamily.CONDENSED_DISPLAY -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    CaptionFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    CaptionFontFamily.SERIF_EDITORIAL -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                }
            }
        }

    private fun applyAlpha(argb: Int, alphaMultiplier: Float): Int {
        val baseAlpha = (argb ushr 24) and 0xFF
        val scaledAlpha = (baseAlpha * alphaMultiplier.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (scaledAlpha shl 24) or (argb and 0x00FFFFFF)
    }
}
