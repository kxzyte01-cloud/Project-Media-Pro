package com.projectmediapro.feature.captions

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import java.util.EnumMap
import com.projectmediapro.core.model.NewsOverlayLayout
import com.projectmediapro.core.model.OccupiedBand
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object CaptionCanvasRenderer {
    private const val BASE_FONT_RATIO = 0.062f
    private const val MAX_LINE_WIDTH_RATIO = 0.86f
    private const val LINE_SPACING_MULTIPLIER = 1.24f
    private const val EDGE_MARGIN_RATIO = 0.05f

    private val NEON_LINE_PALETTE = intArrayOf(
        0xFFFACC15.toInt(), // Electric Gold
        0xFFFF4D9D.toInt(), // Hot Pink
        0xFF22D3EE.toInt(), // Cyber Cyan
        0xFF4ADE80.toInt(), // Acid Lime
        0xFFC084FC.toInt(), // Vivid Purple
        0xFFFB7185.toInt(), // Coral Red
    )

    private val typefaceCache = EnumMap<CaptionFontFamily, Typeface>(CaptionFontFamily::class.java)
    private val customTypefaceCache = LinkedHashMap<String, Typeface>(8, 0.75f, true)
    private val editorialItalicTypeface: Typeface by lazy {
        Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
    }
    private val editorialRegularTypeface: Typeface by lazy {
        Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val ultraHeavyTypeface: Typeface by lazy {
        Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private const val FOREST_GREEN_ARGB: Int = 0xFF14532D.toInt()
    private const val BLOOD_RED_ARGB: Int = 0xFF8B0000.toInt()
    private const val ZINE_CREAM_ARGB: Int = 0xFFF5EFE0.toInt()
    private const val ZINE_GOLD_ARGB: Int = 0xFFFACC15.toInt()
    private const val ZINE_PINK_ARGB: Int = 0xFFFF2E93.toInt()

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
        val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        val fxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val knockoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
        }
        val boxRect = RectF()
        val iconPath = Path()
        var cachedCodeLineId: String? = null
        var cachedCodeMaxW: Float = -1f
        var cachedCodeFireTwist: Boolean = false
        var cachedCodePrevEllipsized: String = ""
        var cachedCodeNextEllipsized: String = ""
        var cachedCodeLineNumBase: Int = -1
        var cachedLineNum0: String = "01"
        var cachedLineNum1: String = "02"
        var cachedLineNum2: String = "03"
    }

    private fun formatTwoDigits(n: Int): String = if (n in 0..9) "0$n" else n.toString()

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
        occupiedBands: List<OccupiedBand> = emptyList(),
    ) {
        if (!frameState.hasVisibleContent || widthPx <= 1f || heightPx <= 1f) return

        val scratch = scratchLocal.get() ?: RenderScratch()
        val resolvedFrameState = if (occupiedBands.isNotEmpty()) {
            val safeY = NewsOverlayLayout.resolveCaptionSafeYFraction(
                requestedYFraction = frameState.style.position.yFraction,
                occupiedBands = occupiedBands,
            )
            if (safeY != frameState.style.position.yFraction) {
                frameState.copy(
                    style = frameState.style.copy(
                        position = frameState.style.position.copy(yFraction = safeY),
                    ),
                )
            } else {
                frameState
            }
        } else {
            frameState
        }
        val style = resolvedFrameState.style

        if (style.templateId == CaptionTemplateId.KNOCKOUT_MASK) {
            drawKnockoutMaskFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = resolvedFrameState,
            )
            return
        }
        if (style.templateId == CaptionTemplateId.COLD_INVERT) {
            drawColdInvertFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = resolvedFrameState,
            )
            return
        }
        if (style.templateId == CaptionTemplateId.CODE_POEM) {
            drawCodePoemFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = resolvedFrameState,
            )
            return
        }
        if (style.templateId == CaptionTemplateId.BLACKOUT_KINETIC) {
            drawBlackoutKineticFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = resolvedFrameState,
            )
            return
        }
        val minDim = min(widthPx, heightPx)
        val templateFontBoost = when (style.templateId) {
            CaptionTemplateId.KINETIC_GRUNGE -> 1.18f
            CaptionTemplateId.KINETIC_COLLAGE -> 1.28f
            CaptionTemplateId.CRIMSON_ZINE -> 1.16f
            CaptionTemplateId.NEON_GLOW_STACK -> 1.12f
            CaptionTemplateId.EDITORIAL_SCRATCH -> 1.08f
            else -> 1.0f
        }
        val fontPx = minDim * BASE_FONT_RATIO * templateFontBoost *
            style.fontSizeScale.coerceIn(CaptionStyleBounds.FONT_SCALE_RANGE)
        val customTypeface = resolveCustomTypeface(style.customFontPath)
        val typeface = customTypeface ?: if (style.fontFamily == CaptionFontFamily.RAIN_GLASS) {
            resolveTypeface(CaptionFontFamily.RAIN_GLASS)
        } else {
            when (style.templateId) {
                CaptionTemplateId.EDITORIAL_SCRATCH -> editorialRegularTypeface
                CaptionTemplateId.KINETIC_GRUNGE -> resolveTypeface(CaptionFontFamily.CONDENSED_DISPLAY)
                CaptionTemplateId.KINETIC_COLLAGE -> ultraHeavyTypeface
                CaptionTemplateId.CRIMSON_ZINE -> editorialRegularTypeface
                else -> resolveTypeface(style.fontFamily)
            }
        }

        val isRainFont = style.rainFontEnabled
        val glyphSpacing = if (style.fontFamily == CaptionFontFamily.RAIN_GLASS) 0.05f else 0f
        val fillPaint = scratch.fillPaint.apply {
            textSize = fontPx
            this.typeface = typeface
            letterSpacing = glyphSpacing
            clearShadowLayer()
            textAlign = Paint.Align.LEFT
        }
        val strokePaint = scratch.strokePaint.apply {
            textSize = fontPx
            this.typeface = typeface
            letterSpacing = glyphSpacing
            strokeWidth = fontPx * style.strokeWidthFraction.coerceIn(CaptionStyleBounds.STROKE_WIDTH_RANGE)
            textAlign = Paint.Align.LEFT
        }

        val spaceWidth = fillPaint.measureText(" ")
        val maxContentWidth = widthPx * if (heightPx > widthPx) 0.78f else MAX_LINE_WIDTH_RATIO

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
        val extraBottomSpace = if (style.templateId == CaptionTemplateId.MUSIC_PLAYER) fontPx * 2.1f else 0f
        val extraTopSpace = if (style.templateId == CaptionTemplateId.MUSIC_PLAYER) fontPx * 1.15f else 0f
        val minCenterY = totalBlockHeight / 2f + edgeMargin + extraTopSpace
        val maxCenterY = max(minCenterY, heightPx - totalBlockHeight / 2f - edgeMargin - extraBottomSpace)
        val centerY = desiredCenterY.coerceIn(minCenterY, maxCenterY)
        val blockTop = centerY - totalBlockHeight / 2f

        // Template-specific ambient layers drawn behind the main lyric words
        when (style.templateId) {
            CaptionTemplateId.KINETIC_GRUNGE -> drawGrungeSpeckles(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = frameState,
            )
            CaptionTemplateId.KINETIC_COLLAGE -> drawMistyDustAndEdgeAccent(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                fontPx = fontPx,
                frameState = frameState,
            )
            CaptionTemplateId.CRIMSON_ZINE -> drawCrimsonZineBackdrop(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                centerY = centerY,
                blockHeight = totalBlockHeight,
                fontPx = fontPx,
                frameState = frameState,
            )
            CaptionTemplateId.EDITORIAL_SCRATCH -> drawEditorialFilmScratches(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                centerX = centerX,
                blockTop = blockTop,
                blockWidth = maxBlockWidth,
                blockHeight = totalBlockHeight,
                fontPx = fontPx,
                frameState = frameState,
            )
            CaptionTemplateId.MUSIC_PLAYER -> drawMusicPlayerBackdropAndNeighbors(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                centerX = centerX,
                blockTop = blockTop,
                blockHeight = totalBlockHeight,
                fontPx = fontPx,
                frameState = frameState,
            )
            else -> Unit
        }

        if (style.backgroundBoxEnabled && !style.transparentBackground) {
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

        // #5 One-hue neon discipline + automatic dark scrim so neon survives bright footage
        val neonAccentColor = if (style.highlightColorArgb != 0xFFFACC15L) {
            style.highlightColorArgb.toInt()
        } else {
            0xFF22D3EE.toInt()
        }
        if (style.templateId == CaptionTemplateId.NEON_GLOW_STACK && !style.transparentBackground) {
            val scrimTop = (blockTop - fontPx * 1.2f).coerceAtLeast(0f)
            val scrimBot = (blockTop + totalBlockHeight + fontPx * 1.2f).coerceAtMost(heightPx)
            scratch.boxPaint.shader = LinearGradient(
                0f, scrimTop, 0f, scrimBot,
                intArrayOf(0x00000000, applyAlpha(0x99000000.toInt(), frameState.lineAlpha), 0x00000000),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, scrimTop, widthPx, scrimBot, scratch.boxPaint)
            scratch.boxPaint.shader = null
        }
        val baselineOffset = -fontMetrics.ascent + (lineHeight - (fontMetrics.descent - fontMetrics.ascent)) / 2f
        var globalWordIndex = 0

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

                    val textToDraw = measured.visibleText

                    when (style.templateId) {
                        CaptionTemplateId.NEON_GLOW_STACK -> drawNeonWord(
                            canvas = canvas,
                            text = textToDraw,
                            drawX = cursorX,
                            baselineY = lineBaselineY,
                            wordState = wordState,
                            neonColor = neonAccentColor,
                            alpha = combinedAlpha,
                            fillPaint = fillPaint,
                            strokePaint = strokePaint,
                        )
                        CaptionTemplateId.EDITORIAL_SCRATCH -> drawEditorialWord(
                            canvas = canvas,
                            text = textToDraw,
                            drawX = cursorX,
                            baselineY = lineBaselineY,
                            wordIndex = globalWordIndex,
                            wordState = wordState,
                            style = style,
                            alpha = combinedAlpha,
                            fillPaint = fillPaint,
                            strokePaint = strokePaint,
                        )
                        CaptionTemplateId.KINETIC_COLLAGE -> drawKineticCollageWord(
                            canvas = canvas,
                            text = textToDraw,
                            drawX = cursorX,
                            baselineY = lineBaselineY,
                            wordIndex = globalWordIndex,
                            lineIndex = frameState.activeLineIndex,
                            wordState = wordState,
                            alpha = combinedAlpha,
                            fillPaint = fillPaint,
                            strokePaint = strokePaint,
                        )
                        CaptionTemplateId.CRIMSON_ZINE -> drawCrimsonZineWord(
                            canvas = canvas,
                            scratch = scratch,
                            text = textToDraw,
                            drawX = cursorX,
                            baselineY = lineBaselineY,
                            wordWidth = measured.fullWidth,
                            fontMetrics = fontMetrics,
                            wordIndex = globalWordIndex,
                            lineIndex = frameState.activeLineIndex,
                            wordState = wordState,
                            alpha = combinedAlpha,
                            fillPaint = fillPaint,
                            strokePaint = strokePaint,
                        )
                        else -> drawWord(
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
                    }

                    // #7 Drawn rect caret on 530ms terminal blink (zero wrap-width jitter)
                    if (style.templateId == CaptionTemplateId.TYPEWRITER && wordState.isActive) {
                        val blinkOn = ((frameState.presentationTimeUs / 530_000L) and 1L) == 0L
                        if (blinkOn) {
                            val visW = fillPaint.measureText(textToDraw)
                            val caretX = cursorX + visW + fontPx * 0.12f
                            val caretW = max(2f, fontPx * 0.08f)
                            scratch.boxPaint.color = applyAlpha(style.highlightColorArgb.toInt(), combinedAlpha)
                            canvas.drawRect(
                                caretX,
                                lineBaselineY + fontMetrics.ascent * 0.92f,
                                caretX + caretW,
                                lineBaselineY + fontMetrics.descent * 0.85f,
                                scratch.boxPaint,
                            )
                        }
                    }

                    if (isRainFont) {
                        drawRainGlyphEffect(
                            canvas = canvas,
                            scratch = scratch,
                            drawX = cursorX,
                            baselineY = lineBaselineY,
                            wordWidth = measured.fullWidth,
                            fontMetrics = fontMetrics,
                            fontPx = fontPx,
                            wordIndex = globalWordIndex,
                            timeSec = frameState.presentationTimeUs / 1_000_000f,
                            alpha = combinedAlpha,
                        )
                    }

                    canvas.restore()
                }
                cursorX += measured.fullWidth
                if (wordIdx < line.words.lastIndex) {
                    cursorX += spaceWidth
                }
                globalWordIndex++
            }
        }

        if (style.templateId == CaptionTemplateId.MUSIC_PLAYER) {
            drawMusicPlayerControls(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                centerX = centerX,
                blockBottomY = blockTop + totalBlockHeight,
                fontPx = fontPx,
                frameState = frameState,
            )
        }
    }

    private fun drawNeonWord(
        canvas: Canvas,
        text: String,
        drawX: Float,
        baselineY: Float,
        wordState: WordRenderState,
        neonColor: Int,
        alpha: Float,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val glowRadius = max(6f, fillPaint.textSize * 0.28f)
        val haloColor = applyAlpha(neonColor, alpha * 0.92f)
        val coreColor = if (wordState.isActive) {
            applyAlpha(0xFFFFFFFF.toInt(), alpha)
        } else {
            applyAlpha(neonColor, alpha)
        }

        // Outer neon tube glow pass
        fillPaint.color = haloColor
        fillPaint.setShadowLayer(glowRadius, 0f, 0f, haloColor)
        canvas.drawText(text, drawX, baselineY, fillPaint)

        // Crisp luminous stroke rim
        strokePaint.strokeWidth = max(2f, fillPaint.textSize * 0.055f)
        strokePaint.color = applyAlpha(neonColor, alpha * 0.85f)
        canvas.drawText(text, drawX, baselineY, strokePaint)

        // Bright neon core
        fillPaint.clearShadowLayer()
        fillPaint.color = coreColor
        canvas.drawText(text, drawX, baselineY, fillPaint)
    }

    private fun drawEditorialWord(
        canvas: Canvas,
        text: String,
        drawX: Float,
        baselineY: Float,
        wordIndex: Int,
        wordState: WordRenderState,
        style: CaptionStyle,
        alpha: Float,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val isHollowEditorial = false
        val useItalic = wordState.isActive
        val previousFillTypeface = fillPaint.typeface
        val previousStrokeTypeface = strokePaint.typeface
        val wordTypeface = if (useItalic) editorialItalicTypeface else editorialRegularTypeface
        fillPaint.typeface = wordTypeface
        strokePaint.typeface = wordTypeface

        if (isHollowEditorial) {
            strokePaint.strokeWidth = max(2f, fillPaint.textSize * 0.055f)
            strokePaint.color = applyAlpha(style.primaryColorArgb.toInt(), alpha * 0.92f)
            canvas.drawText(text, drawX, baselineY, strokePaint)
        } else {
            val fillColor = if (wordState.isActive) {
                applyAlpha(style.highlightColorArgb.toInt(), alpha)
            } else {
                applyAlpha(style.primaryColorArgb.toInt(), alpha)
            }
            val glowRadius = max(3f, fillPaint.textSize * 0.14f)
            fillPaint.setShadowLayer(glowRadius, 0f, 0f, applyAlpha(0xCCDC2626.toInt(), alpha * 0.7f))
            fillPaint.color = fillColor
            canvas.drawText(text, drawX, baselineY, fillPaint)
            fillPaint.clearShadowLayer()
        }

        fillPaint.typeface = previousFillTypeface
        strokePaint.typeface = previousStrokeTypeface
    }

    private fun drawGrungeSpeckles(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        frameState: CaptionFrameState,
    ) {
        val tick = frameState.presentationTimeUs / 80_000L
        val fxPaint = scratch.fxPaint
        val minDim = min(widthPx, heightPx)
        for (i in 0 until 18) {
            val seed = (tick * 1103515245L + i * 2654435761L) and 0x7FFFFFFFL
            val nx = ((seed % 1000L) / 1000f)
            val ny = (((seed / 1000L) % 1000L) / 1000f)
            val radius = minDim * (0.0022f + ((seed / 1_000_000L) % 4L) * 0.0011f)
            val dotAlpha = frameState.lineAlpha * (0.22f + (i % 4) * 0.11f)
            fxPaint.color = applyAlpha(0xFFFFFFFF.toInt(), dotAlpha)
            canvas.drawCircle(nx * widthPx, ny * heightPx, radius, fxPaint)
        }
    }

    private fun drawEditorialFilmScratches(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        centerX: Float,
        blockTop: Float,
        blockWidth: Float,
        blockHeight: Float,
        fontPx: Float,
        frameState: CaptionFrameState,
    ) {
        // Subtle crimson-dark editorial strip behind text
        val padH = fontPx * 0.55f
        val padV = fontPx * 0.24f
        scratch.boxRect.set(
            centerX - blockWidth / 2f - padH,
            blockTop - padV,
            centerX + blockWidth / 2f + padH,
            blockTop + blockHeight + padV,
        )
        scratch.boxPaint.color = applyAlpha(0x881A050A.toInt(), frameState.lineAlpha)
        canvas.drawRoundRect(scratch.boxRect, fontPx * 0.14f, fontPx * 0.14f, scratch.boxPaint)

        // Subtle vertical film-scratch hairlines animated at ~10fps
        val tick = frameState.presentationTimeUs / 100_000L
        val scratchPaint = scratch.fxStrokePaint
        scratchPaint.strokeWidth = max(1f, widthPx * 0.0018f)
        for (i in 0 until 4) {
            val seed = (tick * 48271L + i * 7919L) and 0x7FFFFFFFL
            val x = widthPx * (0.08f + ((seed % 840L) / 1000f))
            val yStart = heightPx * (((seed / 1000L) % 350L) / 1000f)
            val yEnd = (yStart + heightPx * (0.35f + (i % 3) * 0.18f)).coerceAtMost(heightPx)
            scratchPaint.color = applyAlpha(0xFFFCA5A5.toInt(), frameState.lineAlpha * 0.24f)
            canvas.drawLine(x, yStart, x, yEnd, scratchPaint)
        }
    }

    private fun drawMusicPlayerBackdropAndNeighbors(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        centerX: Float,
        blockTop: Float,
        blockHeight: Float,
        fontPx: Float,
        frameState: CaptionFrameState,
    ) {
        // Transparent by default so Music Player floats cleanly over any video;
        // draws atmospheric dark card scrim only when Background Box is enabled.
        if (!frameState.style.transparentBackground) {
            val scrimHalfW = widthPx * 0.44f
            scratch.boxRect.set(
                centerX - scrimHalfW,
                blockTop - fontPx * 1.25f,
                centerX + scrimHalfW,
                blockTop + blockHeight + fontPx * 2.25f,
            )
            scratch.boxPaint.color = applyAlpha(0x52050811.toInt(), frameState.lineAlpha)
            canvas.drawRoundRect(scratch.boxRect, fontPx * 0.45f, fontPx * 0.45f, scratch.boxPaint)
        }

        // Viral multi-layer parallax falling rain streaks + drifting bokeh droplets
        val tSec = frameState.presentationTimeUs / 1_000_000f
        val fxPaint = scratch.fxPaint
        val streakPaint = scratch.fxStrokePaint
        val minDim = min(widthPx, heightPx)

        // Layer 1: Diagonal falling rain streaks at two parallax speeds
        for (i in 0 until 28) {
            val isForeground = (i % 3 == 0)
            val speedY = if (isForeground) 0.58f + (i % 5) * 0.05f else 0.32f + (i % 4) * 0.04f
            val windX = if (isForeground) -0.055f else -0.032f
            val rawX = (((i * 67 + 13) % 100) / 100f + tSec * windX) % 1.0f
            val normX = if (rawX < 0f) rawX + 1.0f else rawX
            val normY = (((i * 41 + 7) % 100) / 100f + tSec * speedY) % 1.0f

            val x1 = normX * widthPx
            val y1 = normY * heightPx
            val streakLen = minDim * (if (isForeground) 0.052f else 0.030f)
            val dx = streakLen * -0.14f
            val dy = streakLen
            streakPaint.strokeWidth = max(1f, minDim * (if (isForeground) 0.0032f else 0.0018f))
            val alpha = frameState.lineAlpha * (if (isForeground) 0.48f else 0.26f)
            streakPaint.color = applyAlpha(0xFFE0F2FE.toInt(), alpha)
            canvas.drawLine(x1, y1, x1 + dx, y1 + dy, streakPaint)
        }

        // Layer 2: Soft glowing snow/raindrop bokeh orbs with gentle wind sway
        for (i in 0 until 18) {
            val sway = sin((tSec * (1.1f + (i % 4) * 0.25f) + i * 0.9f).toDouble()).toFloat() * 0.018f
            val baseX = ((((i * 83 + 17) % 100) / 100f) + sway).coerceIn(0f, 1f)
            val baseY = (((i * 37 + 11) % 100) / 100f + tSec * (0.09f + (i % 4) * 0.03f)) % 1.0f
            val r = minDim * (0.0028f + (i % 4) * 0.0016f)
            val orbAlpha = frameState.lineAlpha * (0.20f + (i % 4) * 0.08f)
            fxPaint.color = applyAlpha(0xFFFFFFFF.toInt(), orbAlpha)
            canvas.drawCircle(baseX * widthPx, baseY * heightPx, r, fxPaint)
        }

        // Dimmed previous & next lines for viral multi-line lyrics context
        val neighborPaint = scratch.fxPaint.apply {
            textSize = fontPx * 0.68f
            typeface = resolveTypeface(frameState.style.fontFamily)
            textAlign = Paint.Align.CENTER
            clearShadowLayer()
        }
        val maxNeighborWidth = widthPx * 0.82f
        frameState.previousLineText?.takeIf { it.isNotBlank() }?.let { prev ->
            neighborPaint.color = applyAlpha(0xFFFFFFFF.toInt(), frameState.lineAlpha * 0.42f)
            val clipped = ellipsizeToWidth(prev, neighborPaint, maxNeighborWidth)
            canvas.drawText(clipped, centerX, blockTop - fontPx * 0.42f, neighborPaint)
        }
        frameState.nextLineText?.takeIf { it.isNotBlank() }?.let { next ->
            neighborPaint.color = applyAlpha(0xFFFFFFFF.toInt(), frameState.lineAlpha * 0.36f)
            val clipped = ellipsizeToWidth(next, neighborPaint, maxNeighborWidth)
            canvas.drawText(clipped, centerX, blockTop + blockHeight + fontPx * 0.82f, neighborPaint)
        }
        neighborPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawMusicPlayerControls(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        centerX: Float,
        blockBottomY: Float,
        fontPx: Float,
        frameState: CaptionFrameState,
    ) {
        val barWidth = (widthPx * 0.62f).coerceAtLeast(fontPx * 5.0f)
        val barLeft = centerX - barWidth / 2f
        val barRight = centerX + barWidth / 2f
        val barY = blockBottomY + fontPx * 1.28f
        val barHeight = max(3f, fontPx * 0.11f)
        val cornerR = barHeight / 2f

        // Background track
        scratch.boxRect.set(barLeft, barY - barHeight / 2f, barRight, barY + barHeight / 2f)
        scratch.boxPaint.color = applyAlpha(0xFFFFFFFF.toInt(), frameState.lineAlpha * 0.28f)
        canvas.drawRoundRect(scratch.boxRect, cornerR, cornerR, scratch.boxPaint)

        // Active progress fill
        val progress = frameState.songProgressFraction.coerceIn(0f, 1f)
        val fillRight = barLeft + barWidth * progress
        scratch.boxRect.set(barLeft, barY - barHeight / 2f, max(barLeft + barHeight, fillRight), barY + barHeight / 2f)
        scratch.boxPaint.color = applyAlpha(0xFFFFFFFF.toInt(), frameState.lineAlpha * 0.92f)
        canvas.drawRoundRect(scratch.boxRect, cornerR, cornerR, scratch.boxPaint)

        // Playhead knob
        val knobR = max(5f, fontPx * 0.18f)
        canvas.drawCircle(fillRight, barY, knobR, scratch.boxPaint)

        // Minimalist transport icons (Prev | Pause | Next) below progress bar
        val iconY = barY + fontPx * 0.68f
        val iconSize = fontPx * 0.36f
        val iconSpread = barWidth * 0.24f
        val iconPaint = scratch.fxPaint.apply {
            color = applyAlpha(0xFFFFFFFF.toInt(), frameState.lineAlpha * 0.88f)
            clearShadowLayer()
        }

        // Center pause bars
        val barW = max(2.5f, iconSize * 0.24f)
        val barGap = max(3f, iconSize * 0.22f)
        scratch.boxRect.set(centerX - barGap - barW, iconY - iconSize * 0.5f, centerX - barGap, iconY + iconSize * 0.5f)
        canvas.drawRoundRect(scratch.boxRect, barW / 2f, barW / 2f, iconPaint)
        scratch.boxRect.set(centerX + barGap, iconY - iconSize * 0.5f, centerX + barGap + barW, iconY + iconSize * 0.5f)
        canvas.drawRoundRect(scratch.boxRect, barW / 2f, barW / 2f, iconPaint)

        // Previous triangle
        val prevX = centerX - iconSpread
        scratch.iconPath.reset()
        scratch.iconPath.moveTo(prevX - iconSize * 0.45f, iconY)
        scratch.iconPath.lineTo(prevX + iconSize * 0.35f, iconY - iconSize * 0.42f)
        scratch.iconPath.lineTo(prevX + iconSize * 0.35f, iconY + iconSize * 0.42f)
        scratch.iconPath.close()
        canvas.drawPath(scratch.iconPath, iconPaint)

        // Next triangle
        val nextX = centerX + iconSpread
        scratch.iconPath.reset()
        scratch.iconPath.moveTo(nextX + iconSize * 0.45f, iconY)
        scratch.iconPath.lineTo(nextX - iconSize * 0.35f, iconY - iconSize * 0.42f)
        scratch.iconPath.lineTo(nextX - iconSize * 0.35f, iconY + iconSize * 0.42f)
        scratch.iconPath.close()
        canvas.drawPath(scratch.iconPath, iconPaint)
    }

    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisW = paint.measureText(ellipsis)
        val avail = (maxWidth - ellipsisW).coerceAtLeast(0f)
        var lo = 1
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (paint.measureText(text, 0, mid) <= avail) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        return text.substring(0, lo) + ellipsis
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
            val radius = max(3f, fillPaint.textSize * 0.18f)
            fillPaint.setShadowLayer(radius, 0f, 0f, shadowArgb)
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
                // #1 Feathered LinearGradient ink edge (0.12*fontSize softness) instead of hard clipRect
                val splitX = drawX + wordWidth * wordState.fillProgress
                val feather = max(6f, fillPaint.textSize * 0.12f)
                fillPaint.color = 0xFFFFFFFF.toInt()
                fillPaint.shader = LinearGradient(
                    splitX - feather * 0.5f,
                    baselineY,
                    splitX + feather * 0.5f,
                    baselineY,
                    highlightArgb,
                    primaryArgb,
                    Shader.TileMode.CLAMP,
                )
                try {
                    canvas.drawText(text, drawX, baselineY, fillPaint)
                } finally {
                    fillPaint.shader = null
                }
            }
        }
    }

    private fun resolveSolidMaskColor(style: CaptionStyle): Int {
        val raw = style.backgroundBoxColorArgb
        // Default translucent slate box (0xB30F172A) falls back to classic solid black for full-screen masks.
        if (raw == 0xB30F172AL) return 0xFF000000.toInt()
        return (0xFF000000L or (raw and 0x00FFFFFFL)).toInt()
    }

    private fun isLightColor(argb: Int): Boolean {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 >= 175
    }
}
