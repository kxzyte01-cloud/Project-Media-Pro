package com.projectmediapro.app.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = object : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun createProject_persistsTrimmedTitleAndReturnsId() = runTest(dispatcher) {
        val repository = FakeProjectRepository()
        val viewModel = HomeViewModel(repository)
        var createdId: String? = null

        viewModel.createProject("  First edit  ") { createdId = it }
        advanceUntilIdle()

        assertEquals("First edit", repository.projects.value.single().title)
        assertEquals(repository.projects.value.single().id, createdId)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun blankProjectName_isRejected() = runTest(dispatcher) {
        val viewModel = HomeViewModel(FakeProjectRepository())

        viewModel.createProject("   ") {}

        assertEquals(HomeError.INVALID_TITLE, viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.projects.size)
    }

    @Test
    fun deleteProject_removesSavedProject() = runTest(dispatcher) {
        val repository = FakeProjectRepository()
        val project = repository.createProject("To delete")
        val viewModel = HomeViewModel(repository)

        viewModel.deleteProject(project.id)
        advanceUntilIdle()

        assertEquals(0, repository.projects.value.size)
        assertNotNull(viewModel.uiState.value)
    }
}

private class FakeProjectRepository : ProjectRepository {
    val projects = MutableStateFlow<List<Project>>(emptyList())

    override fun observeProjects(): Flow<List<Project>> = projects

    override suspend fun createProject(title: String): Project {
        val now = 1_000L
        val project = Project(
            id = "project-${projects.value.size + 1}",
            title = title.trim(),
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        projects.value = projects.value + project
        return project
    }

    override suspend fun getProject(id: String): Project? =
        projects.value.firstOrNull { it.id == id }

    override suspend fun saveProject(project: Project) {
        projects.value = projects.value.filterNot { it.id == project.id } + project
    }

    override suspend fun deleteProject(id: String) {
        projects.value = projects.value.filterNot { it.id == id }
    }
}
