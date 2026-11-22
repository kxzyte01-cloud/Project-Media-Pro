package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipToolsBTest {

    private fun sampleProject(): Project {
        val v1 = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 8_000L,
            trimStartMs = 0L,
            trimEndMs = 8_000L,
            sourceDurationMs = 8_000L,
            rotationDegrees = 90,
            flipHorizontal = true,
        )
        val v2 = Clip(
            id = "v2",
            mediaUri = "p1/v2.mp4",
            startTimeMs = 8_000L,
            durationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            sourceDurationMs = 4_000L,
        )
        return Project(
            id = "p1",
            title = "Crop & Freeze",
            durationMs = 12_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(v1, v2))),
        )
    }

    @Test
    fun clipCrop_sanitizesBoundsEnforcesMinSpanAndNormalizesIdentity() {
        val inverted = ClipCrop(left = 0.9f, top = 0.95f, right = 0.2f, bottom = 0.1f).sanitized()
        assertTrue(inverted.widthFraction >= ClipCrop.MIN_SPAN - 0.0001f)
        assertTrue(inverted.heightFraction >= ClipCrop.MIN_SPAN - 0.0001f)

        val nanCrop = ClipCrop(left = Float.NaN, top = Float.POSITIVE_INFINITY, right = 1f, bottom = 1f).sanitized()
        assertTrue(nanCrop.isIdentity)

        val squareFrom16x9 = ClipCrop.centeredForAspect(targetAspect = 1f, sourceAspect = 16f / 9f)
        assertEquals(0f, squareFrom16x9.top, 0.0001f)
        assertEquals(1f, squareFrom16x9.bottom, 0.0001f)
        assertEquals(9f / 16f, squareFrom16x9.widthFraction, 0.001f)
        assertEquals(0.5f, squareFrom16x9.centerX, 0.001f)
    }

    @Test
    fun setClipCrop_updatesVideoClipAndNormalizesIdentityToNull() {
        val project = sampleProject()
        val cropped = ProjectTimeline.setClipCrop(
            project = project,
            clipId = "v1",
            crop = ClipCrop(left = 0.1f, top = 0.2f, right = 0.8f, bottom = 0.9f),
        )
        val clip = cropped.videoClips()[0]
        assertTrue(clip.hasActiveCrop)
        assertEquals(0.1f, clip.crop?.left ?: -1f, 0.0001f)
        assertEquals(0.9f, clip.crop?.bottom ?: -1f, 0.0001f)

        val cleared = ProjectTimeline.setClipCrop(cropped, "v1", ClipCrop())
        assertNull(cleared.videoClips()[0].crop)
        assertFalse(cleared.videoClips()[0].hasActiveCrop)
    }

    @Test
    fun insertFreezeFrameClip_insertsStillImageAfterSourceInheritingTransformsAndClampingDuration() {
        val project = ProjectTimeline.setClipCrop(
            project = sampleProject(),
            clipId = "v1",
            crop = ClipCrop(left = 0.2f, top = 0.0f, right = 0.8f, bottom = 1.0f),
        )

        val withFreeze = ProjectTimeline.insertFreezeFrameClip(
            project = project,
            sourceClipId = "v1",
            newClipId = "freeze-1",
            imageMediaUri = "p1/freeze-1.png",
            durationMs = 3_000L,
        )
        val clips = withFreeze.videoClips()
        assertEquals(listOf("v1", "freeze-1", "v2"), clips.map { it.id })
        val freeze = clips[1]
        assertTrue(freeze.isStillImage)
        assertEquals("p1/freeze-1.png", freeze.mediaUri)
        assertEquals(8_000L, freeze.startTimeMs)
        assertEquals(3_000L, freeze.durationMs)
        assertEquals(0f, freeze.volume, 0.0001f)
        assertEquals(90, freeze.rotationDegrees)
        assertTrue(freeze.flipHorizontal)
        assertEquals(0.2f, freeze.crop?.left ?: -1f, 0.0001f)
        assertFalse(ProjectTimeline.canTrimClip(freeze))
        assertFalse(ProjectTimeline.canSplitClip(freeze))
        assertEquals(11_000L, clips[2].startTimeMs)
        assertEquals(15_000L, withFreeze.durationMs)
    }

    @Test
    fun projectSerializer_roundTripsCropAndStillImageWithSchemaVersion1() {
        val cropped = ProjectTimeline.setClipCrop(
            project = sampleProject(),
            clipId = "v1",
            crop = ClipCrop(left = 0.15f, top = 0.1f, right = 0.85f, bottom = 0.9f),
        )
        val withFreeze = ProjectTimeline.insertFreezeFrameClip(
            project = cropped,
            sourceClipId = "v1",
            newClipId = "freeze-1",
            imageMediaUri = "p1/freeze-1.png",
            durationMs = 2_000L,
        )
        val restored = ProjectSerializer.decode(ProjectSerializer.encode(withFreeze))
        assertEquals(Project.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(0.15f, restored.videoClips()[0].crop?.left ?: -1f, 0.0001f)
        assertTrue(restored.videoClips()[1].isStillImage)
    }
}
