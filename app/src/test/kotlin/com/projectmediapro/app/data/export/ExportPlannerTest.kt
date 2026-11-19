package com.projectmediapro.app.data.export

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun plan_withValidClips_returnsOrderedExportClipsAndSummedDuration() {
        val rel1 = createClipFile("proj-1", "clip-1")
        val rel2 = createClipFile("proj-1", "clip-2")
        val c1 = Clip(
            id = "clip-1",
            mediaUri = rel1,
            startTimeMs = 0L,
            durationMs = 3_000L,
            trimEndMs = 3_000L,
            sourceDurationMs = 3_000L,
        )
        val c2 = Clip(
            id = "clip-2",
            mediaUri = rel2,
            startTimeMs = 3_000L,
            durationMs = 4_500L,
            trimEndMs = 4_500L,
            sourceDurationMs = 4_500L,
        )
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
        assertTrue(plan.audioClips.isEmpty())
    }

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

    @Test
    fun plan_passesTrimBoundsForEndOnlyTrimAndStartTrim_andIgnoresInvalidTrimRange() {
        val relEndTrimmed = createClipFile("proj-1", "c-end-trim")
        val relStartAndEnd = createClipFile("proj-1", "c-both-trim")
        val relInvalid = createClipFile("proj-1", "c-invalid")
        val initialEndTrimClip = Clip(
            id = "c-end-trim",
            mediaUri = relEndTrimmed,
            startTimeMs = 0L,
            durationMs = 6_000L,
            trimStartMs = 0L,
            trimEndMs = 6_000L,
            sourceDurationMs = 6_000L,
        )
        val bothTrimmed = Clip(
            id = "c-both-trim",
            mediaUri = relStartAndEnd,
            startTimeMs = 6_000L,
            durationMs = 3_000L,
            trimStartMs = 1_000L,
            trimEndMs = 4_000L,
            sourceDurationMs = 6_000L,
        )
        val invalidTrim = Clip(
            id = "c-invalid",
            mediaUri = relInvalid,
            startTimeMs = 9_000L,
            durationMs = 5_000L,
            trimStartMs = 3_000L,
            trimEndMs = 3_000L,
            sourceDurationMs = 5_000L,
        )
        val initialProject = Project(
            id = "proj-1",
            title = "Demo",
            tracks = listOf(
                Track(
                    id = "t-1",
                    type = TrackType.VIDEO,
                    clips = listOf(initialEndTrimClip, bothTrimmed, invalidTrim),
                ),
            ),
        )
        val endTrimmedProject = ProjectTimeline.trimClip(initialProject, "c-end-trim", 0L, 4_000L)

        val plan = ExportPlanner.plan(endTrimmedProject) { storage.resolve(it) }

        assertEquals(12_000L, plan.totalDurationMs)
        assertEquals(0L, plan.clips[0].trimStartMs)
        assertEquals(4_000L, plan.clips[0].trimEndMs)
        assertEquals(1_000L, plan.clips[1].trimStartMs)
        assertEquals(4_000L, plan.clips[1].trimEndMs)
        assertEquals(0L, plan.clips[2].trimStartMs)
        assertEquals(0L, plan.clips[2].trimEndMs)
    }

    @Test
    fun plan_withAudioClips_cutsAtTotalVideoLengthDropsClipsAfterEndSkipsMissingAndAppliesVolume() {
        val vRel = createClipFile("proj-1", "v1")
        val aRel1 = createClipFile("proj-1", "a1", "mp3")
        val aRel2 = createClipFile("proj-1", "a2", "m4a")
        val aRel3 = createClipFile("proj-1", "a3", "wav")

        val v1 = Clip(
            id = "v1",
            mediaUri = vRel,
            startTimeMs = 0L,
            durationMs = 10_000L,
            trimStartMs = 0L,
            trimEndMs = 10_000L,
            sourceDurationMs = 10_000L,
        )
        val a1 = Clip(
            id = "a1",
            mediaUri = aRel1,
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimStartMs = 0L,
            trimEndMs = 4_000L,
            sourceDurationMs = 4_000L,
            volume = 0.75f,
        )
        val missingAudio = Clip(
            id = "a-missing",
            mediaUri = "proj-1/missing.mp3",
            startTimeMs = 4_000L,
            durationMs = 2_000L,
        )
        val a2CrossingEnd = Clip(
            id = "a2",
            mediaUri = aRel2,
            startTimeMs = 6_000L,
            durationMs = 8_000L,
            trimStartMs = 1_000L,
            trimEndMs = 9_000L,
            sourceDurationMs = 10_000L,
            volume = 0.5f,
        )
        val a3AfterEnd = Clip(
            id = "a3",
            mediaUri = aRel3,
            startTimeMs = 14_000L,
            durationMs = 3_000L,
            trimStartMs = 0L,
            trimEndMs = 3_000L,
            sourceDurationMs = 3_000L,
            volume = 0.9f,
        )
        val project = Project(
            id = "proj-1",
            title = "Audio Mix",
            durationMs = 17_000L,
            tracks = listOf(
                Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(v1)),
                Track(id = "t-a", type = TrackType.AUDIO, clips = listOf(a1, missingAudio, a2CrossingEnd, a3AfterEnd)),
            ),
        )

        val plan = ExportPlanner.plan(project) { storage.resolve(it) }

        assertEquals(10_000L, plan.totalDurationMs)
        assertEquals(2, plan.audioClips.size)
        assertEquals(storage.resolve(aRel1)?.absolutePath, plan.audioClips[0].path)
        assertEquals(0L, plan.audioClips[0].trimStartMs)
        assertEquals(0L, plan.audioClips[0].trimEndMs)
        assertEquals(0.75f, plan.audioClips[0].volume, 0.0001f)

        assertEquals(storage.resolve(aRel2)?.absolutePath, plan.audioClips[1].path)
        assertEquals(1_000L, plan.audioClips[1].trimStartMs)
        assertEquals(7_000L, plan.audioClips[1].trimEndMs)
        assertEquals(0.5f, plan.audioClips[1].volume, 0.0001f)
    }

    @Test
    fun estimateExportBytes_hasFloorAndScalesWithDuration() {
        val floor = 50L * 1024L * 1024L
        assertEquals(floor, ExportPlanner.estimateExportBytes(-1_000L))
        assertEquals(floor, ExportPlanner.estimateExportBytes(0L))
        assertEquals(floor, ExportPlanner.estimateExportBytes(5_000L))
        assertEquals(225_000_000L, ExportPlanner.estimateExportBytes(60_000L))
    }

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

    private fun createClipFile(projectId: String, clipId: String, ext: String = "mp4"): String {
        val relativePath = storage.clipRelativePath(projectId, clipId, ext)
        val file = storage.resolve(relativePath)
        file?.parentFile?.mkdirs()
        file?.writeText("media-bytes")
        return relativePath
    }
}
