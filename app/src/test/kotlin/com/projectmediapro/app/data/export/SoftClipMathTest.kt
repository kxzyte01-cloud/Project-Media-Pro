package com.projectmediapro.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftClipMathTest {

    @Test
    fun `softLimitNormalized preserves linear samples below knee and caps hot peaks cleanly`() {
        assertEquals(0.5, SoftClipMath.softLimitNormalized(0.5), 1e-6)
        val boosted = SoftClipMath.softLimitNormalized(1.9)
        assertTrue("Expected boosted sample > 0.95, got $boosted", boosted > 0.95)
        assertTrue("Expected boosted sample <= 1.0, got $boosted", boosted <= 1.0)
        val negativeBoosted = SoftClipMath.softLimitNormalized(-1.9)
        assertTrue("Expected negative boosted >= -1.0, got $negativeBoosted", negativeBoosted >= -1.0)
    }

    @Test
    fun `fadeMultiplier ramps smoothly on fade-in and fade-out`() {
        assertEquals(0.0, SoftClipMath.fadeMultiplier(0L, 4_000L, 1_000L, 1_000L), 1e-6)
        assertEquals(0.5, SoftClipMath.fadeMultiplier(500L, 4_000L, 1_000L, 1_000L), 1e-6)
        assertEquals(1.0, SoftClipMath.fadeMultiplier(2_000L, 4_000L, 1_000L, 1_000L), 1e-6)
        assertEquals(0.5, SoftClipMath.fadeMultiplier(3_500L, 4_000L, 1_000L, 1_000L), 1e-6)
        assertEquals(0.0, SoftClipMath.fadeMultiplier(4_000L, 4_000L, 1_000L, 1_000L), 1e-6)
    }
}
