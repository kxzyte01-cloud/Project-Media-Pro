package com.projectmediapro.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step23OverlayStyleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultStyleIsIdentityAndSkipsBitmapProcessing() {
        val style = OverlayVisualStyle()
        assertTrue(style.isIdentity)
        assertFalse(style.requiresBitmapProcessing)
    }

    @Test
    fun sanitizedClampsOutOfRangeAndNonFiniteValues() {
        val dirty = OverlayVisualStyle(
            cornerRadiusFraction = Float.NaN,
            borderWidthFraction = 5.0f,
            borderColorArgb = 0x0022D3EEL, // zero alpha gets normalized to opaque
            opacity = 0.0f,
            blendMode = OverlayBlendMode.SCREEN_LUMA,
        )
        val clean = dirty.sanitized()
        assertEquals(0f, clean.cornerRadiusFraction, 0.0001f)
        assertEquals(OverlayVisualStyle.MAX_BORDER_WIDTH_FRACTION, clean.borderWidthFraction, 0.0001f)
        assertEquals(0xFF22D3EEL, clean.borderColorArgb)
        assertEquals(OverlayVisualStyle.MIN_OPACITY, clean.opacity, 0.0001f)
        assertEquals(OverlayBlendMode.SCREEN_LUMA, clean.blendMode)
        assertFalse(clean.isIdentity)
        assertTrue(clean.requiresBitmapProcessing)
    }

    @Test
    fun screenLumaKeysPureBlackAndPreservesBrightHighlights() {
        val black = 0xFF000000.toInt()
        val transformedBlack = OverlayVisualStyle.transformPixel(black, OverlayBlendMode.SCREEN_LUMA)
        assertEquals(0, (transformedBlack ushr 24) and 0xFF)

        val brightOrange = 0xFFFF8800.toInt()
        val transformedBright = OverlayVisualStyle.transformPixel(brightOrange, OverlayBlendMode.SCREEN_LUMA)
        val outAlpha = (transformedBright ushr 24) and 0xFF
        assertTrue("Bright light leak pixel should remain opaque", outAlpha >= 245)
    }

    @Test
    fun lightenKeysShadowsMoreAggressivelyThanScreenLuma() {
        val darkGray = 0xFF1A1A1A.toInt() // 26 < 28 cutoff
        val transformedLighten = OverlayVisualStyle.transformPixel(darkGray, OverlayBlendMode.LIGHTEN)
        assertEquals(0, (transformedLighten ushr 24) and 0xFF)
    }

    @Test
    fun multiplyReducesWhiteAlphaAndPreservesDeepColor() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val whiteAlpha = (OverlayVisualStyle.transformPixel(white, OverlayBlendMode.MULTIPLY) ushr 24) and 0xFF
        val blackAlpha = (OverlayVisualStyle.transformPixel(black, OverlayBlendMode.MULTIPLY) ushr 24) and 0xFF
        assertTrue("Multiply should keep dark pixels stronger than white", blackAlpha > whiteAlpha + 150)
    }

    @Test
    fun projectTimelineUpdateOverlayStyleNormalizesIdentityAndPreservesSchemaV1() {
        val base = Project(
            id = "p1",
            title = "Overlay Test",
            createdAt = 1L,
            updatedAt = 1L,
            tracks = listOf(
                Track(
                    id = "v1",
                    type = TrackType.VIDEO,
                    clips = listOf(Clip(id = "c1", mediaUri = "video.mp4", startTimeMs = 0L, durationMs = 5_000L)),
                ),
                Track(
                    id = "o1",
                    type = TrackType.OVERLAY,
                    clips = listOf(Clip(id = "ov1", mediaUri = "leak.png", startTimeMs = 0L, durationMs = 3_000L)),
                ),
            ),
        )

        val styled = ProjectTimeline.updateOverlayStyle(
            project = base,
            clipId = "ov1",
            style = OverlayVisualStyle(
                cornerRadiusFraction = 0.25f,
                borderWidthFraction = 0.03f,
                borderColorArgb = 0xFF22D3EEL,
                opacity = 0.85f,
                blendMode = OverlayBlendMode.SCREEN_LUMA,
            ),
        )
        val clip = styled.overlayClips().single()
        assertTrue(clip.hasCustomOverlayStyle)
        assertNotNull(clip.overlayStyle)
        assertEquals(OverlayBlendMode.SCREEN_LUMA, clip.overlayStyle?.blendMode)

        // Round-trip JSON serialization with schemaVersion = 1
        val encoded = json.encodeToString(styled)
        val decoded = json.decodeFromString<Project>(encoded)
        assertEquals(Project.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(clip.overlayStyle, decoded.overlayClips().single().overlayStyle)

        // Resetting back to identity clears overlayStyle to null
        val reset = ProjectTimeline.updateOverlayStyle(styled, "ov1", OverlayVisualStyle())
        assertNull(reset.overlayClips().single().overlayStyle)
        assertFalse(reset.overlayClips().single().hasCustomOverlayStyle)
    }
}
