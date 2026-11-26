package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyConfigTest {

    @Test
    fun sanitize_clamps_and_normalizes_hex() {
        val clean = ChromaKeyConfig(
            keyColorHex = "00ff00",
            similarity = 5f,
            smoothness = -1f,
            spillSuppression = 2f,
        ).sanitized()
        assertEquals("#00FF00", clean.keyColorHex)
        assertEquals(1f, clean.similarity, 0f)
        assertEquals(0f, clean.smoothness, 0f)
        assertEquals(1f, clean.spillSuppression, 0f)
    }

    @Test
    fun malformed_hex_falls_back_to_green() {
        assertEquals("#00FF00", ChromaKeyConfig(keyColorHex = "nope").sanitized().keyColorHex)
        assertNull(ChromaKeyMath.parseHexColor("#12345"))
    }

    @Test
    fun green_key_distance_near_zero_and_alpha_gate() {
        val (cb, cr) = ChromaKeyMath.rgbToCbCr(0f, 1f, 0f)
        val dist = ChromaKeyMath.chromaDistance(cb, cr, cb, cr)
        assertTrue(dist < 0.001f)
        assertEquals(0f, ChromaKeyMath.keyAlpha(dist, 0.28f, 0.12f), 0.001f)
        assertEquals(1f, ChromaKeyMath.keyAlpha(0.9f, 0.28f, 0.12f), 0.001f)
    }

    @Test
    fun cpu_keyer_makes_green_transparent_and_keeps_red() {
        val pixels = intArrayOf(0xFF00FF00.toInt(), 0xFFFF0000.toInt())
        val (cb, cr) = ChromaKeyMath.rgbToCbCr(0f, 1f, 0f)
        ChromaKeyMath.applyToPixels(pixels, cb, cr, 0.28f, 0.12f, 0.25f)
        assertEquals(0, (pixels[0] ushr 24) and 0xFF)
        assertTrue(((pixels[1] ushr 24) and 0xFF) > 200)
    }
}
