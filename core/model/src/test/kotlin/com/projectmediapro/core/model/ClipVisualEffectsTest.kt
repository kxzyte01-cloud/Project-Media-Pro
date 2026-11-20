package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipVisualEffectsTest {

    @Test
    fun sanitized_clampsRangesNaNsAndHalfDurationFades() {
        val dirty = ClipVisualEffects(
            filter = VideoFilterPreset.CINEMA,
            brightness = Float.NaN,
            contrast = 5f,
            saturation = -250f,
            warmth = 2f,
            transitionIn = ClipTransition.FADE_BLACK,
            fadeInMs = 2_500L,
            transitionOut = ClipTransition.FADE_WHITE,
            fadeOutMs = 2_500L,
        )

        val clean = dirty.sanitized(maxClipDurationMs = 2_000L)

        assertEquals(0f, clean.brightness, 0.0001f)
        assertEquals(ClipVisualEffects.MAX_CONTRAST, clean.contrast, 0.0001f)
        assertEquals(ClipVisualEffects.MIN_SATURATION, clean.saturation, 0.0001f)
        assertEquals(ClipVisualEffects.MAX_WARMTH, clean.warmth, 0.0001f)
        assertEquals(1_000L, clean.fadeInMs)
        assertEquals(1_000L, clean.fadeOutMs)
        assertTrue(clean.hasFades)
        assertFalse(clean.isIdentity)
    }

    @Test
    fun gradeSpec_andWarmthScales_driveNonIdentityBehaviorConsistently() {
        assertEquals(FilterGradeSpec(), VideoFilterPreset.NONE.gradeSpec())
        assertTrue(VideoFilterPreset.BW.gradeSpec().isGrayscale)
        assertTrue(VideoFilterPreset.VINTAGE.gradeSpec().isSepia)
        assertTrue(VideoFilterPreset.WARM.gradeSpec().redScale > VideoFilterPreset.WARM.gradeSpec().blueScale)
        assertTrue(VideoFilterPreset.COOL.gradeSpec().blueScale > VideoFilterPreset.COOL.gradeSpec().redScale)

        val (warmRed, warmBlue) = ClipVisualEffects(warmth = 0.5f).warmthRgbScales()
        assertTrue(warmRed > 1f && warmBlue < 1f)
        assertTrue(ClipVisualEffects().isIdentity)
    }

    @Test
    fun fadeOverlayAt_computesStartMidpointAndEndAlphaAccurately() {
        val effects = ClipVisualEffects(
            transitionIn = ClipTransition.FADE_BLACK,
            fadeInMs = 1_000L,
            transitionOut = ClipTransition.FADE_WHITE,
            fadeOutMs = 1_000L,
        )

        val atStart = requireNotNull(effects.fadeOverlayAt(0L, 4_000L))
        assertEquals(ClipTransition.FADE_BLACK, atStart.first)
        assertEquals(1f, atStart.second, 0.0001f)

        val atHalfIn = requireNotNull(effects.fadeOverlayAt(500L, 4_000L))
        assertEquals(ClipTransition.FADE_BLACK, atHalfIn.first)
        assertEquals(0.5f, atHalfIn.second, 0.0001f)

        assertNull(effects.fadeOverlayAt(2_000L, 4_000L))

        val atHalfOut = requireNotNull(effects.fadeOverlayAt(3_500L, 4_000L))
        assertEquals(ClipTransition.FADE_WHITE, atHalfOut.first)
        assertEquals(0.5f, atHalfOut.second, 0.0001f)

        val atEnd = requireNotNull(effects.fadeOverlayAt(4_000L, 4_000L))
        assertEquals(ClipTransition.FADE_WHITE, atEnd.first)
        assertEquals(1f, atEnd.second, 0.0001f)
    }

    @Test
    fun updateClipVisualEffects_normalizesIdentityAndReclampsOnTrim() {
        val clip = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 6_000L,
            trimEndMs = 6_000L,
            sourceDurationMs = 6_000L,
        )
        val project = Project(
            id = "p1",
            title = "Grade",
            durationMs = 6_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(clip))),
        )

        val graded = ProjectTimeline.updateClipVisualEffects(
            project = project,
            clipId = "v1",
            effects = ClipVisualEffects(
                filter = VideoFilterPreset.VIVID,
                transitionIn = ClipTransition.FADE_BLACK,
                fadeInMs = 2_000L,
            ),
        )
        assertEquals(VideoFilterPreset.VIVID, graded.videoClips().first().visualEffects?.filter)
        assertEquals(2_000L, graded.videoClips().first().visualEffects?.fadeInMs)

        val trimmed = ProjectTimeline.trimClip(graded, "v1", 0L, 2_000L)
        assertEquals(1_000L, trimmed.videoClips().first().visualEffects?.fadeInMs)

        val reset = ProjectTimeline.updateClipVisualEffects(trimmed, "v1", ClipVisualEffects())
        assertNull(reset.videoClips().first().visualEffects)
    }

    @Test
    fun serializer_roundTripsVisualEffectsAndPreservesLegacySchemaV1() {
        val clip = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 5_000L,
            visualEffects = ClipVisualEffects(
                filter = VideoFilterPreset.VINTAGE,
                brightness = 0.1f,
                contrast = 0.2f,
                saturation = -15f,
                warmth = 0.25f,
                transitionOut = ClipTransition.FADE_BLACK,
                fadeOutMs = 800L,
            ),
        )
        val project = Project(
            id = "p1",
            title = "Effects Roundtrip",
            durationMs = 5_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(clip))),
        )

        val restored = ProjectSerializer.deserialize(ProjectSerializer.serialize(project))
        assertEquals(project, restored)
    }
}
