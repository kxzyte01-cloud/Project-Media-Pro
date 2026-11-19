package com.projectmediapro.app.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.OverlayImporter
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.app.editor.EditorError
import com.projectmediapro.app.editor.mapImportError
import com.projectmediapro.core.model.SlotRole
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.TemplateBuilder
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.mainVideoSlots
import com.projectmediapro.core.model.overlaySlots
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface TemplateBuilderUiState {
    data object Loading : TemplateBuilderUiState
    data object NotFound : TemplateBuilderUiState
    data object Error : TemplateBuilderUiState
    data class Loaded(
        val template: Template,
        val selectedSlotId: String? = null,
        val isEditingOverlayDuration: Boolean = false,
        val isImporting: Boolean = false,
        val error: EditorError? = null,
    ) : TemplateBuilderUiState
}

sealed interface TemplateBuilderEvent {
    data class SelectSlot(val slotId: String) : TemplateBuilderEvent
    data class SlotMediaPicked(val slotId: String, val sourceUri: String?) : TemplateBuilderEvent
    data class OverlayPicked(val sourceUri: String?) : TemplateBuilderEvent
    data object AddVideoSlot : TemplateBuilderEvent
    data object AddAudioSlot : TemplateBuilderEvent
    data object OpenOverlayDuration : TemplateBuilderEvent
    data object DismissDialog : TemplateBuilderEvent
    data class ApplyOverlayDuration(val durationMs: Long) : TemplateBuilderEvent
    data class MoveAudioSlot(val slotId: String, val delta: Int) : TemplateBuilderEvent
    data class ToggleReplaceable(val slotId: String) : TemplateBuilderEvent
    data class ClearSlotMedia(val slotId: String) : TemplateBuilderEvent
    data class RemoveSlot(val slotId: String) : TemplateBuilderEvent
}

