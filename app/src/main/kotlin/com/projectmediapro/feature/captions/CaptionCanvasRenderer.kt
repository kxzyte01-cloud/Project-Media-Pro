package com.projectmediapro.feature.captions

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.util.EnumMap
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
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

        if (style.templateId == CaptionTemplateId.KNOCKOUT_MASK) {
            drawKnockoutMaskFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = frameState,
            )
            return
        }
        if (style.templateId == CaptionTemplateId.COLD_INVERT) {
            drawColdInvertFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = frameState,
            )
            return
        }
        if (style.templateId == CaptionTemplateId.CODE_POEM) {
            drawCodePoemFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = frameState,
            )
            return
        }
        if (style.templateId == CaptionTemplateId.BLACKOUT_KINETIC) {
            drawBlackoutKineticFrame(
                canvas = canvas,
                scratch = scratch,
                widthPx = widthPx,
                heightPx = heightPx,
                frameState = frameState,
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

        val neonAccentColor = NEON_LINE_PALETTE[
            (frameState.activeLineIndex and 0x7FFFFFFF) % NEON_LINE_PALETTE.size
        ]
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

                    val textToDraw = if (frameState.showCursor && wordState.isActive) {
                        "${measured.visibleText}|"
                    } else {
                        measured.visibleText
                    }

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
        val isHollowEditorial = !wordState.isActive && (wordIndex % 2 == 1)
        val useItalic = wordState.isActive || (wordIndex % 2 == 1)
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
        // Soft dark atmospheric card scrim behind the Music Player lyric stack for crisp contrast
        val scrimHalfW = widthPx * 0.44f
        scratch.boxRect.set(
            centerX - scrimHalfW,
            blockTop - fontPx * 1.25f,
            centerX + scrimHalfW,
            blockTop + blockHeight + fontPx * 2.25f,
        )
        scratch.boxPaint.color = applyAlpha(0x52050811.toInt(), frameState.lineAlpha)
        canvas.drawRoundRect(scratch.boxRect, fontPx * 0.45f, fontPx * 0.45f, scratch.boxPaint)

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
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
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

    /**
     * Video-through-text clipping mask ("Video Mask"): fills a pitch-black layer and punches
     * the giant active word out with PorterDuff.Mode.CLEAR inside an offscreen layer so the
     * underlying video footage plays live inside the letters in both preview and GPU export.
     */
    /**
     * "Cold Invert" (Dark Academia strobe) lyric edit:
     * - Alternates pitch-black and paper-white full-frame backgrounds on every word
     * - Heavy condensed all-caps display font with every 4th word in distressed bold serif
     * - Handheld wiggle(8, 5) micro-shake + 2-frame scale punch + brief RGB Twitch split
     * - Photocopied grunge noise speckles overlaid directly on the glyph
     */
    /**
     * "Code Poem" (VS Code One Dark Pro + CRT monitor + 2.5D glowing me/you bridge + ember twist):
     * - Dark-room IDE card with `lyrics.js` tab, line-number gutter, CRT scanlines, and dark vignette
     * - Syntax-highlighted JavaScript typewriter reveal (Red keywords, Sky-Blue methods, Yellow active word,
     *   Orange comments, blinking red cursor)
     * - Mirrored water reflection underneath + glowing `you` gliding toward lone red `me`
     * - Ignites glowing orange fire border + rising golden embers on burn/dark-age lines or final 20%
     */
    /**
     * "Blackout" kinetic white-on-black lyric edit:
     * - Pure #000000 black background, crisp #FFFFFF typography, zero shadow & zero stroke
     * - Single-word or 2-word punchy lines use dramatic scale hierarchy (condensed display hero,
     *   wide-tracked statement, and cursive/italic contrast words) with snappy Pop/Zoom entrance
     * - Multi-word lines (3+ words) stack left-aligned with escalating size (small -> medium -> huge)
     */
    private fun drawBlackoutKineticFrame(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        frameState: CaptionFrameState,
    ) {
        val style = frameState.style
        val alpha = frameState.lineAlpha.coerceIn(0f, 1f)
        if (alpha <= 0.01f) return

        // Pure black solid background for hard-cut contrast
        scratch.boxPaint.color = applyAlpha(0xFF000000.toInt(), alpha)
        canvas.drawRect(0f, 0f, widthPx, heightPx, scratch.boxPaint)

        val visibleWords = frameState.words.filter { it.alpha > 0.01f && it.visibleCharCount > 0 }
        if (visibleWords.isEmpty()) return

        val minDim = min(widthPx, heightPx)
        val baseSize = minDim * 0.125f * style.fontSizeScale.coerceIn(CaptionStyleBounds.FONT_SCALE_RANGE)
        val displayFace = resolveCustomTypeface(style.customFontPath)
            ?: resolveTypeface(CaptionFontFamily.CONDENSED_DISPLAY)
        val scriptFace = resolveCustomTypeface(style.customFontPath)
            ?: editorialItalicTypeface

        val prevFillAlign = scratch.fillPaint.textAlign
        val prevLetterSpacing = scratch.fillPaint.letterSpacing
        val prevTypeface = scratch.fillPaint.typeface
        val fillPaint = scratch.fillPaint
        fillPaint.clearShadowLayer()

        try {
            val centerX = widthPx * style.position.xFraction.coerceIn(CaptionStyleBounds.POSITION_X_RANGE)
            val centerY = heightPx * style.position.yFraction.coerceIn(0.20f, 0.80f)
            val timeSec = frameState.presentationTimeUs / 1_000_000f

            if (visibleWords.size >= 3) {
                // Escalating left-aligned vertical stack with pre-fitted exact vertical centering
                val stackLeft = widthPx * 0.14f
                val maxStackW = widthPx * 0.74f
                fillPaint.textAlign = Paint.Align.LEFT

                val fittedSizes = FloatArray(visibleWords.size)
                val fittedWidths = FloatArray(visibleWords.size)
                var totalH = 0f
                visibleWords.forEachIndexed { idx, w ->
                    val raw = w.displayText.take(w.visibleCharCount)
                    val isLast = idx == visibleWords.lastIndex
                    val factor = when {
                        isLast -> 1.28f
                        idx == visibleWords.lastIndex - 1 -> 0.88f
                        else -> 0.72f
                    }
                    fillPaint.typeface = if (idx == 1 && visibleWords.size == 3) scriptFace else displayFace
                    fillPaint.letterSpacing = if (isLast) 0.06f else 0.02f
                    var sz = baseSize * factor
                    fillPaint.textSize = sz
                    val measured = fillPaint.measureText(raw).coerceAtLeast(1f)
                    if (measured > maxStackW) {
                        sz *= (maxStackW / measured)
                        fillPaint.textSize = sz
                    }
                    fittedSizes[idx] = sz
                    fittedWidths[idx] = min(measured, maxStackW)
                    totalH += sz * 1.18f
                }

                var cursorY = centerY - totalH * 0.5f
                visibleWords.forEachIndexed { idx, w ->
                    val raw = w.displayText.take(w.visibleCharCount)
                    val isLast = idx == visibleWords.lastIndex
                    val sz = fittedSizes[idx]
                    val drawW = fittedWidths[idx]
                    fillPaint.typeface = if (idx == 1 && visibleWords.size == 3) scriptFace else displayFace
                    fillPaint.letterSpacing = if (isLast) 0.06f else 0.02f
                    fillPaint.textSize = sz
                    val fm = fillPaint.fontMetrics
                    val rowH = sz * 1.18f
                    val baselineY = cursorY + rowH * 0.5f - (fm.ascent + fm.descent) * 0.5f

                    canvas.save()
                    val pop = if (w.isActive) w.scale.coerceIn(0.8f, 1.3f) else 1f
                    canvas.scale(pop, pop, stackLeft + drawW * 0.5f, cursorY + rowH * 0.5f)
                    fillPaint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha * w.alpha)
                    canvas.drawText(raw, stackLeft, baselineY, fillPaint)
                    canvas.restore()

                    if (style.rainFontEnabled) {
                        drawRainGlyphEffect(
                            canvas = canvas,
                            scratch = scratch,
                            drawX = stackLeft,
                            baselineY = baselineY,
                            wordWidth = drawW,
                            fontMetrics = fm,
                            fontPx = sz,
                            wordIndex = frameState.activeLineIndex * 7 + idx,
                            timeSec = timeSec,
                            alpha = alpha * w.alpha,
                        )
                    }
                    cursorY += rowH
                }
            } else {
                // Single-word or 2-word dramatic focal layout (measure exact drawn casing)
                val activeWord = visibleWords.lastOrNull { it.isActive } ?: visibleWords.last()
                val rawText = if (visibleWords.size == 2) {
                    visibleWords.joinToString(" ") { it.displayText.take(it.visibleCharCount) }
                } else {
                    activeWord.displayText.take(activeWord.visibleCharCount)
                }
                val ordinal = frameState.activeLineIndex * 5 + visibleWords.size
                val role = ordinal % 4
                val isScriptContrast = role == 2 && visibleWords.size == 1
                val isWideTracked = role == 3
                val displayText = if (isScriptContrast) rawText.lowercase(Locale.US) else rawText

                fillPaint.typeface = if (isScriptContrast) scriptFace else displayFace
                fillPaint.letterSpacing = when {
                    isWideTracked -> 0.16f
                    isScriptContrast -> 0.03f
                    else -> 0.02f
                }
                val roleScale = when {
                    isScriptContrast -> 0.92f
                    visibleWords.size == 1 -> 1.48f
                    else -> 1.08f
                }
                var sz = baseSize * roleScale
                fillPaint.textAlign = Paint.Align.CENTER
                fillPaint.textSize = sz
                val measured = fillPaint.measureText(displayText).coerceAtLeast(1f)
                val maxW = widthPx * 0.86f
                if (measured > maxW) {
                    sz *= (maxW / measured)
                    fillPaint.textSize = sz
                }

                val fm = fillPaint.fontMetrics
                val baselineY = centerY - (fm.ascent + fm.descent) * 0.5f
                val pop = if (activeWord.isActive) activeWord.scale.coerceIn(0.8f, 1.32f) else 1f

                canvas.save()
                canvas.scale(pop, pop, centerX, centerY)
                fillPaint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha * activeWord.alpha)
                canvas.drawText(displayText, centerX, baselineY, fillPaint)
                canvas.restore()

                if (style.rainFontEnabled) {
                    val drawW = min(measured, maxW)
                    drawRainGlyphEffect(
                        canvas = canvas,
                        scratch = scratch,
                        drawX = centerX - drawW * 0.5f,
                        baselineY = baselineY,
                        wordWidth = drawW,
                        fontMetrics = fm,
                        fontPx = sz,
                        wordIndex = ordinal,
                        timeSec = timeSec,
                        alpha = alpha * activeWord.alpha,
                    )
                }
            }
        } finally {
            fillPaint.textAlign = prevFillAlign
            fillPaint.letterSpacing = prevLetterSpacing
            fillPaint.typeface = prevTypeface
        }
    }

    private fun drawCodePoemFrame(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        frameState: CaptionFrameState,
    ) {
        val style = frameState.style
        val alpha = frameState.lineAlpha.coerceIn(0f, 1f)
        val minDim = min(widthPx, heightPx)
        val timeSec = frameState.presentationTimeUs / 1_000_000f
        val lineNumBase = (frameState.activeLineIndex * 3 + 12).coerceAtLeast(1)

        val prevFillAlign = scratch.fillPaint.textAlign
        val prevLetterSpacing = scratch.fillPaint.letterSpacing
        val prevFxAlign = scratch.fxPaint.textAlign

        try {
            // 1. Dark monitor room scrim + subtle CRT scanlines
            scratch.boxPaint.color = applyAlpha(0xD9090C12.toInt(), alpha)
            canvas.drawRect(0f, 0f, widthPx, heightPx, scratch.boxPaint)

            val scanStep = max(4f, minDim * 0.0065f)
            scratch.fxStrokePaint.strokeWidth = 1f
            scratch.fxStrokePaint.color = applyAlpha(0x18FFFFFF, alpha)
            var scanY = 0f
            while (scanY < heightPx) {
                canvas.drawLine(0f, scanY, widthPx, scanY, scratch.fxStrokePaint)
                scanY += scanStep
            }

            // 2. IDE window card geometry (auto-expands cleanly on 16:9 landscape so 3 lines never clip)
            val monoTypeface = resolveCustomTypeface(style.customFontPath)
                ?: resolveTypeface(CaptionFontFamily.MONOSPACE)
            var codeFontPx = minDim * 0.044f * style.fontSizeScale.coerceIn(0.75f, 1.35f)
            val tabBarH = codeFontPx * 1.45f
            val lineStep = codeFontPx * 1.52f
            val cardW = widthPx * 0.90f
            val minRequiredCardH = tabBarH + lineStep * 3.5f
            val cardH = max(min(heightPx * 0.42f, minDim * 0.62f), minRequiredCardH)
                .coerceAtMost(heightPx * 0.72f)
            val cardLeft = (widthPx - cardW) * 0.5f
            val cardCenterY = heightPx * style.position.yFraction.coerceIn(0.30f, 0.70f)
            val cardTop = cardCenterY - cardH * 0.5f
            val cardRight = cardLeft + cardW
            val cardBottom = cardTop + cardH
            val cornerR = minDim * 0.025f

            // Detect fire-border twist ("burn", "fire", "dark", "age" or final 20% of track)
            val joinedLower = frameState.words.joinToString(" ") { it.displayText }.lowercase(Locale.US)
            val isFireTwist = frameState.songProgressFraction >= 0.80f ||
                joinedLower.contains("burn") ||
                joinedLower.contains("fire") ||
                joinedLower.contains("dark") ||
                joinedLower.contains("page")

            // IDE card surface
            scratch.boxRect.set(cardLeft, cardTop, cardRight, cardBottom)
            scratch.boxPaint.color = applyAlpha(0xF2141821.toInt(), alpha)
            canvas.drawRoundRect(scratch.boxRect, cornerR, cornerR, scratch.boxPaint)

            // Border (subtle slate normally, glowing fiery amber-orange on twist)
            scratch.fxStrokePaint.strokeWidth = max(2f, minDim * (if (isFireTwist) 0.007f else 0.003f))
            scratch.fxStrokePaint.color = applyAlpha(
                if (isFireTwist) 0xFFF97316.toInt() else 0xFF2B3242.toInt(),
                alpha,
            )
            canvas.drawRoundRect(scratch.boxRect, cornerR, cornerR, scratch.fxStrokePaint)

            // Top VS Code tab bar (`lyrics.js`) with flat bottom seam
            scratch.boxRect.set(cardLeft, cardTop, cardRight, cardTop + tabBarH)
            scratch.boxPaint.color = applyAlpha(0xFF0E1118.toInt(), alpha)
            canvas.drawRoundRect(scratch.boxRect, cornerR, cornerR, scratch.boxPaint)
            canvas.drawRect(cardLeft, cardTop + tabBarH * 0.5f, cardRight, cardTop + tabBarH, scratch.boxPaint)

            val dotY = cardTop + tabBarH * 0.5f
            val dotR = max(3f, codeFontPx * 0.16f)
            val dotColors = intArrayOf(0xFFE06C75.toInt(), 0xFFE5C07B.toInt(), 0xFF98C379.toInt())
            for (d in 0 until 3) {
                scratch.fxPaint.color = applyAlpha(dotColors[d], alpha)
                canvas.drawCircle(cardLeft + codeFontPx * (0.75f + d * 0.65f), dotY, dotR, scratch.fxPaint)
            }

            val tabPaint = scratch.fxPaint.apply {
                typeface = monoTypeface
                textSize = codeFontPx * 0.66f
                textAlign = Paint.Align.LEFT
                color = applyAlpha(0xFFD1D5DB.toInt(), alpha)
            }
            val tabFm = tabPaint.fontMetrics
            canvas.drawText(
                "lyrics.js",
                cardLeft + codeFontPx * 2.85f,
                dotY - (tabFm.ascent + tabFm.descent) * 0.5f,
                tabPaint,
            )

            // 3. Code lines (gutter + previous comment + active syntax-highlighted typewriter line + next preview)
            val gutterX = cardLeft + codeFontPx * 0.75f
            val codeX = cardLeft + codeFontPx * 2.35f
            val maxCodeW = (cardRight - codeX - codeFontPx * 0.75f).coerceAtLeast(1f)
            val firstLineY = cardTop + tabBarH + codeFontPx * 1.55f

            val codePaint = scratch.fillPaint.apply {
                typeface = monoTypeface
                textSize = codeFontPx
                textAlign = Paint.Align.LEFT
                letterSpacing = 0f
                clearShadowLayer()
            }

            // Previous line as One Dark Pro orange comment
            val prevComment = frameState.previousLineText?.takeIf { it.isNotBlank() }
                ?.let { "// ${it.lowercase(Locale.US)}" }
                ?: "// 3:12 a.m. signal active"
            codePaint.color = applyAlpha(0xFF4B5563.toInt(), alpha)
            canvas.drawText(String.format(Locale.US, "%02d", lineNumBase), gutterX, firstLineY, codePaint)
            codePaint.color = applyAlpha(0xFFD19A66.toInt(), alpha * 0.85f)
            canvas.drawText(ellipsizeToWidth(prevComment, codePaint, maxCodeW), codeX, firstLineY, codePaint)

            // Active line: syntax-highlighted words with typewriter reveal
            val activeY = firstLineY + lineStep
            codePaint.color = applyAlpha(0xFF9CA3AF.toInt(), alpha)
            canvas.drawText(String.format(Locale.US, "%02d", lineNumBase + 1), gutterX, activeY, codePaint)

            val visibleWords = frameState.words.filter { it.alpha > 0.01f && it.visibleCharCount > 0 }
            val activeTokens = visibleWords.mapIndexed { idx, w ->
                val raw = w.displayText.take(w.visibleCharCount)
                when {
                    idx == 0 -> raw.lowercase(Locale.US)
                    idx == visibleWords.lastIndex && w.isSpoken -> ".${raw.lowercase(Locale.US)}();"
                    else -> ".${raw.lowercase(Locale.US)}"
                }
            }
            val fullLineText = activeTokens.joinToString("")
            val measuredActiveW = codePaint.measureText(fullLineText)
            val activeFontPx = if (measuredActiveW > maxCodeW && measuredActiveW > 1f) {
                (codeFontPx * (maxCodeW / measuredActiveW)).coerceAtLeast(codeFontPx * 0.62f)
            } else {
                codeFontPx
            }
            codePaint.textSize = activeFontPx

            var cursorX = codeX
            visibleWords.forEachIndexed { idx, w ->
                val raw = w.displayText.take(w.visibleCharCount)
                val token = activeTokens[idx]
                val tokenLower = raw.lowercase(Locale.US)
                val tokenColor = when {
                    w.isActive -> 0xFFE5C07B.toInt() // Warm yellow active typing highlight
                    tokenLower == "me" || tokenLower == "if" || tokenLower == "burn" || tokenLower == "no" ->
                        0xFFE06C75.toInt() // One Dark Red keyword
                    idx > 0 -> 0xFF61AFEF.toInt() // One Dark Sky-Blue method call
                    else -> 0xFFF8FAFC.toInt() // Crisp white subject
                }
                codePaint.color = applyAlpha(tokenColor, alpha * w.alpha)
                val tokenW = codePaint.measureText(token)
                if (cursorX + tokenW <= cardRight - codeFontPx * 0.5f) {
                    canvas.drawText(token, cursorX, activeY, codePaint)
                    cursorX += tokenW
                }
            }

            if (style.rainFontEnabled && cursorX > codeX) {
                drawRainGlyphEffect(
                    canvas = canvas,
                    scratch = scratch,
                    drawX = codeX,
                    baselineY = activeY,
                    wordWidth = cursorX - codeX,
                    fontMetrics = codePaint.fontMetrics,
                    fontPx = codeFontPx,
                    wordIndex = frameState.activeLineIndex,
                    timeSec = timeSec,
                    alpha = alpha,
                )
            }

            // Blinking red terminal cursor
            val blinkOn = ((timeSec * 3.2f).toInt() and 1) == 0
            if (frameState.showCursor || blinkOn) {
                val cursorH = codeFontPx * 1.05f
                scratch.boxPaint.color = applyAlpha(0xFFEF4444.toInt(), alpha)
                canvas.drawRect(
                    cursorX + 2f,
                    activeY - cursorH * 0.82f,
                    cursorX + max(3f, codeFontPx * 0.14f),
                    activeY + cursorH * 0.18f,
                    scratch.boxPaint,
                )
            }

            codePaint.textSize = codeFontPx
            // Next line preview dimmed in slate
            val nextY = activeY + lineStep
            codePaint.color = applyAlpha(0xFF4B5563.toInt(), alpha * 0.7f)
            canvas.drawText(String.format(Locale.US, "%02d", lineNumBase + 2), gutterX, nextY, codePaint)
            val nextCode = frameState.nextLineText?.takeIf { it.isNotBlank() }
                ?.let { "if (${it.lowercase(Locale.US)}) {" }
                ?: "return solitude;"
            codePaint.color = applyAlpha(0xFF64748B.toInt(), alpha * 0.65f)
            canvas.drawText(ellipsizeToWidth(nextCode, codePaint, maxCodeW), codeX, nextY, codePaint)

            // 4. Mirrored water reflection & glowing `you` / `me` bridge markers below the code block
            val bridgeY = cardBottom + codeFontPx * 1.65f
            if (bridgeY < heightPx - codeFontPx) {
                // Water horizon line
                scratch.fxStrokePaint.strokeWidth = 1.5f
                scratch.fxStrokePaint.color = applyAlpha(0x4461AFEF, alpha)
                canvas.drawLine(cardLeft, bridgeY, cardRight, bridgeY, scratch.fxStrokePaint)

                // `you` (white-grey glowing block gliding across the bridge with song progress)
                val progress = frameState.songProgressFraction.coerceIn(0f, 1f)
                val youX = cardLeft + cardW * (0.14f + progress * 0.62f)
                val meX = cardRight - cardW * 0.14f
                val markerW = max(10f, codeFontPx * 0.55f)
                val markerH = max(18f, codeFontPx * 1.05f)

                // Red glow trail under `me`
                scratch.boxPaint.color = applyAlpha(0x55EF4444, alpha)
                canvas.drawRect(meX - markerW * 0.4f, bridgeY, meX + markerW * 0.4f, bridgeY + markerH * 1.8f, scratch.boxPaint)
                scratch.boxPaint.color = applyAlpha(0xFFEF4444.toInt(), alpha)
                canvas.drawRect(meX - markerW * 0.5f, bridgeY - markerH, meX + markerW * 0.5f, bridgeY, scratch.boxPaint)

                // White glow trail under `you`
                scratch.boxPaint.color = applyAlpha(0x44E2E8F0, alpha)
                canvas.drawRect(youX - markerW * 0.35f, bridgeY, youX + markerW * 0.35f, bridgeY + markerH * 1.4f, scratch.boxPaint)
                scratch.boxPaint.color = applyAlpha(0xFFE2E8F0.toInt(), alpha)
                canvas.drawRect(youX - markerW * 0.5f, bridgeY - markerH, youX + markerW * 0.5f, bridgeY, scratch.boxPaint)

                val labelPaint = scratch.fxPaint.apply {
                    typeface = monoTypeface
                    textSize = codeFontPx * 0.58f
                    textAlign = Paint.Align.CENTER
                }
                labelPaint.color = applyAlpha(0xFFE2E8F0.toInt(), alpha)
                canvas.drawText("you", youX, bridgeY - markerH - 6f, labelPaint)
                labelPaint.color = applyAlpha(0xFFEF4444.toInt(), alpha)
                canvas.drawText("me", meX, bridgeY - markerH - 6f, labelPaint)
            }

            // 5. Rising golden fire embers on the fire twist
            if (isFireTwist) {
                for (e in 0 until 20) {
                    val seed = (e * 37 + 11)
                    val ex = cardLeft + cardW * (((seed * 19) % 100) / 100f)
                    val rise = ((timeSec * (0.45f + (e % 4) * 0.12f) + (seed % 100) / 100f) % 1f)
                    val ey = cardBottom - rise * (cardH * 1.15f)
                    val er = max(1.8f, minDim * (0.0025f + (e % 3) * 0.0014f))
                    val emberColor = if (e % 2 == 0) 0xFFF59E0B.toInt() else 0xFFFB923C.toInt()
                    scratch.fxPaint.color = applyAlpha(emberColor, alpha * (1f - rise))
                    canvas.drawCircle(ex, ey, er, scratch.fxPaint)
                }
            }
        } finally {
            scratch.fillPaint.textAlign = prevFillAlign
            scratch.fillPaint.letterSpacing = prevLetterSpacing
            scratch.fxPaint.textAlign = prevFxAlign
        }
    }

    private fun drawColdInvertFrame(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        frameState: CaptionFrameState,
    ) {
        val style = frameState.style
        val activeIdx = frameState.words.indexOfFirst { it.isActive && it.visibleCharCount > 0 }
            .takeIf { it >= 0 }
            ?: frameState.words.indexOfLast { it.isSpoken && it.displayText.isNotBlank() }
                .takeIf { it >= 0 }
            ?: frameState.words.indexOfFirst { it.displayText.isNotBlank() }
        if (activeIdx < 0) return

        val activeWord = frameState.words[activeIdx]
        val wordText = activeWord.displayText.trim()
        if (wordText.isEmpty()) return

        val alpha = frameState.lineAlpha.coerceIn(0f, 1f)
        val wordOrdinal = (frameState.activeLineIndex * 7 + activeIdx)
        val isInvertedWhiteBg = (wordOrdinal and 1) == 1
        val useDistressedSerif = (wordOrdinal % 4) == 3

        val bgColor = if (isInvertedWhiteBg) 0xFFF4F4F0.toInt() else 0xFF060608.toInt()
        val fgColor = if (isInvertedWhiteBg) 0xFF0A0A0C.toInt() else 0xFFFFFFFF.toInt()
        val strokeColor = if (isInvertedWhiteBg) 0xFFFFFFFF.toInt() else 0xFF0A0A0C.toInt()

        scratch.boxPaint.color = applyAlpha(bgColor, alpha)
        canvas.drawRect(0f, 0f, widthPx, heightPx, scratch.boxPaint)

        val minDim = min(widthPx, heightPx)
        val typeface = resolveCustomTypeface(style.customFontPath) ?: if (useDistressedSerif) {
            editorialRegularTypeface
        } else {
            resolveTypeface(CaptionFontFamily.CONDENSED_DISPLAY)
        }
        val desiredSize = minDim * 0.21f * style.fontSizeScale.coerceIn(CaptionStyleBounds.FONT_SCALE_RANGE)
        val fillPaint = scratch.fillPaint
        val strokePaint = scratch.strokePaint
        val prevFillAlign = fillPaint.textAlign
        val prevStrokeAlign = strokePaint.textAlign
        val prevLetterSpacing = fillPaint.letterSpacing
        val prevFillTypeface = fillPaint.typeface
        val prevStrokeTypeface = strokePaint.typeface

        try {
            fillPaint.apply {
                this.typeface = typeface
                textSize = desiredSize
                textAlign = Paint.Align.CENTER
                letterSpacing = if (useDistressedSerif) 0.01f else -0.025f
                clearShadowLayer()
            }
            val measuredW = fillPaint.measureText(wordText).coerceAtLeast(1f)
            val maxAllowedW = widthPx * 0.86f
            val fittedSize = if (measuredW > maxAllowedW) {
                desiredSize * (maxAllowedW / measuredW)
            } else {
                desiredSize
            }
            fillPaint.textSize = fittedSize

            strokePaint.apply {
                this.typeface = typeface
                textSize = fittedSize
                textAlign = Paint.Align.CENTER
                letterSpacing = fillPaint.letterSpacing
                strokeWidth = max(2f, fittedSize * 0.032f)
            }

            val fm = fillPaint.fontMetrics
            val timeSec = frameState.presentationTimeUs / 1_000_000f
            val wiggleAmp = minDim * 0.0055f
            val wiggleX = (sin((timeSec * 24.0 + wordOrdinal * 1.7)).toFloat() * 0.65f +
                cos((timeSec * 37.0 + wordOrdinal * 2.3)).toFloat() * 0.35f) * wiggleAmp
            val wiggleY = (cos((timeSec * 21.0 + wordOrdinal * 1.1)).toFloat() * 0.65f +
                sin((timeSec * 33.0 + wordOrdinal * 3.1)).toFloat() * 0.35f) * wiggleAmp

            val centerX = widthPx * style.position.xFraction.coerceIn(CaptionStyleBounds.POSITION_X_RANGE) + wiggleX
            val centerY = heightPx * style.position.yFraction.coerceIn(0.25f, 0.75f) + wiggleY
            val baselineY = centerY - (fm.ascent + fm.descent) / 2f
            val wordScale = if (activeWord.isActive) activeWord.scale.coerceIn(0.85f, 1.25f) else 1.0f

            canvas.save()
            try {
                canvas.scale(wordScale, wordScale, centerX, centerY)

                if (activeWord.isActive && activeWord.fillProgress < 0.25f) {
                    val twitchStrength = (1f - activeWord.fillProgress / 0.25f)
                    val rgbShift = fittedSize * 0.042f * twitchStrength
                    fillPaint.color = applyAlpha(0xB822D3EE.toInt(), alpha * twitchStrength)
                    canvas.drawText(wordText, centerX - rgbShift, baselineY, fillPaint)
                    fillPaint.color = applyAlpha(0xB8FB7185.toInt(), alpha * twitchStrength)
                    canvas.drawText(wordText, centerX + rgbShift, baselineY, fillPaint)
                }

                strokePaint.color = applyAlpha(strokeColor, alpha)
                canvas.drawText(wordText, centerX, baselineY, strokePaint)
                fillPaint.color = applyAlpha(fgColor, alpha)
                canvas.drawText(wordText, centerX, baselineY, fillPaint)

                val textW = fillPaint.measureText(wordText)
                val boxLeft = centerX - textW * 0.5f
                val boxTop = baselineY + fm.ascent * 0.88f
                val boxH = (fm.descent - fm.ascent).coerceAtLeast(fittedSize * 0.6f)
                for (s in 0 until 26) {
                    val h = (wordOrdinal * 41 + s * 73 + 19) and 0x7FFFFFFF
                    val sx = boxLeft + textW * (((h * 29) and 0x7FFFFFFF) % 100 / 100f)
                    val sy = boxTop + boxH * (((h * 47) and 0x7FFFFFFF) % 100 / 100f)
                    val sr = max(1f, fittedSize * (0.008f + (s % 4) * 0.0045f))
                    scratch.fxPaint.color = applyAlpha(bgColor, alpha * (0.45f + (s % 3) * 0.18f))
                    canvas.drawCircle(sx, sy, sr, scratch.fxPaint)
                }

                if (style.rainFontEnabled) {
                    drawRainGlyphEffect(
                        canvas = canvas,
                        scratch = scratch,
                        drawX = centerX - textW * 0.5f,
                        baselineY = baselineY,
                        wordWidth = textW,
                        fontMetrics = fm,
                        fontPx = fittedSize,
                        wordIndex = wordOrdinal,
                        timeSec = timeSec,
                        alpha = alpha,
                    )
                }
            } finally {
                canvas.restore()
            }
        } finally {
            fillPaint.textAlign = prevFillAlign
            fillPaint.letterSpacing = prevLetterSpacing
            fillPaint.typeface = prevFillTypeface
            strokePaint.textAlign = prevStrokeAlign
            strokePaint.letterSpacing = prevLetterSpacing
            strokePaint.typeface = prevStrokeTypeface
        }
    }

    private fun drawKnockoutMaskFrame(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        frameState: CaptionFrameState,
    ) {
        val style = frameState.style
        val activeWord = frameState.words.firstOrNull { it.isActive && it.visibleCharCount > 0 }
            ?: frameState.words.lastOrNull { it.isSpoken && it.displayText.isNotBlank() }
            ?: frameState.words.firstOrNull { it.displayText.isNotBlank() }
            ?: return
        val wordText = activeWord.displayText.trim()
        if (wordText.isEmpty()) return

        val minDim = min(widthPx, heightPx)
        val typeface = resolveCustomTypeface(style.customFontPath) ?: ultraHeavyTypeface
        val desiredSize = minDim * 0.24f * style.fontSizeScale.coerceIn(CaptionStyleBounds.FONT_SCALE_RANGE)

        val knockoutPaint = scratch.knockoutPaint.apply {
            this.typeface = typeface
            textSize = desiredSize
            textAlign = Paint.Align.CENTER
            xfermode = null
        }
        val measuredW = knockoutPaint.measureText(wordText).coerceAtLeast(1f)
        val maxAllowedW = widthPx * 0.88f
        val fittedSize = if (measuredW > maxAllowedW) {
            desiredSize * (maxAllowedW / measuredW)
        } else {
            desiredSize
        }
        knockoutPaint.textSize = fittedSize

        val fm = knockoutPaint.fontMetrics
        val centerX = widthPx * style.position.xFraction.coerceIn(CaptionStyleBounds.POSITION_X_RANGE)
        val centerY = heightPx * style.position.yFraction.coerceIn(0.25f, 0.75f)
        val baselineY = centerY - (fm.ascent + fm.descent) / 2f
        val maskAlpha = frameState.lineAlpha.coerceIn(0f, 1f)

        val saveCount = canvas.saveLayer(0f, 0f, widthPx, heightPx, null)
        try {
            scratch.boxPaint.color = applyAlpha(0xFF000000.toInt(), maskAlpha)
            canvas.drawRect(0f, 0f, widthPx, heightPx, scratch.boxPaint)

            canvas.save()
            val wordScale = if (activeWord.isActive) activeWord.scale.coerceIn(0.6f, 1.4f) else 1.0f
            canvas.scale(wordScale, wordScale, centerX, centerY)
            knockoutPaint.xfermode = clearXfermode
            knockoutPaint.color = 0xFF000000.toInt()
            canvas.drawText(wordText, centerX, baselineY, knockoutPaint)
            knockoutPaint.xfermode = null

            val rimWidth = fittedSize * (style.strokeWidthFraction * 0.25f).coerceIn(0f, 0.04f)
            if (rimWidth >= 1f) {
                val rimPaint = scratch.strokePaint.apply {
                    this.typeface = typeface
                    textSize = fittedSize
                    textAlign = Paint.Align.CENTER
                    strokeWidth = rimWidth
                    color = applyAlpha(0x40FFFFFF, maskAlpha)
                }
                canvas.drawText(wordText, centerX, baselineY, rimPaint)
            }
            canvas.restore()
        } finally {
            knockoutPaint.xfermode = null
            scratch.strokePaint.textAlign = Paint.Align.LEFT
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * Moody aerial dust motes + optional 90-degree rotated vertical left-edge accent word
     * inspired by minimalist nature kinetic lyric visualizers.
     */
    private fun drawMistyDustAndEdgeAccent(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        fontPx: Float,
        frameState: CaptionFrameState,
    ) {
        val alpha = frameState.lineAlpha.coerceIn(0f, 1f)
        val timeSec = frameState.presentationTimeUs / 1_000_000f
        val minDim = min(widthPx, heightPx)

        // Subtle drifting mist / film-dust motes
        for (i in 0 until 18) {
            val seed = (i + 1) * 1.618034f
            val x = ((seed * 37.3f + timeSec * (0.025f + (i % 4) * 0.012f)) % 1f).let {
                if (it < 0f) it + 1f else it
            } * widthPx
            val y = ((seed * 19.7f + sin(timeSec * 0.7f + seed) * 0.03f) % 1f).let {
                if (it < 0f) it + 1f else it
            } * heightPx
            val radius = minDim * (0.0018f + (i % 3) * 0.0011f)
            scratch.fxPaint.color = applyAlpha(0x55FFFFFF, alpha * (0.4f + (i % 3) * 0.2f))
            canvas.drawCircle(x, y, radius, scratch.fxPaint)
        }

        // 90-degree vertical left-edge echo word when a line has 3+ words
        if (frameState.words.size >= 3) {
            val accentWord = frameState.words.last().displayText
            if (accentWord.isNotBlank()) {
                val edgePaint = scratch.fxPaint.apply {
                    typeface = ultraHeavyTypeface
                    textSize = fontPx * 0.42f
                    color = applyAlpha(0xB8FFFFFF.toInt(), alpha)
                    textAlign = Paint.Align.CENTER
                }
                canvas.save()
                val marginX = widthPx * 0.055f
                val midY = heightPx * 0.5f
                canvas.rotate(-90f, marginX, midY)
                canvas.drawText(accentWord, marginX, midY, edgePaint)
                canvas.restore()
                edgePaint.textAlign = Paint.Align.LEFT
            }
        }
    }

    /**
     * Cycles words across Pure White (with 3D offset shadow), Deep Forest Green with crisp
     * white outline, and Hollow Outlined White to mirror high-contrast kinetic lyric collages.
     */
    private fun drawKineticCollageWord(
        canvas: Canvas,
        text: String,
        drawX: Float,
        baselineY: Float,
        wordIndex: Int,
        lineIndex: Int,
        wordState: WordRenderState,
        alpha: Float,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val variant = (wordIndex + lineIndex) % 3
        val outlinePx = max(2f, fillPaint.textSize * 0.055f)
        strokePaint.strokeWidth = outlinePx
        fillPaint.clearShadowLayer()

        when (variant) {
            0 -> {
                // Pure white solid with crisp 3D offset drop shadow
                val shadowShift = fillPaint.textSize * 0.055f
                fillPaint.color = applyAlpha(0xBB052E16.toInt(), alpha)
                canvas.drawText(text, drawX + shadowShift, baselineY + shadowShift, fillPaint)
                fillPaint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha)
                canvas.drawText(text, drawX, baselineY, fillPaint)
            }
            1 -> {
                // Deep forest green fill with bold pure-white outline
                strokePaint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha)
                canvas.drawText(text, drawX, baselineY, strokePaint)
                fillPaint.color = applyAlpha(FOREST_GREEN_ARGB, alpha)
                canvas.drawText(text, drawX, baselineY, fillPaint)
            }
            else -> {
                // Hollow outlined white; fills with translucent forest green when active
                if (wordState.isActive) {
                    fillPaint.color = applyAlpha(0xD914532D.toInt(), alpha)
                    canvas.drawText(text, drawX, baselineY, fillPaint)
                }
                strokePaint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha)
                canvas.drawText(text, drawX, baselineY, strokePaint)
            }
        }
    }

    /**
     * Dark-romantic 2014 zine backdrop: alternates cream-paper and blood-red washes,
     * draws a horizontal zine banner strip behind the lyric block, a spiky red sunburst,
     * and flanking wireframe diamond icons.
     */
    private fun drawCrimsonZineBackdrop(
        canvas: Canvas,
        scratch: RenderScratch,
        widthPx: Float,
        heightPx: Float,
        centerY: Float,
        blockHeight: Float,
        fontPx: Float,
        frameState: CaptionFrameState,
    ) {
        val alpha = frameState.lineAlpha.coerceIn(0f, 1f)
        val isCrimsonPhase = (frameState.activeLineIndex and 1) == 1
        val minDim = min(widthPx, heightPx)
        val timeSec = frameState.presentationTimeUs / 1_000_000f

        // Subtle paper / blood-red mood wash over the underlying sketch or footage
        val washColor = if (isCrimsonPhase) 0x7A4A0404 else 0x26F5EFE0
        scratch.boxPaint.color = applyAlpha(washColor, alpha)
        canvas.drawRect(0f, 0f, widthPx, heightPx, scratch.boxPaint)

        // Horizontal zine bar slicing behind the lyric block
        val barHalfH = max(blockHeight * 0.42f, fontPx * 0.65f)
        val barColor = if (isCrimsonPhase) 0xE6F5EFE0.toInt() else 0xDC8B0000.toInt()
        scratch.boxPaint.color = applyAlpha(barColor, alpha)
        canvas.drawRect(0f, centerY - barHalfH, widthPx, centerY + barHalfH, scratch.boxPaint)

        // Hand-drawn spiky sunburst (top-left on cream lines, bottom-right on crimson lines)
        val sunX = if (isCrimsonPhase) widthPx * 0.84f else widthPx * 0.14f
        val sunY = if (isCrimsonPhase) heightPx * 0.84f else heightPx * 0.14f
        val sunR = minDim * 0.055f
        val sunColor = if (isCrimsonPhase) ZINE_GOLD_ARGB else BLOOD_RED_ARGB
        scratch.fxStrokePaint.color = applyAlpha(sunColor, alpha * 0.9f)
        scratch.fxStrokePaint.strokeWidth = max(2f, minDim * 0.0045f)
        for (ray in 0 until 12) {
            val angle = (ray * (Math.PI * 2.0 / 12.0)) + (timeSec * 0.45)
            val inner = sunR * 0.42f
            val outer = if (ray % 2 == 0) sunR * 1.32f else sunR * 0.92f
            canvas.drawLine(
                sunX + (cos(angle) * inner).toFloat(),
                sunY + (sin(angle) * inner).toFloat(),
                sunX + (cos(angle) * outer).toFloat(),
                sunY + (sin(angle) * outer).toFloat(),
                scratch.fxStrokePaint,
            )
        }
        scratch.fxPaint.color = applyAlpha(sunColor, alpha * 0.85f)
        canvas.drawCircle(sunX, sunY, sunR * 0.34f, scratch.fxPaint)

        // Flanking wireframe diamonds near bottom margin
        val diamondSize = minDim * 0.036f
        val diamondY = heightPx * 0.90f
        val diamondColor = if (isCrimsonPhase) ZINE_CREAM_ARGB else BLOOD_RED_ARGB
        scratch.fxStrokePaint.color = applyAlpha(diamondColor, alpha * 0.88f)
        scratch.fxStrokePaint.strokeWidth = max(1.8f, minDim * 0.0035f)
        drawWireframeDiamond(canvas, scratch, widthPx * 0.22f, diamondY, diamondSize)
        drawWireframeDiamond(canvas, scratch, widthPx * 0.78f, diamondY, diamondSize)
    }

    private fun drawWireframeDiamond(
        canvas: Canvas,
        scratch: RenderScratch,
        cx: Float,
        cy: Float,
        size: Float,
    ) {
        val path = scratch.iconPath
        path.reset()
        path.moveTo(cx - size * 0.55f, cy - size * 0.45f)
        path.lineTo(cx + size * 0.55f, cy - size * 0.45f)
        path.lineTo(cx + size, cy - size * 0.08f)
        path.lineTo(cx, cy + size)
        path.lineTo(cx - size, cy - size * 0.08f)
        path.close()
        canvas.drawPath(path, scratch.fxStrokePaint)
        canvas.drawLine(cx - size, cy - size * 0.08f, cx + size, cy - size * 0.08f, scratch.fxStrokePaint)
        canvas.drawLine(cx - size * 0.45f, cy - size * 0.08f, cx, cy + size, scratch.fxStrokePaint)
        canvas.drawLine(cx + size * 0.45f, cy - size * 0.08f, cx, cy + size, scratch.fxStrokePaint)
    }

    /**
     * Mixed dark-romantic zine typography: cycles words across ransom-note letter boxes,
     * gold serif with floating halo ring, bold black-on-cream/red block + script echo,
     * and glowing crimson/hot-pink accent serif.
     */
    private fun drawCrimsonZineWord(
        canvas: Canvas,
        scratch: RenderScratch,
        text: String,
        drawX: Float,
        baselineY: Float,
        wordWidth: Float,
        fontMetrics: Paint.FontMetrics,
        wordIndex: Int,
        lineIndex: Int,
        wordState: WordRenderState,
        alpha: Float,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val variant = (wordIndex + lineIndex) % 4
        val prevTypeface = fillPaint.typeface
        val prevStrokeTypeface = strokePaint.typeface
        fillPaint.clearShadowLayer()

        when (variant) {
            0 -> {
                // Ransom-note style: each character in its own tilted cream box with blood-red border
                val upper = text.uppercase(Locale.US)
                val boxTop = baselineY + fontMetrics.ascent * 0.92f
                val boxBottom = baselineY + fontMetrics.descent * 0.95f
                val padX = fillPaint.textSize * 0.055f
                fillPaint.typeface = ultraHeavyTypeface
                var totalRansomW = 0f
                for (idx in upper.indices) {
                    totalRansomW += fillPaint.measureText(upper, idx, idx + 1) + padX * 1.4f
                }
                val fitScale = if (totalRansomW > wordWidth && totalRansomW > 1f) {
                    (wordWidth / totalRansomW).coerceIn(0.55f, 1f)
                } else {
                    1f
                }
                val startX = drawX + ((wordWidth - totalRansomW * fitScale) * 0.5f).coerceAtLeast(0f)
                val midY = (boxTop + boxBottom) / 2f
                canvas.save()
                if (fitScale < 1f) {
                    canvas.scale(fitScale, fitScale, startX, midY)
                }
                var charX = startX
                for (idx in upper.indices) {
                    val ch = upper.substring(idx, idx + 1)
                    val chW = fillPaint.measureText(ch)
                    val tilt = if (idx % 2 == 0) -3.5f else 3.2f
                    val midX = charX + chW / 2f
                    canvas.save()
                    canvas.rotate(tilt, midX, midY)
                    scratch.boxRect.set(charX - padX, boxTop, charX + chW + padX, boxBottom)
                    scratch.boxPaint.color = applyAlpha(ZINE_CREAM_ARGB, alpha)
                    canvas.drawRect(scratch.boxRect, scratch.boxPaint)
                    scratch.fxStrokePaint.color = applyAlpha(BLOOD_RED_ARGB, alpha)
                    scratch.fxStrokePaint.strokeWidth = max(2f, fillPaint.textSize * 0.045f)
                    canvas.drawRect(scratch.boxRect, scratch.fxStrokePaint)
                    fillPaint.color = applyAlpha(0xFF111111.toInt(), alpha)
                    canvas.drawText(ch, charX, baselineY, fillPaint)
                    canvas.restore()
                    charX += chW + padX * 1.4f
                }
                canvas.restore()
            }
            1 -> {
                // Gold-yellow serif with floating thin gold halo ring above the word
                fillPaint.typeface = editorialRegularTypeface
                strokePaint.typeface = editorialRegularTypeface
                strokePaint.strokeWidth = max(2f, fillPaint.textSize * 0.055f)
                strokePaint.color = applyAlpha(0xFF1A0505.toInt(), alpha)
                canvas.drawText(text, drawX, baselineY, strokePaint)
                fillPaint.color = applyAlpha(ZINE_GOLD_ARGB, alpha)
                canvas.drawText(text, drawX, baselineY, fillPaint)

                val haloCx = drawX + wordWidth / 2f
                val haloCy = baselineY + fontMetrics.ascent * 1.08f
                val haloRx = max(wordWidth * 0.32f, fillPaint.textSize * 0.45f)
                val haloRy = fillPaint.textSize * 0.14f
                scratch.boxRect.set(haloCx - haloRx, haloCy - haloRy, haloCx + haloRx, haloCy + haloRy)
                scratch.fxStrokePaint.color = applyAlpha(ZINE_GOLD_ARGB, alpha * 0.92f)
                scratch.fxStrokePaint.strokeWidth = max(2f, fillPaint.textSize * 0.04f)
                canvas.drawOval(scratch.boxRect, scratch.fxStrokePaint)
            }
            2 -> {
                // Heavy black block with white outline + cursive white script echo overlaid
                fillPaint.typeface = ultraHeavyTypeface
                strokePaint.typeface = ultraHeavyTypeface
                strokePaint.strokeWidth = max(2.5f, fillPaint.textSize * 0.065f)
                strokePaint.color = applyAlpha(0xFFFFFFFF.toInt(), alpha)
                canvas.drawText(text, drawX, baselineY, strokePaint)
                fillPaint.color = applyAlpha(0xFF0A0A0A.toInt(), alpha)
                canvas.drawText(text, drawX, baselineY, fillPaint)

                fillPaint.typeface = editorialItalicTypeface
                fillPaint.color = applyAlpha(0xE6FFFFFF.toInt(), alpha)
                canvas.drawText(text.lowercase(Locale.US), drawX + fillPaint.textSize * 0.06f, baselineY + fillPaint.textSize * 0.08f, fillPaint)
            }
            else -> {
                // Editorial blood-red / hot-pink accent serif with ghostly offset shadow
                fillPaint.typeface = editorialItalicTypeface
                val accent = if (wordState.isActive) ZINE_PINK_ARGB else BLOOD_RED_ARGB
                val ghostShift = fillPaint.textSize * 0.06f
                fillPaint.color = applyAlpha(0x88000000.toInt(), alpha)
                canvas.drawText(text, drawX + ghostShift, baselineY + ghostShift, fillPaint)
                strokePaint.typeface = editorialItalicTypeface
                strokePaint.strokeWidth = max(1.8f, fillPaint.textSize * 0.04f)
                strokePaint.color = applyAlpha(ZINE_CREAM_ARGB, alpha)
                canvas.drawText(text, drawX, baselineY, strokePaint)
                fillPaint.color = applyAlpha(accent, alpha)
                canvas.drawText(text, drawX, baselineY, fillPaint)
            }
        }

        fillPaint.typeface = prevTypeface
        strokePaint.typeface = prevStrokeTypeface
    }

    /**
     * Unified Rain-Glass glyph effect: renders luminous wet-glass mist glints right across
     * the letterforms plus fine vertical rain trails and teardrop beads shedding directly
     * from each word so the typography and rain read as one cohesive material across every
     * lyric template.
     */
    private fun drawRainGlyphEffect(
        canvas: Canvas,
        scratch: RenderScratch,
        drawX: Float,
        baselineY: Float,
        wordWidth: Float,
        fontMetrics: Paint.FontMetrics,
        fontPx: Float,
        wordIndex: Int,
        timeSec: Float,
        alpha: Float,
    ) {
        if (wordWidth <= 2f || alpha <= 0.01f) return
        val glyphTop = baselineY + fontMetrics.ascent * 0.85f
        val glyphHeight = (fontMetrics.descent - fontMetrics.ascent).coerceAtLeast(fontPx * 0.6f)
        val dropCount = (wordWidth / max(12f, fontPx * 0.42f)).toInt().coerceIn(3, 14)

        val streakPaint = scratch.fxStrokePaint
        val beadPaint = scratch.fxPaint

        for (i in 0 until dropCount) {
            val seed = (wordIndex * 19 + i * 31 + 7)
            val laneFraction = (i + 0.35f + ((seed % 5) * 0.09f)) / dropCount.toFloat()
            val x = drawX + wordWidth * laneFraction.coerceIn(0.04f, 0.96f)

            val fallSpeed = 0.65f + (seed % 5) * 0.16f
            val cycle = ((timeSec * fallSpeed) + ((seed % 100) / 100f)) % 1.0f
            val travelDist = glyphHeight * 1.45f
            val tipY = glyphTop + cycle * travelDist
            val tailLen = fontPx * (0.28f + (seed % 3) * 0.12f)
            val tailTopY = max(glyphTop - fontPx * 0.12f, tipY - tailLen)

            val envelope = when {
                cycle < 0.18f -> cycle / 0.18f
                cycle > 0.72f -> ((1f - cycle) / 0.28f).coerceIn(0f, 1f)
                else -> 1f
            }
            val dropAlpha = alpha * envelope
            if (dropAlpha <= 0.02f) continue

            val windDrift = -tailLen * 0.08f
            streakPaint.strokeWidth = max(1f, fontPx * (if (i % 3 == 0) 0.036f else 0.022f))
            streakPaint.color = applyAlpha(0xC8E0F2FE.toInt(), dropAlpha * 0.72f)
            canvas.drawLine(x - windDrift, tailTopY, x, tipY, streakPaint)

            val beadRadius = max(1.1f, fontPx * (if (i % 3 == 0) 0.034f else 0.022f))
            beadPaint.color = applyAlpha(0xFFFFFFFF.toInt(), dropAlpha * 0.88f)
            canvas.drawCircle(x, tipY, beadRadius, beadPaint)
        }

        val glintCount = (dropCount / 2).coerceIn(2, 6)
        for (g in 0 until glintCount) {
            val gSeed = wordIndex * 13 + g * 29 + 3
            val gx = drawX + wordWidth * (((gSeed * 37) % 90 + 5) / 100f)
            val gy = glyphTop + glyphHeight * (((gSeed * 53) % 72 + 12) / 100f)
            val shimmer = 0.45f + 0.55f * ((sin((timeSec * 3.2f + gSeed).toDouble()).toFloat() + 1f) * 0.5f)
            val glintR = max(1.2f, fontPx * 0.028f)
            beadPaint.color = applyAlpha(0xD9F0F9FF.toInt(), alpha * shimmer * 0.65f)
            canvas.drawCircle(gx, gy, glintR, beadPaint)
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

    private fun resolveCustomTypeface(path: String?): Typeface? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        return synchronized(customTypefaceCache) {
            customTypefaceCache[path] ?: runCatching {
                Typeface.createFromFile(file)
            }.getOrNull()?.also { loaded ->
                if (customTypefaceCache.size >= 12) {
                    val eldest = customTypefaceCache.entries.iterator().next().key
                    customTypefaceCache.remove(eldest)
                }
                customTypefaceCache[path] = loaded
            }
        }
    }

    private fun resolveTypeface(family: CaptionFontFamily): Typeface =
        synchronized(typefaceCache) {
            typefaceCache.getOrPut(family) {
                when (family) {
                    CaptionFontFamily.SANS_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    CaptionFontFamily.CONDENSED_DISPLAY -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    CaptionFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    CaptionFontFamily.SERIF_EDITORIAL -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                    CaptionFontFamily.RAIN_GLASS -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                }
            }
        }

    private fun applyAlpha(argb: Int, alphaMultiplier: Float): Int {
        val baseAlpha = (argb ushr 24) and 0xFF
        val scaledAlpha = (baseAlpha * alphaMultiplier.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (scaledAlpha shl 24) or (argb and 0x00FFFFFF)
    }
}
