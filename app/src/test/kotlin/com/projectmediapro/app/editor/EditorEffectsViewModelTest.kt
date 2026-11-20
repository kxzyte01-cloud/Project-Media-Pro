package com.projectmediapro.app.editor

import com.projectmediapro.app.data.media.ImportedVideo
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipTransition
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.VideoFilterPreset
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorEffectsViewModelTest {

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
    fun clipEffects_applyUndoRedoAndReset() = runTest(testDispatcher) {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media"))
        val videoClip = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 8_000L,
            trimEndMs = 8_000L,
            sourceDurationMs = 8_000L,
        )
        val initialProject = Project(
            id = "p1",
            title = "Effects Project",
            durationMs = 8_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(videoClip))),
        )
        val viewModel = EditorViewModel(
            projectId = "p1",
            repository = InMemoryRepo(initialProject),
            videoImporter = StubImporter,
            mediaStorage = storage,
        )
        advanceUntilIdle()

        viewModel.onEvent(EditorEvent.OpenClipEffects)
        val dialogState = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(EditorDialog.ClipEffects("v1"), dialogState.activeDialog)

        val graded = ClipVisualEffects(
            filter = VideoFilterPreset.CINEMA,
            brightness = 0.1f,
            contrast = 0.15f,
            transitionIn = ClipTransition.FADE_BLACK,
            fadeInMs = 800L,
        )
        viewModel.onEvent(EditorEvent.ApplyClipEffects(graded))
        advanceUntilIdle()

        val afterApply = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(VideoFilterPreset.CINEMA, afterApply.project.videoClips().first().visualEffects?.filter)
        assertTrue(afterApply.canUndo)

        viewModel.onEvent(EditorEvent.Undo)
        advanceUntilIdle()
        val afterUndo = viewModel.uiState.value as EditorUiState.Loaded
        assertNull(afterUndo.project.videoClips().first().visualEffects)

        viewModel.onEvent(EditorEvent.Redo)
        advanceUntilIdle()
        val afterRedo = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(VideoFilterPreset.CINEMA, afterRedo.project.videoClips().first().visualEffects?.filter)

        viewModel.onEvent(EditorEvent.ApplyClipEffects(ClipVisualEffects()))
        advanceUntilIdle()
        val afterReset = viewModel.uiState.value as EditorUiState.Loaded
        assertNull(afterReset.project.videoClips().first().visualEffects)
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
