package com.projectmediapro.app.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.core.model.Template
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TemplatesError {
    LOAD_FAILED,
    SAVE_FAILED,
    DELETE_FAILED,
}

data class TemplatesUiState(
    val templates: List<Template> = emptyList(),
    val isCreateDialogVisible: Boolean = false,
    val draftName: String = "",
    val pendingDeleteId: String? = null,
    val error: TemplatesError? = null,
)

sealed interface TemplatesEvent {
    data class ShowCreateDialog(val defaultName: String) : TemplatesEvent
    data class UpdateDraftName(val name: String) : TemplatesEvent
    data object DismissCreateDialog : TemplatesEvent
    data class ConfirmCreate(val fallbackName: String) : TemplatesEvent
    data class RequestDelete(val templateId: String) : TemplatesEvent
    data object CancelDelete : TemplatesEvent
    data object ConfirmDelete : TemplatesEvent
}

sealed interface TemplatesEffect {
    data class OpenTemplate(val templateId: String) : TemplatesEffect
}

class TemplatesViewModel(
    private val templateRepository: TemplateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState())
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TemplatesEffect>(Channel.BUFFERED)
    val effects: Flow<TemplatesEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            refreshTemplates()
        }
    }

    fun onEvent(event: TemplatesEvent) {
        when (event) {
            is TemplatesEvent.ShowCreateDialog -> {
                _uiState.update { state ->
                    state.copy(
                        isCreateDialogVisible = true,
                        draftName = event.defaultName.take(TemplateRepository.MAX_NAME_LENGTH),
                        error = null,
                    )
                }
            }
            is TemplatesEvent.UpdateDraftName -> {
                _uiState.update { state ->
                    state.copy(draftName = event.name.take(TemplateRepository.MAX_NAME_LENGTH))
                }
            }
            TemplatesEvent.DismissCreateDialog -> {
                _uiState.update { it.copy(isCreateDialogVisible = false, draftName = "") }
            }
            is TemplatesEvent.ConfirmCreate -> {
                val raw = _uiState.value.draftName.trim()
                val resolvedName = (if (raw.isNotEmpty()) raw else event.fallbackName.trim())
                    .take(TemplateRepository.MAX_NAME_LENGTH)
                _uiState.update { it.copy(isCreateDialogVisible = false, draftName = "", error = null) }
                viewModelScope.launch {
                    val created = try {
                        templateRepository.createTemplate(resolvedName)
                    } catch (_: IOException) {
                        _uiState.update { it.copy(error = TemplatesError.SAVE_FAILED) }
                        return@launch
                    }
                    refreshTemplates()
                    _effects.send(TemplatesEffect.OpenTemplate(created.id))
                }
            }
            is TemplatesEvent.RequestDelete -> {
                _uiState.update { it.copy(pendingDeleteId = event.templateId, error = null) }
            }
            TemplatesEvent.CancelDelete -> {
                _uiState.update { it.copy(pendingDeleteId = null) }
            }
            TemplatesEvent.ConfirmDelete -> {
                val targetId = _uiState.value.pendingDeleteId ?: return
                _uiState.update { it.copy(pendingDeleteId = null, error = null) }
                viewModelScope.launch {
                    try {
                        templateRepository.deleteTemplate(targetId)
                    } catch (_: IOException) {
                        _uiState.update { it.copy(error = TemplatesError.DELETE_FAILED) }
                        return@launch
                    }
                    refreshTemplates()
                }
            }
        }
    }

    private suspend fun refreshTemplates() {
        try {
            val loaded = templateRepository.listTemplates()
            _uiState.update { it.copy(templates = loaded) }
        } catch (_: IOException) {
            _uiState.update { it.copy(error = TemplatesError.LOAD_FAILED) }
        }
    }

    companion object {
        fun provideFactory(
            templateRepository: TemplateRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TemplatesViewModel(templateRepository = templateRepository) as T
            }
        }
    }
}
