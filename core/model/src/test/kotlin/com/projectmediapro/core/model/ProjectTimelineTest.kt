package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProjectTimelineTest {

    @Test
    fun appendClip_onEmptyProjectWithoutTracks_createsVideoTrackAndCalculatesDuration() {
        val empty = Project(id = "p-1", title = "Empty", tracks = emptyList())
        val clip = Clip(
            id = "c-1",
            mediaUri = "p-1/c-1.mp4",
            startTimeMs = 500L,
            durationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
        )

        val updated = ProjectTimeline.appendClip(empty, clip, "new-video-track")

        assertEquals(1, updated.tracks.size)
        assertEquals("new-video-track", updated.tracks[0].id)
        assertEquals(TrackType.VIDEO, updated.tracks[0].type)
        assertEquals(1, updated.tracks[0].clips.size)
        assertEquals(0L, updated.tracks[0].clips[0].startTimeMs)
        assertEquals(4_000L, updated.durationMs)
    }

    @Test
    fun appendClip_severalClips_placesEachAtEndOfPreviousClip() {
        val initial = Project(
            id = "p-1",
            title = "Demo",
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO)),
        )
        val c1 = Clip(id = "c1", mediaUri = "p-1/c1.mp4", startTimeMs = 0L, durationMs = 2_500L)
        val c2 = Clip(id = "c2", mediaUri = "p-1/c2.mp4", startTimeMs = 0L, durationMs = 3_500L)
        val c3 = Clip(id = "c3", mediaUri = "p-1/c3.mp4", startTimeMs = 0L, durationMs = 4_000L)

        val step1 = ProjectTimeline.appendClip(initial, c1, "unused")
        val step2 = ProjectTimeline.appendClip(step1, c2, "unused")
        val step3 = ProjectTimeline.appendClip(step2, c3, "unused")

        val clips = step3.tracks[0].clips
        assertEquals(listOf(0L, 2_500L, 6_000L), clips.map { it.startTimeMs })
        assertEquals(10_000L, step3.durationMs)
    }

    @Test
    fun removeClip_firstOrMiddleClip_ripplesLaterClipsEarlierWithoutGaps() {
        val c1 = Clip(id = "c1", mediaUri = "p/1.mp4", startTimeMs = 0L, durationMs = 2_000L)
        val c2 = Clip(id = "c2", mediaUri = "p/2.mp4", startTimeMs = 2_000L, durationMs = 5_000L)
        val c3 = Clip(id = "c3", mediaUri = "p/3.mp4", startTimeMs = 7_000L, durationMs = 3_000L)
        val project = Project(
            id = "p",
            title = "Ripple",
            durationMs = 10_000L,
            tracks = listOf(Track(id = "v", type = TrackType.VIDEO, clips = listOf(c1, c2, c3))),
        )

        val afterFirstRemoved = ProjectTimeline.removeClip(project, "c1")
        assertEquals(listOf("c2", "c3"), afterFirstRemoved.tracks[0].clips.map { it.id })
        assertEquals(listOf(0L, 5_000L), afterFirstRemoved.tracks[0].clips.map { it.startTimeMs })
        assertEquals(8_000L, afterFirstRemoved.durationMs)

        val afterMiddleRemoved = ProjectTimeline.removeClip(project, "c2")
        assertEquals(listOf("c1", "c3"), afterMiddleRemoved.tracks[0].clips.map { it.id })
        assertEquals(listOf(0L, 2_000L), afterMiddleRemoved.tracks[0].clips.map { it.startTimeMs })
        assertEquals(5_000L, afterMiddleRemoved.durationMs)
    }

    @Test
    fun removeClip_missingId_returnsProjectUnchanged() {
        val c1 = Clip(id = "c1", mediaUri = "p/1.mp4", startTimeMs = 0L, durationMs = 2_000L)
        val project = Project(
            id = "p",
            title = "Unchanged",
            durationMs = 2_000L,
            tracks = listOf(Track(id = "v", type = TrackType.VIDEO, clips = listOf(c1))),
        )

        val result = ProjectTimeline.removeClip(project, "unknown-clip")

        assertSame(project, result)
    }

    @Test
    fun twoTracks_appendAndRemove_recalculatesDurationAcrossAllTracks() {
        val audioClip = Clip(id = "a1", mediaUri = "p/a1.m4a", startTimeMs = 0L, durationMs = 15_000L)
        val videoClip = Clip(id = "v1", mediaUri = "p/v1.mp4", startTimeMs = 0L, durationMs = 6_000L)
        val project = Project(
            id = "p",
            title = "Two Tracks",
            durationMs = 15_000L,
            tracks = listOf(
                Track(id = "t-audio", type = TrackType.AUDIO, clips = listOf(audioClip)),
                Track(id = "t-video", type = TrackType.VIDEO, clips = listOf(videoClip)),
            ),
        )

        val v2 = Clip(id = "v2", mediaUri = "p/v2.mp4", startTimeMs = 0L, durationMs = 12_000L)
        val appended = ProjectTimeline.appendClip(project, v2, "unused")
        assertEquals(18_000L, appended.durationMs)

        val removedV2 = ProjectTimeline.removeClip(appended, "v2")
        assertEquals(15_000L, removedV2.durationMs)

        val removedAudio = ProjectTimeline.removeClip(removedV2, "a1")
        assertEquals(6_000L, removedAudio.durationMs)
    }

    @Test
    fun formatDuration_handlesNegativeZeroSecondsMinutesAndHours() {
        assertEquals("0:00", ProjectTimeline.formatDuration(Long.MIN_VALUE))
        assertEquals("0:00", ProjectTimeline.formatDuration(-1L))
        assertEquals("0:00", ProjectTimeline.formatDuration(0L))
        assertEquals("0:00", ProjectTimeline.formatDuration(999L))
        assertEquals("0:07", ProjectTimeline.formatDuration(7_000L))
        assertEquals("1:05", ProjectTimeline.formatDuration(65_000L))
        assertEquals("59:59", ProjectTimeline.formatDuration(3_599_000L))
        assertEquals("1:02:03", ProjectTimeline.formatDuration(3_723_000L))
    }
}
