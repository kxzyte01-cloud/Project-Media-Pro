package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class VideoFilterPreset {
    NONE,
    VIVID,
    WARM,
    COOL,
    CINEMA,
    VINTAGE,
    BW,
    FADE_FILM,
    DREAMY_GLOW,
    RETRO_FILM,
    CYBER_NEON,
    NOIR_PUNCH,
    MISTY_MOSS,
    GOLDEN_SUNSET,
    CRIMSON_MOOD,
}

@Serializable
enum class ClipTransition {
    NONE,
    FADE_BLACK,
    FADE_WHITE,
    ZOOM_IN,
    ZOOM_OUT,
    WHIP_LEFT,
    WHIP_RIGHT,
    SLIDE_UP,
}

/**
 * Evaluated per-frame motion + scrim transform for a [ClipTransition], shared 1:1 between
 * Compose live preview and Media3 GPU export.
 */
data class ClipTransitionTransform(
    val scale: Float = 1f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
    val scrimAlpha: Float = 0f,
    val scrimIsWhite: Boolean = false,
) {
    val isIdentity: Boolean
        get() = scale == 1f &&
            translationXFraction == 0f &&
            translationYFraction == 0f &&
            scrimAlpha <= 0.001f

    companion object {
        val IDENTITY = ClipTransitionTransform()
    }
}

/**
 * Single source of truth for each [VideoFilterPreset]'s color grading parameters, consumed by both
 * Media3 GPU export (`VideoEffectFactory`) and live dialog preview (`ClipEffectsDialog`).
 */
data class FilterGradeSpec(
    val redScale: Float = 1f,
    val greenScale: Float = 1f,
    val blueScale: Float = 1f,
    val saturationDelta: Float = 0f,
    val contrastDelta: Float = 0f,
    val brightnessDelta: Float = 0f,
    val isGrayscale: Boolean = false,
    val isSepia: Boolean = false,
) {
    val hasRgbScale: Boolean
        get() = redScale != 1f || greenScale != 1f || blueScale != 1f

    companion object {
        const val SEPIA_RR = 0.393f
        const val SEPIA_RG = 0.769f
        const val SEPIA_RB = 0.189f
        const val SEPIA_GR = 0.349f
        const val SEPIA_GG = 0.686f
        const val SEPIA_GB = 0.168f
        const val SEPIA_BR = 0.272f
        const val SEPIA_BG = 0.534f
        const val SEPIA_BB = 0.131f
    }
}

fun VideoFilterPreset.gradeSpec(): FilterGradeSpec = when (this) {
    VideoFilterPreset.NONE -> FilterGradeSpec()
    VideoFilterPreset.VIVID -> FilterGradeSpec(
        saturationDelta = 32f,
        contrastDelta = 0.14f,
    )
    VideoFilterPreset.WARM -> FilterGradeSpec(
        redScale = 1.12f,
        greenScale = 1.03f,
        blueScale = 0.88f,
        saturationDelta = 10f,
    )
    VideoFilterPreset.COOL -> FilterGradeSpec(
        redScale = 0.88f,
        greenScale = 1.01f,
        blueScale = 1.14f,
        contrastDelta = 0.06f,
    )
    VideoFilterPreset.CINEMA -> FilterGradeSpec(
        redScale = 1.06f,
        greenScale = 1.00f,
        blueScale = 0.92f,
        saturationDelta = -14f,
        contrastDelta = 0.20f,
    )
    VideoFilterPreset.VINTAGE -> FilterGradeSpec(
        contrastDelta = -0.06f,
        isSepia = true,
    )
    VideoFilterPreset.BW -> FilterGradeSpec(
        contrastDelta = 0.10f,
        isGrayscale = true,
    )
    VideoFilterPreset.FADE_FILM -> FilterGradeSpec(
        saturationDelta = -24f,
        contrastDelta = -0.14f,
        brightnessDelta = 0.06f,
    )
    VideoFilterPreset.DREAMY_GLOW -> FilterGradeSpec(
        redScale = 1.08f,
        greenScale = 1.03f,
        blueScale = 1.06f,
        saturationDelta = 14f,
        contrastDelta = -0.10f,
        brightnessDelta = 0.10f,
    )
    VideoFilterPreset.RETRO_FILM -> FilterGradeSpec(
        redScale = 1.10f,
        greenScale = 1.02f,
        blueScale = 0.84f,
        saturationDelta = -12f,
        contrastDelta = 0.12f,
        brightnessDelta = 0.03f,
    )
    VideoFilterPreset.CYBER_NEON -> FilterGradeSpec(
        redScale = 1.12f,
        greenScale = 0.90f,
        blueScale = 1.22f,
        saturationDelta = 38f,
        contrastDelta = 0.22f,
    )
    VideoFilterPreset.NOIR_PUNCH -> FilterGradeSpec(
        contrastDelta = 0.34f,
        brightnessDelta = -0.04f,
        isGrayscale = true,
    )
    VideoFilterPreset.MISTY_MOSS -> FilterGradeSpec(
        redScale = 0.88f,
        greenScale = 1.04f,
        blueScale = 0.96f,
        saturationDelta = -22f,
        contrastDelta = 0.10f,
        brightnessDelta = -0.04f,
    )
    VideoFilterPreset.GOLDEN_SUNSET -> FilterGradeSpec(
        redScale = 1.14f,
        greenScale = 1.02f,
        blueScale = 0.84f,
        saturationDelta = 24f,
        contrastDelta = 0.08f,
        brightnessDelta = 0.03f,
    )
    VideoFilterPreset.CRIMSON_MOOD -> FilterGradeSpec(
        redScale = 1.18f,
        greenScale = 0.84f,
        blueScale = 0.86f,
        saturationDelta = -18f,
        contrastDelta = 0.18f,
        brightnessDelta = -0.06f,
    )
}

