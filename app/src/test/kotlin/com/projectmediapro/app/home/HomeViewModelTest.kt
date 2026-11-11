package com.projectmediapro.app.home

import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createFlow_savesProjectRefreshesListAndEmitsOpenProjectEffect() = runTest(testDispatcher) {
        val fakeRepo = FakeProjectRepository()
        val viewModel = HomeViewModel(fakeRepo)

        viewModel.onEvent(HomeEvent.ShowCreateDialog("Project 1"))
        assertTrue(viewModel.uiState.value.isCreateDialogVisible)
        assertEquals("Project 1", viewModel.uiState.value.draftTitle)

        viewModel.onEvent(HomeEvent.UpdateDraftTitle("Summer Reel"))
        viewModel.onEvent(HomeEvent.ConfirmCreate("Project 1"))

        assertFalse(viewModel.uiState.value.isCreateDialogVisible)
        assertEquals(1, viewModel.uiState.value.projects.size)
        assertEquals("Summer Reel", viewModel.uiState.value.projects[0].title)

        val effect = viewModel.effects.first()
        assertEquals(HomeEffect.OpenProject("proj-1"), effect)
    }

    @Test
    fun confirmCreate_whenTitleIsBlank_usesFallbackDefaultTitle() = runTest(testDispatcher) {
        val fakeRepo = FakeProjectRepository()
        val viewModel = HomeViewModel(fakeRepo)

        viewModel.onEvent(HomeEvent.ShowCreateDialog("Project 1"))
        viewModel.onEvent(HomeEvent.UpdateDraftTitle("   "))
        viewModel.onEvent(HomeEvent.ConfirmCreate("Project 1"))

        assertEquals(1, viewModel.uiState.value.projects.size)
        assertEquals("Project 1", viewModel.uiState.value.projects[0].title)
    }

    @Test
    fun deleteFlow_requiresConfirmationAndSupportsCancelAndConfirm() = runTest(testDispatcher) {
        val fakeRepo = FakeProjectRepository()
        val existing = fakeRepo.createProject("My Clip")
        val viewModel = HomeViewModel(fakeRepo)

        assertEquals(1, viewModel.uiState.value.projects.size)

        viewModel.onEvent(HomeEvent.RequestDelete(existing.id))
        assertEquals(existing.id, viewModel.uiState.value.pendingDeleteId)
        assertEquals(1, viewModel.uiState.value.projects.size)

        viewModel.onEvent(HomeEvent.CancelDelete)
        assertNull(viewModel.uiState.value.pendingDeleteId)
        assertEquals(1, viewModel.uiState.value.projects.size)

        viewModel.onEvent(HomeEvent.RequestDelete(existing.id))
        viewModel.onEvent(HomeEvent.ConfirmDelete)
        assertNull(viewModel.uiState.value.pendingDeleteId)
        assertTrue(viewModel.uiState.value.projects.isEmpty())
    }

    @Test
    fun repositoryFailures_setMatchingErrorEnumWithoutCrashing() = runTest(testDispatcher) {
        val fakeRepo = FakeProjectRepository(shouldFailLoad = true)
        val viewModel = HomeViewModel(fakeRepo)
        assertEquals(HomeError.LOAD_FAILED, viewModel.uiState.value.error)

        fakeRepo.shouldFailLoad = false
        fakeRepo.shouldFailSave = true
        viewModel.onEvent(HomeEvent.ShowCreateDialog("Project 1"))
        viewModel.onEvent(HomeEvent.ConfirmCreate("Project 1"))
        assertEquals(HomeError.SAVE_FAILED, viewModel.uiState.value.error)

        fakeRepo.shouldFailSave = false
        fakeRepo.shouldFailDelete = true
        viewModel.onEvent(HomeEvent.RequestDelete("proj-1"))
        viewModel.onEvent(HomeEvent.ConfirmDelete)
        assertEquals(HomeError.DELETE_FAILED, viewModel.uiState.value.error)
    }
}

private class FakeProjectRepository(
    var shouldFailLoad: Boolean = false,
    var shouldFailSave: Boolean = false,
    var shouldFailDelete: Boolean = false,
) : ProjectRepository {

    private val items = mutableListOf<Project>()
    private var nextId = 1

    override suspend fun listProjects(): List<Project> {
        if (shouldFailLoad) throw IOException("Load failure")
        return items.sortedByDescending { it.updatedAt }
    }

    override suspend fun getProject(id: String): Project? {
        if (shouldFailLoad) throw IOException("Load failure")
        return items.firstOrNull { it.id == id }
    }

    override suspend fun createProject(title: String): Project {
        if (shouldFailSave) throw IOException("Save failure")
        val project = Project(
            id = "proj-${nextId++}",
            title = title,
            createdAt = 100L * nextId,
            updatedAt = 100L * nextId,
        )
        items.add(project)
        return project
    }

    override suspend fun saveProject(project: Project) {
        if (shouldFailSave) throw IOException("Save failure")
        items.removeAll { it.id == project.id }
        items.add(project)
    }

    override suspend fun deleteProject(id: String) {
        if (shouldFailDelete) throw IOException("Delete failure")
        items.removeAll { it.id == id }
    }
}
