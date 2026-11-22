package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.KeyframePresets
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportKeyframePlanTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: ProjectMediaStorage

    @Before
    fun setUp() {
        storage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    @Test
    fun plan_propagatesSanitizedKeyframesToOverlaysAndTextOverlays() {
        val relVideo = createMedia("proj-kf", "v1", "mp4")
        val relOverlay = createMedia("proj-kf", "o1", "png")
        val overlayKeyframes = KeyframePresets.zoomIn(0.5f, 0.5f)
        val textKeyframes = KeyframePresets.slideUp(0.5f, 0.8f)

        val project = Project(
            id = "proj-kf",
            title = "Keyframe Export",
            durationMs = 6_000L,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "v1",
                            mediaUri = relVideo,
                            startTimeMs = 0L,
                            durationMs = 6_000L,
                            trimEndMs = 6_000L,
                            sourceDurationMs = 6_000L,
                        ),
                    ),
                ),
                Track(
                    id = "t-o",
                    type = TrackType.OVERLAY,
                    clips = listOf(
                        Clip(
                            id = "o1",
                            mediaUri = relOverlay,
                            startTimeMs = 0L,
                            durationMs = 4_000L,
                            keyframes = overlayKeyframes,
                        ),
                    ),
                ),
                Track(
                    id = "t-t",
                    type = TrackType.TEXT,
                    clips = listOf(
                        Clip(
                            id = "txt1",
                            mediaUri = "",
                            startTimeMs = 4_000L,
                            durationMs = 4_000L,
                            textContent = "Clipped Title",
                            keyframes = textKeyframes,
                        ),
                    ),
                ),
            ),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }
        assertEquals(1, plan.overlays.size)
        assertEquals(3, plan.overlays[0].keyframes.size)
        assertEquals(4_000L, plan.overlays[0].animationDurationMs)
        assertEquals(1, plan.textOverlays.size)
        assertEquals(3, plan.textOverlays[0].keyframes.size)
        assertEquals(4_000L, plan.textOverlays[0].startTimeMs)
        assertEquals(6_000L, plan.textOverlays[0].endTimeMs)
        assertEquals(4_000L, plan.textOverlays[0].animationDurationMs)
    }

    private fun createMedia(projectId: String, clipId: String, ext: String): String {
        val rel = storage.clipRelativePath(projectId, clipId, ext)
        val file = requireNotNull(storage.resolve(rel))
        file.parentFile?.mkdirs()
        file.writeText("media-bytes")
        return rel
    }
}
