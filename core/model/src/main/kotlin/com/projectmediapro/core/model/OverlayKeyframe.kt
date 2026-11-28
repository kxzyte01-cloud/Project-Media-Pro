package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyframeEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
}

enum class KeyframePresetId {
    NONE,
    FADE_IN_OUT,
    ZOOM_IN,
    SLIDE_UP,
    POP_SPIN,
    KEN_BURNS_ZOOM_IN,
    KEN_BURNS_PAN_LEFT,
    KEN_BURNS_PAN_RIGHT,
}

/**
 * One keyframe on an overlay or text clip. [timeFraction] is normalized (`0f..1f`) across the
 * clip's duration so keyframes automatically stretch if the clip's timing changes.
 */
@Serializable
data class OverlayKeyframe(
    val timeFraction: Float = 0f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val scale: Float = DEFAULT_SCALE,
    val rotationDegrees: Float = 0f,
    val opacity: Float = DEFAULT_OPACITY,
    val easing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT,
) {
    fun sanitized(
        defaultCenterX: Float = 0.5f,
        defaultCenterY: Float = 0.5f,
    ): OverlayKeyframe {
        val safeDefaultX = sanitizeCoordinate(defaultCenterX, 0.5f)
        val safeDefaultY = sanitizeCoordinate(defaultCenterY, 0.5f)
        return OverlayKeyframe(
            timeFraction = sanitizeFinite(timeFraction, 0f).coerceIn(0f, 1f),
            centerX = sanitizeFinite(centerX, safeDefaultX).coerceIn(0f, 1f),
            centerY = sanitizeFinite(centerY, safeDefaultY).coerceIn(0f, 1f),
            scale = sanitizeFinite(scale, DEFAULT_SCALE).coerceIn(MIN_SCALE, MAX_SCALE),
            rotationDegrees = sanitizeFinite(rotationDegrees, 0f).coerceIn(MIN_ROTATION_DEG, MAX_ROTATION_DEG),
            opacity = sanitizeFinite(opacity, DEFAULT_OPACITY).coerceIn(0f, 1f),
            easing = easing,
        )
    }

    companion object {
        const val MIN_SCALE: Float = 0.25f
        const val DEFAULT_SCALE: Float = 1.0f
        const val MAX_SCALE: Float = 2.5f
        const val MIN_ROTATION_DEG: Float = -180f
        const val MAX_ROTATION_DEG: Float = 180f
        const val DEFAULT_OPACITY: Float = 1.0f
        const val MAX_KEYFRAMES_PER_CLIP: Int = 12

        fun sanitizeFinite(value: Float, fallback: Float): Float =
            if (value.isNaN() || value.isInfinite()) fallback else value

        fun sanitizeCoordinate(value: Float, fallback: Float = 0.5f): Float =
            sanitizeFinite(value, fallback).coerceIn(0f, 1f)
    }
}

data class ResolvedOverlayTransform(
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val opacity: Float,
)

object KeyframeInterpolator {

    fun sanitizeList(
        keyframes: List<OverlayKeyframe>,
        defaultCenterX: Float = 0.5f,
        defaultCenterY: Float = 0.5f,
    ): List<OverlayKeyframe> {
        if (keyframes.isEmpty()) return emptyList()
        val safeX = OverlayKeyframe.sanitizeCoordinate(defaultCenterX, 0.5f)
        val safeY = OverlayKeyframe.sanitizeCoordinate(defaultCenterY, 0.5f)
        return keyframes
            .map { it.sanitized(safeX, safeY) }
            .sortedBy { it.timeFraction }
            .take(OverlayKeyframe.MAX_KEYFRAMES_PER_CLIP)
    }

    fun evaluateAtTime(
        baseCenterX: Float,
        baseCenterY: Float,
        keyframes: List<OverlayKeyframe>,
        elapsedTime: Long,
        totalDuration: Long,
    ): ResolvedOverlayTransform {
        val progress = progressFor(elapsedTime, totalDuration)
        return evaluate(baseCenterX, baseCenterY, keyframes, progress)
    }

