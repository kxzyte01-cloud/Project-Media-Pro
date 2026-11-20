package com.projectmediapro.app.editor

import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.CanvasBackground
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.TrackType

enum class EditorError {
    IMPORT_FAILED,
    IMPORT_NOT_A_VIDEO,
    IMPORT_NOT_AUDIO,
    IMPORT_NOT_IMAGE,
    IMPORT_NO_SPACE,
    SAVE_FAILED,
    EXPORT_FAILED,
    EXPORT_UNSUPPORTED,
    EXPORT_NO_SPACE,
    EXPORT_SAVE_FAILED,
    EXPORT_NOTHING,
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val percent: Int) : ExportUiState
    data class Done(val savedToGallery: Boolean) : ExportUiState
}

sealed interface EditorDialog {
    data class Trim(val clipId: String) : EditorDialog
    data class Split(val clipId: String) : EditorDialog
    data class Volume(val clipId: String) : EditorDialog
    data class OverlayPosition(val clipId: String) : EditorDialog
    data class OverlayTiming(val clipId: String) : EditorDialog
    data class TextEdit(val clipId: String?) : EditorDialog
    data class TextPosition(val clipId: String) : EditorDialog
    data class TextTiming(val clipId: String) : EditorDialog
    data object Canvas : EditorDialog
    data object Quality : EditorDialog
}

data class AudioPreviewState(
    val clipId: String,
    val path: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val volume: Float = 1.0f,
)

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data object Error : EditorUiState
    data class Loaded(
        val project: Project,
        val previewClips: List<PreviewClip> = emptyList(),
        val overlayFilePaths: Map<String, String> = emptyMap(),
        val selectedClipId: String? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val activeDialog: EditorDialog? = null,
        val importingTrack: TrackType? = null,
        val audioPreview: AudioPreviewState? = null,
        val pendingRemoveClipId: String? = null,
        val export: ExportUiState = ExportUiState.Idle,
        val error: EditorError? = null,
    ) : EditorUiState {
        val isImporting: Boolean
            get() = importingTrack != null
        val isImportingAudio: Boolean
            get() = importingTrack == TrackType.AUDIO
        val isImportingOverlay: Boolean
            get() = importingTrack == TrackType.OVERLAY
        val isBusy: Boolean
            get() = importingTrack != null || export is ExportUiState.Running
    }
}

sealed interface EditorEvent {
    data class VideoPicked(val sourceUri: String?) : EditorEvent
    data class AudioPicked(val sourceUri: String?) : EditorEvent
    data class OverlayPicked(val sourceUri: String?) : EditorEvent
    data class SelectClip(val clipId: String) : EditorEvent
    data object OpenTrim : EditorEvent
    data object OpenSplit : EditorEvent
    data object OpenVolume : EditorEvent
    data object OpenOverlayPosition : EditorEvent
    data object OpenOverlayTiming : EditorEvent
    data object OpenAddText : EditorEvent
    data object OpenEditText : EditorEvent
    data object OpenTextPosition : EditorEvent
    data object OpenTextTiming : EditorEvent
    data object OpenCanvas : EditorEvent
    data object OpenExportQuality : EditorEvent
    data object DismissDialog : EditorEvent
    data class ApplyTrim(val startMs: Long, val endMs: Long) : EditorEvent
    data class ApplySplit(val offsetMs: Long) : EditorEvent
    data class ApplyVolume(val volume: Float) : EditorEvent
    data class ApplyOverlayPosition(
        val centerX: Float,
        val centerY: Float,
        val widthFraction: Float,
    ) : EditorEvent
    data class ApplyOverlayTiming(
        val startTimeMs: Long,
        val durationMs: Long,
    ) : EditorEvent
    data class ApplyText(
        val text: String,
        val style: TextOverlayStyle,
    ) : EditorEvent
    data class ApplyTextPosition(
        val centerX: Float,
        val centerY: Float,
        val widthFraction: Float,
    ) : EditorEvent
    data class ApplyTextTiming(
        val startTimeMs: Long,
        val durationMs: Long,
    ) : EditorEvent
    data class ApplyCanvas(
        val aspect: CanvasAspect,
        val background: CanvasBackground,
    ) : EditorEvent
    data class SelectExportQuality(val quality: ExportQuality) : EditorEvent
    data class MoveSelected(val delta: Int) : EditorEvent
    data object ToggleAudioPreview : EditorEvent
    data object StopAudioPreview : EditorEvent
    data object Undo : EditorEvent
    data object Redo : EditorEvent
    data class RequestRemoveClip(val clipId: String) : EditorEvent
    data object ConfirmRemoveClip : EditorEvent
    data object CancelRemoveClip : EditorEvent
    data class StartExport(
        val destinationUri: String? = null,
        val quality: ExportQuality? = null,
    ) : EditorEvent
    data object CancelExport : EditorEvent
    data object DismissExportDone : EditorEvent
}
