package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipToolsATest {

    private fun twoVideoProject(): Project {
        val v1 = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 8_000L,
            trimStartMs = 0L,
            trimEndMs = 8_000L,
            sourceDurationMs = 8_000L,
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
            title = "Speed & Transform",
            durationMs = 12_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(v1, v2))),
        )
    }

    @Test
    fun setClipSpeed_scalesDurationRepositionsFollowingClipsAndClampsBounds() {
        val project = twoVideoProject()

        val doubled = ProjectTimeline.setClipSpeed(project, "v1", 2.0f)
        val v1Fast = doubled.videoClips()[0]
        val v2AfterFast = doubled.videoClips()[1]
        assertEquals(2.0f, v1Fast.playbackSpeed, 0.0001f)
        assertEquals(4_000L, v1Fast.durationMs)
        assertEquals(4_000L, v2AfterFast.startTimeMs)
        assertEquals(8_000L, doubled.durationMs)

        val slowed = ProjectTimeline.setClipSpeed(doubled, "v1", 0.5f)
        assertEquals(0.5f, slowed.videoClips()[0].playbackSpeed, 0.0001f)
        assertEquals(16_000L, slowed.videoClips()[0].durationMs)
        assertEquals(16_000L, slowed.videoClips()[1].startTimeMs)
        assertEquals(20_000L, slowed.durationMs)

        val clampedLow = ProjectTimeline.setClipSpeed(project, "v1", 0.05f)
        assertEquals(Clip.MIN_PLAYBACK_SPEED, clampedLow.videoClips()[0].playbackSpeed, 0.0001f)

        val clampedHigh = ProjectTimeline.setClipSpeed(project, "v1", 10.0f)
        assertEquals(Clip.MAX_PLAYBACK_SPEED, clampedHigh.videoClips()[0].playbackSpeed, 0.0001f)

        val nanReset = ProjectTimeline.setClipSpeed(doubled, "v1", Float.NaN)
        assertEquals(Clip.DEFAULT_PLAYBACK_SPEED, nanReset.videoClips()[0].playbackSpeed, 0.0001f)
        assertEquals(8_000L, nanReset.videoClips()[0].durationMs)
    }

    @Test
    fun setClipSpeed_clampsExistingFadeDurationsToNewTimelineDuration() {
        val base = twoVideoProject()
        val withFade = ProjectTimeline.updateClipVisualEffects(
            project = base,
            clipId = "v1",
            effects = ClipVisualEffects(
                transitionIn = ClipTransition.FADE_BLACK,
                fadeInMs = 3_000L,
            ),
        )

        val fast = ProjectTimeline.setClipSpeed(withFade, "v1", 4.0f)
        assertEquals(2_000L, fast.videoClips()[0].durationMs)
        assertEquals(1_000L, fast.videoClips()[0].visualEffects?.fadeInMs)
    }

    @Test
    fun setClipTransform_normalizesRotationAndUpdatesFlipFlags() {
        val project = twoVideoProject()

        val transformed = ProjectTimeline.setClipTransform(
            project = project,
            clipId = "v1",
            rotationDegrees = -90,
            flipHorizontal = true,
            flipVertical = false,
        )
        val clip = transformed.videoClips()[0]
        assertEquals(270, clip.rotationDegrees)
        assertTrue(clip.flipHorizontal)
        assertFalse(clip.flipVertical)
        assertTrue(clip.hasActiveTransform)

        val wrapped = ProjectTimeline.setClipTransform(
            project = transformed,
            clipId = "v1",
            rotationDegrees = 450,
            flipHorizontal = false,
            flipVertical = true,
        )
        assertEquals(90, wrapped.videoClips()[0].rotationDegrees)
        assertFalse(wrapped.videoClips()[0].flipHorizontal)
        assertTrue(wrapped.videoClips()[0].flipVertical)

        val reset = ProjectTimeline.setClipTransform(
            project = wrapped,
            clipId = "v1",
            rotationDegrees = 360,
            flipHorizontal = false,
            flipVertical = false,
        )
        assertEquals(0, reset.videoClips()[0].rotationDegrees)
        assertFalse(reset.videoClips()[0].hasActiveTransform)
    }

    @Test
    fun duplicateClip_insertsCopyAfterOriginalAndRejectsInvalidIds() {
        val project = twoVideoProject()

        val duplicated = ProjectTimeline.duplicateClip(project, "v1", "v1-copy")
        val clips = duplicated.videoClips()
        assertEquals(listOf("v1", "v1-copy", "v2"), clips.map { it.id })
        assertEquals("p1/v1.mp4", clips[1].mediaUri)
        assertEquals(0L, clips[0].startTimeMs)
        assertEquals(8_000L, clips[1].startTimeMs)
        assertEquals(16_000L, clips[2].startTimeMs)
        assertEquals(20_000L, duplicated.durationMs)

        assertEquals(duplicated, ProjectTimeline.duplicateClip(duplicated, "v1", ""))
        assertEquals(duplicated, ProjectTimeline.duplicateClip(duplicated, "v1", "v1"))
        assertEquals(duplicated, ProjectTimeline.duplicateClip(duplicated, "v1", "v2"))
    }

    @Test
    fun projectSerializer_preservesSchemaVersion1WithLegacyAndTransformFields() {
        val project = ProjectTimeline.setClipTransform(
            project = ProjectTimeline.setClipSpeed(twoVideoProject(), "v1", 1.5f),
            clipId = "v1",
            rotationDegrees = 90,
            flipHorizontal = true,
            flipVertical = false,
        )
        val encoded = ProjectSerializer.serialize(project)
        val decoded = ProjectSerializer.deserialize(encoded)
        val restored = decoded.videoClips()[0]

        assertEquals(Project.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1.5f, restored.playbackSpeed, 0.0001f)
        assertEquals(90, restored.rotationDegrees)
        assertTrue(restored.flipHorizontal)
        assertFalse(restored.flipVertical)

        val legacyJson = """{"schemaVersion":1,"id":"p-old","title":"Legacy","tracks":[{"id":"t1","type":"VIDEO","clips":[{"id":"c1","mediaUri":"p-old/c1.mp4","startTimeMs":0,"durationMs":3000}]}]}"""
        val legacyProject = ProjectSerializer.deserialize(legacyJson)
        val legacyClip = legacyProject.videoClips().first()
        assertEquals(1.0f, legacyClip.playbackSpeed, 0.0001f)
        assertEquals(1.0f, legacyClip.volume, 0.0001f)
        assertEquals(0, legacyClip.rotationDegrees)
        assertFalse(legacyClip.flipHorizontal)
        assertFalse(legacyClip.flipVertical)
    }
}