    fun evaluateSanitizedAtTime(
        baseCenterX: Float,
        baseCenterY: Float,
        sanitizedKeyframes: List<OverlayKeyframe>,
        elapsedTime: Long,
        totalDuration: Long,
    ): ResolvedOverlayTransform {
        val progress = progressFor(elapsedTime, totalDuration)
        return evaluateSanitized(baseCenterX, baseCenterY, sanitizedKeyframes, progress)
    }

    fun evaluate(
        baseCenterX: Float,
        baseCenterY: Float,
        keyframes: List<OverlayKeyframe>,
        progress: Float,
    ): ResolvedOverlayTransform {
        val safeBaseX = OverlayKeyframe.sanitizeCoordinate(baseCenterX, 0.5f)
        val safeBaseY = OverlayKeyframe.sanitizeCoordinate(baseCenterY, 0.5f)
        if (keyframes.isEmpty()) {
            return staticTransform(safeBaseX, safeBaseY)
        }
        val sorted = sanitizeList(keyframes, safeBaseX, safeBaseY)
        return evaluateSanitized(safeBaseX, safeBaseY, sorted, progress)
    }

    fun evaluateSanitized(
        baseCenterX: Float,
        baseCenterY: Float,
        sanitizedKeyframes: List<OverlayKeyframe>,
        progress: Float,
    ): ResolvedOverlayTransform {
        if (sanitizedKeyframes.isEmpty()) {
            return staticTransform(
                OverlayKeyframe.sanitizeCoordinate(baseCenterX, 0.5f),
                OverlayKeyframe.sanitizeCoordinate(baseCenterY, 0.5f),
            )
        }
        val t = OverlayKeyframe.sanitizeFinite(progress, 0f).coerceIn(0f, 1f)
        val first = sanitizedKeyframes.first()
        if (sanitizedKeyframes.size == 1 || t <= first.timeFraction) {
            return first.toResolved()
        }
        val last = sanitizedKeyframes.last()
        if (t >= last.timeFraction) {
            return last.toResolved()
        }

        var prev = first
        for (i in 1 until sanitizedKeyframes.size) {
            val next = sanitizedKeyframes[i]
            if (t <= next.timeFraction) {
                val span = next.timeFraction - prev.timeFraction
                if (span <= 0.0001f) return next.toResolved()
                val rawAlpha = ((t - prev.timeFraction) / span).coerceIn(0f, 1f)
                val eased = applyEasing(rawAlpha, next.easing)
                return ResolvedOverlayTransform(
                    centerX = lerp(prev.centerX, next.centerX, eased).coerceIn(0f, 1f),
                    centerY = lerp(prev.centerY, next.centerY, eased).coerceIn(0f, 1f),
                    scale = lerp(prev.scale, next.scale, eased).coerceIn(
                        OverlayKeyframe.MIN_SCALE,
                        OverlayKeyframe.MAX_SCALE,
                    ),
                    rotationDegrees = lerp(prev.rotationDegrees, next.rotationDegrees, eased).coerceIn(
                        OverlayKeyframe.MIN_ROTATION_DEG,
                        OverlayKeyframe.MAX_ROTATION_DEG,
                    ),
                    opacity = lerp(prev.opacity, next.opacity, eased).coerceIn(0f, 1f),
                )
            }
            prev = next
        }
        return last.toResolved()
    }

    fun suggestNextTimeFraction(keyframes: List<OverlayKeyframe>): Float {
        if (keyframes.isEmpty()) return 0f
        val sorted = keyframes.map { OverlayKeyframe.sanitizeFinite(it.timeFraction, 0f).coerceIn(0f, 1f) }.sorted()
        if (sorted.size == 1) {
            return if (sorted[0] < 0.5f) 1f else 0f
        }
        var bestMid = 0.5f
        var largestGap = -1f
        if (sorted.first() > 0.05f) {
            largestGap = sorted.first()
            bestMid = 0f
        }
        for (i in 0 until sorted.lastIndex) {
            val gap = sorted[i + 1] - sorted[i]
            if (gap > largestGap) {
                largestGap = gap
                bestMid = (sorted[i] + sorted[i + 1]) / 2f
            }
        }
        val tailGap = 1f - sorted.last()
        if (tailGap > largestGap && tailGap > 0.05f) {
            bestMid = 1f
        }
        return bestMid.coerceIn(0f, 1f)
    }

