package com.projectmediapro.app.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TrackType
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class EditorError { IMPORT_FAILED, IMPORT_NOT_A_VIDEO, IMPORT_NO_SPACE, SAVE_FAILED }

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data object Error : EditorUiState
    data class Loaded(
        val project: Project,
        val previewPaths: List<String> = emptyList(),
        val isImporting: Boolean = false,
        val pendingRemoveClipId: String? = null,
        val error: EditorError? = null,
    ) : EditorUiState
}

sealed interface EditorEvent {
    data class VideoPicked(val sourceUri: String?) : EditorEvent
    data class RequestRemoveClip(val clipId: String) : EditorEvent
    data object ConfirmRemoveClip : EditorEvent
    data object CancelRemoveClip : EditorEvent
}

class EditorViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val videoImporter: VideoImporter,
    private val mediaStorage: ProjectMediaStorage,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private val changeMutex = Mutex()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val project = repository.getProject(projectId)
                if (project != null) EditorUiState.Loaded(project, resolvePreviewPaths(project))
                else EditorUiState.NotFound
            } catch (_: IOException) {
                EditorUiState.Error
            }
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.VideoPicked -> handleVideoPicked(event.sourceUri)
            is EditorEvent.RequestRemoveClip -> updateLoaded { it.copy(pendingRemoveClipId = event.clipId) }
            EditorEvent.CancelRemoveClip -> updateLoaded { it.copy(pendingRemoveClipId = null) }
            EditorEvent.ConfirmRemoveClip -> handleConfirmRemoveClip()
        }
    }

    private fun handleVideoPicked(sourceUri: String?) {
        if (sourceUri == null) return
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isImporting) return
        _uiState.value = initial.copy(isImporting = true, error = null)

        viewModelScope.launch {
            val imported = try {
                videoImporter.importVideo(projectId, sourceUri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VideoImportException) {
                updateLoaded { it.copy(isImporting = false, error = mapImportError(failure.reason)) }
                return@launch
            }

            var saved = false
            try {
                changeMutex.withLock {
                    val beforeSave = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    val previousProject = beforeSave.project
                    val d = imported.durationMs
                    val clip = Clip(idProvider(), imported.relativePath, 0L, d, 0L, d)
                    val updated = ProjectTimeline.appendClip(previousProject, clip, idProvider())
                    try {
                        repository.saveProject(updated)
                        saved = true
                        _uiState.value = beforeSave.copy(
                            project = updated,
                            previewPaths = resolvePreviewPaths(updated),
                            isImporting = false,
                            error = null,
                        )
                    } catch (_: IOException) {
                        mediaStorage.deleteClipFile(imported.relativePath)
                        _uiState.value = beforeSave.copy(
                            project = previousProject,
                            previewPaths = resolvePreviewPaths(previousProject),
                            isImporting = false,
                            error = EditorError.SAVE_FAILED,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                if (!saved) mediaStorage.deleteClipFile(imported.relativePath)
                throw cancelled
            }
        }
    }

    private fun handleConfirmRemoveClip() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        val targetClipId = current.pendingRemoveClipId ?: return
        _uiState.value = current.copy(pendingRemoveClipId = null, error = null)

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                val previousProject = latest.project
                val removedClip = previousProject.tracks.asSequence()
                    .flatMap { it.clips.asSequence() }
                    .firstOrNull { it.id == targetClipId } ?: return@withLock
                val updated = ProjectTimeline.removeClip(previousProject, targetClipId)
                try {
                    repository.saveProject(updated)
                } catch (_: IOException) {
                    _uiState.value = latest.copy(
                        project = previousProject,
                        previewPaths = resolvePreviewPaths(previousProject),
                        error = EditorError.SAVE_FAILED,
                    )
                    return@withLock
                }
                val stillReferenced = updated.tracks.asSequence()
                    .flatMap { it.clips.asSequence() }
                    .any { it.mediaUri == removedClip.mediaUri }
                if (!stillReferenced) mediaStorage.deleteClipFile(removedClip.mediaUri)
                _uiState.value = latest.copy(
                    project = updated,
                    previewPaths = resolvePreviewPaths(updated),
                    error = null,
                )
            }
        }
    }

    private inline fun updateLoaded(transform: (EditorUiState.Loaded) -> EditorUiState.Loaded) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = transform(current)
    }

    private fun resolvePreviewPaths(project: Project): List<String> {
        val videoTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO } ?: return emptyList()
        return videoTrack.clips.mapNotNull { clip ->
            mediaStorage.resolve(clip.mediaUri)?.takeIf { it.isFile }?.absolutePath
        }
    }

    private fun mapImportError(reason: Reason): EditorError = when (reason) {
        Reason.NOT_A_VIDEO -> EditorError.IMPORT_NOT_A_VIDEO
        Reason.NO_SPACE -> EditorError.IMPORT_NO_SPACE
        Reason.UNREADABLE, Reason.FAILED -> EditorError.IMPORT_FAILED
    }

    companion object {
        fun provideFactory(
            projectId: String,
            repository: ProjectRepository,
            videoImporter: VideoImporter,
            mediaStorage: ProjectMediaStorage,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditorViewModel(projectId, repository, videoImporter, mediaStorage) as T
        }
    }
}
