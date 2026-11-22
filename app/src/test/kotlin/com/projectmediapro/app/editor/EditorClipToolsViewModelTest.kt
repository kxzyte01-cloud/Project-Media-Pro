package com.projectmediapro.app.editor

import com.projectmediapro.app.data.media.ImportedVideo
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.videoClips
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorClipToolsViewModelTest {

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
    fun videoClipTools_speedVolumeTransformDuplicateAndUndoRedo() = runTest(testDispatcher) {
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
            title = "Clip Tools Project",
            durationMs = 8_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(videoClip))),
        )
        var nextId = 1
        val viewModel = EditorViewModel(
            projectId = "p1",
            repository = InMemoryRepo(initialProject),
            videoImporter = StubImporter,
            mediaStorage = storage,
            idProvider = { "dup-${nextId++}" },
        )
        advanceUntilIdle()

        viewModel.onEvent(EditorEvent.OpenSpeed)
        assertEquals(EditorDialog.Speed("v1"), (viewModel.uiState.value as EditorUiState.Loaded).activeDialog)
        viewModel.onEvent(EditorEvent.ApplySpeed(2.0f))
        advanceUntilIdle()
        val afterSpeed = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(2.0f, afterSpeed.project.videoClips().first().playbackSpeed, 0.0001f)
        assertEquals(4_000L, afterSpeed.project.videoClips().first().durationMs)
        assertEquals(2.0f, afterSpeed.previewClips.first().playbackSpeed, 0.0001f)

        viewModel.onEvent(EditorEvent.OpenVolume)
        assertEquals(EditorDialog.Volume("v1"), (viewModel.uiState.value as EditorUiState.Loaded).activeDialog)
        viewModel.onEvent(EditorEvent.ApplyVolume(0.0f))
        advanceUntilIdle()
        val afterMute = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(0.0f, afterMute.project.videoClips().first().volume, 0.0001f)
        assertEquals(0.0f, afterMute.previewClips.first().volume, 0.0001f)

        viewModel.onEvent(EditorEvent.OpenTransform)
        assertEquals(EditorDialog.Transform("v1"), (viewModel.uiState.value as EditorUiState.Loaded).activeDialog)
        viewModel.onEvent(EditorEvent.ApplyTransform(rotationDegrees = 90, flipHorizontal = true, flipVertical = false))
        advanceUntilIdle()
        val afterTransform = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(90, afterTransform.project.videoClips().first().rotationDegrees)
        assertTrue(afterTransform.project.videoClips().first().flipHorizontal)
        assertEquals(90, afterTransform.previewClips.first().rotationDegrees)
        assertTrue(afterTransform.previewClips.first().flipHorizontal)

        viewModel.onEvent(EditorEvent.DuplicateSelected)
        advanceUntilIdle()
        val afterDup = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(listOf("v1", "dup-1"), afterDup.project.videoClips().map { it.id })
        assertEquals("dup-1", afterDup.selectedClipId)
        assertEquals(8_000L, afterDup.project.durationMs)
        assertTrue(afterDup.canUndo)

        viewModel.onEvent(EditorEvent.Undo)
        advanceUntilIdle()
        val afterUndo = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(listOf("v1"), afterUndo.project.videoClips().map { it.id })
        assertTrue(afterUndo.canRedo)

        viewModel.onEvent(EditorEvent.Redo)
        advanceUntilIdle()
        val afterRedo = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(listOf("v1", "dup-1"), afterRedo.project.videoClips().map { it.id })
        assertFalse(afterRedo.canRedo)
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
