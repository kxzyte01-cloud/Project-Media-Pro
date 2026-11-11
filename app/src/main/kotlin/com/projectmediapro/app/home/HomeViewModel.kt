package com.projectmediapro.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Non-text error categories for the Home screen, mapped to string resources in the UI layer.
 */
enum class HomeError {
    LOAD_FAILED,
    SAVE_FAILED,
    DELETE_FAILED,
}

/**
 * Immutable UI state for the Home screen.
 */
data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isCreateDialogVisible: Boolean = false,
    val draftTitle: String = "",
    val pendingDeleteId: String? = null,
    val error: HomeError? = null,
)

/**
 * User events emitted from the Home screen.
 */
sealed interface HomeEvent {
    data class ShowCreateDialog(val defaultTitle: String) : HomeEvent
    data class UpdateDraftTitle(val title: String) : HomeEvent
    data object DismissCreateDialog : HomeEvent
    data class ConfirmCreate(val fallbackTitle: String) : HomeEvent
    data class RequestDelete(val projectId: String) : HomeEvent
    data object CancelDelete : HomeEvent
    data object ConfirmDelete : HomeEvent
}

/**
 * One-shot navigation effects emitted by [HomeViewModel].
 */
sealed interface HomeEffect {
    data class OpenProject(val projectId: String) : HomeEffect
}

/**
 * Unidirectional ViewModel for managing saved projects on the Home screen.
 */
class HomeViewModel(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            refreshProjects()
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.ShowCreateDialog -> {
                _uiState.update { state ->
                    state.copy(
                        isCreateDialogVisible = true,
                        draftTitle = event.defaultTitle.take(ProjectRepository.MAX_TITLE_LENGTH),
                        error = null,
                    )
                }
            }

            is HomeEvent.UpdateDraftTitle -> {
                _uiState.update { state ->
                    state.copy(
                        draftTitle = event.title.take(ProjectRepository.MAX_TITLE_LENGTH),
                    )
                }
            }

            is HomeEvent.DismissCreateDialog -> {
                _uiState.update { state ->
                    state.copy(
                        isCreateDialogVisible = false,
                        draftTitle = "",
                    )
                }
            }

            is HomeEvent.ConfirmCreate -> {
                val rawDraft = _uiState.value.draftTitle.trim()
                val resolvedTitle = (if (rawDraft.isNotEmpty()) rawDraft else event.fallbackTitle.trim())
                    .take(ProjectRepository.MAX_TITLE_LENGTH)
                _uiState.update { state ->
                    state.copy(
                        isCreateDialogVisible = false,
                        draftTitle = "",
                        error = null,
                    )
                }
                viewModelScope.launch {
                    val created = try {
                        projectRepository.createProject(resolvedTitle)
                    } catch (_: IOException) {
                        _uiState.update { it.copy(error = HomeError.SAVE_FAILED) }
                        return@launch
                    }
                    refreshProjects()
                    _effects.send(HomeEffect.OpenProject(created.id))
                }
            }

            is HomeEvent.RequestDelete -> {
                _uiState.update { state ->
                    state.copy(
                        pendingDeleteId = event.projectId,
                        error = null,
                    )
                }
            }

            is HomeEvent.CancelDelete -> {
                _uiState.update { state ->
                    state.copy(pendingDeleteId = null)
                }
            }

            is HomeEvent.ConfirmDelete -> {
                val targetId = _uiState.value.pendingDeleteId ?: return
                _uiState.update { state ->
                    state.copy(
                        pendingDeleteId = null,
                        error = null,
                    )
                }
                viewModelScope.launch {
                    try {
                        projectRepository.deleteProject(targetId)
                    } catch (_: IOException) {
                        _uiState.update { it.copy(error = HomeError.DELETE_FAILED) }
                        return@launch
                    }
                    refreshProjects()
                }
            }
        }
    }

    private suspend fun refreshProjects() {
        try {
            val loaded = projectRepository.listProjects()
            _uiState.update { state ->
                state.copy(projects = loaded)
            }
        } catch (_: IOException) {
            _uiState.update { state ->
                state.copy(error = HomeError.LOAD_FAILED)
            }
        }
    }

    companion object {
        fun provideFactory(
            projectRepository: ProjectRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(projectRepository = projectRepository) as T
            }
        }
    }
}
