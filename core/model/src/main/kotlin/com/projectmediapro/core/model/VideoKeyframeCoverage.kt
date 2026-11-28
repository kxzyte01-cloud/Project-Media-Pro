package com.projectmediapro.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Coverage clamp for MAIN-TRACK video keyframes (Ken Burns zoom / pan / rotate).
 *
 * The video frame occupies the full NDC square [-1,1]^2 after Presentation. A rotated,
 * translated square of half-size s covers the viewport iff
 *   s >= max(c*(1+|tx|) + sn*(1+|ty|), sn*(1+|tx|) + c*(1+|ty|)),  c=|cos|, sn=|sin|.
 * We enforce the sufficient symmetric bound: s' = max(s, c+sn) capped at MAX_SCALE,
 * then clamp |tx|,|ty| <= s'/(c+sn) - 1. Ken Burns presets (s=1.16, |t|=0.14, margin 0.16)
 * pass through unchanged; zoom-out below full-frame and extreme pans are corrected so
 * black borders are never exposed. Overlay/sticker keyframes must NOT use this clamp.
 */
object VideoKeyframeCoverage {

    data class ClampedTransform(
        val scale: Float,
        val translationX: Float,
        val translationY: Float,
    )

    fun clamp(
        scale: Float,
        rotationDegrees: Float,
        translationX: Float,
        translationY: Float,
    ): ClampedTransform {
        val safeScale = OverlayKeyframe.sanitizeFinite(scale, OverlayKeyframe.DEFAULT_SCALE)
        val safeRotation = OverlayKeyframe.sanitizeFinite(rotationDegrees, 0f)
            .coerceIn(OverlayKeyframe.MIN_ROTATION_DEG, OverlayKeyframe.MAX_ROTATION_DEG)
        val safeTx = OverlayKeyframe.sanitizeFinite(translationX, 0f)
        val safeTy = OverlayKeyframe.sanitizeFinite(translationY, 0f)
        val radians = safeRotation * (PI.toFloat() / 180f)
        val cover = (abs(cos(radians)) + abs(sin(radians))).coerceAtLeast(1f)
        val effectiveScale = maxOf(safeScale, cover).coerceAtMost(OverlayKeyframe.MAX_SCALE)
        val maxTranslation = (effectiveScale / cover - 1f).coerceAtLeast(0f)
        return ClampedTransform(
            scale = effectiveScale,
            translationX = safeTx.coerceIn(-maxTranslation, maxTranslation),
            translationY = safeTy.coerceIn(-maxTranslation, maxTranslation),
        )
    }
}
