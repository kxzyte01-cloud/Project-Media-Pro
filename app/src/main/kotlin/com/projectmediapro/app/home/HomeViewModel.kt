```kotlin
package com.projectmediapro.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.media.ProjectMediaHealthChecker
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.videoClips
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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
 * Display ordering for projects on the Home screen.
 */
enum class HomeSortOrder {
    RECENT,
    TITLE_ASC,
}

/**
 * Immutable UI state for the Home screen.
 */
/**
 * Resolved local file metadata for loading a project's Home card thumbnail off the UI thread.
 */
data class HomeThumbnailTarget(
    val filePath: String,
    val isStillImage: Boolean,
    val trimStartMs: Long,
    val trimEndMs: Long,
)

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val thumbnailTargets: Map<String, HomeThumbnailTarget> = emptyMap(),
    val searchQuery: String = "",
    val sortOrder: HomeSortOrder = HomeSortOrder.RECENT,
    val isCreateDialogVisible: Boolean = false,
    val draftTitle: String = "",
    val pendingRenameId: String? = null,
    val renameDraftTitle: String = "",
    val pendingDeleteId: String? = null,
    val duplicatingProjectId: String? = null,
    val error: HomeError? = null,
) {
    val visibleProjects: List<Project>
        get() {
            val trimmed = searchQuery.trim()
            val filtered = if (trimmed.isEmpty()) {
                projects
            } else {
                projects.filter { it.title.contains(trimmed, ignoreCase = true) }
            }
            return when (sortOrder) {
                HomeSortOrder.RECENT -> filtered.sortedWith(
                    compareByDescending<Project> { it.updatedAt }.thenBy { it.id },
                )
                HomeSortOrder.TITLE_ASC -> filtered.sortedWith(
                    compareBy<Project> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id },
                )
            }
        }
}

/**
 * User events emitted from the Home screen.
 */
sealed interface HomeEvent {
    data class UpdateSearchQuery(val query: String) : HomeEvent
    data class SelectSortOrder(val sortOrder: HomeSortOrder) : HomeEvent
    data class ShowCreateDialog(val defaultTitle: String) : HomeEvent
    data class UpdateDraftTitle(val title: String) : HomeEvent
    data object DismissCreateDialog : HomeEvent
    data class ConfirmCreate(val fallbackTitle: String) : HomeEvent
    data class RequestRename(val projectId: String) : HomeEvent
    data class UpdateRenameTitle(val title: String) : HomeEvent
    data object DismissRenameDialog : HomeEvent
    data object ConfirmRename : HomeEvent
    data class DuplicateProject(val projectId: String, val copySuffix: String) : HomeEvent
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
    private val mediaStorage: ProjectMediaStorage? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshProjects()
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.UpdateSearchQuery -> {
                _uiState.update { state ->
                    state.copy(searchQuery = event.query.take(ProjectRepository.MAX_TITLE_LENGTH))
                }
            }

            is HomeEvent.SelectSortOrder -> {
                _uiState.update { state ->
                    state.copy(sortOrder = event.sortOrder)
                }
            }

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

            is HomeEvent.RequestRename -> {
                if (_uiState.value.duplicatingProjectId == event.projectId) return
                val existing = _uiState.value.projects.firstOrNull { it.id == event.projectId } ?: return
                _uiState.update { state ->
                    state.copy(
                        pendingRenameId = existing.id,
                        renameDraftTitle = existing.title.take(ProjectRepository.MAX_TITLE_LENGTH),
                        error = null,
                    )
                }
            }

            is HomeEvent.UpdateRenameTitle -> {
                _uiState.update { state ->
                    state.copy(
                        renameDraftTitle = event.title.take(ProjectRepository.MAX_TITLE_LENGTH),
                    )
                }
            }

            is HomeEvent.DismissRenameDialog -> {
                _uiState.update { state ->
                    state.copy(
                        pendingRenameId = null,
                        renameDraftTitle = "",
                    )
                }
            }

            is HomeEvent.ConfirmRename -> {
                val targetId = _uiState.value.pendingRenameId ?: return
                if (_uiState.value.duplicatingProjectId == targetId) return
                val newTitle = _uiState.value.renameDraftTitle.trim().take(ProjectRepository.MAX_TITLE_LENGTH)
                if (newTitle.isEmpty()) return
                _uiState.update { state ->
                    state.copy(
                        pendingRenameId = null,
                        renameDraftTitle = "",
                        error = null,
                    )
                }
                viewModelScope.launch {
                    try {
                        val renamed = projectRepository.renameProject(targetId, newTitle)
                        if (renamed == null) {
                            _uiState.update { it.copy(error = HomeError.SAVE_FAILED) }
                        } else {
                            refreshProjects()
                        }
                    } catch (_: IOException) {
                        _uiState.update { it.copy(error = HomeError.SAVE_FAILED) }
                    }
                }
            }

            is HomeEvent.DuplicateProject -> {
                if (_uiState.value.duplicatingProjectId != null) return
                _uiState.update { it.copy(duplicatingProjectId = event.projectId, error = null) }
                viewModelScope.launch {
                    try {
                        val duplicated = projectRepository.duplicateProject(
                            id = event.projectId,
                            copySuffix = event.copySuffix,
                        )
                        if (duplicated == null) {
                            _uiState.update { it.copy(error = HomeError.SAVE_FAILED) }
                        } else {
                            refreshProjects()
                        }
                    } catch (_: IOException) {
                        _uiState.update { it.copy(error = HomeError.SAVE_FAILED) }
                    } finally {
                        _uiState.update { it.copy(duplicatingProjectId = null) }
                    }
                }
            }

            is HomeEvent.RequestDelete -> {
                if (_uiState.value.duplicatingProjectId == event.projectId) return
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
                if (_uiState.value.duplicatingProjectId == targetId) return
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
            val thumbs = if (ioDispatcher != null) {
                kotlinx.coroutines.withContext(ioDispatcher) {
                    resolveThumbnailTargets(loaded)
                }
            } else {
                resolveThumbnailTargets(loaded)
            }
            _uiState.update { state ->
                state.copy(
                    projects = loaded,
                    thumbnailTargets = thumbs,
                )
            }
        } catch (_: IOException) {
            _uiState.update { state ->
                state.copy(error = HomeError.LOAD_FAILED)
            }
        }
    }

    private fun resolveThumbnailTargets(projects: List<Project>): Map<String, HomeThumbnailTarget> {
        val storage = mediaStorage ?: return emptyMap()
        return buildMap {
            for (project in projects) {
                val firstHealthy = project.videoClips().firstNotNullOfOrNull { clip ->
                    val file = storage.resolve(clip.mediaUri)
                    if (file != null && ProjectMediaHealthChecker.isHealthyMediaFile(file)) {
                        HomeThumbnailTarget(
                            filePath = file.absolutePath,
                            isStillImage = clip.isStillImage,
                            trimStartMs = clip.trimStartMs,
                            trimEndMs = clip.trimEndMs,
                        )
                    } else {
                        null
                    }
                }
                if (firstHealthy != null) {
                    put(project.id, firstHealthy)
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            projectRepository: ProjectRepository,
            mediaStorage: ProjectMediaStorage? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    projectRepository = projectRepository,
                    mediaStorage = mediaStorage,
                    ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
                ) as T
            }
        }
    }
}
```
