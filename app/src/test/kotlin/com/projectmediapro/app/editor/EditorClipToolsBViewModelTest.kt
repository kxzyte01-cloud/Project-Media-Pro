package com.projectmediapro.app.editor

import com.projectmediapro.app.data.media.FreezeFrameExtractor
import com.projectmediapro.app.data.media.ImportedVideo
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipCrop
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.videoClips
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorClipToolsBViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cropAndFreezeFrame_applyUndoRedoAndSurfaceExtractorError() = runTest(testDispatcher) {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media"))
        val rel = storage.clipRelativePath("p1", "v1", "mp4")
        val file = requireNotNull(storage.resolve(rel))
        file.parentFile?.mkdirs()
        file.writeText("video-bytes")

        val videoClip = Clip(
            id = "v1",
            mediaUri = rel,
            startTimeMs = 0L,
            durationMs = 8_000L,
            trimStartMs = 0L,
            trimEndMs = 8_000L,
            sourceDurationMs = 8_000L,
        )
        val initialProject = Project(
            id = "p1",
            title = "Crop & Freeze VM",
            durationMs = 8_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(videoClip))),
        )
        var shouldFailFreeze = false
        var lastExtractedTimeUs = -1L
        val fakeExtractor = object : FreezeFrameExtractor {
            override suspend fun extractFrame(videoFile: File, timeUs: Long, outputFile: File) {
                if (shouldFailFreeze) throw IOException("decode error")
                lastExtractedTimeUs = timeUs
                outputFile.parentFile?.mkdirs()
                outputFile.writeText("png-bytes")
            }
        }
        var nextId = 1
        val viewModel = EditorViewModel(
            projectId = "p1",
            repository = InMemoryRepo(initialProject),
            videoImporter = StubImporter,
            mediaStorage = storage,
            idProvider = { "freeze-${nextId++}" },
            freezeFrameExtractor = fakeExtractor,
        )
        advanceUntilIdle()

        viewModel.onEvent(EditorEvent.OpenCrop)
        assertEquals(EditorDialog.Crop("v1"), (viewModel.uiState.value as EditorUiState.Loaded).activeDialog)
        val crop = ClipCrop(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.9f)
        viewModel.onEvent(EditorEvent.ApplyCrop(crop))
        advanceUntilIdle()
        val afterCrop = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(crop, afterCrop.project.videoClips().first().crop)
        assertEquals(crop, afterCrop.previewClips.first().crop)

        viewModel.onEvent(EditorEvent.OpenFreezeFrame)
        assertEquals(EditorDialog.FreezeFrame("v1"), (viewModel.uiState.value as EditorUiState.Loaded).activeDialog)
        viewModel.onEvent(EditorEvent.ApplyFreezeFrame(sourceTimeMs = 3_500L, durationMs = 2_500L))
        advanceUntilIdle()
        assertEquals(3_500_000L, lastExtractedTimeUs)
        val afterFreeze = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(listOf("v1", "freeze-1"), afterFreeze.project.videoClips().map { it.id })
        assertEquals("freeze-1", afterFreeze.selectedClipId)
        assertTrue(afterFreeze.project.videoClips()[1].isStillImage)
        assertTrue(afterFreeze.previewClips[1].isStillImage)
        assertEquals(10_500L, afterFreeze.project.durationMs)

        viewModel.onEvent(EditorEvent.OpenFreezeFrame)
        assertNull((viewModel.uiState.value as EditorUiState.Loaded).activeDialog)

        viewModel.onEvent(EditorEvent.Undo)
        advanceUntilIdle()
        assertEquals(listOf("v1"), (viewModel.uiState.value as EditorUiState.Loaded).project.videoClips().map { it.id })

        shouldFailFreeze = true
        viewModel.onEvent(EditorEvent.ApplyFreezeFrame(sourceTimeMs = 1_000L, durationMs = 2_000L))
        advanceUntilIdle()
        val afterError = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(EditorError.FREEZE_FRAME_FAILED, afterError.error)
    }

    private class InMemoryRepo(initial: Project) : ProjectRepository {
        private val projects = mutableMapOf(initial.id to initial)

        override suspend fun listProjects(): List<Project> = projects.values.toList()
        override suspend fun getProject(id: String): Project? = projects[id]
        override suspend fun createProject(title: String): Project {
            val created = Project(id = "new", title = title)
            projects[created.id] = created
            return created
        }

        override suspend fun saveProject(project: Project) {
            projects[project.id] = project
        }

        override suspend fun deleteProject(id: String) {
            projects.remove(id)
        }
    }

    private object StubImporter : VideoImporter {
        override suspend fun importVideo(projectId: String, sourceUri: String): ImportedVideo {
            return ImportedVideo(relativePath = "$projectId/stub.mp4", durationMs = 5_000L)
        }
    }
}
