package com.projectmediapro.app.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.export.ExportPlanner
import com.projectmediapro.app.data.export.ExportSaver
import com.projectmediapro.app.data.export.VideoExportException
import com.projectmediapro.app.data.export.VideoExporter
import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.UndoRedoManager
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.videoClips
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

class EditorViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val videoImporter: VideoImporter,
    private val mediaStorage: ProjectMediaStorage,
    private val audioImporter: AudioImporter = FailingAudioImporter,
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
    private val history = UndoRedoManager(Project(id = projectId, title = ""))

    init {
        viewModelScope.launch {
            changeMutex.withLock {
                _uiState.value = try {
                    val project = repository.getProject(projectId)
                    if (project != null) {
                        history.reset(project)
                        EditorUiState.Loaded(
                            project = project,
                            previewClips = resolvePreviewClips(project, mediaStorage),
                            selectedClipId = project.videoClips().firstOrNull()?.id
                                ?: project.audioClips().firstOrNull()?.id,
                        )
                    } else {
                        EditorUiState.NotFound
                    }
                } catch (_: IOException) {
                    EditorUiState.Error
                }
            }
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.VideoPicked -> handleMediaPicked(event.sourceUri, TrackType.VIDEO)
            is EditorEvent.AudioPicked -> handleMediaPicked(event.sourceUri, TrackType.AUDIO)
            is EditorEvent.SelectClip -> handleSelectClip(event.clipId)
            EditorEvent.OpenTrim -> openSelectedClipDialog(
                predicate = { ProjectTimeline.canTrimClip(it.clip) },
                dialogFactory = EditorDialog::Trim,
            )
            EditorEvent.OpenSplit -> openSelectedClipDialog(
                predicate = { !it.isAudio && ProjectTimeline.canSplitClip(it.clip) },
                dialogFactory = EditorDialog::Split,
            )
            EditorEvent.OpenVolume -> openSelectedClipDialog(
                predicate = { it.isAudio },
                dialogFactory = EditorDialog::Volume,
            )
            EditorEvent.OpenCanvas -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy) return
                _uiState.value = current.copy(activeDialog = EditorDialog.Canvas, audioPreview = null)
            }
            EditorEvent.OpenExportQuality -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy || current.project.videoClips().isEmpty()) return
                _uiState.value = current.copy(activeDialog = EditorDialog.Quality, audioPreview = null)
            }
            EditorEvent.DismissDialog -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                _uiState.value = current.copy(activeDialog = null)
            }
            is EditorEvent.ApplyTrim -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Trim)?.clipId
                    ?: current.selectedClipId
                    ?: return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.trimClip(project, targetId, event.startMs, event.endMs)
                }
            }
            is EditorEvent.ApplySplit -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Split)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isAudio != false) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.splitClip(project, targetId, event.offsetMs, idProvider())
                }
            }
            is EditorEvent.ApplyVolume -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Volume)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isAudio != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.setClipVolume(project, targetId, event.volume)
                }
            }
            is EditorEvent.ApplyCanvas -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                commitUndoableEdit(current.selectedClipId) { project ->
                    project.copy(
                        canvasAspect = event.aspect,
                        canvasBackground = event.background,
                    )
                }
            }
            is EditorEvent.SelectExportQuality -> handleSelectExportQuality(event.quality)
            is EditorEvent.MoveSelected -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = current.selectedClipId ?: return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.moveClip(project, targetId, event.delta)
                }
            }
            EditorEvent.ToggleAudioPreview -> handleToggleAudioPreview()
            EditorEvent.StopAudioPreview -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.audioPreview != null) {
                    _uiState.value = current.copy(audioPreview = null)
                }
            }
            EditorEvent.Undo -> stepHistory(
                step = UndoRedoManager<Project>::undo,
                rollback = UndoRedoManager<Project>::redo,
            )
            EditorEvent.Redo -> stepHistory(
                step = UndoRedoManager<Project>::redo,
                rollback = UndoRedoManager<Project>::undo,
            )
            is EditorEvent.RequestRemoveClip -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy || current.project.findClipLocation(event.clipId) == null) return
                _uiState.value = current.copy(
                    pendingRemoveClipId = event.clipId,
                    activeDialog = null,
                    audioPreview = null,
                )
            }
            EditorEvent.CancelRemoveClip -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                _uiState.value = current.copy(pendingRemoveClipId = null)
            }
            EditorEvent.ConfirmRemoveClip -> handleConfirmRemoveClip()
            is EditorEvent.StartExport -> handleStartExport(event.destinationUri, event.quality)
            EditorEvent.CancelExport -> handleCancelExport()
            EditorEvent.DismissExportDone -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.export is ExportUiState.Done) {
                    _uiState.value = current.copy(export = ExportUiState.Idle)
                }
            }
        }
    }

    private fun handleSelectClip(clipId: String) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy || current.project.findClipLocation(clipId) == null) return
        val updatedAudioPreview = if (current.selectedClipId == clipId) current.audioPreview else null
        _uiState.value = current.copy(
            selectedClipId = clipId,
            activeDialog = null,
            audioPreview = updatedAudioPreview,
        )
    }

    private fun handleToggleAudioPreview() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val location = current.project.findClipLocation(current.selectedClipId) ?: return
        if (!location.isAudio) return
        val nextPreview = if (current.audioPreview?.clipId == location.clip.id) {
            null
        } else {
            resolveAudioPreview(location.clip, mediaStorage)
        }
        _uiState.value = current.copy(audioPreview = nextPreview)
    }

    private fun openSelectedClipDialog(
        predicate: (ClipLocation) -> Boolean,
        dialogFactory: (String) -> EditorDialog,
    ) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val location = current.project.findClipLocation(current.selectedClipId) ?: return
        if (!predicate(location)) return
        _uiState.value = current.copy(
            activeDialog = dialogFactory(location.clip.id),
            audioPreview = null,
        )
    }

    private fun handleSelectExportQuality(quality: ExportQuality) {
        persistProjectTransition(
            preferredSelectedClipId = null,
            prepareTarget = { previous ->
                if (previous.exportQuality == quality) null else previous.copy(exportQuality = quality)
            },
        )
    }

    private fun commitUndoableEdit(
        preferredSelectedClipId: String?,
        transform: (Project) -> Project,
    ) {
        persistProjectTransition(
            preferredSelectedClipId = preferredSelectedClipId,
            prepareTarget = { previous ->
                val updated = transform(previous)
                if (updated == previous) null else updated
            },
            onSaveSuccess = { updated -> history.pushState(updated) },
        )
    }

    private fun stepHistory(
        step: (UndoRedoManager<Project>) -> Project?,
        rollback: (UndoRedoManager<Project>) -> Project?,
    ) {
        persistProjectTransition(
            preferredSelectedClipId = null,
            prepareTarget = { previous ->
                step(history)?.copy(exportQuality = previous.exportQuality)
            },
            onSaveFailure = { rollback(history) },
        )
    }

    private fun persistProjectTransition(
        preferredSelectedClipId: String?,
        prepareTarget: (Project) -> Project?,
        onSaveSuccess: (Project) -> Unit = {},
        onSaveFailure: () -> Unit = {},
    ) {
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isBusy) return

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                if (latest.isBusy) return@withLock
                val previousProject = latest.project
                val targetProject = prepareTarget(previousProject)
                if (targetProject == null) {
                    if (latest.activeDialog != null) {
                        _uiState.value = latest.copy(activeDialog = null)
                    }
                    return@withLock
                }

                try {
                    repository.saveProject(targetProject)
                    onSaveSuccess(targetProject)
                    _uiState.value = latest.withSyncedProject(
                        previousProject = previousProject,
                        targetProject = targetProject,
                        preferredSelectedId = preferredSelectedClipId ?: latest.selectedClipId,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                } catch (_: IOException) {
                    onSaveFailure()
                    _uiState.value = latest.withSaveFailedRollback(
                        previousProject = previousProject,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                }
            }
        }
    }

    private fun handleMediaPicked(
        sourceUri: String?,
        trackType: TrackType,
    ) {
        if (sourceUri == null) return
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isBusy) return
        val isAudio = trackType == TrackType.AUDIO

        _uiState.value = initial.copy(
            isImporting = true,
            isImportingAudio = isAudio,
            activeDialog = null,
            audioPreview = null,
            pendingRemoveClipId = null,
            error = null,
        )

        viewModelScope.launch {
            val (relativePath, durationMs) = try {
                if (isAudio) {
                    val imported = audioImporter.importAudio(projectId, sourceUri)
                    imported.relativePath to imported.durationMs
                } else {
                    val imported = videoImporter.importVideo(projectId, sourceUri)
                    imported.relativePath to imported.durationMs
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VideoImportException) {
                finishImportWithError(mapImportError(failure.reason, trackType))
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
                        mediaUri = relativePath,
                        startTimeMs = 0L,
                        durationMs = durationMs,
                        trimStartMs = 0L,
                        trimEndMs = durationMs,
                        sourceDurationMs = durationMs,
                    )
                    val updatedProject = ProjectTimeline.appendClip(
                        project = previousProject,
                        clip = clip,
                        newTrackId = idProvider(),
                        trackType = trackType,
                    )
                    try {
                        repository.saveProject(updatedProject)
                        saved = true
                        history.reset(updatedProject)
                        _uiState.value = beforeSave.withSyncedProject(
                            previousProject = previousProject,
                            targetProject = updatedProject,
                            preferredSelectedId = beforeSave.selectedClipId ?: clip.id,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
                    } catch (_: IOException) {
                        mediaStorage.deleteClipFile(relativePath)
                        _uiState.value = beforeSave.withSaveFailedRollback(
                            previousProject = previousProject,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
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

    private fun handleConfirmRemoveClip() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val targetClipId = current.pendingRemoveClipId ?: return
        _uiState.value = current.copy(pendingRemoveClipId = null, audioPreview = null, error = null)

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                if (latest.isBusy) return@withLock
                val previousProject = latest.project
                val removedClip = previousProject.findClipLocation(targetClipId)?.clip ?: return@withLock

                val updatedProject = ProjectTimeline.removeClip(previousProject, targetClipId)
                try {
                    repository.saveProject(updatedProject)
                } catch (_: IOException) {
                    _uiState.value = latest.withSaveFailedRollback(
                        previousProject = previousProject,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                    return@withLock
                }

                if (!ProjectTimeline.isMediaReferenced(updatedProject, removedClip.mediaUri)) {
                    mediaStorage.deleteClipFile(removedClip.mediaUri)
                }

                history.reset(updatedProject)
                _uiState.value = latest.withSyncedProject(
                    previousProject = previousProject,
                    targetProject = updatedProject,
                    preferredSelectedId = latest.selectedClipId ?: targetClipId,
                    mediaStorage = mediaStorage,
                    history = history,
                )
            }
        }
    }

    private fun handleStartExport(
        destinationUri: String?,
        qualityOverride: ExportQuality?,
    ) {
        val exporter = videoExporter
        val saver = exportSaver
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.project.videoClips().isEmpty() || initial.isBusy) return
        if (exportJob?.isActive == true) return
        if (exporter == null || saver == null) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.EXPORT_FAILED)
            return
        }

        val effectiveProject = if (qualityOverride != null && qualityOverride != initial.project.exportQuality) {
            initial.project.copy(exportQuality = qualityOverride)
        } else {
            initial.project
        }

        val plan = try {
            ExportPlanner.plan(effectiveProject, mediaStorage::resolve)
        } catch (_: IllegalStateException) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.EXPORT_NOTHING)
            return
        }
        if (cacheSpaceProvider() < ExportPlanner.estimateExportBytes(plan.totalDurationMs)) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.EXPORT_NO_SPACE)
            return
        }

        _uiState.value = initial.copy(
            project = effectiveProject,
            export = ExportUiState.Running(0),
            activeDialog = null,
            audioPreview = null,
            pendingRemoveClipId = null,
            error = null,
        )
        val projectTitle = effectiveProject.title
        val tempFile = exportTempFile()
        exportJob = viewModelScope.launch {
            if (effectiveProject != initial.project) {
                changeMutex.withLock {
                    runCatching { repository.saveProject(effectiveProject) }
                }
            }
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
        exportTempFile().delete()
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.export is ExportUiState.Running) {
            _uiState.value = current.copy(export = ExportUiState.Idle, error = null)
        }
    }

    private fun finishImportWithError(error: EditorError) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = current.copy(
            isImporting = false,
            isImportingAudio = false,
            error = error,
        )
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
        exportTempFile().delete()
        super.onCleared()
    }

    private fun exportTempFile(): File = File(tempDirectory, "$projectId.mp4")

    companion object {
        fun provideFactory(
            projectId: String,
            repository: ProjectRepository,
            videoImporter: VideoImporter,
            mediaStorage: ProjectMediaStorage,
            audioImporter: AudioImporter = FailingAudioImporter,
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
                    audioImporter = audioImporter,
                    videoExporter = videoExporter,
                    exportSaver = exportSaver,
                    cacheSpaceProvider = cacheSpaceProvider,
                    tempDirectory = tempDirectory,
                ) as T
            }
        }
    }
}
