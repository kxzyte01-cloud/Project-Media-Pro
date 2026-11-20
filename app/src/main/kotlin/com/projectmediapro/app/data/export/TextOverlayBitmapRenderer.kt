package com.projectmediapro.app.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.TextAlignment
import com.projectmediapro.core.model.TextFontFamily
import com.projectmediapro.core.model.TextOverlayStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Shared WYSIWYG text & lyric bitmap renderer used by live preview and Media3 export.
 * Uses Android [Typeface] families and [StaticLayout.Builder] (API 23+, minSdk 26)
 * so rendering stays 100% offline with zero bundled font assets.
 */
object TextOverlayBitmapRenderer {

    private const val LINE_SPACING_MULTIPLIER = 1.15f
    private const val SHADOW_COLOR = 0xB3000000.toInt()

    fun render(
        text: String,
        style: TextOverlayStyle,
        canvasWidthPx: Int,
        canvasHeightPx: Int,
        maxWidthFraction: Float = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
    ): Bitmap? {
        val cleanedText = text.trim()
        if (cleanedText.isEmpty() || canvasWidthPx <= 0 || canvasHeightPx <= 0) {
            return null
        }

        val safeStyle = style.sanitized()
        val safeWidthFraction = if (maxWidthFraction.isNaN()) {
            Clip.DEFAULT_TEXT_WIDTH_FRACTION
        } else {
            maxWidthFraction.coerceIn(Clip.MIN_OVERLAY_WIDTH_FRACTION, Clip.MAX_OVERLAY_WIDTH_FRACTION)
        }

        val textSizePx = (canvasHeightPx * safeStyle.textSizeFraction).coerceAtLeast(12f)
        val strokeWidthPx = if (safeStyle.strokeWidthFraction > 0f) {
            (textSizePx * safeStyle.strokeWidthFraction).coerceAtLeast(1f)
        } else {
            0f
        }
        val shadowRadiusPx = if (safeStyle.hasShadow) (textSizePx * 0.14f).coerceAtLeast(2f) else 0f
        val shadowOffsetYPx = if (safeStyle.hasShadow) (textSizePx * 0.06f).coerceAtLeast(1f) else 0f

        val padHorizontalPx = if (safeStyle.hasBackground) textSizePx * 0.45f else 0f
        val padVerticalPx = if (safeStyle.hasBackground) textSizePx * 0.28f else 0f
        val extraMarginPx = ceil(strokeWidthPx + shadowRadiusPx + shadowOffsetYPx).toInt() + 2

        val maxBoxWidthPx = (canvasWidthPx * safeWidthFraction).roundToInt().coerceAtLeast(16)
        val maxTextWidthPx = (maxBoxWidthPx - ((padHorizontalPx + extraMarginPx) * 2f).roundToInt())
            .coerceAtLeast(16)

        val typeface = resolveTypeface(safeStyle.fontFamily, safeStyle.isBold, safeStyle.isItalic)
        val layoutAlignment = when (safeStyle.alignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val fillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSizePx
            this.typeface = typeface
            this.color = safeStyle.textColorArgb.toInt()
            this.style = Paint.Style.FILL
            this.letterSpacing = safeStyle.letterSpacingEm
            if (safeStyle.hasShadow && strokeWidthPx <= 0f) {
                setShadowLayer(shadowRadiusPx, 0f, shadowOffsetYPx, SHADOW_COLOR)
            }
        }

        val measureLayout = buildStaticLayout(cleanedText, fillPaint, maxTextWidthPx, layoutAlignment)
        var maxLineWidthPx = 0f
        for (i in 0 until measureLayout.lineCount) {
            maxLineWidthPx = maxOf(maxLineWidthPx, measureLayout.getLineWidth(i))
        }
        val tightTextWidthPx = ceil(maxLineWidthPx).toInt().coerceIn(1, maxTextWidthPx)
        val finalFillLayout = if (tightTextWidthPx < maxTextWidthPx) {
            buildStaticLayout(cleanedText, fillPaint, tightTextWidthPx, layoutAlignment)
        } else {
            measureLayout
        }

        val strokeLayout = if (strokeWidthPx > 0f) {
            val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = textSizePx
                this.typeface = typeface
                this.color = safeStyle.strokeColorArgb.toInt()
                this.style = Paint.Style.STROKE
                this.strokeWidth = strokeWidthPx
                this.strokeJoin = Paint.Join.ROUND
                this.strokeCap = Paint.Cap.ROUND
                this.letterSpacing = safeStyle.letterSpacingEm
                if (safeStyle.hasShadow) {
                    setShadowLayer(shadowRadiusPx, 0f, shadowOffsetYPx, SHADOW_COLOR)
                }
            }
            buildStaticLayout(cleanedText, strokePaint, finalFillLayout.width, layoutAlignment)
        } else {
            null
        }

        val bitmapWidth = (finalFillLayout.width + (padHorizontalPx * 2f).roundToInt() + extraMarginPx * 2)
            .coerceIn(1, canvasWidthPx)
        val bitmapHeight = (finalFillLayout.height + (padVerticalPx * 2f).roundToInt() + extraMarginPx * 2)
            .coerceIn(1, canvasHeightPx)

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (safeStyle.hasBackground) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = safeStyle.backgroundColorArgb.toInt()
                this.style = Paint.Style.FILL
            }
            val bgRect = RectF(
                extraMarginPx.toFloat(),
                extraMarginPx.toFloat(),
                (bitmapWidth - extraMarginPx).toFloat().coerceAtLeast(extraMarginPx + 1f),
                (bitmapHeight - extraMarginPx).toFloat().coerceAtLeast(extraMarginPx + 1f),
            )
            val cornerRadius = (textSizePx * 0.30f).coerceAtLeast(6f)
            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
        }

        canvas.save()
        canvas.translate(extraMarginPx + padHorizontalPx, extraMarginPx + padVerticalPx)
        strokeLayout?.draw(canvas)
        finalFillLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        alignment: Layout.Alignment,
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .build()
    }

    private fun resolveTypeface(
        family: TextFontFamily,
        isBold: Boolean,
        isItalic: Boolean,
    ): Typeface {
        val styleFlags = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val base = when (family) {
            TextFontFamily.SANS -> Typeface.SANS_SERIF
            TextFontFamily.SERIF -> Typeface.SERIF
            TextFontFamily.MONOSPACE -> Typeface.MONOSPACE
            TextFontFamily.CONDENSED -> Typeface.create("sans-serif-condensed", styleFlags)
        }
        return if (family == TextFontFamily.CONDENSED) {
            base
        } else {
            Typeface.create(base, styleFlags)
        }
    }
}
