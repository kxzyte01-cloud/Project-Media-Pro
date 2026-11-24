package com.projectmediapro.app.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.template.TemplatePackCodec
import com.projectmediapro.app.data.template.TemplateRepository
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.projectmediapro.core.model.StarterTemplates
import com.projectmediapro.core.model.Template
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class TemplatesError {
    LOAD_FAILED,
    SAVE_FAILED,
    DELETE_FAILED,
}

data class TemplatesUiState(
    val templates: List<Template> = emptyList(),
    val starterTemplates: List<Template> = StarterTemplates.all(),
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
    data class CustomizeStarter(val starter: Template) : TemplatesEvent
    data class RequestDelete(val templateId: String) : TemplatesEvent
    data object CancelDelete : TemplatesEvent
    data object ConfirmDelete : TemplatesEvent
    data class ImportTemplatePack(val openInputStream: () -> InputStream?) : TemplatesEvent
    data class ExportTemplatePack(
        val templateId: String,
        val openOutputStream: () -> OutputStream?,
    ) : TemplatesEvent
}

sealed interface TemplatesEffect {
    data class OpenTemplate(val templateId: String) : TemplatesEffect
}

class TemplatesViewModel(
    private val templateRepository: TemplateRepository,
    private val templateMediaStorage: ProjectMediaStorage? = null,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplatesUiState())
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TemplatesEffect>(Channel.BUFFERED)
    val effects: Flow<TemplatesEffect> = _effects.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
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
            is TemplatesEvent.CustomizeStarter -> {
                viewModelScope.launch {
                    val created = try {
                        val base = templateRepository.createTemplate(event.starter.name)
                        val copiedSlots = event.starter.slots.map { it.copy(id = idProvider()) }
                        val customized = base.copy(
                            description = event.starter.description,
                            slots = copiedSlots,
                            canvasAspect = event.starter.canvasAspect,
                            canvasBackground = event.starter.canvasBackground,
                            captionStyleJson = event.starter.captionStyleJson,
                        )
                        templateRepository.saveTemplate(customized)
                        customized
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
            is TemplatesEvent.ImportTemplatePack -> {
                val storage = templateMediaStorage ?: return
                viewModelScope.launch {
                    try {
                        val imported = withContext(Dispatchers.IO) {
                            val stream = event.openInputStream()
                                ?: throw IOException("Could not open template pack")
                            stream.use { input ->
                                TemplatePackCodec.importTemplatePack(input, storage, idProvider)
                            }
                        }
                        templateRepository.saveTemplate(imported)
                        refreshTemplates()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        _uiState.update { it.copy(error = TemplatesError.LOAD_FAILED) }
                    }
                }
            }
            is TemplatesEvent.ExportTemplatePack -> {
                val storage = templateMediaStorage ?: return
                val target = _uiState.value.templates.firstOrNull { it.id == event.templateId } ?: return
                viewModelScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val stream = event.openOutputStream()
                                ?: throw IOException("Could not create template pack")
                            stream.use { output ->
                                TemplatePackCodec.exportTemplatePack(target, storage, output)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        _uiState.update { it.copy(error = TemplatesError.SAVE_FAILED) }
                    }
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
            templateMediaStorage: ProjectMediaStorage? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TemplatesViewModel(
                    templateRepository = templateRepository,
                    templateMediaStorage = templateMediaStorage,
                ) as T
            }
        }
    }
}
