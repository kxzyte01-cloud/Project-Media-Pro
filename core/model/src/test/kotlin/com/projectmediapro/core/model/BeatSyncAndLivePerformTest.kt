package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatSyncAndLivePerformTest {

    @Test
    fun `addBeatMarker sorts by time and upgrades effect within dedup window`() {
        val project = Project(id = "p1", title = "Beat Edit")
        val withFirst = ProjectTimeline.addBeatMarker(
            project,
            BeatMarker(id = "b1", timeMs = 1_000L, effect = BeatEffectKind.NONE),
        )
        val withSecond = ProjectTimeline.addBeatMarker(
            withFirst,
            BeatMarker(id = "b2", timeMs = 500L, effect = BeatEffectKind.FLASH),
        )
        // Tapping Zoom Punch 40ms away from b1 merges into b1 instead of duplicating
        val upgraded = ProjectTimeline.addBeatMarker(
            withSecond,
            BeatMarker(id = "b3", timeMs = 1_040L, effect = BeatEffectKind.ZOOM_PUNCH),
        )

        assertEquals(2, upgraded.beatMarkers.size)
        assertEquals(500L, upgraded.beatMarkers[0].timeMs)
        assertEquals(BeatEffectKind.FLASH, upgraded.beatMarkers[0].effect)
        assertEquals(1_000L, upgraded.beatMarkers[1].timeMs)
        assertEquals(BeatEffectKind.ZOOM_PUNCH, upgraded.beatMarkers[1].effect)
    }

    @Test
    fun `autoSplitVideoOnBeats splits video clips cleanly at beat timestamps`() {
        val clip = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 6_000L,
            sourceDurationMs = 6_000L,
            trimStartMs = 0L,
            trimEndMs = 6_000L,
        )
        val project = Project(
            id = "p1",
            title = "Beat Cut",
            tracks = listOf(Track(id = "tv", type = TrackType.VIDEO, clips = listOf(clip))),
            durationMs = 6_000L,
            beatMarkers = listOf(
                BeatMarker(id = "b1", timeMs = 2_000L),
                BeatMarker(id = "b2", timeMs = 4_000L),
            ),
        )

        var counter = 0
        val cut = ProjectTimeline.autoSplitVideoOnBeats(project) { "cut-${++counter}" }
        val clips = cut.videoClips()
        assertEquals(3, clips.size)
        assertEquals(2_000L, ProjectTimeline.effectiveDurationMs(clips[0]))
        assertEquals(2_000L, ProjectTimeline.effectiveDurationMs(clips[1]))
        assertEquals(2_000L, ProjectTimeline.effectiveDurationMs(clips[2]))
    }

    @Test
    fun `beatEffectEvaluator computes active punch, shake, flash, and glitch decay`() {
        val markers = listOf(
            BeatMarker(id = "z", timeMs = 1_000L, effect = BeatEffectKind.ZOOM_PUNCH, durationMs = 260L),
            BeatMarker(id = "f", timeMs = 2_000L, effect = BeatEffectKind.FLASH, durationMs = 260L),
            BeatMarker(id = "g", timeMs = 3_000L, effect = BeatEffectKind.GLITCH, durationMs = 260L),
        )

        val zoomSample = BeatEffectEvaluator.evaluate(markers, timeMs = 1_020L)
        assertTrue(zoomSample.scale > 1.05f)

        val flashSample = BeatEffectEvaluator.evaluate(markers, timeMs = 2_010L)
        assertTrue(flashSample.flashAlpha > 0.4f)

        val glitchSample = BeatEffectEvaluator.evaluate(markers, timeMs = 3_020L)
        assertTrue(glitchSample.glitchAmount > 0.4f)

        val idleSample = BeatEffectEvaluator.evaluate(markers, timeMs = 5_000L)
        assertTrue(idleSample.isIdentity)
    }
}
