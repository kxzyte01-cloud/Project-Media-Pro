package com.projectmediapro.app.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.projectmediapro.core.model.OverlayBlendMode
import com.projectmediapro.core.model.OverlayVisualStyle
import kotlin.math.min

/**
 * Shared 1:1 bitmap styler used by both live Compose preview ([OverlayPreviewLayer])
 * and Media3 GPU export ([TimedBitmapOverlay]) so rounded corners, PiP borders,
 * and blend modes match identically.
 */
object OverlayBitmapStyler {

    /**
     * Returns a styled copy of [source] when [style] requires pixel blending,
     * rounded-corner clipping, or a PiP border; otherwise returns [source] as-is.
     *
     * Ownership contract: this function never recycles [source]. Callers that
     * no longer need [source] should recycle it if the returned bitmap is a
     * different instance (`result !== source`).
     */
    fun applyStyle(source: Bitmap, style: OverlayVisualStyle?): Bitmap {
        val clean = style?.sanitized() ?: return source
        if (!clean.requiresBitmapProcessing) return source

        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        var blended: Bitmap? = null
        val contentBitmap: Bitmap = if (clean.blendMode != OverlayBlendMode.NORMAL) {
            applyBlendMode(source, clean.blendMode).also { blended = it }
        } else {
            source
        }

        val needsGeometry = clean.cornerRadiusFraction > 0f || clean.borderWidthFraction > 0f
        if (!needsGeometry) {
            return contentBitmap
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val shortSide = min(width, height).toFloat()
        val radiusPx = (shortSide * clean.cornerRadiusFraction).coerceAtMost(shortSide / 2f)
        val borderPx = (shortSide * clean.borderWidthFraction).coerceAtMost(shortSide * 0.15f)
        val fullBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (radiusPx > 0f) {
            val clipPath = Path().apply {
                addRoundRect(fullBounds, radiusPx, radiusPx, Path.Direction.CW)
            }
            val saveCount = canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(contentBitmap, 0f, 0f, bitmapPaint)
            canvas.restoreToCount(saveCount)
        } else {
            canvas.drawBitmap(contentBitmap, 0f, 0f, bitmapPaint)
        }

        if (borderPx > 0f) {
            val halfStroke = borderPx / 2f
            val borderBounds = RectF(
                halfStroke,
                halfStroke,
                (width - halfStroke).coerceAtLeast(halfStroke),
                (height - halfStroke).coerceAtLeast(halfStroke),
            )
            val borderRadius = (radiusPx - halfStroke).coerceAtLeast(0f)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = borderPx
                color = clean.borderColorArgb.toInt()
            }
            if (borderRadius > 0f) {
                canvas.drawRoundRect(borderBounds, borderRadius, borderRadius, borderPaint)
            } else {
                canvas.drawRect(borderBounds, borderPaint)
            }
        }

        blended?.let { temp ->
            if (temp !== source && !temp.isRecycled) temp.recycle()
        }
        return output
    }

    private fun applyBlendMode(source: Bitmap, mode: OverlayBlendMode): Bitmap {
        val width = source.width
        val height = source.height
        val argbSource = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return source
        }
        val pixels = IntArray(width * height)
        argbSource.getPixels(pixels, 0, width, 0, 0, width, height)
        if (argbSource !== source && !argbSource.isRecycled) {
            argbSource.recycle()
        }

        for (i in pixels.indices) {
            pixels[i] = OverlayVisualStyle.transformPixel(pixels[i], mode)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
