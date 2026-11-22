package com.projectmediapro.app.data.export

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import android.graphics.Matrix
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import com.projectmediapro.core.model.BeatEffectEvaluator
import com.projectmediapro.core.model.BeatEffectKind
import com.projectmediapro.core.model.BeatMarker
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipCrop
import com.projectmediapro.core.model.ClipTransition
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.FilterGradeSpec
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.SpeedRampEvaluator
import com.projectmediapro.core.model.SpeedRampPreset
import com.projectmediapro.core.model.gradeSpec

/**
 * Builds Media3 1.4.1 GPU [Effect] chains for [ClipVisualEffects], clip crop, rotate/flip
 * transforms, and constant playback speed changes.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object VideoEffectFactory {

    private val IDENTITY_MATRIX: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    // Media3 RgbMatrix uses OpenGL column-major 4x4 layout.
    private val VINTAGE_SEPIA_MATRIX: FloatArray = floatArrayOf(
        FilterGradeSpec.SEPIA_RR, FilterGradeSpec.SEPIA_GR, FilterGradeSpec.SEPIA_BR, 0f,
        FilterGradeSpec.SEPIA_RG, FilterGradeSpec.SEPIA_GG, FilterGradeSpec.SEPIA_BG, 0f,
        FilterGradeSpec.SEPIA_RB, FilterGradeSpec.SEPIA_GB, FilterGradeSpec.SEPIA_BB, 0f,
        0f, 0f, 0f, 1f,
    )

    fun buildCropEffects(crop: ClipCrop?): List<Effect> {
        if (crop == null) return emptyList()
        val safe = crop.sanitized()
        if (safe.isIdentity) return emptyList()
        // Media3 Crop takes OpenGL NDC bounds (-1..1) with bottom < top (Y-up).
        val ndcLeft = safe.left * 2f - 1f
        val ndcRight = safe.right * 2f - 1f
        val ndcTop = 1f - safe.top * 2f
        val ndcBottom = 1f - safe.bottom * 2f
        return listOf(Crop(ndcLeft, ndcRight, ndcBottom, ndcTop))
    }

    fun buildTransformEffects(
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
    ): List<Effect> {
        val normalizedRotation = ProjectTimeline.normalizeRotationDegrees(rotationDegrees)
        if (normalizedRotation == 0 && !flipHorizontal && !flipVertical) {
            return emptyList()
        }
        // Media3 ScaleAndRotateTransformation rotates counter-clockwise, while the editor UI uses
        // clockwise degrees to match Android View/Compose rotation conventions.
        val ccwDegrees = ((360 - normalizedRotation) % 360).toFloat()
        val scaleX = if (flipHorizontal) -1f else 1f
        val scaleY = if (flipVertical) -1f else 1f
        return listOf(
            ScaleAndRotateTransformation.Builder()
                .setScale(scaleX, scaleY)
                .setRotationDegrees(ccwDegrees)
                .build(),
        )
    }

    fun buildBeatDropEffects(
        beatMarkers: List<BeatMarker>,
        soundReactiveEnabled: Boolean = false,
    ): List<Effect> {
        if (beatMarkers.isEmpty()) return emptyList()
        val active = if (soundReactiveEnabled) beatMarkers else beatMarkers.filter { it.effect != BeatEffectKind.NONE }
        if (active.isEmpty()) return emptyList()
        return buildList {
            if (soundReactiveEnabled || active.any { it.effect == BeatEffectKind.ZOOM_PUNCH || it.effect == BeatEffectKind.SHAKE || it.effect == BeatEffectKind.GLITCH }) {
                add(BeatDropMatrixTransformation(active, soundReactiveEnabled))
            }
            if (soundReactiveEnabled || active.any { it.effect == BeatEffectKind.FLASH || it.effect == BeatEffectKind.GLITCH }) {
                add(BeatDropRgbMatrix(active, soundReactiveEnabled))
            }
        }
    }

    fun buildSpeedProvider(
        playbackSpeed: Float,
        speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
        sourceSpanMs: Long = 0L,
    ): SpeedProvider? {
        if (speedRamp != SpeedRampPreset.NONE && sourceSpanMs > 0L) {
            return RampSpeedProvider(speedRamp, sourceSpanMs * 1_000L)
        }
        val safeSpeed = ProjectTimeline.sanitizeSpeed(playbackSpeed)
        if (safeSpeed == Clip.DEFAULT_PLAYBACK_SPEED) return null
        return ConstantSpeedProvider(safeSpeed)
    }

    fun buildSpeedEffects(
        playbackSpeed: Float,
        speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
        sourceSpanMs: Long = 0L,
    ): List<Effect> {
        val provider = buildSpeedProvider(playbackSpeed, speedRamp, sourceSpanMs) ?: return emptyList()
        return listOf(SpeedChangeEffect(provider))
    }

    fun buildClipEffects(
        effects: ClipVisualEffects?,
        clipDurationMs: Long,
    ): List<Effect> {
        if (effects == null) return emptyList()
        val safe = effects.sanitized(clipDurationMs.takeIf { it > 0L })
        if (safe.isIdentity) return emptyList()

        return buildList {
            appendFilterSpec(safe.filter.gradeSpec())

            if (safe.brightness != 0f) {
                add(Brightness(safe.brightness))
            }
            if (safe.contrast != 0f) {
                add(Contrast(safe.contrast))
            }
            if (safe.saturation != 0f) {
                add(
                    HslAdjustment.Builder()
                        .adjustSaturation(safe.saturation)
                        .build(),
                )
            }
            if (safe.warmth != 0f) {
                val (redScale, blueScale) = safe.warmthRgbScales()
                add(
                    RgbAdjustment.Builder()
                        .setRedScale(redScale)
                        .setGreenScale(1f)
                        .setBlueScale(blueScale)
                        .build(),
                )
            }
            if (clipDurationMs > 0L && safe.hasFades) {
                val hasMotionTransition = safe.transitionIn !in listOf(ClipTransition.NONE, ClipTransition.FADE_BLACK, ClipTransition.FADE_WHITE) ||
                    safe.transitionOut !in listOf(ClipTransition.NONE, ClipTransition.FADE_BLACK, ClipTransition.FADE_WHITE)
                if (hasMotionTransition) {
                    add(ClipTransitionMatrixTransformation(safe, clipDurationMs))
                }
                add(ClipFadeRgbMatrix(safe, clipDurationMs))
            }
        }
    }

    private fun MutableList<Effect>.appendFilterSpec(spec: FilterGradeSpec) {
        if (spec.isGrayscale) {
            add(RgbFilter.createGrayscaleFilter())
        }
        if (spec.isSepia) {
            add(RgbMatrix { _, _ -> VINTAGE_SEPIA_MATRIX })
        }
        if (spec.hasRgbScale) {
            add(
                RgbAdjustment.Builder()
                    .setRedScale(spec.redScale)
                    .setGreenScale(spec.greenScale)
                    .setBlueScale(spec.blueScale)
                    .build(),
            )
        }
        if (spec.saturationDelta != 0f) {
            add(HslAdjustment.Builder().adjustSaturation(spec.saturationDelta).build())
        }
        if (spec.contrastDelta != 0f) {
            add(Contrast(spec.contrastDelta))
        }
        if (spec.brightnessDelta != 0f) {
            add(Brightness(spec.brightnessDelta))
        }
    }

    private class BeatDropMatrixTransformation(
        private val markers: List<BeatMarker>,
        private val soundReactiveEnabled: Boolean,
    ) : MatrixTransformation {
        private val matrixLocal = object : ThreadLocal<Matrix>() {
            override fun initialValue(): Matrix = Matrix()
        }

        override fun getMatrix(presentationTimeUs: Long): Matrix {
            val matrix = matrixLocal.get() ?: Matrix()
            matrix.reset()
            val sample = BeatEffectEvaluator.evaluate(markers, presentationTimeUs / 1_000L, soundReactiveEnabled)
            if (!sample.isIdentity) {
                if (sample.scale != 1f) {
                    matrix.postScale(sample.scale, sample.scale)
                }
                if (sample.translationXFraction != 0f || sample.translationYFraction != 0f) {
                    matrix.postTranslate(
                        sample.translationXFraction * 2f,
                        -sample.translationYFraction * 2f,
                    )
                }
            }
            return matrix
        }
    }

    private class BeatDropRgbMatrix(
        private val markers: List<BeatMarker>,
        private val soundReactiveEnabled: Boolean,
    ) : RgbMatrix {
        private val matrixLocal = object : ThreadLocal<FloatArray>() {
            override fun initialValue(): FloatArray = FloatArray(16)
        }

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val sample = BeatEffectEvaluator.evaluate(markers, presentationTimeUs / 1_000L, soundReactiveEnabled)
            if (sample.flashAlpha <= 0.001f && sample.glitchAmount <= 0.001f && sample.reactiveGlow <= 0.001f) {
                return IDENTITY_MATRIX
            }
            val out = matrixLocal.get() ?: FloatArray(16)
            val flash = (sample.flashAlpha + sample.reactiveGlow * 0.08f).coerceIn(0f, 0.9f)
            val glitch = sample.glitchAmount.coerceIn(0f, 1f)
            val keep = 1f - flash
            val rBoost = keep * (1f + glitch * 0.25f)
            val gBoost = keep * (1f - glitch * 0.12f)
            val bBoost = keep * (1f + glitch * 0.30f)
            // Column-major 4x4 RGB matrix
            out[0] = rBoost; out[1] = 0f;     out[2] = 0f;     out[3] = 0f
            out[4] = 0f;     out[5] = gBoost; out[6] = 0f;     out[7] = 0f
            out[8] = 0f;     out[9] = 0f;     out[10] = bBoost; out[11] = 0f
            out[12] = flash; out[13] = flash; out[14] = flash; out[15] = 1f
            return out
        }
    }

    private class ConstantSpeedProvider(
        private val speed: Float,
    ) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    private class RampSpeedProvider(
        private val preset: SpeedRampPreset,
        private val sourceDurationUs: Long,
    ) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float =
            SpeedRampEvaluator.speedAt(preset, timeUs, sourceDurationUs)

        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
            val next = SpeedRampEvaluator.nextChangeTimeUs(preset, timeUs, sourceDurationUs)
            return if (next >= 0L) next else C.TIME_UNSET
        }
    }

    private class ClipTransitionMatrixTransformation(
        private val effects: ClipVisualEffects,
        private val clipDurationMs: Long,
    ) : MatrixTransformation {
        private val matrixLocal = object : ThreadLocal<Matrix>() {
            override fun initialValue(): Matrix = Matrix()
        }

        override fun getMatrix(presentationTimeUs: Long): Matrix {
            val matrix = matrixLocal.get() ?: Matrix()
            matrix.reset()
            val posMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
            val transform = effects.transitionTransformAt(posMs, clipDurationMs)
            if (!transform.isIdentity) {
                if (transform.scale != 1f) {
                    matrix.postScale(transform.scale, transform.scale)
                }
                if (transform.translationXFraction != 0f || transform.translationYFraction != 0f) {
                    matrix.postTranslate(
                        transform.translationXFraction * 2f,
                        -transform.translationYFraction * 2f,
                    )
                }
            }
            return matrix
        }
    }

    private class ClipFadeRgbMatrix(
        private val effects: ClipVisualEffects,
        private val clipDurationMs: Long,
    ) : RgbMatrix {

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val posMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
            val transform = effects.transitionTransformAt(posMs, clipDurationMs)
            if (transform.scrimAlpha <= 0.001f) return IDENTITY_MATRIX
            val overlayAlpha = transform.scrimAlpha.coerceIn(0f, 1f)
            val visibility = (1f - overlayAlpha).coerceIn(0f, 1f)
            val offset = if (transform.scrimIsWhite) overlayAlpha else 0f
            return floatArrayOf(
                visibility, 0f, 0f, 0f,
                0f, visibility, 0f, 0f,
                0f, 0f, visibility, 0f,
                offset, offset, offset, 1f,
            )
        }
    }
}
