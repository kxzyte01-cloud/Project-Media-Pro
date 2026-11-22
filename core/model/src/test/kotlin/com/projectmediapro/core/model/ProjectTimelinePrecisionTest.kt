package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectTimelinePrecisionTest {

    @Test
    fun replaceClipMedia_swapsFileWhilePreservingEditsAndClampingTrim() {
        val original = Clip(
            id = "v1",
            mediaUri = "p1/old.mp4",
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimStartMs = 1_000L,
            trimEndMs = 9_000L,
            sourceDurationMs = 10_000L,
            playbackSpeed = 2.0f,
            volume = 0.6f,
            rotationDegrees = 90,
            flipHorizontal = true,
            crop = ClipCrop(left = 0.1f, top = 0.1f, right = 0.9f, bottom = 0.9f),
        )
        val project = Project(
            id = "p1",
            title = "Replace",
            durationMs = 4_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(original))),
        )

        val replaced = ProjectTimeline.replaceClipMedia(
            project = project,
            clipId = "v1",
            newMediaUri = "p1/new.mp4",
            newDurationMs = 5_000L,
        )
        val clip = replaced.videoClips().single()
        assertEquals("p1/new.mp4", clip.mediaUri)
        assertEquals(5_000L, clip.sourceDurationMs)
        assertEquals(1_000L, clip.trimStartMs)
        assertEquals(5_000L, clip.trimEndMs)
        assertEquals(2_000L, clip.durationMs)
        assertEquals(2.0f, clip.playbackSpeed, 0.001f)
        assertEquals(0.6f, clip.volume, 0.001f)
        assertEquals(90, clip.rotationDegrees)
        assertEquals(true, clip.flipHorizontal)
        assertFalse(clip.isStillImage)
    }

    @Test
    fun copyClipFromPlayhead_andTillPlayhead_sliceVideoAndOverlayAccurately() {
        val video = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 10_000L,
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            sourceDurationMs = 10_000L,
        )
        val overlay = Clip(
            id = "o1",
            mediaUri = "p1/o1.png",
            startTimeMs = 2_000L,
            durationMs = 6_000L,
        )
        val project = Project(
            id = "p1",
            title = "Copy Playhead",
            durationMs = 10_000L,
            tracks = listOf(
                Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(video)),
                Track(id = "t-o", type = TrackType.OVERLAY, clips = listOf(overlay)),
            ),
        )

        val fromVideo = ProjectTimeline.copyClipFromPlayhead(project, "v1", playheadMs = 4_000L, newClipId = "v2")
        assertEquals(2, fromVideo.videoClips().size)
        val v2 = fromVideo.videoClips()[1]
        assertEquals(4_000L, v2.trimStartMs)
        assertEquals(10_000L, v2.trimEndMs)
        assertEquals(6_000L, v2.durationMs)

        val tillVideo = ProjectTimeline.copyClipTillPlayhead(project, "v1", playheadMs = 4_000L, newClipId = "v3")
        val v3 = tillVideo.videoClips()[1]
        assertEquals(0L, v3.trimStartMs)
        assertEquals(4_000L, v3.trimEndMs)
        assertEquals(4_000L, v3.durationMs)

        val fromOverlay = ProjectTimeline.copyClipFromPlayhead(project, "o1", playheadMs = 5_000L, newClipId = "o2")
        val o2 = fromOverlay.overlayClips()[1]
        assertEquals(5_000L, o2.startTimeMs)
        assertEquals(3_000L, o2.durationMs)

        val tillOverlay = ProjectTimeline.copyClipTillPlayhead(project, "o1", playheadMs = 5_000L, newClipId = "o3")
        val o3 = tillOverlay.overlayClips()[1]
        assertEquals(2_000L, o3.startTimeMs)
        assertEquals(3_000L, o3.durationMs)

        val toOverlay = ProjectTimeline.copyClipToPlayhead(project, "o1", playheadMs = 6_000L, newClipId = "o4")
        val o4 = toOverlay.overlayClips()[1]
        assertEquals(6_000L, o4.startTimeMs)
        assertEquals(4_000L, o4.durationMs)
    }
}
