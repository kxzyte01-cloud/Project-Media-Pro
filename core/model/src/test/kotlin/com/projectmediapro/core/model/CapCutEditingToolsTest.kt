package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapCutEditingToolsTest {

    private fun sampleProject(): Project {
        val clip1 = Clip(
            id = "v1",
            mediaUri = "media/v1.mp4",
            startTimeMs = 0L,
            durationMs = 10_000L,
            sourceDurationMs = 10_000L,
            trimStartMs = 0L,
            trimEndMs = 10_000L,
        )
        val clip2 = Clip(
            id = "v2",
            mediaUri = "media/v2.mp4",
            startTimeMs = 10_000L,
            durationMs = 4_000L,
            sourceDurationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
        )
        val stillClip = Clip(
            id = "v3",
            mediaUri = "media/freeze.png",
            startTimeMs = 14_000L,
            durationMs = 2_000L,
            sourceDurationMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
            isStillImage = true,
        )
        return Project(
            id = "p1",
            title = "CapCut Test",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            durationMs = 16_000L,
            tracks = listOf(
                Track(id = "t_video", type = TrackType.VIDEO, clips = listOf(clip1, clip2, stillClip)),
            ),
        )
    }

    @Test
    fun splitVideoClipAtPlayheadSplitsSelectedClipAndRespectsMinDuration() {
        val project = sampleProject()
        val split = ProjectTimeline.splitVideoClipAtPlayhead(
            project = project,
            timelinePositionMs = 4_000L,
            newClipId = "v1_b",
            targetClipId = "v1",
        )
        val videos = split.videoClips()
        assertEquals(4, videos.size)
        assertEquals(4_000L, videos[0].durationMs)
        assertEquals(6_000L, videos[1].durationMs)
        assertEquals("v1_b", videos[1].id)

        // Too close to start (< 500ms) must be a no-op
        val blocked = ProjectTimeline.splitVideoClipAtPlayhead(
            project = project,
            timelinePositionMs = 200L,
            newClipId = "v1_bad",
            targetClipId = "v1",
        )
        assertEquals(3, blocked.videoClips().size)
    }

    @Test
    fun trimLeftAndRightToPlayheadEnforceMinClipDuration() {
        val project = sampleProject()

        // Cut Till Here (Trim Right to Playhead at 6s on 10s clip)
        val cutRight = ProjectTimeline.trimVideoClipRightToPlayhead(
            project = project,
            timelinePositionMs = 6_000L,
            targetClipId = "v1",
        )
        assertEquals(6_000L, cutRight.videoClips().first().durationMs)
        assertEquals(6_000L, cutRight.videoClips().first().trimEndMs)

        // Cut Left to Playhead at 3s on 10s clip
        val cutLeft = ProjectTimeline.trimVideoClipLeftToPlayhead(
            project = project,
            timelinePositionMs = 3_000L,
            targetClipId = "v1",
        )
        assertEquals(7_000L, cutLeft.videoClips().first().durationMs)
        assertEquals(3_000L, cutLeft.videoClips().first().trimStartMs)

        // Guard: Cut Left leaving < 500ms must be a no-op
        val blockedLeft = ProjectTimeline.trimVideoClipLeftToPlayhead(
            project = project,
            timelinePositionMs = 9_700L,
            targetClipId = "v1",
        )
        assertEquals(10_000L, blockedLeft.videoClips().first().durationMs)
    }

    @Test
    fun applyVisualEffectsAndAudioMixToAllClipsUpdatesEveryEligibleClip() {
        val project = sampleProject()
        val fx = ClipVisualEffects(
            filter = VideoFilterPreset.CINEMA,
            brightness = 0.1f,
            transitionIn = ClipTransition.FADE_BLACK,
            fadeInMs = 2_500L,
        )
        val withFx = ProjectTimeline.applyVisualEffectsToAllVideoClips(project, fx)
        withFx.videoClips().forEach { clip ->
            assertEquals(VideoFilterPreset.CINEMA, clip.visualEffects?.filter)
            // Transition fade must be clamped to <= half clip duration
            assertTrue((clip.visualEffects?.fadeInMs ?: 0L) <= clip.durationMs / 2L)
        }

        val withAudio = ProjectTimeline.applyAudioMixToAllClips(
            project = project,
            trackType = TrackType.VIDEO,
            volume = 1.0f,
            audioBoost = 1.5f,
            fadeInMs = 1_000L,
            fadeOutMs = 1_000L,
            audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.VOCAL_CLARITY),
        )
        val videos = withAudio.videoClips()
        assertEquals(1.0f, videos[0].volume, 0.001f)
        assertEquals(1.5f, videos[1].audioBoost, 0.001f)
        assertEquals(AudioEqPreset.VOCAL_CLARITY, videos[1].audioEnhance?.eqPreset)
        // Still image clip should remain untouched
        assertEquals(1.0f, videos[2].volume, 0.001f)
    }

    @Test
    fun beatEffectEvaluatorBinarySearchMatchesLargeSortedBeatList() {
        val markers = (0 until 250).map { idx ->
            BeatMarker(
                id = "b_$idx",
                timeMs = idx * 500L,
                effect = if (idx == 180) BeatEffectKind.ZOOM_PUNCH else BeatEffectKind.NONE,
                durationMs = 300L,
                intensity = 0.9f,
            )
        }
        val hit = BeatEffectEvaluator.evaluate(markers, timeMs = 90_050L, soundReactiveEnabled = false)
        assertNotEquals(BeatEffectSample.IDENTITY, hit)
        assertTrue(hit.scale > 1.05f)

        val miss = BeatEffectEvaluator.evaluate(markers, timeMs = 90_450L, soundReactiveEnabled = false)
        assertEquals(BeatEffectSample.IDENTITY, miss)
    }

    @Test
    fun trimLeftAndRightWorkOnUntrimmedFullLengthClip() {
        // Explicit regression test for P0-3: trimEndMs == sourceDurationMs (resolvedTrimBounds returns 0,0)
        val untrimmed = Clip(
            id = "u1",
            mediaUri = "media/u1.mp4",
            startTimeMs = 0L,
            durationMs = 8_000L,
            sourceDurationMs = 8_000L,
            trimStartMs = 0L,
            trimEndMs = 0L,
        )
        val project = Project(
            id = "p_untrimmed",
            title = "Untrimmed",
            durationMs = 8_000L,
            tracks = listOf(Track(id = "tv", type = TrackType.VIDEO, clips = listOf(untrimmed))),
        )
        val rightTrimmed = ProjectTimeline.trimVideoClipRightToPlayhead(project, 5_000L, "u1")
        assertEquals(5_000L, rightTrimmed.videoClips().first().durationMs)

        val leftTrimmed = ProjectTimeline.trimVideoClipLeftToPlayhead(project, 2_000L, "u1")
        assertEquals(6_000L, leftTrimmed.videoClips().first().durationMs)
    }

    @Test
    fun speedRampInverseMappingRoundTripsAccurately() {
        val sourceSpanMs = 8_000L
        for (preset in SpeedRampPreset.entries) {
            val totalTimelineMs = SpeedRampEvaluator.scaledDurationMs(sourceSpanMs, 1.0f, preset)
            val midSource = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(totalTimelineMs / 2L, sourceSpanMs, 1.0f, preset)
            val backTimeline = SpeedRampEvaluator.sourceOffsetToTimelineOffsetMs(midSource, sourceSpanMs, 1.0f, preset)
            assertTrue(
                "Round-trip drift too large for $preset: expected ${totalTimelineMs / 2L}, got $backTimeline",
                kotlin.math.abs(backTimeline - (totalTimelineMs / 2L)) <= 20L,
            )
        }
    }

    @Test
    fun beatEffectEvaluatorHandlesUnsortedMiddleMarkersAbove16Items() {
        val markers = (0 until 20).map { idx ->
            BeatMarker(id = "b_$idx", timeMs = idx * 1_000L, effect = BeatEffectKind.NONE)
        }.toMutableList()
        // Insert an out-of-order ZOOM_PUNCH in the middle while first <= last still holds
        markers[10] = BeatMarker(id = "b_out_of_order", timeMs = 2_500L, effect = BeatEffectKind.ZOOM_PUNCH, durationMs = 300L, intensity = 0.9f)
        val hit = BeatEffectEvaluator.evaluate(markers, timeMs = 2_550L, soundReactiveEnabled = false)
        assertTrue("Unsorted middle marker must still trigger", hit.scale > 1.05f)
    }
}
