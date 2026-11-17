package com.projectmediapro.app.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.export.ExportPlanner
import com.projectmediapro.app.data.export.ExportSaver
import com.projectmediapro.app.data.export.VideoExportException
import com.projectmediapro.app.data.export.VideoExporter
import com.projectmediapro.app.data.export.videoClips
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Non-text error categories for the Editor screen, mapped to string resources in the UI layer.
 */
enum class EditorError {
    IMPORT_FAILED,
    IMPORT_NOT_A_VIDEO,
    IMPORT_NO_SPACE,
    SAVE_FAILED,
    EXPORT_FAILED,
    EXPORT_UNSUPPORTED,
    EXPORT_NO_SPACE,
    EXPORT_SAVE_FAILED,
    EXPORT_NOTHING,
}

/**
 * Progress of exporting the project to a video file.
 */
sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val percent: Int) : ExportUiState
    data class Done(val savedToGallery: Boolean) : ExportUiState
}

/**
 * UI state for the Editor screen.
 */
sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data object Error : EditorUiState
    data class Loaded(
        val project: Project,
        val previewPaths: List<String> = emptyList(),
        val isImporting: Boolean = false,
        val pendingRemoveClipId: String? = null,
        val export: ExportUiState = ExportUiState.Idle,
        val error: EditorError? = null,
    ) : EditorUiState
}

/**
 * User events emitted from the Editor screen.
 */
sealed interface EditorEvent {
    data class VideoPicked(val sourceUri: String?) : EditorEvent
    data class RequestRemoveClip(val clipId: String) : EditorEvent
    data object ConfirmRemoveClip : EditorEvent
    data object CancelRemoveClip : EditorEvent
    data class StartExport(val destinationUri: String? = null) : EditorEvent
    data object CancelExport : EditorEvent
    data object DismissExportDone : EditorEvent
}

/**
 * Unidirectional ViewModel for loading a [Project], importing and removing clips, and exporting the result.
 */
class EditorViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val videoImporter: VideoImporter,
    private val mediaStorage: ProjectMediaStorage,
    private val videoExporter: VideoExporter? = null,
    private val exportSaver: ExportSaver? = null,
    private val cacheSpaceProvider: () -> Long = { Long.MAX_VALUE },
    private val tempDirectory: File = File(System.getProperty("java.io.tmpdir") ?: ".", "exports"),
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val changeMutex = Mutex()
    private val exportMutex = Mutex()
    private var exportJob: Job? = null

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val project = repository.getProject(projectId)
                if (project != null) {
                    EditorUiState.Loaded(
                        project = project,
                        previewPaths = resolvePreviewPaths(project),
                    )
                } else {
                    EditorUiState.NotFound
                }
            } catch (_: IOException) {
                EditorUiState.Error
            }
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.VideoPicked -> handleVideoPicked(event.sourceUri)
            is EditorEvent.RequestRemoveClip -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.export is ExportUiState.Running) return
                _uiState.value = current.copy(pendingRemoveClipId = event.clipId)
            }
            EditorEvent.CancelRemoveClip -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                _uiState.value = current.copy(pendingRemoveClipId = null)
            }
            EditorEvent.ConfirmRemoveClip -> handleConfirmRemoveClip()
            is EditorEvent.StartExport -> handleStartExport(event.destinationUri)
            EditorEvent.CancelExport -> handleCancelExport()
            EditorEvent.DismissExportDone -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.export is ExportUiState.Done) {
                    _uiState.value = current.copy(export = ExportUiState.Idle)
                }
            }
        }
    }

    private fun handleVideoPicked(sourceUri: String?) {
        if (sourceUri == null) return
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isImporting || initial.export is ExportUiState.Running) return

        _uiState.value = initial.copy(isImporting = true, error = null)

        viewModelScope.launch {
            val imported = try {
                videoImporter.importVideo(projectId, sourceUri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VideoImportException) {
                finishImportWithError(mapImportError(failure.reason))
                return@launch
            } catch (_: Exception) {
                finishImportWithError(EditorError.IMPORT_FAILED)
                return@launch
            }

            var saved = false
            try {
                changeMutex.withLock {
                    val beforeSave = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    val previousProject = beforeSave.project
                    val clip = Clip(
                        id = idProvider(),
                        mediaUri = imported.relativePath,
                        startTimeMs = 0L,
                        durationMs = imported.durationMs,
                        trimStartMs = 0L,
                        trimEndMs = imported.durationMs,
                    )
                    val updatedProject = ProjectTimeline.appendClip(
                        project = previousProject,
                        clip = clip,
                        newTrackId = idProvider(),
                    )
                    try {
                        repository.saveProject(updatedProject)
                        saved = true
                        _uiState.value = beforeSave.copy(
                            project = updatedProject,
                            previewPaths = resolvePreviewPaths(updatedProject),
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
                if (!saved) {
                    mediaStorage.deleteClipFile(imported.relativePath)
                }
                throw cancelled
            }
        }
    }

    private fun handleConfirmRemoveClip() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.export is ExportUiState.Running) return
        val targetClipId = current.pendingRemoveClipId ?: return
        _uiState.value = current.copy(pendingRemoveClipId = null, error = null)

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                val previousProject = latest.project
                val removedClip = previousProject.tracks
                    .asSequence()
                    .flatMap { it.clips.asSequence() }
                    .firstOrNull { it.id == targetClipId }
                    ?: return@withLock

                val updatedProject = ProjectTimeline.removeClip(previousProject, targetClipId)
                try {
                    repository.saveProject(updatedProject)
                } catch (_: IOException) {
                    _uiState.value = latest.copy(
                        project = previousProject,
                        previewPaths = resolvePreviewPaths(previousProject),
                        error = EditorError.SAVE_FAILED,
                    )
                    return@withLock
                }

                val stillReferenced = updatedProject.tracks
                    .asSequence()
                    .flatMap { it.clips.asSequence() }
                    .any { it.mediaUri == removedClip.mediaUri }
                if (!stillReferenced) {
                    mediaStorage.deleteClipFile(removedClip.mediaUri)
                }

                _uiState.value = latest.copy(
                    project = updatedProject,
                    previewPaths = resolvePreviewPaths(updatedProject),
                    error = null,
                )
            }
        }
    }

    private fun handleStartExport(destinationUri: String?) {
        val exporter = videoExporter
        val saver = exportSaver
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        val hasClips = initial.project.videoClips().isNotEmpty()
        if (!hasClips || initial.isImporting || initial.export is ExportUiState.Running) return
        if (exportJob?.isActive == true) return
        if (exporter == null || saver == null) {
            _uiState.value = initial.copy(error = EditorError.EXPORT_FAILED)
            return
        }

        val plan = try {
            ExportPlanner.plan(initial.project, mediaStorage::resolve)
        } catch (_: IllegalStateException) {
            _uiState.value = initial.copy(error = EditorError.EXPORT_NOTHING)
            return
        }
        if (cacheSpaceProvider() < ExportPlanner.estimateExportBytes(plan.totalDurationMs)) {
            _uiState.value = initial.copy(error = EditorError.EXPORT_NO_SPACE)
            return
        }

        _uiState.value = initial.copy(
            export = ExportUiState.Running(0),
            pendingRemoveClipId = null,
            error = null,
        )
        val projectTitle = initial.project.title
        val tempFile = File(tempDirectory, "$projectId.mp4")
        exportJob = viewModelScope.launch {
            exportMutex.withLock {
                try {
                    tempDirectory.mkdirs()
                    if (tempFile.exists()) tempFile.delete()
                    exporter.export(plan, tempFile) { percent -> updateExportProgress(percent) }
                    try {
                        if (destinationUri != null) {
                            saver.saveToUri(tempFile, destinationUri)
                        } else {
                            saver.saveToGallery(tempFile, projectTitle)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        finishExport(ExportUiState.Idle, EditorError.EXPORT_SAVE_FAILED)
                        return@withLock
                    }
                    finishExport(ExportUiState.Done(savedToGallery = destinationUri == null), null)
                } catch (cancelled: CancellationException) {
                    finishExport(ExportUiState.Idle, null)
                    throw cancelled
                } catch (failure: VideoExportException) {
                    finishExport(ExportUiState.Idle, mapExportError(failure.reason))
                } catch (_: Exception) {
                    finishExport(ExportUiState.Idle, EditorError.EXPORT_FAILED)
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    private fun handleCancelExport() {
        exportJob?.cancel()
        exportJob = null
        File(tempDirectory, "$projectId.mp4").delete()
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.export is ExportUiState.Running) {
            _uiState.value = current.copy(export = ExportUiState.Idle, error = null)
        }
    }

    private fun finishImportWithError(error: EditorError) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = current.copy(isImporting = false, error = error)
    }

    private fun updateExportProgress(percent: Int) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.export is ExportUiState.Running) {
            _uiState.value = current.copy(export = ExportUiState.Running(percent.coerceIn(0, 100)))
        }
    }

    private fun finishExport(export: ExportUiState, error: EditorError?) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = current.copy(export = export, error = error)
    }

    override fun onCleared() {
        exportJob?.cancel()
        File(tempDirectory, "$projectId.mp4").delete()
        super.onCleared()
    }

    private fun resolvePreviewPaths(project: Project): List<String> =
        project.videoClips().mapNotNull { clip ->
            val file = mediaStorage.resolve(clip.mediaUri)
            if (file != null && file.isFile) file.absolutePath else null
        }

    private fun mapImportError(reason: Reason): EditorError = when (reason) {
        Reason.NOT_A_VIDEO -> EditorError.IMPORT_NOT_A_VIDEO
        Reason.NO_SPACE -> EditorError.IMPORT_NO_SPACE
        Reason.UNREADABLE, Reason.FAILED -> EditorError.IMPORT_FAILED
    }

    private fun mapExportError(reason: VideoExportException.Reason): EditorError = when (reason) {
        VideoExportException.Reason.UNSUPPORTED -> EditorError.EXPORT_UNSUPPORTED
        VideoExportException.Reason.NO_SPACE -> EditorError.EXPORT_NO_SPACE
        VideoExportException.Reason.FAILED -> EditorError.EXPORT_FAILED
    }

    companion object {
        fun provideFactory(
            projectId: String,
            repository: ProjectRepository,
            videoImporter: VideoImporter,
            mediaStorage: ProjectMediaStorage,
            videoExporter: VideoExporter? = null,
            exportSaver: ExportSaver? = null,
            cacheSpaceProvider: () -> Long = { Long.MAX_VALUE },
            tempDirectory: File = File(System.getProperty("java.io.tmpdir") ?: ".", "exports"),
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(
                    projectId = projectId,
                    repository = repository,
                    videoImporter = videoImporter,
                    mediaStorage = mediaStorage,
                    videoExporter = videoExporter,
                    exportSaver = exportSaver,
                    cacheSpaceProvider = cacheSpaceProvider,
                    tempDirectory = tempDirectory,
                ) as T
            }
        }
    }
}
