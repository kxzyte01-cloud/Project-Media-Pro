package com.projectmediapro.app.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Editor screen in Step 1.
 */
sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data object Error : EditorUiState
    data class Loaded(val project: Project) : EditorUiState
}

/**
 * Loads a single [Project] by ID from [ProjectRepository].
 */
class EditorViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val project = projectRepository.getProject(projectId)
                if (project != null) EditorUiState.Loaded(project) else EditorUiState.NotFound
            } catch (_: IOException) {
                EditorUiState.Error
            }
        }
    }

    companion object {
        fun provideFactory(
            projectId: String,
            projectRepository: ProjectRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(
                    projectId = projectId,
                    projectRepository = projectRepository,
                ) as T
            }
        }
    }
}
