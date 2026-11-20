package com.projectmediapro.app.editor

import com.projectmediapro.app.data.media.ImportedVideo
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.TextStylePreset
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.textClips
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorTextViewModelTest {

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
    fun textLifecycle_addEditPositionTimingUndoAndRemove() = runTest(testDispatcher) {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media"))
        val videoClip = Clip(
            id = "v1",
            mediaUri = "p1/v1.mp4",
            startTimeMs = 0L,
            durationMs = 10_000L,
            trimEndMs = 10_000L,
            sourceDurationMs = 10_000L,
        )
        val initialProject = Project(
            id = "p1",
            title = "Lyric Project",
            durationMs = 10_000L,
            tracks = listOf(Track(id = "t-v", type = TrackType.VIDEO, clips = listOf(videoClip))),
        )
        val repository = InMemoryProjectRepository(initialProject)
        var idCounter = 0
        val viewModel = EditorViewModel(
            projectId = "p1",
            repository = repository,
            videoImporter = StubVideoImporter,
            mediaStorage = storage,
            idProvider = { "gen-${++idCounter}" },
        )
        advanceUntilIdle()

        viewModel.onEvent(EditorEvent.OpenAddText)
        val afterOpenAdd = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(EditorDialog.TextEdit(clipId = null), afterOpenAdd.activeDialog)

        val karaokeStyle = TextOverlayStyle.preset(TextStylePreset.KARAOKE_PILL)
        viewModel.onEvent(EditorEvent.ApplyText(text = "First line", style = karaokeStyle))
        advanceUntilIdle()

        val afterAdd = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(1, afterAdd.project.textClips().size)
        val textClip = afterAdd.project.textClips().first()
        assertEquals("First line", textClip.textContent)
        assertEquals(textClip.id, afterAdd.selectedClipId)
        assertTrue(afterAdd.canUndo)

        viewModel.onEvent(EditorEvent.OpenEditText)
        viewModel.onEvent(
            EditorEvent.ApplyText(
                text = "Updated chorus",
                style = TextOverlayStyle.preset(TextStylePreset.NEON_GLOW),
            ),
        )
        advanceUntilIdle()

        val afterEdit = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals("Updated chorus", afterEdit.project.textClips().first().textContent)

        viewModel.onEvent(EditorEvent.OpenTextPosition)
        viewModel.onEvent(EditorEvent.ApplyTextPosition(centerX = 0.5f, centerY = 0.25f, widthFraction = 0.7f))
        advanceUntilIdle()

        val afterPos = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(0.25f, afterPos.project.textClips().first().overlayCenterY, 0.0001f)

        viewModel.onEvent(EditorEvent.OpenTextTiming)
        viewModel.onEvent(EditorEvent.ApplyTextTiming(startTimeMs = 2_000L, durationMs = 3_000L))
        advanceUntilIdle()

        val afterTiming = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(2_000L, afterTiming.project.textClips().first().startTimeMs)
        assertEquals(3_000L, afterTiming.project.textClips().first().durationMs)

        viewModel.onEvent(EditorEvent.Undo)
        advanceUntilIdle()
        val afterUndo = viewModel.uiState.value as EditorUiState.Loaded
        assertEquals(0L, afterUndo.project.textClips().first().startTimeMs)

        viewModel.onEvent(EditorEvent.RequestRemoveClip(textClip.id))
        viewModel.onEvent(EditorEvent.ConfirmRemoveClip)
        advanceUntilIdle()
        val afterRemove = viewModel.uiState.value as EditorUiState.Loaded
        assertTrue(afterRemove.project.textClips().isEmpty())
    }

    private class InMemoryProjectRepository(initial: Project) : ProjectRepository {
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

    private object StubVideoImporter : VideoImporter {
        override suspend fun importVideo(projectId: String, sourceUri: String): ImportedVideo {
            return ImportedVideo(relativePath = "$projectId/stub.mp4", durationMs = 5_000L)
        }
    }
}
