package com.projectmediapro.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Opt-in GLSL chroma-key configuration for video PiP overlays.
 * Backward-compatible: every field defaults so schemaVersion = 1 projects deserialize cleanly.
 */
@Serializable
data class ChromaKeyConfig(
    val enabled: Boolean = false,
    val keyColorHex: String = DEFAULT_KEY_HEX,
    val similarity: Float = DEFAULT_SIMILARITY,
    val smoothness: Float = DEFAULT_SMOOTHNESS,
    val spillSuppression: Float = DEFAULT_SPILL,
) {
    val isIdentity: Boolean
        get() = !enabled

    fun sanitized(): ChromaKeyConfig {
        val hex = normalizeHex(keyColorHex) ?: DEFAULT_KEY_HEX
        return copy(
            keyColorHex = hex,
            similarity = ChromaKeyMath.sanitizeFinite(similarity, DEFAULT_SIMILARITY).coerceIn(0f, 1f),
            smoothness = ChromaKeyMath.sanitizeFinite(smoothness, DEFAULT_SMOOTHNESS).coerceIn(0f, 1f),
            spillSuppression = ChromaKeyMath.sanitizeFinite(spillSuppression, DEFAULT_SPILL).coerceIn(0f, 1f),
        )
    }

    /** Parsed 0xFFRRGGBB key color, falling back to green on malformed input. */
    fun keyColorArgb(): Int =
        ChromaKeyMath.parseHexColor(keyColorHex)
            ?: ChromaKeyMath.parseHexColor(DEFAULT_KEY_HEX)
            ?: 0xFF00FF00.toInt()

    /** (Cb, Cr) chroma of the key color in normalized 0..1 space. */
    fun keyChroma(): Pair<Float, Float> {
        val argb = keyColorArgb()
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return ChromaKeyMath.rgbToCbCr(r, g, b)
    }

    companion object {
        const val DEFAULT_KEY_HEX = "#00FF00"
        const val DEFAULT_SIMILARITY = 0.28f
        const val DEFAULT_SMOOTHNESS = 0.12f
        const val DEFAULT_SPILL = 0.25f
        const val GREEN_HEX = "#00FF00"
        const val BLUE_HEX = "#0000FF"
        const val MAGENTA_HEX = "#FF00FF"
        val SWATCH_HEXES = listOf(GREEN_HEX, BLUE_HEX, MAGENTA_HEX)

        fun normalizeHex(raw: String): String? {
            val trimmed = raw.trim()
            val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            return if (HEX_REGEX.matches(withHash)) withHash.uppercase() else null
        }

        private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}

/**
 * Pure-JVM chroma-key math shared 1:1 between the GLSL shader (ChromaKeyGlEffect),
 * the CPU PiP export path, and unit tests. No Android imports.
 */
object ChromaKeyMath {
    fun sanitizeFinite(value: Float, fallback: Float): Float =
        if (value.isNaN() || value.isInfinite()) fallback else value

    fun parseHexColor(raw: String): Int? {
        val hex = ChromaKeyConfig.normalizeHex(raw) ?: return null
        val rgb = hex.substring(1).toLongOrNull(16) ?: return null
        return (0xFF000000L or rgb).toInt()
    }

    /** BT.601 normalized chroma. Inputs r,g,b in 0..1. Returns (Cb, Cr) in 0..1. */
    fun rgbToCbCr(r: Float, g: Float, b: Float): Pair<Float, Float> {
        val cb = (-0.168736f * r - 0.331264f * g + 0.5f * b) + 0.5f
        val cr = (0.5f * r - 0.418688f * g - 0.081312f * b) + 0.5f
        return cb to cr
    }

    fun chromaDistance(cb: Float, cr: Float, keyCb: Float, keyCr: Float): Float {
        val dc = cb - keyCb
        val dr = cr - keyCr
        return sqrt(dc * dc + dr * dr)
    }

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val span = max(edge1 - edge0, 1e-4f)
        val t = ((x - edge0) / span).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 0 = fully keyed (transparent), 1 = fully kept. Matches GLSL smoothstep gate. */
    fun keyAlpha(distance: Float, similarity: Float, smoothness: Float): Float =
        smoothstep(similarity, similarity + max(smoothness, 1e-4f), distance)

    /**
     * CPU keyer for PiP bitmaps. Mutates [pixels] (ARGB_8888) in place.
     * Despill pulls fringe pixels toward luma, identical math to the GLSL path.
     */
    fun applyToPixels(
        pixels: IntArray,
        keyCb: Float,
        keyCr: Float,
        similarity: Float,
        smoothness: Float,
        spill: Float,
    ) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            if (a == 0) continue
            val r = ((p ushr 16) and 0xFF) / 255f
            val g = ((p ushr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val (cb, cr) = rgbToCbCr(r, g, b)
            val dist = chromaDistance(cb, cr, keyCb, keyCr)
            val alpha = keyAlpha(dist, similarity, smoothness)
            val keyness = 1f - smoothstep(similarity, similarity + smoothness + 0.20f, dist)
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val despill = spill * keyness * 0.85f
            val nr = r + (luma - r) * despill
            val ng = g + (luma - g) * despill
            val nb = b + (luma - b) * despill
            val outA = (a * alpha).toInt().coerceIn(0, 255)
            val outR = (nr * 255f).toInt().coerceIn(0, 255)
            val outG = (ng * 255f).toInt().coerceIn(0, 255)
            val outB = (nb * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }
}