    fun applyEasing(t: Float, easing: KeyframeEasing): Float {
        val x = OverlayKeyframe.sanitizeFinite(t, 0f).coerceIn(0f, 1f)
        return when (easing) {
            KeyframeEasing.LINEAR -> x
            KeyframeEasing.EASE_IN -> x * x * x
            KeyframeEasing.EASE_OUT -> {
                val inv = 1f - x
                1f - inv * inv * inv
            }
            KeyframeEasing.EASE_IN_OUT -> x * x * (3f - 2f * x)
        }
    }

    private fun progressFor(elapsedTime: Long, totalDuration: Long): Float {
        val safeDuration = totalDuration.coerceAtLeast(1L)
        return (elapsedTime.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    private fun staticTransform(centerX: Float, centerY: Float): ResolvedOverlayTransform =
        ResolvedOverlayTransform(
            centerX = centerX,
            centerY = centerY,
            scale = OverlayKeyframe.DEFAULT_SCALE,
            rotationDegrees = 0f,
            opacity = OverlayKeyframe.DEFAULT_OPACITY,
        )

    private fun OverlayKeyframe.toResolved(): ResolvedOverlayTransform = ResolvedOverlayTransform(
        centerX = centerX,
        centerY = centerY,
        scale = scale,
        rotationDegrees = rotationDegrees,
        opacity = opacity,
    )

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

object KeyframePresets {

    fun build(preset: KeyframePresetId, centerX: Float, centerY: Float): List<OverlayKeyframe> {
        val safeX = OverlayKeyframe.sanitizeCoordinate(centerX, 0.5f)
        val safeY = OverlayKeyframe.sanitizeCoordinate(centerY, 0.5f)
        return when (preset) {
            KeyframePresetId.NONE -> emptyList()
            KeyframePresetId.FADE_IN_OUT -> fadeInOut(safeX, safeY)
            KeyframePresetId.ZOOM_IN -> zoomIn(safeX, safeY)
            KeyframePresetId.SLIDE_UP -> slideUp(safeX, safeY)
            KeyframePresetId.POP_SPIN -> popSpin(safeX, safeY)
            KeyframePresetId.KEN_BURNS_ZOOM_IN -> kenBurnsZoomIn(safeX, safeY)
            KeyframePresetId.KEN_BURNS_PAN_LEFT -> kenBurnsPanLeft(safeX, safeY)
            KeyframePresetId.KEN_BURNS_PAN_RIGHT -> kenBurnsPanRight(safeX, safeY)
        }
    }

    fun matchingPreset(
        keyframes: List<OverlayKeyframe>,
        centerX: Float,
        centerY: Float,
    ): KeyframePresetId? {
        if (keyframes.isEmpty()) return KeyframePresetId.NONE
        val normalized = KeyframeInterpolator.sanitizeList(keyframes, centerX, centerY)
        return KeyframePresetId.entries.firstOrNull { preset ->
            preset != KeyframePresetId.NONE && build(preset, centerX, centerY) == normalized
        }
    }

    fun fadeInOut(centerX: Float, centerY: Float): List<OverlayKeyframe> = listOf(
        OverlayKeyframe(timeFraction = 0f, centerX = centerX, centerY = centerY, opacity = 0f, easing = KeyframeEasing.EASE_OUT),
        OverlayKeyframe(timeFraction = 0.18f, centerX = centerX, centerY = centerY, opacity = 1f, easing = KeyframeEasing.EASE_OUT),
        OverlayKeyframe(timeFraction = 0.82f, centerX = centerX, centerY = centerY, opacity = 1f, easing = KeyframeEasing.LINEAR),
        OverlayKeyframe(timeFraction = 1f, centerX = centerX, centerY = centerY, opacity = 0f, easing = KeyframeEasing.EASE_IN),
    )

    fun zoomIn(centerX: Float, centerY: Float): List<OverlayKeyframe> = listOf(
        OverlayKeyframe(timeFraction = 0f, centerX = centerX, centerY = centerY, scale = 0.65f, opacity = 0f, easing = KeyframeEasing.EASE_OUT),
        OverlayKeyframe(timeFraction = 0.25f, centerX = centerX, centerY = centerY, scale = 1.0f, opacity = 1f, easing = KeyframeEasing.EASE_OUT),
        OverlayKeyframe(timeFraction = 1f, centerX = centerX, centerY = centerY, scale = 1.08f, opacity = 1f, easing = KeyframeEasing.LINEAR),
    )

    fun slideUp(centerX: Float, centerY: Float): List<OverlayKeyframe> = listOf(
        OverlayKeyframe(
            timeFraction = 0f,
            centerX = centerX,
            centerY = (centerY + 0.14f).coerceIn(0f, 1f),
            opacity = 0f,
            easing = KeyframeEasing.EASE_OUT,
        ),
        OverlayKeyframe(
            timeFraction = 0.25f,
            centerX = centerX,
            centerY = centerY,
            opacity = 1f,
            easing = KeyframeEasing.EASE_OUT,
        ),
        OverlayKeyframe(
            timeFraction = 1f,
            centerX = centerX,
            centerY = centerY,
            opacity = 1f,
            easing = KeyframeEasing.LINEAR,
        ),
    )

    fun popSpin(centerX: Float, centerY: Float): List<OverlayKeyframe> = listOf(
        OverlayKeyframe(
            timeFraction = 0f,
            centerX = centerX,
            centerY = centerY,
            scale = 0.45f,
            rotationDegrees = -14f,
            opacity = 0f,
            easing = KeyframeEasing.EASE_OUT,
        ),
        OverlayKeyframe(
            timeFraction = 0.22f,
            centerX = centerX,
            centerY = centerY,
            scale = 1.06f,
            rotationDegrees = 2f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_OUT,
        ),
        OverlayKeyframe(
            timeFraction = 0.36f,
            centerX = centerX,
            centerY = centerY,
            scale = 1.0f,
            rotationDegrees = 0f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_IN_OUT,
        ),
    )

    fun kenBurnsZoomIn(centerX: Float, centerY: Float): List<OverlayKeyframe> = listOf(
        OverlayKeyframe(
            timeFraction = 0f,
            centerX = centerX,
            centerY = centerY,
            scale = 1.0f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_IN_OUT,
        ),
        OverlayKeyframe(
            timeFraction = 1f,
            centerX = centerX,
            centerY = centerY,
            scale = 1.22f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_IN_OUT,
        ),
    )

    fun kenBurnsPanLeft(centerX: Float, centerY: Float): List<OverlayKeyframe> = listOf(
        OverlayKeyframe(
            timeFraction = 0f,
            centerX = (centerX + 0.06f).coerceIn(0f, 1f),
            centerY = centerY,
            scale = 1.18f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_IN_OUT,
        ),
        OverlayKeyframe(
            timeFraction = 1f,
            centerX = (centerX - 0.06f).coerceIn(0f, 1f),
            centerY = centerY,
            scale = 1.18f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_IN_OUT,
        ),
    )

    fun kenBurnsPanRight(centerX: Float, centerY: Float): List<OverlayKeyframe> = listOf(
        OverlayKeyframe(
            timeFraction = 0f,
            centerX = (centerX - 0.06f).coerceIn(0f, 1f),
            centerY = centerY,
            scale = 1.18f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_IN_OUT,
        ),
        OverlayKeyframe(
            timeFraction = 1f,
            centerX = (centerX + 0.06f).coerceIn(0f, 1f),
            centerY = centerY,
            scale = 1.18f,
            opacity = 1f,
            easing = KeyframeEasing.EASE_IN_OUT,
        ),
    )
}
