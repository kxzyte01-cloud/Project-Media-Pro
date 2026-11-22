package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipCrop
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportClipToolsBTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: ProjectMediaStorage

    @Before
    fun setUp() {
        storage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    @Test
    fun plan_propagatesCropAndStillImageFlagAndNormalizesIdentityCropToNull() {
        val relVideo = createMedia("proj-1", "v1", "mp4")
        val relFreeze = createMedia("proj-1", "f1", "png")
        val project = Project(
            id = "proj-1",
            title = "Crop & Freeze Export",
            durationMs = 7_000L,
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
                            trimEndMs = 4_000L,
                            sourceDurationMs = 4_000L,
                            crop = ClipCrop(left = 0.1f, top = 0.2f, right = 0.9f, bottom = 0.8f),
                        ),
                        Clip(
                            id = "f1",
                            mediaUri = relFreeze,
                            startTimeMs = 4_000L,
                            durationMs = 3_000L,
                            trimStartMs = 0L,
                            trimEndMs = 3_000L,
                            sourceDurationMs = 3_000L,
                            crop = ClipCrop(),
                            isStillImage = true,
                        ),
                    ),
                ),
            ),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(7_000L, plan.totalDurationMs)
        assertEquals(2, plan.clips.size)
        assertEquals(0.1f, plan.clips[0].crop?.left ?: -1f, 0.0001f)
        assertFalse(plan.clips[0].isStillImage)
        assertNull(plan.clips[1].crop)
        assertTrue(plan.clips[1].isStillImage)
        assertEquals(3_000L, plan.clips[1].durationMs)
    }

    private fun createMedia(projectId: String, clipId: String, ext: String): String {
        val rel = storage.clipRelativePath(projectId, clipId, ext)
        val file = requireNotNull(storage.resolve(rel))
        file.parentFile?.mkdirs()
        file.writeText("media-bytes")
        return rel
    }
}
