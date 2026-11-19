package com.projectmediapro.app.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.app.data.template.TemplateProjectCreator
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.app.data.template.TemplateSlotImportException
import com.projectmediapro.app.editor.EditorError
import com.projectmediapro.app.editor.mapImportError
import com.projectmediapro.core.model.StarterTemplates
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.TrackType
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface UseTemplateUiState {
    data object Loading : UseTemplateUiState
    data object NotFound : UseTemplateUiState
    data object Error : UseTemplateUiState
    data class Loaded(
        val template: Template,
        val projectTitle: String,
        val pickedUrisBySlotId: Map<String, String> = emptyMap(),
        val isCreating: Boolean = false,
        val error: EditorError? = null,
    ) : UseTemplateUiState {
        val hasAtLeastOneVideoReady: Boolean
            get() = template.slots.any { slot ->
                slot.requiredType == TrackType.VIDEO &&
                    (pickedUrisBySlotId[slot.id] != null || slot.filledMediaUri != null)
            }
    }
}

sealed interface UseTemplateEvent {
    data class UpdateProjectTitle(val title: String) : UseTemplateEvent
    data class SlotMediaPicked(val slotId: String, val sourceUri: String?) : UseTemplateEvent
    data class ClearPickedSlot(val slotId: String) : UseTemplateEvent
    data object CreateProject : UseTemplateEvent
}

sealed interface UseTemplateEffect {
    data class OpenProject(val projectId: String) : UseTemplateEffect
}

class UseTemplateViewModel(
    private val templateId: String,
    private val templateRepository: TemplateRepository,
    private val projectCreator: TemplateProjectCreator,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UseTemplateUiState>(UseTemplateUiState.Loading)
    val uiState: StateFlow<UseTemplateUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UseTemplateEffect>(Channel.BUFFERED)
    val effects: Flow<UseTemplateEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val template = StarterTemplates.findById(templateId)
                    ?: templateRepository.getTemplate(templateId)
                if (template != null) {
                    UseTemplateUiState.Loaded(
                        template = template,
                        projectTitle = template.name.take(ProjectRepository.MAX_TITLE_LENGTH),
                    )
                } else {
                    UseTemplateUiState.NotFound
                }
            } catch (_: IOException) {
                UseTemplateUiState.Error
            }
        }
    }

    fun onEvent(event: UseTemplateEvent) {
        val current = _uiState.value as? UseTemplateUiState.Loaded ?: return
        if (current.isCreating) return

        when (event) {
            is UseTemplateEvent.UpdateProjectTitle -> {
                _uiState.value = current.copy(
                    projectTitle = event.title.take(ProjectRepository.MAX_TITLE_LENGTH),
                )
            }
            is UseTemplateEvent.SlotMediaPicked -> {
                val uri = event.sourceUri ?: return
                if (current.template.slots.none { it.id == event.slotId }) return
                _uiState.value = current.copy(
                    pickedUrisBySlotId = current.pickedUrisBySlotId + (event.slotId to uri),
                    error = null,
                )
            }
            is UseTemplateEvent.ClearPickedSlot -> {
                _uiState.value = current.copy(
                    pickedUrisBySlotId = current.pickedUrisBySlotId - event.slotId,
                    error = null,
                )
            }
            UseTemplateEvent.CreateProject -> handleCreateProject(current)
        }
    }

    private fun handleCreateProject(current: UseTemplateUiState.Loaded) {
        if (!current.hasAtLeastOneVideoReady) {
            _uiState.value = current.copy(error = EditorError.EXPORT_NOTHING)
            return
        }
        val title = current.projectTitle.trim()
            .ifEmpty { current.template.name }
            .take(ProjectRepository.MAX_TITLE_LENGTH)

        _uiState.value = current.copy(isCreating = true, error = null)
        viewModelScope.launch {
            try {
                val projectId = projectCreator.createProjectFromTemplate(
                    template = current.template,
                    projectTitle = title,
                    pickedUrisBySlotId = current.pickedUrisBySlotId,
                )
                _uiState.value = current.copy(isCreating = false, error = null)
                _effects.send(UseTemplateEffect.OpenProject(projectId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: TemplateSlotImportException) {
                _uiState.value = current.copy(
                    isCreating = false,
                    error = mapImportError(failure.reason, failure.trackType),
                )
            } catch (_: IOException) {
                _uiState.value = current.copy(isCreating = false, error = EditorError.SAVE_FAILED)
            } catch (_: Exception) {
                _uiState.value = current.copy(isCreating = false, error = EditorError.IMPORT_FAILED)
            }
        }
    }

    companion object {
        fun provideFactory(
            templateId: String,
            templateRepository: TemplateRepository,
            projectCreator: TemplateProjectCreator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UseTemplateViewModel(
                    templateId = templateId,
                    templateRepository = templateRepository,
                    projectCreator = projectCreator,
                ) as T
            }
        }
    }
}
