package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportClipToolsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: ProjectMediaStorage

    @Before
    fun setUp() {
        storage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    @Test
    fun plan_propagatesVideoSpeedVolumeAndTransformAndScalesTotalDuration() {
        val relVideo = createMedia("proj-1", "v1", "mp4")
        val relAudio = createMedia("proj-1", "a1", "mp3")
        val project = Project(
            id = "proj-1",
            title = "Clip Tools Export",
            durationMs = 4_000L,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "v1",
                            mediaUri = relVideo,
                            startTimeMs = 0L,
                            durationMs = 4_000L,
                            trimStartMs = 0L,
                            trimEndMs = 8_000L,
                            sourceDurationMs = 8_000L,
                            playbackSpeed = 2.0f,
                            volume = 0.0f,
                            rotationDegrees = 90,
                            flipHorizontal = true,
                            flipVertical = false,
                        ),
                    ),
                ),
                Track(
                    id = "t-a",
                    type = TrackType.AUDIO,
                    clips = listOf(
                        Clip(
                            id = "a1",
                            mediaUri = relAudio,
                            startTimeMs = 0L,
                            durationMs = 5_000L,
                            trimStartMs = 0L,
                            trimEndMs = 10_000L,
                            sourceDurationMs = 10_000L,
                            playbackSpeed = 2.0f,
                            volume = 0.6f,
                        ),
                    ),
                ),
            ),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(4_000L, plan.totalDurationMs)
        assertEquals(1, plan.clips.size)
        val exportClip = plan.clips[0]
        assertEquals(4_000L, exportClip.durationMs)
        assertEquals(2.0f, exportClip.playbackSpeed, 0.0001f)
        assertEquals(0.0f, exportClip.volume, 0.0001f)
        assertEquals(90, exportClip.rotationDegrees)
        assertTrue(exportClip.flipHorizontal)
        assertFalse(exportClip.flipVertical)

        assertEquals(1, plan.audioClips.size)
        val exportAudio = plan.audioClips[0]
        assertEquals(2.0f, exportAudio.playbackSpeed, 0.0001f)
        assertEquals(0.6f, exportAudio.volume, 0.0001f)
        // 4_000 ms of 2.0x timeline consumes 8_000 ms of source audio.
        assertEquals(0L, exportAudio.trimStartMs)
        assertEquals(8_000L, exportAudio.trimEndMs)
    }

    private fun createMedia(projectId: String, clipId: String, ext: String): String {
        val rel = storage.clipRelativePath(projectId, clipId, ext)
        val file = requireNotNull(storage.resolve(rel))
        file.parentFile?.mkdirs()
        file.writeText("media-bytes")
        return rel
    }
}
