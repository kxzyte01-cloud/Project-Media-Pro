package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step22SpeedRampAndTransitionsTest {

    @Test
    fun `clip defaults speedRamp to NONE for schema v1 compatibility`() {
        val clip = Clip(id = "c1", mediaUri = "p1/c1.mp4", startTimeMs = 0L, durationMs = 4_000L)
        assertEquals(SpeedRampPreset.NONE, clip.speedRamp)
    }

    @Test
    fun `speedRampEvaluator produces valid speeds, monotonic change points, and analytical duration`() {
        val sourceSpanMs = 4_000L
        val sourceDurationUs = sourceSpanMs * 1_000L

        for (preset in SpeedRampPreset.entries) {
            if (preset == SpeedRampPreset.NONE) continue
            val scaledMs = SpeedRampEvaluator.scaledDurationMs(sourceSpanMs, 1.0f, preset)
            assertTrue("Expected positive scaled duration for $preset", scaledMs > 0L)

            var cursorUs = 0L
            var hops = 0
            while (hops < 20) {
                val speed = SpeedRampEvaluator.speedAt(preset, cursorUs, sourceDurationUs)
                assertTrue(speed in Clip.MIN_PLAYBACK_SPEED..Clip.MAX_PLAYBACK_SPEED)
                val nextUs = SpeedRampEvaluator.nextChangeTimeUs(preset, cursorUs, sourceDurationUs)
                if (nextUs < 0L) break
                assertTrue("Expected strictly increasing nextChangeTimeUs", nextUs > cursorUs)
                cursorUs = nextUs
                hops++
            }
            assertTrue("Expected multiple ramp segments for $preset", hops in 1..10)
        }
    }

    @Test
    fun `motion transitions compute smooth scale, translation, and scrim on entry and exit`() {
        val zoomEffects = ClipVisualEffects(
            transitionIn = ClipTransition.ZOOM_IN,
            fadeInMs = 600L,
            transitionOut = ClipTransition.WHIP_LEFT,
            fadeOutMs = 600L,
        )

        val enter = zoomEffects.transitionTransformAt(positionInClipMs = 150L, clipDurationMs = 3_000L)
        assertFalse(enter.isIdentity)
        assertTrue(enter.scale > 1.05f)

        val mid = zoomEffects.transitionTransformAt(positionInClipMs = 1_500L, clipDurationMs = 3_000L)
        assertTrue(mid.isIdentity)

        val exit = zoomEffects.transitionTransformAt(positionInClipMs = 2_850L, clipDurationMs = 3_000L)
        assertFalse(exit.isIdentity)
        assertTrue(exit.translationXFraction > 0.05f)
    }
}
