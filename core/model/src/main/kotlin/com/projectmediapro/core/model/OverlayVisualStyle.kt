package com.projectmediapro.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compositing blend modes for image / Picture-in-Picture overlays.
 *
 * All modes execute identically in both live preview and GPU export.
 */
@Serializable
enum class OverlayBlendMode {
    /** Standard alpha compositing. */
    NORMAL,

    /**
     * Luminance-keyed screen blend tailored for black-background light leaks,
     * fire, smoke, and lens flares: near-black pixels become transparent while
     * bright highlights stay luminous.
     */
    SCREEN_LUMA,

    /**
     * Crisp highlight-only blend that aggressively keys out shadows and mid-darks
     * for snow, sparks, and subtle light streaks.
     */
    LIGHTEN,

    /**
     * Soft darkened color-wash look that lifts near-white areas to transparency
     * while preserving rich midtones and shadows.
     */
    MULTIPLY,
}

/**
 * Visual styling for an image / PiP overlay clip.
 *
 * Stored as a nullable field on [Clip] so existing `schemaVersion = 1` projects
 * deserialize with zero migration overhead.
 */
@Serializable
data class OverlayVisualStyle(
    val cornerRadiusFraction: Float = 0f,
    val borderWidthFraction: Float = 0f,
    val borderColorArgb: Long = DEFAULT_BORDER_COLOR_ARGB,
    val opacity: Float = DEFAULT_OPACITY,
    val blendMode: OverlayBlendMode = OverlayBlendMode.NORMAL,
) {
    val isIdentity: Boolean
        get() = cornerRadiusFraction <= EPSILON &&
            borderWidthFraction <= EPSILON &&
            opacity >= (DEFAULT_OPACITY - EPSILON) &&
            blendMode == OverlayBlendMode.NORMAL

    val requiresBitmapProcessing: Boolean
        get() = cornerRadiusFraction > EPSILON ||
            borderWidthFraction > EPSILON ||
            blendMode != OverlayBlendMode.NORMAL

    fun sanitized(): OverlayVisualStyle {
        val cleanCorner = OverlayKeyframe.sanitizeFinite(cornerRadiusFraction, 0f)
            .coerceIn(0f, MAX_CORNER_RADIUS_FRACTION)
        val cleanBorder = OverlayKeyframe.sanitizeFinite(borderWidthFraction, 0f)
            .coerceIn(0f, MAX_BORDER_WIDTH_FRACTION)
        val cleanOpacity = OverlayKeyframe.sanitizeFinite(opacity, DEFAULT_OPACITY)
            .coerceIn(MIN_OPACITY, DEFAULT_OPACITY)
        val cleanColor = (borderColorArgb and 0xFFFFFFFFL).let { argb ->
            if ((argb ushr 24) == 0L) argb or 0xFF000000L else argb
        }
        return OverlayVisualStyle(
            cornerRadiusFraction = if (cleanCorner <= EPSILON) 0f else cleanCorner,
            borderWidthFraction = if (cleanBorder <= EPSILON) 0f else cleanBorder,
            borderColorArgb = cleanColor,
            opacity = cleanOpacity,
            blendMode = blendMode,
        )
    }

    companion object {
        const val MIN_VISIBLE_FRACTION: Float = 0.0005f
        private const val EPSILON = MIN_VISIBLE_FRACTION

        const val MAX_CORNER_RADIUS_FRACTION: Float = 0.5f
        const val MAX_BORDER_WIDTH_FRACTION: Float = 0.08f
        const val MIN_OPACITY: Float = 0.05f
        const val DEFAULT_OPACITY: Float = 1.0f
        const val DEFAULT_BORDER_COLOR_ARGB: Long = 0xFFFFFFFFL

        /** Curated high-contrast PiP border color swatches (ARGB). */
        val BORDER_COLOR_SWATCHES: List<Long> = listOf(
            0xFFFFFFFFL, // Crisp White
            0xFF111827L, // Obsidian Black
            0xFF22D3EEL, // Neon Cyan
            0xFFFBBF24L, // Amber Gold
            0xFFFB7185L, // Coral Rose
            0xFF34D399L, // Emerald
        )

        /**
         * Transforms a single ARGB pixel according to [mode].
         * Pure JVM helper so both the Android bitmap styler and unit tests share
         * identical math.
         */
        fun transformPixel(argb: Int, mode: OverlayBlendMode): Int {
            if (mode == OverlayBlendMode.NORMAL) return argb
            val a = (argb ushr 24) and 0xFF
            if (a == 0) return 0
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF

            return when (mode) {
                OverlayBlendMode.NORMAL -> argb
                OverlayBlendMode.SCREEN_LUMA -> {
                    val maxCh = max(r, max(g, b))
                    if (maxCh <= 6) {
                        0
                    } else {
                        val lumaFactor = ((maxCh - 6) / 249f).coerceIn(0f, 1f)
                        val newA = (a * lumaFactor).roundToInt().coerceIn(0, 255)
                        // Un-darken RGB slightly so bright light leaks stay vibrant
                        val lift = 255f / maxCh.coerceAtLeast(1)
                        val blendLift = 1f + (lift - 1f) * 0.45f
                        val newR = (r * blendLift).roundToInt().coerceIn(0, 255)
                        val newG = (g * blendLift).roundToInt().coerceIn(0, 255)
                        val newB = (b * blendLift).roundToInt().coerceIn(0, 255)
                        (newA shl 24) or (newR shl 16) or (newG shl 8) or newB
                    }
                }
                OverlayBlendMode.LIGHTEN -> {
                    val maxCh = max(r, max(g, b))
                    if (maxCh <= 28) {
                        0
                    } else {
                        val norm = ((maxCh - 28) / 227f).coerceIn(0f, 1f)
                        // Quadratic ramp keeps sparks/highlights crisp while dropping shadows
                        val newA = (a * norm * norm).roundToInt().coerceIn(0, 255)
                        (newA shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
                OverlayBlendMode.MULTIPLY -> {
                    // Lift near-white areas to transparency while darkening rich tones
                    val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                    val darkness = (1f - luma).coerceIn(0f, 1f)
                    val newA = (a * (0.20f + 0.80f * darkness)).roundToInt().coerceIn(0, 255)
                    val newR = (r * 0.88f).roundToInt().coerceIn(0, 255)
                    val newG = (g * 0.88f).roundToInt().coerceIn(0, 255)
                    val newB = (b * 0.88f).roundToInt().coerceIn(0, 255)
                    (newA shl 24) or (newR shl 16) or (newG shl 8) or newB
                }
            }
        }
    }
}
