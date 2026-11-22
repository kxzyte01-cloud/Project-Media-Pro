package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
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

class ExportFastPathTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: ProjectMediaStorage

    @Before
    fun setUp() {
        storage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    @Test
    fun canTransmuxVideo_returnsTrueForUnmodifiedOriginalClipWithinCapAndFalseWhenEffectsOrFpsSet() {
        val relVideo = createMedia("proj-fast", "v1", "mp4")
        val baseProject = Project(
            id = "proj-fast",
            title = "Fast Export",
            durationMs = 5_000L,
            canvasAspect = CanvasAspect.ORIGINAL,
            exportQuality = ExportQuality.FULL_HD_1080P,
            exportFrameRate = ExportFrameRate.AUTO,
            exportBitrate = ExportBitrate.STANDARD,
            tracks = listOf(
                Track(
                    id = "t-v",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "v1",
                            mediaUri = relVideo,
                            startTimeMs = 0L,
                            durationMs = 5_000L,
                            trimEndMs = 5_000L,
                            sourceDurationMs = 5_000L,
                        ),
                    ),
                ),
            ),
        )

        val fastPlan = ExportPlanner.plan(baseProject) { storage.resolve(it) }
        assertTrue(ExportPlanner.canTransmuxVideo(fastPlan, sourceLongSidePx = 1920))
        assertFalse(ExportPlanner.canTransmuxVideo(fastPlan, sourceLongSidePx = 3840))

        val fps60Plan = ExportPlanner.plan(baseProject.copy(exportFrameRate = ExportFrameRate.FPS_60)) {
            storage.resolve(it)
        }
        assertFalse(ExportPlanner.canTransmuxVideo(fps60Plan, sourceLongSidePx = 1920))

        val rotatedPlan = ExportPlanner.plan(
            baseProject.copy(
                tracks = listOf(
                    Track(
                        id = "t-v",
                        type = TrackType.VIDEO,
                        clips = listOf(baseProject.tracks[0].clips[0].copy(rotationDegrees = 90)),
                    ),
                ),
            ),
        ) { storage.resolve(it) }
        assertFalse(ExportPlanner.canTransmuxVideo(rotatedPlan, sourceLongSidePx = 1920))
    }

    @Test
    fun exportGeometry_supports2kAndScalesBitrateByQualityAndFps() {
        assertEquals(Pair(2560, 1440), ExportGeometry.sizeFor(CanvasAspect.RATIO_16_9, ExportQuality.QHD_1440P, 0, 0))
        val fast720 = ExportGeometry.targetBitrateBps(ExportQuality.HD_720P, ExportFrameRate.FPS_30, ExportBitrate.FAST_SMALL)
        val high1080 = ExportGeometry.targetBitrateBps(ExportQuality.FULL_HD_1080P, ExportFrameRate.FPS_60, ExportBitrate.HIGH)
        assertTrue(high1080 > fast720)
    }

    private fun createMedia(projectId: String, clipId: String, ext: String): String {
        val rel = storage.clipRelativePath(projectId, clipId, ext)
        val file = requireNotNull(storage.resolve(rel))
        file.parentFile?.mkdirs()
        file.writeText("media-bytes")
        return rel
    }
}
