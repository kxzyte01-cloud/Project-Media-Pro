package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportPlannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: ProjectMediaStorage

    @Before
    fun setUp() {
        storage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    // Proves that clips are exported in timeline order with a summed duration and no trim when untrimmed.
    @Test
    fun plan_withValidClips_returnsOrderedExportClipsAndSummedDuration() {
        val rel1 = createClipFile("proj-1", "clip-1")
        val rel2 = createClipFile("proj-1", "clip-2")
        val c1 = Clip(id = "clip-1", mediaUri = rel1, startTimeMs = 0L, durationMs = 3_000L, trimEndMs = 3_000L)
        val c2 = Clip(id = "clip-2", mediaUri = rel2, startTimeMs = 3_000L, durationMs = 4_500L, trimEndMs = 4_500L)
        val project = Project(
            id = "proj-1",
            title = "Demo",
            durationMs = 7_500L,
            tracks = listOf(Track(id = "t-1", type = TrackType.VIDEO, clips = listOf(c1, c2))),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(7_500L, plan.totalDurationMs)
        assertEquals(2, plan.clips.size)
        assertEquals(storage.resolve(rel1)?.absolutePath, plan.clips[0].path)
        assertEquals(0L, plan.clips[0].trimEndMs)
        assertEquals(storage.resolve(rel2)?.absolutePath, plan.clips[1].path)
        assertEquals(0L, plan.clips[1].trimEndMs)
    }

    // Proves that a clip whose file is missing is skipped while the other clips are still exported.
    @Test
    fun plan_skipsMissingFiles_andUsesRemainingValidClips() {
        val existingRel = createClipFile("proj-1", "clip-2")
        val missing = Clip(id = "clip-1", mediaUri = "proj-1/missing.mp4", startTimeMs = 0L, durationMs = 2_000L)
        val valid = Clip(id = "clip-2", mediaUri = existingRel, startTimeMs = 2_000L, durationMs = 5_000L)
        val project = Project(
            id = "proj-1",
            title = "Demo",
            tracks = listOf(Track(id = "t-1", type = TrackType.VIDEO, clips = listOf(missing, valid))),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(1, plan.clips.size)
        assertEquals(5_000L, plan.totalDurationMs)
    }

    // Proves that exporting a project whose clip files are all missing is reported as an error.
    @Test(expected = IllegalStateException::class)
    fun plan_whenAllClipsMissing_throwsIllegalStateException() {
        val missing = Clip(id = "clip-1", mediaUri = "proj-1/missing.mp4", startTimeMs = 0L, durationMs = 2_000L)
        val project = Project(
            id = "proj-1",
            title = "Demo",
            tracks = listOf(Track(id = "t-1", type = TrackType.VIDEO, clips = listOf(missing))),
        )

        ExportPlanner.plan(project) { storage.resolve(it) }
    }

    // Proves that trim bounds are passed only for trimmed clips and an invalid trim range is ignored.
    @Test
    fun plan_passesTrimBoundsOnlyWhenTrimmed_andIgnoresInvalidTrimRange() {
        val relTrimmed = createClipFile("proj-1", "c-trim")
        val relInvalid = createClipFile("proj-1", "c-invalid")
        val trimmed = Clip(
            id = "c-trim",
            mediaUri = relTrimmed,
            startTimeMs = 0L,
            durationMs = 6_000L,
            trimStartMs = 1_000L,
            trimEndMs = 4_000L,
        )
        val invalidTrim = Clip(
            id = "c-invalid",
            mediaUri = relInvalid,
            startTimeMs = 3_000L,
            durationMs = 5_000L,
            trimStartMs = 3_000L,
            trimEndMs = 3_000L,
        )
        val project = Project(
            id = "proj-1",
            title = "Demo",
            tracks = listOf(Track(id = "t-1", type = TrackType.VIDEO, clips = listOf(trimmed, invalidTrim))),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(8_000L, plan.totalDurationMs)
        assertEquals(1_000L, plan.clips[0].trimStartMs)
        assertEquals(4_000L, plan.clips[0].trimEndMs)
        assertEquals(0L, plan.clips[1].trimStartMs)
        assertEquals(0L, plan.clips[1].trimEndMs)
    }

    // Proves that the space estimate has a 50 MB floor and grows with duration (about 20 Mbps plus margin).
    @Test
    fun estimateExportBytes_hasFloorAndScalesWithDuration() {
        val floor = 50L * 1024L * 1024L
        assertEquals(floor, ExportPlanner.estimateExportBytes(-1_000L))
        assertEquals(floor, ExportPlanner.estimateExportBytes(0L))
        assertEquals(floor, ExportPlanner.estimateExportBytes(5_000L))
        assertEquals(225_000_000L, ExportPlanner.estimateExportBytes(60_000L))
    }

    // Proves that big videos are scaled down to a 1920 long side, small ones are kept, and sizes are even.
    @Test
    fun fitExportSize_capsLongSideKeepsAspectAndMakesSizesEven() {
        assertEquals(Pair(1920, 1080), ExportPlanner.fitExportSize(1920, 1080))
        assertEquals(Pair(1920, 1080), ExportPlanner.fitExportSize(3840, 2160))
        assertEquals(Pair(1080, 1920), ExportPlanner.fitExportSize(1080, 1920))
        assertEquals(Pair(1080, 1920), ExportPlanner.fitExportSize(2160, 3840))
        assertEquals(Pair(1278, 718), ExportPlanner.fitExportSize(1279, 719))
        assertEquals(Pair(2, 2), ExportPlanner.fitExportSize(1, 1))
        assertNull(ExportPlanner.fitExportSize(0, 100))
        assertNull(ExportPlanner.fitExportSize(100, -5))
    }

    private fun createClipFile(projectId: String, clipId: String): String {
        val relativePath = storage.clipRelativePath(projectId, clipId, "mp4")
        val file = storage.resolve(relativePath)
        file?.parentFile?.mkdirs()
        file?.writeText("video-bytes")
        return relativePath
    }
}
