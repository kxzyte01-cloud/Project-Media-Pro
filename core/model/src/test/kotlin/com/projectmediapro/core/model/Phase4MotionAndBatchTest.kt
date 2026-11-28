package com.projectmediapro.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4MotionAndBatchTest {

    @Test
    fun kenBurnsZoomIn_interpolatesSmoothlyFrom100To122Percent() {
        val keyframes = KeyframePresets.build(KeyframePresetId.KEN_BURNS_ZOOM_IN, 0.5f, 0.5f)
        assertEquals(2, keyframes.size)
        val start = KeyframeInterpolator.evaluate(0.5f, 0.5f, keyframes, 0f)
        val mid = KeyframeInterpolator.evaluate(0.5f, 0.5f, keyframes, 0.5f)
        val end = KeyframeInterpolator.evaluate(0.5f, 0.5f, keyframes, 1f)
        assertEquals(1.0f, start.scale, 0.01f)
        assertTrue("Mid scale (${mid.scale}) should be strictly between 1.0 and 1.22", mid.scale in 1.05f..1.18f)
        assertEquals(1.22f, end.scale, 0.01f)
    }

    @Test
    fun kenBurnsPanLeftAndRight_shiftCenterXInOppositeDirections() {
        val panLeft = KeyframePresets.build(KeyframePresetId.KEN_BURNS_PAN_LEFT, 0.5f, 0.5f)
        val panRight = KeyframePresets.build(KeyframePresetId.KEN_BURNS_PAN_RIGHT, 0.5f, 0.5f)
        val leftStart = KeyframeInterpolator.evaluate(0.5f, 0.5f, panLeft, 0f)
        val leftEnd = KeyframeInterpolator.evaluate(0.5f, 0.5f, panLeft, 1f)
        assertTrue(leftEnd.centerX < leftStart.centerX)
        assertTrue(leftStart.scale > 1.05f)
        val rightStart = KeyframeInterpolator.evaluate(0.5f, 0.5f, panRight, 0f)
        val rightEnd = KeyframeInterpolator.evaluate(0.5f, 0.5f, panRight, 1f)
        assertTrue(rightEnd.centerX > rightStart.centerX)
        assertTrue(rightStart.scale > 1.05f)
    }

    @Test
    fun kenBurnsPresets_passThroughCoverageClampUnchanged() {
        val presets = listOf(
            KeyframePresetId.KEN_BURNS_ZOOM_IN,
            KeyframePresetId.KEN_BURNS_PAN_LEFT,
            KeyframePresetId.KEN_BURNS_PAN_RIGHT,
        )
        for (preset in presets) {
            val keyframes = KeyframePresets.build(preset, 0.5f, 0.5f)
            for (step in 0..20) {
                val t = step / 20f
                val r = KeyframeInterpolator.evaluate(0.5f, 0.5f, keyframes, t)
                val tx = (r.centerX - 0.5f) * 2f
                val ty = (0.5f - r.centerY) * 2f
                val clamped = VideoKeyframeCoverage.clamp(r.scale, r.rotationDegrees, tx, ty)
                assertEquals("scale changed for $preset at $t", r.scale, clamped.scale, 1e-5f)
                assertEquals("tx changed for $preset at $t", tx, clamped.translationX, 1e-5f)
                assertEquals("ty changed for $preset at $t", ty, clamped.translationY, 1e-5f)
            }
        }
    }

    @Test
    fun coverageClamp_preventsZoomOutAndExtremePan() {
        val clamped = VideoKeyframeCoverage.clamp(0.25f, 0f, 1f, -1f)
        assertEquals(1f, clamped.scale, 1e-5f)
        assertEquals(0f, clamped.translationX, 1e-5f)
        assertEquals(0f, clamped.translationY, 1e-5f)
        assertTrue(coversViewport(clamped.scale, 0f, clamped.translationX, clamped.translationY))
    }

    @Test
    fun coverageClamp_boostsScaleForRotatedFrames() {
        val clamped = VideoKeyframeCoverage.clamp(1.06f, -14f, 0f, 0f)
        assertTrue(clamped.scale >= 1.2122f - 1e-3f)
        assertTrue(coversViewport(clamped.scale, -14f, clamped.translationX, clamped.translationY))
    }

    @Test
    fun coverageClamp_clampsPanToScaleMargin() {
        val clamped = VideoKeyframeCoverage.clamp(1.16f, 0f, 1f, -1f)
        assertEquals(1.16f, clamped.scale, 1e-5f)
        assertEquals(0.16f, clamped.translationX, 1e-4f)
        assertEquals(-0.16f, clamped.translationY, 1e-4f)
    }

    @Test
    fun coverageClamp_capsAtMaxScaleAndShrinksPan() {
        val clamped = VideoKeyframeCoverage.clamp(2.5f, 45f, 1f, 1f)
        assertEquals(2.5f, clamped.scale, 1e-5f)
        val maxT = 2.5f / 1.41421356f - 1f
        assertTrue(abs(clamped.translationX) <= maxT + 1e-4f)
        assertTrue(coversViewport(clamped.scale, 45f, clamped.translationX, clamped.translationY))
    }

    @Test
    fun coverageClamp_sanitizesNonFiniteInputs() {
        val clamped = VideoKeyframeCoverage.clamp(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NEGATIVE_INFINITY)
        assertTrue(coversViewport(clamped.scale, 0f, clamped.translationX, clamped.translationY))
    }

    @Test
    fun coverageClamp_fuzzNeverExposesBorders() {
        val random = Random(7)
        repeat(2000) {
            val scale = 0.25f + random.nextFloat() * 2.25f
            val rotation = -180f + random.nextFloat() * 360f
            val tx = -1f + random.nextFloat() * 2f
            val ty = -1f + random.nextFloat() * 2f
            val clamped = VideoKeyframeCoverage.clamp(scale, rotation, tx, ty)
            assertTrue(
                "Border exposed for scale=$scale rot=$rotation tx=$tx ty=$ty -> $clamped",
                coversViewport(clamped.scale, rotation, clamped.translationX, clamped.translationY),
            )
        }
    }

    @Test
    fun interpolator_holdsFirstAndLastKeyframeOutsideTheirSpan() {
        val keyframes = listOf(
            OverlayKeyframe(timeFraction = 0.2f, scale = 1.1f),
            OverlayKeyframe(timeFraction = 0.8f, scale = 1.3f),
        )
        assertEquals(1.1f, KeyframeInterpolator.evaluate(0.5f, 0.5f, keyframes, 0f).scale, 1e-5f)
        assertEquals(1.3f, KeyframeInterpolator.evaluate(0.5f, 0.5f, keyframes, 1f).scale, 1e-5f)
    }

    private fun coversViewport(scale: Float, rotationDegrees: Float, tx: Float, ty: Float): Boolean {
        val radians = rotationDegrees * (PI.toFloat() / 180f)
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        val needed = maxOf(
            c * (1f + abs(tx)) + s * (1f + abs(ty)),
            s * (1f + abs(tx)) + c * (1f + abs(ty)),
        )
        return scale >= needed - 1e-4f
    }
}
