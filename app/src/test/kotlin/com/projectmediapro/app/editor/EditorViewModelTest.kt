package com.projectmediapro.app.editor

import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

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
    fun init_withExistingId_emitsLoadedState() = runTest(testDispatcher) {
        val existing = Project(id = "valid-id-1", title = "Demo Project")
        val repo = FakeEditorProjectRepository(listOf(existing))

        val viewModel = EditorViewModel(
            projectId = "valid-id-1",
            projectRepository = repo,
        )

        assertEquals(EditorUiState.Loaded(existing), viewModel.uiState.value)
    }

    @Test
    fun init_withRepositoryFailure_emitsErrorState() = runTest(testDispatcher) {
        val viewModel = EditorViewModel(
            projectId = "valid-id-1",
            projectRepository = FakeEditorProjectRepository(
                initialProjects = emptyList(),
                shouldFailLoad = true,
            ),
        )

        assertEquals(EditorUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun init_withMissingOrInvalidId_emitsNotFoundState() = runTest(testDispatcher) {
        val existing = Project(id = "valid-id-1", title = "Demo Project")
        val repo = FakeEditorProjectRepository(listOf(existing))

        val missingVm = EditorViewModel(
            projectId = "missing-id",
            projectRepository = repo,
        )
        assertEquals(EditorUiState.NotFound, missingVm.uiState.value)

        val invalidVm = EditorViewModel(
            projectId = "../invalid",
            projectRepository = repo,
        )
        assertEquals(EditorUiState.NotFound, invalidVm.uiState.value)
    }
}

private class FakeEditorProjectRepository(
    initialProjects: List<Project>,
    private val shouldFailLoad: Boolean = false,
) : ProjectRepository {
    private val map = initialProjects.associateBy { it.id }.toMutableMap()

    override suspend fun listProjects(): List<Project> = map.values.toList()

    override suspend fun getProject(id: String): Project? {
        if (shouldFailLoad) throw IOException("Storage read failed")
        return map[id]
    }

    override suspend fun createProject(title: String): Project {
        val project = Project(id = "new-id", title = title)
        map[project.id] = project
        return project
    }

    override suspend fun saveProject(project: Project) {
        map[project.id] = project
    }

    override suspend fun deleteProject(id: String) {
        map.remove(id)
    }
}
