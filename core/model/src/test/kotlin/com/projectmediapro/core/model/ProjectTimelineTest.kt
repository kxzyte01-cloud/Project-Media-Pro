package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
            sourceDurationMs = 4_000L,
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
    fun appendClip_audioTrackType_createsAudioTrackAndPositionsAudioClipsSequentially() {
        val v1 = Clip(id = "v1", mediaUri = "p/v1.mp4", startTimeMs = 0L, durationMs = 8_000L)
        val initial = Project(
            id = "p",
            title = "Audio Demo",
            durationMs = 8_000L,
            tracks = listOf(Track(id = "t-video", type = TrackType.VIDEO, clips = listOf(v1))),
        )
        val a1 = Clip(id = "a1", mediaUri = "p/a1.mp3", startTimeMs = 0L, durationMs = 3_000L)
        val a2 = Clip(id = "a2", mediaUri = "p/a2.m4a", startTimeMs = 0L, durationMs = 4_000L)

        val withFirstAudio = ProjectTimeline.appendClip(initial, a1, "t-audio", TrackType.AUDIO)
        val withSecondAudio = ProjectTimeline.appendClip(withFirstAudio, a2, "unused", TrackType.AUDIO)

        assertEquals(listOf("v1"), withSecondAudio.videoClips().map { it.id })
        assertEquals(listOf("a1", "a2"), withSecondAudio.audioClips().map { it.id })
        assertEquals(listOf(0L, 3_000L), withSecondAudio.audioClips().map { it.startTimeMs })
        assertEquals(8_000L, withSecondAudio.durationMs)
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
    fun effectiveSourceDurationMs_andTrimPredicates_resolveKnownAndFallbackBounds() {
        val explicit = Clip(
            id = "c1",
            mediaUri = "p/1.mp4",
            startTimeMs = 0L,
            durationMs = 2_000L,
            trimStartMs = 500L,
            trimEndMs = 2_500L,
            sourceDurationMs = 8_000L,
        )
        val unknownWithTrimEnd = Clip(
            id = "c2",
            mediaUri = "p/2.mp4",
            startTimeMs = 0L,
            durationMs = 3_000L,
            trimStartMs = 1_000L,
            trimEndMs = 4_000L,
            sourceDurationMs = 0L,
        )
        val untrimmed = Clip(
            id = "c3",
            mediaUri = "p/3.mp4",
            startTimeMs = 0L,
            durationMs = 5_000L,
            trimStartMs = 0L,
            trimEndMs = 5_000L,
            sourceDurationMs = 5_000L,
        )

        assertEquals(8_000L, ProjectTimeline.effectiveSourceDurationMs(explicit))
        assertEquals(4_000L, ProjectTimeline.effectiveSourceDurationMs(unknownWithTrimEnd))
        assertEquals(5_000L, ProjectTimeline.effectiveSourceDurationMs(untrimmed))
        assertTrue(ProjectTimeline.isClipTrimmed(explicit))
        assertFalse(ProjectTimeline.isClipTrimmed(untrimmed))
        assertEquals(500L to 2_500L, ProjectTimeline.resolvedTrimBounds(explicit))
        assertEquals(0L to 0L, ProjectTimeline.resolvedTrimBounds(untrimmed))
    }

    @Test
    fun trimClip_updatesClipDurationRipplesSubsequentClipsAndValidatesBounds() {
        val c1 = Clip(
            id = "c1",
            mediaUri = "p/1.mp4",
            startTimeMs = 0L,
            durationMs = 5_000L,
            trimStartMs = 0L,
            trimEndMs = 5_000L,
            sourceDurationMs = 5_000L,
        )
        val c2 = Clip(
            id = "c2",
            mediaUri = "p/2.mp4",
            startTimeMs = 5_000L,
            durationMs = 3_000L,
            trimStartMs = 0L,
            trimEndMs = 3_000L,
            sourceDurationMs = 3_000L,
        )
        val project = Project(
            id = "p",
            title = "Trim",
            durationMs = 8_000L,
            tracks = listOf(Track(id = "v", type = TrackType.VIDEO, clips = listOf(c1, c2))),
        )

        val trimmed = ProjectTimeline.trimClip(project, "c1", newStartMs = 1_000L, newEndMs = 3_500L)
        val clips = trimmed.tracks[0].clips
        assertEquals(1_000L, clips[0].trimStartMs)
        assertEquals(3_500L, clips[0].trimEndMs)
        assertEquals(2_500L, clips[0].durationMs)
        assertEquals(5_000L, clips[0].sourceDurationMs)
        assertEquals(0L, clips[0].startTimeMs)
        assertEquals(2_500L, clips[1].startTimeMs)
        assertEquals(5_500L, trimmed.durationMs)

        val minTrimmed = ProjectTimeline.trimClip(trimmed, "c1", newStartMs = 2_000L, newEndMs = 2_500L)
        assertEquals(500L, minTrimmed.tracks[0].clips[0].durationMs)
        assertEquals(3_500L, minTrimmed.durationMs)

        assertSame(project, ProjectTimeline.trimClip(project, "missing", 0L, 1_000L))
        assertSame(project, ProjectTimeline.trimClip(project, "c1", -1L, 2_000L))
        assertSame(project, ProjectTimeline.trimClip(project, "c1", 2_000L, 2_000L))
        assertSame(project, ProjectTimeline.trimClip(project, "c1", 2_000L, 2_499L))
        assertSame(project, ProjectTimeline.trimClip(project, "c1", 0L, 5_001L))
    }

    @Test
    fun splitClip_createsTwoAdjacentPartsSharingFile_ripplesLaterClips_andRejectsInvalidOffsets() {
        val c1 = Clip(
            id = "c1",
            mediaUri = "p/shared.mp4",
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimStartMs = 1_000L,
            trimEndMs = 5_000L,
            sourceDurationMs = 6_000L,
        )
        val c2 = Clip(
            id = "c2",
            mediaUri = "p/other.mp4",
            startTimeMs = 4_000L,
            durationMs = 2_000L,
            trimStartMs = 0L,
            trimEndMs = 2_000L,
            sourceDurationMs = 2_000L,
        )
        val project = Project(
            id = "p",
            title = "Split",
            durationMs = 6_000L,
            tracks = listOf(Track(id = "v", type = TrackType.VIDEO, clips = listOf(c1, c2))),
        )

        val split = ProjectTimeline.splitClip(project, "c1", offsetMs = 1_500L, newClipId = "c1-part2")
        val clips = split.tracks[0].clips
        assertEquals(listOf("c1", "c1-part2", "c2"), clips.map { it.id })
        assertEquals("p/shared.mp4", clips[0].mediaUri)
        assertEquals("p/shared.mp4", clips[1].mediaUri)
        assertEquals(1_000L, clips[0].trimStartMs)
        assertEquals(2_500L, clips[0].trimEndMs)
        assertEquals(1_500L, clips[0].durationMs)
        assertEquals(6_000L, clips[0].sourceDurationMs)
        assertEquals(2_500L, clips[1].trimStartMs)
        assertEquals(5_000L, clips[1].trimEndMs)
        assertEquals(2_500L, clips[1].durationMs)
        assertEquals(6_000L, clips[1].sourceDurationMs)
        assertEquals(listOf(0L, 1_500L, 4_000L), clips.map { it.startTimeMs })
        assertEquals(6_000L, split.durationMs)

        assertSame(project, ProjectTimeline.splitClip(project, "missing", 1_000L, "new-id"))
        assertSame(project, ProjectTimeline.splitClip(project, "c1", 499L, "new-id"))
        assertSame(project, ProjectTimeline.splitClip(project, "c1", 3_501L, "new-id"))
        assertSame(project, ProjectTimeline.splitClip(project, "c1", 1_500L, ""))
        assertSame(project, ProjectTimeline.splitClip(project, "c1", 1_500L, "c1"))
        assertSame(project, ProjectTimeline.splitClip(project, "c1", 1_500L, "c2"))
    }

    @Test
    fun moveClip_swapsWithinTrackRecalculatesStartTimes_andLeavesEndsUnchanged() {
        val c1 = Clip(id = "c1", mediaUri = "p/1.mp4", startTimeMs = 0L, durationMs = 1_000L)
        val c2 = Clip(id = "c2", mediaUri = "p/2.mp4", startTimeMs = 1_000L, durationMs = 3_000L)
        val c3 = Clip(id = "c3", mediaUri = "p/3.mp4", startTimeMs = 4_000L, durationMs = 2_000L)
        val project = Project(
            id = "p",
            title = "Move",
            durationMs = 6_000L,
            tracks = listOf(Track(id = "v", type = TrackType.VIDEO, clips = listOf(c1, c2, c3))),
        )

        assertSame(project, ProjectTimeline.moveClip(project, "c1", -1))
        assertSame(project, ProjectTimeline.moveClip(project, "c3", 1))
        assertSame(project, ProjectTimeline.moveClip(project, "c2", 0))
        assertSame(project, ProjectTimeline.moveClip(project, "c2", 2))
        assertSame(project, ProjectTimeline.moveClip(project, "missing", 1))

        val movedLater = ProjectTimeline.moveClip(project, "c1", 1)
        assertEquals(listOf("c2", "c1", "c3"), movedLater.tracks[0].clips.map { it.id })
        assertEquals(listOf(0L, 3_000L, 4_000L), movedLater.tracks[0].clips.map { it.startTimeMs })
        assertEquals(6_000L, movedLater.durationMs)

        val movedEarlier = ProjectTimeline.moveClip(movedLater, "c3", -1)
        assertEquals(listOf("c2", "c3", "c1"), movedEarlier.tracks[0].clips.map { it.id })
        assertEquals(listOf(0L, 3_000L, 5_000L), movedEarlier.tracks[0].clips.map { it.startTimeMs })
        assertEquals(6_000L, movedEarlier.durationMs)
    }

    @Test
    fun setClipVolume_clampsToZeroAndOne_worksOnAudioClips_andReturnsUnchangedWhenMissing() {
        val a1 = Clip(
            id = "a1",
            mediaUri = "p/a1.mp3",
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            sourceDurationMs = 4_000L,
            volume = 1.0f,
        )
        val a2 = Clip(
            id = "a2",
            mediaUri = "p/a2.mp3",
            startTimeMs = 4_000L,
            durationMs = 3_000L,
            trimStartMs = 0L,
            trimEndMs = 3_000L,
            sourceDurationMs = 3_000L,
            volume = 0.5f,
        )
        val project = Project(
            id = "p",
            title = "Audio Ops",
            durationMs = 7_000L,
            tracks = listOf(Track(id = "t-a", type = TrackType.AUDIO, clips = listOf(a1, a2))),
        )

        val halfVolume = ProjectTimeline.setClipVolume(project, "a1", 0.65f)
        assertEquals(0.65f, halfVolume.audioClips()[0].volume, 0.0001f)

        val clampedLow = ProjectTimeline.setClipVolume(project, "a1", -0.5f)
        assertEquals(0.0f, clampedLow.audioClips()[0].volume, 0.0001f)

        val clampedHigh = ProjectTimeline.setClipVolume(project, "a2", 1.8f)
        assertEquals(1.0f, clampedHigh.audioClips()[1].volume, 0.0001f)

        assertSame(project, ProjectTimeline.setClipVolume(project, "missing", 0.5f))
        assertSame(project, ProjectTimeline.setClipVolume(project, "a2", 0.5f))

        val trimmedAudio = ProjectTimeline.trimClip(project, "a1", 500L, 2_500L)
        assertEquals(2_000L, trimmedAudio.audioClips()[0].durationMs)
        assertEquals(2_000L, trimmedAudio.audioClips()[1].startTimeMs)
        assertEquals(5_000L, trimmedAudio.durationMs)

        val movedAudio = ProjectTimeline.moveClip(project, "a1", 1)
        assertEquals(listOf("a2", "a1"), movedAudio.audioClips().map { it.id })
        assertEquals(listOf(0L, 3_000L), movedAudio.audioClips().map { it.startTimeMs })
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