@Serializable
data class ClipVisualEffects(
    val filter: VideoFilterPreset = VideoFilterPreset.NONE,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val transitionIn: ClipTransition = ClipTransition.NONE,
    val fadeInMs: Long = 0L,
    val transitionOut: ClipTransition = ClipTransition.NONE,
    val fadeOutMs: Long = 0L,
) {
    val hasFades: Boolean
        get() = (transitionIn != ClipTransition.NONE && fadeInMs > 0L) ||
            (transitionOut != ClipTransition.NONE && fadeOutMs > 0L)

    val isIdentity: Boolean
        get() = filter == VideoFilterPreset.NONE &&
            brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            warmth == 0f &&
            !hasFades

    fun warmthRgbScales(): Pair<Float, Float> {
        val safeWarmth = clampFloat(warmth, MIN_WARMTH, MAX_WARMTH)
        val redScale = (1f + safeWarmth * WARMTH_CHANNEL_FACTOR).coerceIn(0.5f, 1.5f)
        val blueScale = (1f - safeWarmth * WARMTH_CHANNEL_FACTOR).coerceIn(0.5f, 1.5f)
        return redScale to blueScale
    }

    fun sanitized(maxClipDurationMs: Long? = null): ClipVisualEffects {
        val maxFadeMs = if (maxClipDurationMs != null && maxClipDurationMs > 0L) {
            minOf(MAX_FADE_MS, maxClipDurationMs / 2L)
        } else {
            MAX_FADE_MS
        }
        val cleanInMs = if (transitionIn == ClipTransition.NONE) {
            0L
        } else {
            fadeInMs.coerceIn(0L, maxFadeMs)
        }
        val cleanOutMs = if (transitionOut == ClipTransition.NONE) {
            0L
        } else {
            fadeOutMs.coerceIn(0L, maxFadeMs)
        }
        return copy(
            brightness = clampFloat(brightness, MIN_BRIGHTNESS, MAX_BRIGHTNESS),
            contrast = clampFloat(contrast, MIN_CONTRAST, MAX_CONTRAST),
            saturation = clampFloat(saturation, MIN_SATURATION, MAX_SATURATION),
            warmth = clampFloat(warmth, MIN_WARMTH, MAX_WARMTH),
            transitionIn = if (cleanInMs > 0L) transitionIn else ClipTransition.NONE,
            fadeInMs = cleanInMs,
            transitionOut = if (cleanOutMs > 0L) transitionOut else ClipTransition.NONE,
            fadeOutMs = cleanOutMs,
        )
    }

    fun fadeOverlayAt(
        positionInClipMs: Long,
        clipDurationMs: Long,
    ): Pair<ClipTransition, Float>? {
        if (clipDurationMs <= 0L) return null
        val safe = sanitized(clipDurationMs)
        val posMs = positionInClipMs.coerceIn(0L, clipDurationMs)

        if (safe.transitionIn != ClipTransition.NONE && safe.fadeInMs > 0L && posMs < safe.fadeInMs) {
            val progress = (posMs.toFloat() / safe.fadeInMs.toFloat()).coerceIn(0f, 1f)
            return safe.transitionIn to (1f - progress)
        }
        if (safe.transitionOut != ClipTransition.NONE && safe.fadeOutMs > 0L) {
            val fadeOutStartMs = clipDurationMs - safe.fadeOutMs
            if (posMs >= fadeOutStartMs) {
                val elapsed = (posMs - fadeOutStartMs).toFloat()
                val progress = (elapsed / safe.fadeOutMs.toFloat()).coerceIn(0f, 1f)
                return safe.transitionOut to progress
            }
        }
        return null
    }

    fun transitionTransformAt(
        positionInClipMs: Long,
        clipDurationMs: Long,
    ): ClipTransitionTransform {
        if (clipDurationMs <= 0L) return ClipTransitionTransform.IDENTITY
        val safe = sanitized(clipDurationMs)
        val posMs = positionInClipMs.coerceIn(0L, clipDurationMs)

        // Incoming transition (amount goes 1f -> 0f as clip enters)
        if (safe.transitionIn != ClipTransition.NONE && safe.fadeInMs > 0L && posMs < safe.fadeInMs) {
            val raw = (1f - posMs.toFloat() / safe.fadeInMs.toFloat()).coerceIn(0f, 1f)
            return evalTransitionTransform(safe.transitionIn, raw, isExiting = false)
        }
        // Outgoing transition (amount goes 0f -> 1f as clip exits)
        if (safe.transitionOut != ClipTransition.NONE && safe.fadeOutMs > 0L) {
            val outStart = clipDurationMs - safe.fadeOutMs
            if (posMs >= outStart) {
                val raw = ((posMs - outStart).toFloat() / safe.fadeOutMs.toFloat()).coerceIn(0f, 1f)
                return evalTransitionTransform(safe.transitionOut, raw, isExiting = true)
            }
        }
        return ClipTransitionTransform.IDENTITY
    }

    private fun evalTransitionTransform(
        transition: ClipTransition,
        rawAmount: Float,
        isExiting: Boolean,
    ): ClipTransitionTransform {
        val eased = rawAmount * rawAmount * (3f - 2f * rawAmount) // cubic smoothstep
        val dir = if (isExiting) -1f else 1f
        return when (transition) {
            ClipTransition.NONE -> ClipTransitionTransform.IDENTITY
            ClipTransition.FADE_BLACK -> ClipTransitionTransform(scrimAlpha = eased, scrimIsWhite = false)
            ClipTransition.FADE_WHITE -> ClipTransitionTransform(scrimAlpha = eased, scrimIsWhite = true)
            ClipTransition.ZOOM_IN -> ClipTransitionTransform(
                scale = 1f + 0.36f * eased,
                scrimAlpha = (eased * 0.45f).coerceIn(0f, 0.5f),
            )
            ClipTransition.ZOOM_OUT -> ClipTransitionTransform(
                scale = (1f - 0.24f * eased).coerceAtLeast(0.72f),
                scrimAlpha = (eased * 0.50f).coerceIn(0f, 0.55f),
            )
            ClipTransition.WHIP_LEFT -> ClipTransitionTransform(
                scale = 1f + 0.14f * eased,
                translationXFraction = -0.35f * eased * dir,
                scrimAlpha = (eased * 0.35f).coerceIn(0f, 0.4f),
            )
            ClipTransition.WHIP_RIGHT -> ClipTransitionTransform(
                scale = 1f + 0.14f * eased,
                translationXFraction = 0.35f * eased * dir,
                scrimAlpha = (eased * 0.35f).coerceIn(0f, 0.4f),
            )
            ClipTransition.SLIDE_UP -> ClipTransitionTransform(
                scale = 1f + 0.10f * eased,
                translationYFraction = -0.32f * eased * dir,
                scrimAlpha = (eased * 0.35f).coerceIn(0f, 0.4f),
            )
        }
    }

    companion object {
        const val MIN_BRIGHTNESS: Float = -0.5f
        const val MAX_BRIGHTNESS: Float = 0.5f
        const val MIN_CONTRAST: Float = -0.5f
        const val MAX_CONTRAST: Float = 0.5f
        const val MIN_SATURATION: Float = -100f
        const val MAX_SATURATION: Float = 100f
        const val MIN_WARMTH: Float = -0.5f
        const val MAX_WARMTH: Float = 0.5f
        const val WARMTH_CHANNEL_FACTOR: Float = 0.30f

        const val MIN_FADE_MS: Long = 100L
        const val DEFAULT_FADE_MS: Long = 600L
        const val MAX_FADE_MS: Long = 3_000L

        private fun clampFloat(value: Float, min: Float, max: Float): Float {
            if (value.isNaN()) return 0f
            return value.coerceIn(min, max)
        }
    }
}
