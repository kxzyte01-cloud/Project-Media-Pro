package com.projectmediapro.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isCreating: Boolean = false,
    val error: HomeError? = null,
)

enum class HomeError { CREATE_FAILED, DELETE_FAILED, INVALID_TITLE }

/** Owns the local project list and project creation/deletion operations. */
class HomeViewModel(
    private val repository: ProjectRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(projects = projects)
            }
        }
    }

    fun createProject(title: String, onCreated: (String) -> Unit) {
        if (title.isBlank() || title.trim().length > MAX_TITLE_LENGTH) {
            _uiState.value = _uiState.value.copy(error = HomeError.INVALID_TITLE)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            runCatching { repository.createProject(title) }
                .onSuccess { project -> onCreated(project.id) }
                .onFailure { _uiState.value = _uiState.value.copy(error = HomeError.CREATE_FAILED) }
            _uiState.value = _uiState.value.copy(isCreating = false)
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteProject(id) }
                .onFailure { _uiState.value = _uiState.value.copy(error = HomeError.DELETE_FAILED) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        private const val MAX_TITLE_LENGTH = 80

        fun provideFactory(repository: ProjectRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(repository) as T
                }
            }
    }
}
