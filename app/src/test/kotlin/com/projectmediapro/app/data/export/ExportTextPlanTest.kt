package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.TextStylePreset
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportTextPlanTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: ProjectMediaStorage

    @Before
    fun setUp() {
        storage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    @Test
    fun plan_clampsAndDropsTextOverlaysAgainstVideoLengthAndSkipsBlankText() {
        val videoRel = storage.clipRelativePath("proj-1", "v1", "mp4")
        val videoFile = storage.resolve(videoRel)!!
        videoFile.parentFile?.mkdirs()
        videoFile.writeText("video-bytes")

        val project = Project(
            id = "proj-1",
            title = "Text Export",
            durationMs = 5_000L,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "v1",
                            mediaUri = videoRel,
                            startTimeMs = 0L,
                            durationMs = 5_000L,
                            trimEndMs = 5_000L,
                            sourceDurationMs = 5_000L,
                        ),
                    ),
                ),
                Track(
                    id = "t-text",
                    type = TrackType.TEXT,
                    clips = listOf(
                        Clip(
                            id = "txt-clamped",
                            mediaUri = "",
                            startTimeMs = 3_000L,
                            durationMs = 4_000L,
                            overlayCenterX = 0.5f,
                            overlayCenterY = 0.8f,
                            overlayWidthFraction = 0.8f,
                            textContent = "  Hook lyric  ",
                            textStyle = TextOverlayStyle.preset(TextStylePreset.BOLD_OUTLINE),
                        ),
                        Clip(
                            id = "txt-after-end",
                            mediaUri = "",
                            startTimeMs = 5_000L,
                            durationMs = 2_000L,
                            textContent = "Out of bounds",
                        ),
                        Clip(
                            id = "txt-blank",
                            mediaUri = "",
                            startTimeMs = 0L,
                            durationMs = 2_000L,
                            textContent = "   ",
                        ),
                    ),
                ),
            ),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(1, plan.textOverlays.size)
        val planned = plan.textOverlays.first()
        assertEquals("Hook lyric", planned.text)
        assertEquals(3_000L, planned.startTimeMs)
        assertEquals(5_000L, planned.endTimeMs)
        assertEquals(0.5f, planned.centerX, 0.0001f)
        assertEquals(0.8f, planned.centerY, 0.0001f)
        assertEquals(0.8f, planned.widthFraction, 0.0001f)
        assertEquals(TextOverlayStyle.preset(TextStylePreset.BOLD_OUTLINE), planned.style)
        assertEquals(3, project.tracks[1].clips.size)
    }
}
