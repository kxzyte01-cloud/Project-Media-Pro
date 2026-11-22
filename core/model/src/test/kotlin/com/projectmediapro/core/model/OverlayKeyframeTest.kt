package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayKeyframeTest {

    @Test
    fun evaluate_interpolatesLinearAndEasedKeyframesAccurately() {
        val k0 = OverlayKeyframe(
            timeFraction = 0f,
            centerX = 0.2f,
            centerY = 0.8f,
            scale = 0.5f,
            rotationDegrees = -20f,
            opacity = 0f,
            easing = KeyframeEasing.LINEAR,
        )
        val k1 = OverlayKeyframe(
            timeFraction = 1f,
            centerX = 0.8f,
            centerY = 0.2f,
            scale = 1.5f,
            rotationDegrees = 20f,
            opacity = 1f,
            easing = KeyframeEasing.LINEAR,
        )

        val mid = KeyframeInterpolator.evaluate(0.5f, 0.5f, listOf(k0, k1), 0.5f)
        assertEquals(0.5f, mid.centerX, 0.001f)
        assertEquals(0.5f, mid.centerY, 0.001f)
        assertEquals(1.0f, mid.scale, 0.001f)
        assertEquals(0f, mid.rotationDegrees, 0.001f)
        assertEquals(0.5f, mid.opacity, 0.001f)

        val timedMid = KeyframeInterpolator.evaluateAtTime(
            baseCenterX = 0.5f,
            baseCenterY = 0.5f,
            keyframes = listOf(k0, k1),
            elapsedTime = 2_000L,
            totalDuration = 4_000L,
        )
        assertEquals(mid, timedMid)

        val easeInMid = KeyframeInterpolator.applyEasing(0.5f, KeyframeEasing.EASE_IN)
        val easeOutMid = KeyframeInterpolator.applyEasing(0.5f, KeyframeEasing.EASE_OUT)
        assertTrue(easeInMid < 0.5f)
        assertTrue(easeOutMid > 0.5f)
    }

    @Test
    fun evaluate_handlesNaNAndSingleKeyframeBoundsSafely() {
        val resolvedEmpty = KeyframeInterpolator.evaluate(Float.NaN, Float.POSITIVE_INFINITY, emptyList(), Float.NaN)
        assertEquals(0.5f, resolvedEmpty.centerX, 0.001f)
        assertEquals(0.5f, resolvedEmpty.centerY, 0.001f)
        assertEquals(1.0f, resolvedEmpty.opacity, 0.001f)

        val single = OverlayKeyframe(timeFraction = 0.4f, centerX = 0.3f, centerY = 0.7f, scale = 1.2f, opacity = 0.6f)
        val atStart = KeyframeInterpolator.evaluate(0.5f, 0.5f, listOf(single), 0f)
        val atEnd = KeyframeInterpolator.evaluate(0.5f, 0.5f, listOf(single), 1f)
        assertEquals(atStart, atEnd)
        assertEquals(1.2f, atStart.scale, 0.001f)
    }

    @Test
    fun suggestNextTimeFraction_splitsLargestGapInsteadOfStackingAtMidpoint() {
        assertEquals(0f, KeyframeInterpolator.suggestNextTimeFraction(emptyList()), 0.001f)
        assertEquals(1f, KeyframeInterpolator.suggestNextTimeFraction(listOf(OverlayKeyframe(timeFraction = 0f))), 0.001f)
        assertEquals(
            0.5f,
            KeyframeInterpolator.suggestNextTimeFraction(
                listOf(OverlayKeyframe(timeFraction = 0f), OverlayKeyframe(timeFraction = 1f)),
            ),
            0.001f,
        )
        assertEquals(
            0.25f,
            KeyframeInterpolator.suggestNextTimeFraction(
                listOf(
                    OverlayKeyframe(timeFraction = 0f),
                    OverlayKeyframe(timeFraction = 0.5f),
                    OverlayKeyframe(timeFraction = 1f),
                ),
            ),
            0.001f,
        )
    }

    @Test
    fun matchingPreset_detectsActivePresetAndReturnsNullWhenEdited() {
        val zoom = KeyframePresets.build(KeyframePresetId.ZOOM_IN, 0.5f, 0.8f)
        assertEquals(KeyframePresetId.ZOOM_IN, KeyframePresets.matchingPreset(zoom, 0.5f, 0.8f))
        assertEquals(KeyframePresetId.NONE, KeyframePresets.matchingPreset(emptyList(), 0.5f, 0.8f))

        val modified = zoom.mapIndexed { idx, kf -> if (idx == 0) kf.copy(scale = 0.9f) else kf }
        assertNull(KeyframePresets.matchingPreset(modified, 0.5f, 0.8f))
    }

    @Test
    fun updateClipKeyframes_updatesTextAndOverlayClipsAndSerializesWithSchemaVersion1() {
        val base = Project(
            id = "p1",
            title = "Keyframes",
            durationMs = 8_000L,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(Clip(id = "v1", mediaUri = "p1/v1.mp4", startTimeMs = 0L, durationMs = 8_000L)),
                ),
            ),
        )
        val withText = ProjectTimeline.addTextClip(
            project = base,
            clipId = "txt1",
            text = "Hello",
            newTrackId = "t-txt",
        )
        val preset = KeyframePresets.zoomIn(Clip.DEFAULT_TEXT_CENTER_X, Clip.DEFAULT_TEXT_CENTER_Y)
        val animated = ProjectTimeline.updateClipKeyframes(withText, "txt1", preset)

        val clip = animated.textClips().single()
        assertTrue(clip.hasKeyframes)
        assertEquals(3, clip.keyframes.size)

        val restored = ProjectSerializer.deserialize(ProjectSerializer.serialize(animated))
        assertEquals(Project.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(3, restored.textClips().single().keyframes.size)

        val cleared = ProjectTimeline.updateClipKeyframes(animated, "txt1", emptyList())
        assertFalse(cleared.textClips().single().hasKeyframes)
    }
}
