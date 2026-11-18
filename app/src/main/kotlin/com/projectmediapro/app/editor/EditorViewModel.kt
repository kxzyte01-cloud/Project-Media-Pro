package com.projectmediapro.app.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.export.ExportPlanner
import com.projectmediapro.app.data.export.ExportSaver
import com.projectmediapro.app.data.export.VideoExportException
import com.projectmediapro.app.data.export.VideoExporter
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.UndoRedoManager
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

/**
 * Unidirectional ViewModel for loading a [Project], importing, editing, and removing clips,
 * managing 50-step undo/redo history, and exporting the result.
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
                            previewClips = resolvePreviewClips(project),
                            selectedClipId = project.videoClips().firstOrNull()?.id,
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
            is EditorEvent.VideoPicked -> handleVideoPicked(event.sourceUri)
            is EditorEvent.SelectClip -> handleSelectClip(event.clipId)
            EditorEvent.OpenTrim -> openSelectedClipDialog(
                predicate = ProjectTimeline::canTrimClip,
                dialogFactory = EditorDialog::Trim,
            )
            EditorEvent.OpenSplit -> openSelectedClipDialog(
                predicate = ProjectTimeline::canSplitClip,
                dialogFactory = EditorDialog::Split,
            )
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
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.splitClip(project, targetId, event.offsetMs, idProvider())
                }
            }
            is EditorEvent.MoveSelected -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = current.selectedClipId ?: return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.moveClip(project, targetId, event.delta)
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
                if (isBusy(current)) return
                if (current.project.videoClips().none { it.id == event.clipId }) return
                _uiState.value = current.copy(
                    pendingRemoveClipId = event.clipId,
                    activeDialog = null,
                )
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

    private fun handleSelectClip(clipId: String) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (isBusy(current)) return
        if (current.project.videoClips().none { it.id == clipId }) return
        _uiState.value = current.copy(selectedClipId = clipId, activeDialog = null)
    }

    private fun openSelectedClipDialog(
        predicate: (Clip) -> Boolean,
        dialogFactory: (String) -> EditorDialog,
    ) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (isBusy(current)) return
        val selectedId = current.selectedClipId ?: return
        val clip = current.project.videoClips().firstOrNull { it.id == selectedId } ?: return
        if (!predicate(clip)) return
        _uiState.value = current.copy(activeDialog = dialogFactory(selectedId))
    }

    private fun commitUndoableEdit(
        preferredSelectedClipId: String,
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
            prepareTarget = { step(history) },
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
        if (isBusy(initial)) return

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                if (isBusy(latest)) return@withLock
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
                    )
                } catch (_: IOException) {
                    onSaveFailure()
                    _uiState.value = latest.withSaveFailedRollback(previousProject)
                }
            }
        }
    }

    private fun handleVideoPicked(sourceUri: String?) {
        if (sourceUri == null) return
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (isBusy(initial)) return

        _uiState.value = initial.copy(
            isImporting = true,
            activeDialog = null,
            pendingRemoveClipId = null,
            error = null,
        )

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
                        sourceDurationMs = imported.durationMs,
                    )
                    val updatedProject = ProjectTimeline.appendClip(
                        project = previousProject,
                        clip = clip,
                        newTrackId = idProvider(),
                    )
                    try {
                        repository.saveProject(updatedProject)
                        saved = true
                        history.reset(updatedProject)
                        _uiState.value = beforeSave.withSyncedProject(
                            previousProject = previousProject,
                            targetProject = updatedProject,
                            preferredSelectedId = beforeSave.selectedClipId,
                        )
                    } catch (_: IOException) {
                        mediaStorage.deleteClipFile(imported.relativePath)
                        _uiState.value = beforeSave.withSaveFailedRollback(previousProject)
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
        if (isBusy(current)) return
        val targetClipId = current.pendingRemoveClipId ?: return
        _uiState.value = current.copy(pendingRemoveClipId = null, error = null)

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                if (isBusy(latest)) return@withLock
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
                    _uiState.value = latest.withSaveFailedRollback(previousProject)
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
                )
            }
        }
    }

    private fun handleStartExport(destinationUri: String?) {
        val exporter = videoExporter
        val saver = exportSaver
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        val hasClips = initial.project.videoClips().isNotEmpty()
        if (!hasClips || isBusy(initial)) return
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
            activeDialog = null,
            pendingRemoveClipId = null,
            error = null,
        )
        val projectTitle = initial.project.title
        val tempFile = exportTempFile()
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
        exportTempFile().delete()
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
        exportTempFile().delete()
        super.onCleared()
    }

    private fun exportTempFile(): File = File(tempDirectory, "$projectId.mp4")

    private fun isBusy(state: EditorUiState.Loaded): Boolean =
        state.isImporting || state.export is ExportUiState.Running

    private fun EditorUiState.Loaded.withSyncedProject(
        previousProject: Project,
        targetProject: Project,
        preferredSelectedId: String?,
    ): EditorUiState.Loaded = copy(
        project = targetProject,
        previewClips = resolvePreviewClips(targetProject),
        selectedClipId = resolveSelection(
            previousClips = previousProject.videoClips(),
            previousSelectedId = preferredSelectedId,
            newClips = targetProject.videoClips(),
        ),
        canUndo = history.canUndo,
        canRedo = history.canRedo,
        activeDialog = null,
        isImporting = false,
        error = null,
    )

    private fun EditorUiState.Loaded.withSaveFailedRollback(
        previousProject: Project,
    ): EditorUiState.Loaded = copy(
        project = previousProject,
        previewClips = resolvePreviewClips(previousProject),
        canUndo = history.canUndo,
        canRedo = history.canRedo,
        activeDialog = null,
        isImporting = false,
        error = EditorError.SAVE_FAILED,
    )

    private fun resolvePreviewClips(project: Project): List<PreviewClip> =
        project.videoClips().mapNotNull { clip ->
            val file = mediaStorage.resolve(clip.mediaUri)
            if (file == null || !file.isFile) return@mapNotNull null
            val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
            PreviewClip(
                path = file.absolutePath,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
            )
        }

    private fun resolveSelection(
        previousClips: List<Clip>,
        previousSelectedId: String?,
        newClips: List<Clip>,
    ): String? {
        if (newClips.isEmpty()) return null
        if (previousSelectedId != null) {
            if (newClips.any { it.id == previousSelectedId }) {
                return previousSelectedId
            }
            val oldIndex = previousClips.indexOfFirst { it.id == previousSelectedId }
            if (oldIndex >= 0) {
                return newClips[oldIndex.coerceAtMost(newClips.lastIndex)].id
            }
        }
        return newClips.first().id
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
