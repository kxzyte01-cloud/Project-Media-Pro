package com.projectmediapro.core.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Beat-drop visual effect triggered at a [BeatMarker] timestamp (via DJ Live Perform pads or manual sync).
 */
@Serializable
enum class BeatEffectKind {
    NONE,
    ZOOM_PUNCH,
    SHAKE,
    FLASH,
    GLITCH,
}

/**
 * A timeline beat marker with an optional live-performed beat-drop visual effect.
 */
@Serializable
data class BeatMarker(
    val id: String,
    val timeMs: Long,
    val effect: BeatEffectKind = BeatEffectKind.NONE,
    val durationMs: Long = DEFAULT_EFFECT_DURATION_MS,
    val intensity: Float = DEFAULT_INTENSITY,
) {
    fun sanitized(): BeatMarker = copy(
        timeMs = timeMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceIn(MIN_EFFECT_DURATION_MS, MAX_EFFECT_DURATION_MS),
        intensity = if (intensity.isNaN() || intensity.isInfinite()) {
            DEFAULT_INTENSITY
        } else {
            intensity.coerceIn(0.1f, 1.0f)
        },
    )

    companion object {
        const val MIN_EFFECT_DURATION_MS: Long = 100L
        const val DEFAULT_EFFECT_DURATION_MS: Long = 260L
        const val MAX_EFFECT_DURATION_MS: Long = 800L
        const val DEFAULT_INTENSITY: Float = 0.85f
    }
}

/**
 * Evaluated per-frame beat-drop & sound-reactive state shared by both live preview and Media3 export.
 */
data class BeatEffectSample(
    val scale: Float = 1f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
    val flashAlpha: Float = 0f,
    val glitchAmount: Float = 0f,
    val reactiveGlow: Float = 0f,
) {
    val isIdentity: Boolean
        get() = scale == 1f &&
            translationXFraction == 0f &&
            translationYFraction == 0f &&
            flashAlpha <= 0.001f &&
            glitchAmount <= 0.001f &&
            reactiveGlow <= 0.001f

    companion object {
        val IDENTITY = BeatEffectSample()
    }
}

object BeatEffectEvaluator {

    private const val SOUND_REACTIVE_PULSE_MS = 220L

    fun evaluate(
        markers: List<BeatMarker>,
        timeMs: Long,
        soundReactiveEnabled: Boolean = false,
    ): BeatEffectSample {
        if (markers.isEmpty() || timeMs < 0L) return BeatEffectSample.IDENTITY

        var scale = 1f
        var tx = 0f
        var ty = 0f
        var flash = 0f
        var glitch = 0f
        var reactiveGlow = 0f

        for (raw in markers) {
            val isExplicitEffect = raw.effect != BeatEffectKind.NONE
            if (!isExplicitEffect && !soundReactiveEnabled) continue

            val duration = if (isExplicitEffect) {
                raw.durationMs.coerceIn(
                    BeatMarker.MIN_EFFECT_DURATION_MS,
                    BeatMarker.MAX_EFFECT_DURATION_MS,
                )
            } else {
                SOUND_REACTIVE_PULSE_MS
            }
            val elapsed = timeMs - raw.timeMs
            if (elapsed < 0L || elapsed > duration) continue

            val progress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val decay = (1f - progress) * (1f - progress)
            val power = if (raw.intensity.isNaN() || raw.intensity.isInfinite()) {
                BeatMarker.DEFAULT_INTENSITY
            } else {
                raw.intensity.coerceIn(0.1f, 1.0f)
            }

            if (soundReactiveEnabled) {
                scale = maxOf(scale, 1f + 0.055f * power * decay)
                reactiveGlow = maxOf(reactiveGlow, (0.65f * power * decay).coerceIn(0f, 1f))
            }

            when (raw.effect) {
                BeatEffectKind.ZOOM_PUNCH -> {
                    val punch = 0.20f * power * decay
                    scale = maxOf(scale, 1f + punch)
                }
                BeatEffectKind.SHAKE -> {
                    val angle = progress * 5.0 * 2.0 * PI
                    val amp = 0.028f * power * decay
                    tx += (sin(angle) * amp).toFloat()
                    ty += (cos(angle * 1.35) * amp * 0.85).toFloat()
                    scale = maxOf(scale, 1f + 0.06f * power * decay)
                }
                BeatEffectKind.FLASH -> {
                    flash = maxOf(flash, (0.82f * power * decay).coerceIn(0f, 0.90f))
                }
                BeatEffectKind.GLITCH -> {
                    val jitter = sin(progress * 9.0 * PI).toFloat()
                    tx += jitter * 0.018f * power * decay
                    glitch = maxOf(glitch, (power * decay).coerceIn(0f, 1f))
                }
                BeatEffectKind.NONE -> Unit
            }
        }

        if (scale == 1f && tx == 0f && ty == 0f && flash <= 0.001f && glitch <= 0.001f && reactiveGlow <= 0.001f) {
            return BeatEffectSample.IDENTITY
        }
        return BeatEffectSample(
            scale = scale,
            translationXFraction = tx.coerceIn(-0.08f, 0.08f),
            translationYFraction = ty.coerceIn(-0.08f, 0.08f),
            flashAlpha = flash,
            glitchAmount = glitch,
            reactiveGlow = reactiveGlow,
        )
    }
}
