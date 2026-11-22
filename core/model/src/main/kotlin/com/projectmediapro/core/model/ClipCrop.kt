package com.projectmediapro.core.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Normalized crop rectangle in top-down UI coordinates (`0f..1f`), where `(0f, 0f)` is top-left
 * and `(1f, 1f)` is bottom-right. Identity is `(0f, 0f, 1f, 1f)`.
 */
@Serializable
data class ClipCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val widthFraction: Float
        get() = (right - left).coerceAtLeast(0f)

    val heightFraction: Float
        get() = (bottom - top).coerceAtLeast(0f)

    val centerX: Float
        get() = (left + right) / 2f

    val centerY: Float
        get() = (top + bottom) / 2f

    val isIdentity: Boolean
        get() = abs(left) < EPSILON &&
            abs(top) < EPSILON &&
            abs(right - 1f) < EPSILON &&
            abs(bottom - 1f) < EPSILON

    fun sanitized(): ClipCrop {
        val rawLeft = if (left.isNaN() || left.isInfinite()) 0f else left.coerceIn(0f, 1f - MIN_SPAN)
        val rawTop = if (top.isNaN() || top.isInfinite()) 0f else top.coerceIn(0f, 1f - MIN_SPAN)
        val rawRight = if (right.isNaN() || right.isInfinite()) 1f else right.coerceIn(MIN_SPAN, 1f)
        val rawBottom = if (bottom.isNaN() || bottom.isInfinite()) 1f else bottom.coerceIn(MIN_SPAN, 1f)

        val (clampedLeft, clampedRight) = ensureMinSpan(rawLeft, rawRight)
        val (clampedTop, clampedBottom) = ensureMinSpan(rawTop, rawBottom)
        val candidate = ClipCrop(
            left = clampedLeft,
            top = clampedTop,
            right = clampedRight,
            bottom = clampedBottom,
        )
        return if (candidate.isIdentity) ClipCrop() else candidate
    }

    private fun ensureMinSpan(start: Float, end: Float): Pair<Float, Float> {
        if (end - start >= MIN_SPAN) return start to end
        val safeStart = start.coerceAtMost(1f - MIN_SPAN)
        val safeEnd = (safeStart + MIN_SPAN).coerceAtMost(1f)
        return safeStart to safeEnd
    }

    companion object {
        const val MIN_SPAN: Float = 0.2f
        private const val EPSILON: Float = 0.001f

        /**
         * Returns a centered crop box whose relative width/height ratio matches [targetAspect]
         * assuming a reference source aspect ratio [sourceAspect] (defaults to `16:9`).
         */
        fun centeredForAspect(
            targetAspect: Float,
            sourceAspect: Float = 16f / 9f,
        ): ClipCrop {
            if (targetAspect <= 0f || sourceAspect <= 0f ||
                targetAspect.isNaN() || sourceAspect.isNaN()
            ) {
                return ClipCrop()
            }
            val ratio = targetAspect / sourceAspect
            return if (ratio < 1f) {
                val width = ratio.coerceIn(MIN_SPAN, 1f)
                val inset = (1f - width) / 2f
                ClipCrop(left = inset, top = 0f, right = 1f - inset, bottom = 1f).sanitized()
            } else {
                val height = (1f / ratio).coerceIn(MIN_SPAN, 1f)
                val inset = (1f - height) / 2f
                ClipCrop(left = 0f, top = inset, right = 1f, bottom = 1f - inset).sanitized()
            }
        }
    }
}
