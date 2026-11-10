package com.projectmediapro.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val error: HomeError? = null,
)

enum class HomeError { LOAD_FAILED, CREATE_FAILED, DELETE_FAILED, INVALID_TITLE }

/** Owns the local project list and project creation/deletion operations. */
class HomeViewModel(
    private val repository: ProjectRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProjects()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = HomeError.LOAD_FAILED,
                    )
                }
                .collect { projects ->
                    _uiState.value = _uiState.value.copy(
                        projects = projects,
                        isLoading = false,
                    )
                }
        }
    }

    fun createProject(title: String, onCreated: (String) -> Unit) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || cleanTitle.length > MAX_TITLE_LENGTH) {
            _uiState.value = _uiState.value.copy(error = HomeError.INVALID_TITLE)
            return
        }
        if (_uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val project = repository.createProject(cleanTitle)
                _uiState.value = _uiState.value.copy(isCreating = false, error = null)
                onCreated(project.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = HomeError.CREATE_FAILED,
                )
            }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteProject(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(error = HomeError.DELETE_FAILED)
            }
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