class TemplateBuilderViewModel(
    private val templateId: String,
    private val repository: TemplateRepository,
    private val mediaStorage: ProjectMediaStorage,
    private val videoImporter: VideoImporter,
    private val audioImporter: AudioImporter,
    private val overlayImporter: OverlayImporter,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<TemplateBuilderUiState>(TemplateBuilderUiState.Loading)
    val uiState: StateFlow<TemplateBuilderUiState> = _uiState.asStateFlow()

    private val changeMutex = Mutex()

    init {
        viewModelScope.launch {
            changeMutex.withLock {
                _uiState.value = try {
                    val template = repository.getTemplate(templateId)
                    if (template != null) {
                        TemplateBuilderUiState.Loaded(
                            template = template,
                            selectedSlotId = template.slots.firstOrNull()?.id,
                        )
                    } else {
                        TemplateBuilderUiState.NotFound
                    }
                } catch (_: IOException) {
                    TemplateBuilderUiState.Error
                }
            }
        }
    }

    fun onEvent(event: TemplateBuilderEvent) {
        when (event) {
            is TemplateBuilderEvent.SelectSlot -> {
                val current = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
                if (current.isImporting || current.template.slots.none { it.id == event.slotId }) return
                _uiState.value = current.copy(
                    selectedSlotId = event.slotId,
                    isEditingOverlayDuration = false,
                )
            }
            is TemplateBuilderEvent.SlotMediaPicked -> handleSlotMediaPicked(event.slotId, event.sourceUri)
            is TemplateBuilderEvent.OverlayPicked -> handleOverlayPicked(event.sourceUri)
            TemplateBuilderEvent.AddVideoSlot -> {
                val newId = idProvider()
                mutateTemplate(preferredSelectedSlotId = newId) { template ->
                    val nextNumber = template.mainVideoSlots().size + 1
                    TemplateBuilder.addVideoSlot(template, newId, "Video $nextNumber")
                }
            }
            TemplateBuilderEvent.AddAudioSlot -> {
                val newId = idProvider()
                mutateTemplate(preferredSelectedSlotId = newId) { template ->
                    val nextNumber = template.audioSlots().size + 1
                    TemplateBuilder.addAudioSlot(template, newId, "Audio $nextNumber")
                }
            }
            TemplateBuilderEvent.OpenOverlayDuration -> {
                val current = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
                if (current.isImporting) return
                val selected = current.template.slots.firstOrNull { it.id == current.selectedSlotId } ?: return
                if (selected.role != SlotRole.OVERLAY) return
                _uiState.value = current.copy(isEditingOverlayDuration = true)
            }
            TemplateBuilderEvent.DismissDialog -> {
                val current = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
                _uiState.value = current.copy(isEditingOverlayDuration = false)
            }
            is TemplateBuilderEvent.ApplyOverlayDuration -> {
                val current = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
                val targetId = current.selectedSlotId ?: return
                mutateTemplate(preferredSelectedSlotId = targetId) { template ->
                    val slot = template.slots.firstOrNull { it.id == targetId } ?: return@mutateTemplate template
                    TemplateBuilder.updateOverlayTiming(
                        template = template,
                        slotId = targetId,
                        startTimeMs = slot.startTimeMs,
                        durationMs = event.durationMs,
                    )
                }
            }
            is TemplateBuilderEvent.MoveAudioSlot -> {
                mutateTemplate(preferredSelectedSlotId = event.slotId) { template ->
                    TemplateBuilder.moveAudioSlot(template, event.slotId, event.delta)
                }
            }
            is TemplateBuilderEvent.ToggleReplaceable -> {
                mutateTemplate(preferredSelectedSlotId = event.slotId) { template ->
                    TemplateBuilder.toggleSlotReplaceable(template, event.slotId)
                }
            }
            is TemplateBuilderEvent.ClearSlotMedia -> {
                mutateTemplateWithCleanup(preferredSelectedSlotId = event.slotId) { template ->
                    TemplateBuilder.clearSlotMedia(template, event.slotId)
                }
            }
            is TemplateBuilderEvent.RemoveSlot -> {
                mutateTemplateWithCleanup(preferredSelectedSlotId = null) { template ->
                    TemplateBuilder.removeSlot(template, event.slotId)
                }
            }
        }
    }

    private fun handleSlotMediaPicked(slotId: String, sourceUri: String?) {
        if (sourceUri == null) return
        val initial = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
        if (initial.isImporting) return
        val slot = initial.template.slots.firstOrNull { it.id == slotId } ?: return

        _uiState.value = initial.copy(
            isImporting = true,
            isEditingOverlayDuration = false,
            error = null,
        )

        viewModelScope.launch {
            val (relativePath, durationMs) = try {
                when (slot.requiredType) {
                    TrackType.AUDIO -> {
                        val imported = audioImporter.importAudio(templateId, sourceUri)
                        imported.relativePath to imported.durationMs
                    }
                    TrackType.OVERLAY -> {
                        val imported = overlayImporter.importOverlay(templateId, sourceUri)
                        imported.relativePath to TemplateBuilder.DEFAULT_OVERLAY_DURATION_MS
                    }
                    else -> {
                        val imported = videoImporter.importVideo(templateId, sourceUri)
                        imported.relativePath to imported.durationMs
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VideoImportException) {
                finishImportError(mapImportError(failure.reason, slot.requiredType))
                return@launch
            } catch (_: Exception) {
                finishImportError(EditorError.IMPORT_FAILED)
                return@launch
            }

            var saved = false
            try {
                changeMutex.withLock {
                    val latest = _uiState.value as? TemplateBuilderUiState.Loaded ?: return@withLock
                    val previous = latest.template
                    val oldUri = previous.slots.firstOrNull { it.id == slotId }?.filledMediaUri
                    val updated = TemplateBuilder.setSlotMedia(previous, slotId, relativePath, durationMs)
                    try {
                        repository.saveTemplate(updated)
                        saved = true
                        if (oldUri != null && oldUri != relativePath &&
                            !TemplateBuilder.isMediaReferenced(updated, oldUri)
                        ) {
                            mediaStorage.deleteClipFile(oldUri)
                        }
                        _uiState.value = latest.copy(
                            template = updated,
                            selectedSlotId = slotId,
                            isImporting = false,
                            error = null,
                        )
                    } catch (_: IOException) {
                        mediaStorage.deleteClipFile(relativePath)
                        _uiState.value = latest.copy(isImporting = false, error = EditorError.SAVE_FAILED)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (!saved) {
                    mediaStorage.deleteClipFile(relativePath)
                }
                throw cancelled
            }
        }
    }

    private fun handleOverlayPicked(sourceUri: String?) {
        if (sourceUri == null) return
        val initial = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
        if (initial.isImporting) return

        _uiState.value = initial.copy(
            isImporting = true,
            isEditingOverlayDuration = false,
            error = null,
        )

        viewModelScope.launch {
            val relativePath = try {
                overlayImporter.importOverlay(templateId, sourceUri).relativePath
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VideoImportException) {
                finishImportError(mapImportError(failure.reason, TrackType.OVERLAY))
                return@launch
            } catch (_: Exception) {
                finishImportError(EditorError.IMPORT_FAILED)
                return@launch
            }

            var saved = false
            try {
                changeMutex.withLock {
                    val latest = _uiState.value as? TemplateBuilderUiState.Loaded ?: return@withLock
                    val previous = latest.template
                    val newSlotId = idProvider()
                    val nextNumber = previous.overlaySlots().size + 1
                    val updated = TemplateBuilder.addOverlaySlot(
                        template = previous,
                        slotId = newSlotId,
                        label = "Overlay $nextNumber",
                        mediaUri = relativePath,
                    )
                    try {
                        repository.saveTemplate(updated)
                        saved = true
                        _uiState.value = latest.copy(
                            template = updated,
                            selectedSlotId = newSlotId,
                            isImporting = false,
                            error = null,
                        )
                    } catch (_: IOException) {
                        mediaStorage.deleteClipFile(relativePath)
                        _uiState.value = latest.copy(isImporting = false, error = EditorError.SAVE_FAILED)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (!saved) {
                    mediaStorage.deleteClipFile(relativePath)
                }
                throw cancelled
            }
        }
    }

    private fun mutateTemplate(
        preferredSelectedSlotId: String?,
        transform: (Template) -> Template,
    ) {
        mutateTemplateWithCleanup(preferredSelectedSlotId, transform)
    }

    private fun mutateTemplateWithCleanup(
        preferredSelectedSlotId: String?,
        transform: (Template) -> Template,
    ) {
        val initial = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
        if (initial.isImporting) return

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? TemplateBuilderUiState.Loaded ?: return@withLock
                if (latest.isImporting) return@withLock
                val previous = latest.template
                val updated = transform(previous)
                if (updated == previous) {
                    if (latest.isEditingOverlayDuration) {
                        _uiState.value = latest.copy(isEditingOverlayDuration = false)
                    }
                    return@withLock
                }

                try {
                    repository.saveTemplate(updated)
                } catch (_: IOException) {
                    _uiState.value = latest.copy(
                        isEditingOverlayDuration = false,
                        error = EditorError.SAVE_FAILED,
                    )
                    return@withLock
                }

                val removedUris = previous.slots.mapNotNull { it.filledMediaUri }.toSet() -
                    updated.slots.mapNotNull { it.filledMediaUri }.toSet()
                for (uri in removedUris) {
                    if (!TemplateBuilder.isMediaReferenced(updated, uri)) {
                        mediaStorage.deleteClipFile(uri)
                    }
                }

                val nextSelected = when {
                    preferredSelectedSlotId != null &&
                        updated.slots.any { it.id == preferredSelectedSlotId } -> preferredSelectedSlotId
                    latest.selectedSlotId != null &&
                        updated.slots.any { it.id == latest.selectedSlotId } -> latest.selectedSlotId
                    else -> updated.slots.firstOrNull()?.id
                }
                _uiState.value = latest.copy(
                    template = updated,
                    selectedSlotId = nextSelected,
                    isEditingOverlayDuration = false,
                    error = null,
                )
            }
        }
    }

    private fun finishImportError(error: EditorError) {
        val current = _uiState.value as? TemplateBuilderUiState.Loaded ?: return
        _uiState.value = current.copy(isImporting = false, error = error)
    }

    companion object {
        fun provideFactory(
            templateId: String,
            repository: TemplateRepository,
            mediaStorage: ProjectMediaStorage,
            videoImporter: VideoImporter,
            audioImporter: AudioImporter,
            overlayImporter: OverlayImporter,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TemplateBuilderViewModel(
                    templateId = templateId,
                    repository = repository,
                    mediaStorage = mediaStorage,
                    videoImporter = videoImporter,
                    audioImporter = audioImporter,
                    overlayImporter = overlayImporter,
                ) as T
            }
        }
    }
}
