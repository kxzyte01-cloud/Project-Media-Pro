package com.projectmediapro.app.editor

import com.projectmediapro.core.model.BeatEffectKind
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.CanvasBackground
import com.projectmediapro.core.model.ClipCrop
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.OverlayKeyframe
import com.projectmediapro.core.model.OverlayVisualStyle
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.SpeedRampPreset
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.feature.captions.CaptionDocument
import com.projectmediapro.feature.captions.CaptionStyle
import com.projectmediapro.feature.captions.CaptionTemplateId

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
    CAPTIONS_FAILED,
    CAPTIONS_TRUNCATED,
    FREEZE_FRAME_FAILED,
    EXTRACT_AUDIO_FAILED,
    BEATS_DETECT_FAILED,
    SNAPSHOT_FAILED,
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val percent: Int) : ExportUiState
    data class Done(val savedToGallery: Boolean) : ExportUiState
}

sealed interface EditorDialog {
    data class Trim(val clipId: String) : EditorDialog
    data class Split(val clipId: String) : EditorDialog
    data class Speed(val clipId: String) : EditorDialog
    data class Volume(val clipId: String) : EditorDialog
    data class Transform(val clipId: String) : EditorDialog
    data class Crop(val clipId: String) : EditorDialog
    data class FreezeFrame(val clipId: String) : EditorDialog
    data class ClipEffects(val clipId: String) : EditorDialog
    data class OverlayPosition(val clipId: String) : EditorDialog
    data class OverlayTiming(val clipId: String) : EditorDialog
    data class TextEdit(val clipId: String?) : EditorDialog
    data class TextPosition(val clipId: String) : EditorDialog
    data class TextTiming(val clipId: String) : EditorDialog
    data class Keyframes(val clipId: String) : EditorDialog
    data object Captions : EditorDialog
    data object Beats : EditorDialog
    data object Canvas : EditorDialog
    data object Quality : EditorDialog
}

data class AudioPreviewState(
    val clipId: String,
    val path: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val volume: Float = 1.0f,
    val playbackSpeed: Float = Clip.DEFAULT_PLAYBACK_SPEED,
)

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data object NotFound : EditorUiState
    data object Error : EditorUiState
    data class Loaded(
        val project: Project,
        val previewClips: List<PreviewClip> = emptyList(),
        val previewAudioClips: List<PreviewClip> = emptyList(),
        val overlayFilePaths: Map<String, String> = emptyMap(),
        val clipFilePaths: Map<String, String> = emptyMap(),
        val selectedClipId: String? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val activeDialog: EditorDialog? = null,
        val importingTrack: TrackType? = null,
        val audioPreview: AudioPreviewState? = null,
        val pendingRemoveClipId: String? = null,
        val captionDocument: CaptionDocument? = null,
        val isTranscribingCaptions: Boolean = false,
        val isDetectingBeats: Boolean = false,
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
    data object ClearSelection : EditorEvent
    data object OpenTrim : EditorEvent
    data object OpenSplit : EditorEvent
    data object OpenSpeed : EditorEvent
    data object OpenVolume : EditorEvent
    data object OpenTransform : EditorEvent
    data object OpenCrop : EditorEvent
    data object OpenFreezeFrame : EditorEvent
    data object OpenClipEffects : EditorEvent
    data object OpenOverlayPosition : EditorEvent
    data object OpenOverlayTiming : EditorEvent
    data object OpenAddText : EditorEvent
    data object OpenEditText : EditorEvent
    data object OpenTextPosition : EditorEvent
    data object OpenTextTiming : EditorEvent
    data object OpenKeyframes : EditorEvent
    data object OpenCaptions : EditorEvent
    data object OpenBeats : EditorEvent
    data object AutoDetectBeats : EditorEvent
    data class TapBeatMarker(val timeMs: Long, val effect: BeatEffectKind = BeatEffectKind.NONE) : EditorEvent
    data class RemoveBeatMarker(val markerId: String) : EditorEvent
    data object ClearBeatMarkers : EditorEvent
    data object AutoCutOnBeats : EditorEvent
    data class ToggleSoundReactive(val enabled: Boolean) : EditorEvent
    data object OpenCanvas : EditorEvent
    data object OpenExportQuality : EditorEvent
    data object DismissDialog : EditorEvent
    data class ApplyTrim(val startMs: Long, val endMs: Long) : EditorEvent
    data class ApplySplit(val offsetMs: Long) : EditorEvent
    data class ApplySpeed(val speed: Float, val speedRamp: SpeedRampPreset = SpeedRampPreset.NONE) : EditorEvent
    data class ApplyVolume(
        val volume: Float,
        val audioBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
        val fadeInMs: Long = 0L,
        val fadeOutMs: Long = 0L,
    ) : EditorEvent
    data class ApplyTransform(
        val rotationDegrees: Int,
        val flipHorizontal: Boolean,
        val flipVertical: Boolean,
    ) : EditorEvent
    data class ApplyClipEffects(val effects: ClipVisualEffects) : EditorEvent
    data class ApplyCrop(val crop: ClipCrop?) : EditorEvent
    data class ApplyFreezeFrame(val sourceTimeMs: Long, val durationMs: Long) : EditorEvent
    data object ExtractAudioFromSelected : EditorEvent
    data class SaveFrameSnapshot(
        val timelinePositionMs: Long,
        val destinationUri: String? = null,
    ) : EditorEvent
    data object DuplicateSelected : EditorEvent
    data class ReplaceClipPicked(val sourceUri: String?) : EditorEvent
    data class CopyFromHere(val playheadMs: Long) : EditorEvent
    data class CopyTillHere(val playheadMs: Long) : EditorEvent
    data class CopyToHere(val playheadMs: Long) : EditorEvent
    data class ResizeTimedClip(
        val clipId: String,
        val startTimeMs: Long,
        val durationMs: Long,
    ) : EditorEvent
    data class ApplyOverlayPosition(
        val centerX: Float,
        val centerY: Float,
        val widthFraction: Float,
        val style: OverlayVisualStyle? = null,
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
    data class ApplyKeyframes(val keyframes: List<OverlayKeyframe>) : EditorEvent
    data class GenerateCaptions(val providedScript: String? = null) : EditorEvent
    data class SelectCaptionPreset(val presetId: String) : EditorEvent
    data class SelectCaptionTemplate(val templateId: CaptionTemplateId) : EditorEvent
    data class UpdateCaptionOffsetUs(val offsetUs: Long) : EditorEvent
    data class UpdateCaptionStyle(val style: CaptionStyle) : EditorEvent
    data class UpdateCaptionWordText(val wordId: String, val newText: String) : EditorEvent
    data class ImportSrtCaptions(val srtText: String) : EditorEvent
    data object ClearCaptions : EditorEvent
    data object ReportCaptionError : EditorEvent
    data class ApplyCanvas(
        val aspect: CanvasAspect,
        val background: CanvasBackground,
    ) : EditorEvent
    data class SelectExportQuality(
        val quality: ExportQuality,
        val frameRate: ExportFrameRate? = null,
        val bitrate: ExportBitrate? = null,
        val opticalFlowEnabled: Boolean? = null,
    ) : EditorEvent
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
        val frameRate: ExportFrameRate? = null,
        val bitrate: ExportBitrate? = null,
        val opticalFlowEnabled: Boolean? = null,
    ) : EditorEvent
    data object CancelExport : EditorEvent
    data object DismissExportDone : EditorEvent
}
