package com.projectmediapro.app.data.export

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipCrop
import com.projectmediapro.core.model.ClipTransition
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.FilterGradeSpec
import com.projectmediapro.core.model.ProjectTimeline
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

    fun buildSpeedEffects(playbackSpeed: Float): List<Effect> {
        val safeSpeed = ProjectTimeline.sanitizeSpeed(playbackSpeed)
        if (safeSpeed == Clip.DEFAULT_PLAYBACK_SPEED) return emptyList()
        return listOf(SpeedChangeEffect(ConstantSpeedProvider(safeSpeed)))
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

    private class ConstantSpeedProvider(
        private val speed: Float,
    ) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    private class ClipFadeRgbMatrix(
        private val effects: ClipVisualEffects,
        private val clipDurationMs: Long,
    ) : RgbMatrix {

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val posMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
            val fade = effects.fadeOverlayAt(posMs, clipDurationMs) ?: return IDENTITY_MATRIX
            val (transition, overlayAlpha) = fade
            val visibility = (1f - overlayAlpha).coerceIn(0f, 1f)
            val offset = when (transition) {
                ClipTransition.FADE_WHITE -> overlayAlpha.coerceIn(0f, 1f)
                ClipTransition.FADE_BLACK, ClipTransition.NONE -> 0f
            }
            return floatArrayOf(
                visibility, 0f, 0f, 0f,
                0f, visibility, 0f, 0f,
                0f, 0f, visibility, 0f,
                offset, offset, offset, 1f,
            )
        }
    }
}
