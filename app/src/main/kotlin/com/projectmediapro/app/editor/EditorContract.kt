package com.projectmediapro.app.editor

import com.projectmediapro.core.model.Project

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
 * Active clip editing dialog on the Editor screen.
 */
sealed interface EditorDialog {
    data class Trim(val clipId: String) : EditorDialog
    data class Split(val clipId: String) : EditorDialog
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
        val previewClips: List<PreviewClip> = emptyList(),
        val selectedClipId: String? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val activeDialog: EditorDialog? = null,
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
    data class SelectClip(val clipId: String) : EditorEvent
    data object OpenTrim : EditorEvent
    data object OpenSplit : EditorEvent
    data object DismissDialog : EditorEvent
    data class ApplyTrim(val startMs: Long, val endMs: Long) : EditorEvent
    data class ApplySplit(val offsetMs: Long) : EditorEvent
    data class MoveSelected(val delta: Int) : EditorEvent
    data object Undo : EditorEvent
    data object Redo : EditorEvent
    data class RequestRemoveClip(val clipId: String) : EditorEvent
    data object ConfirmRemoveClip : EditorEvent
    data object CancelRemoveClip : EditorEvent
    data class StartExport(val destinationUri: String? = null) : EditorEvent
    data object CancelExport : EditorEvent
    data object DismissExportDone : EditorEvent
}
