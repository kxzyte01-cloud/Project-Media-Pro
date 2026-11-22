package com.projectmediapro.app.editor

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.data.export.ExportGeometry
import com.projectmediapro.app.data.export.sanitizeExportName
import com.projectmediapro.app.data.media.SubtitleFileIo
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasBackground
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.overlayClips
import com.projectmediapro.core.model.textClips
import com.projectmediapro.core.model.videoClips
import com.projectmediapro.feature.captions.CaptionDocument
import com.projectmediapro.feature.captions.CaptionPreviewOverlay
import com.projectmediapro.feature.captions.CaptionSheetCallbacks
import com.projectmediapro.feature.captions.CaptionTime
import com.projectmediapro.feature.captions.CaptionsEditorSheet
import com.projectmediapro.feature.captions.SrtCaptionCodec
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    uiState: EditorUiState,
    onEvent: (EditorEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        onEvent(EditorEvent.VideoPicked(uri?.toString()))
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        onEvent(EditorEvent.AudioPicked(uri?.toString()))
    }

    val overlayPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        onEvent(EditorEvent.OverlayPicked(uri?.toString()))
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        if (uri != null) {
            onEvent(EditorEvent.StartExport(destinationUri = uri.toString()))
        }
    }

    val topBarTitle = when (uiState) {
        is EditorUiState.Loaded -> uiState.project.title
        EditorUiState.Loading -> stringResource(R.string.editor_loading_title)
        EditorUiState.NotFound -> stringResource(R.string.editor_not_found_title)
        EditorUiState.Error -> stringResource(R.string.editor_error_title)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaProTopBar(
                title = topBarTitle,
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                },
                onNavigationClick = onNavigateBack,
            )
        },
    ) { innerPadding ->
        when (uiState) {
            EditorUiState.Loading -> {
                CenteredEditorStatus(modifier = Modifier.padding(innerPadding)) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.editor_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MediaProSpacing.Small),
                    )
                }
            }
            EditorUiState.NotFound -> {
                CenteredEditorStatus(modifier = Modifier.padding(innerPadding)) {
                    Text(
                        text = stringResource(R.string.editor_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            EditorUiState.Error -> {
                CenteredEditorStatus(modifier = Modifier.padding(innerPadding)) {
                    Text(
                        text = stringResource(R.string.editor_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is EditorUiState.Loaded -> {
                LoadedEditorContent(
                    state = uiState,
                    onPickVideo = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                    onPickAudio = {
                        audioPickerLauncher.launch("audio/*")
                    },
                    onPickOverlay = {
                        overlayPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onConfirmExportQuality = { quality ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            onEvent(EditorEvent.StartExport(quality = quality))
                        } else {
                            onEvent(EditorEvent.SelectExportQuality(quality))
                            createDocumentLauncher.launch("${sanitizeExportName(uiState.project.title)}.mp4")
                        }
                    },
                    onEvent = onEvent,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun LoadedEditorContent(
    state: EditorUiState.Loaded,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickOverlay: () -> Unit,
    onConfirmExportQuality: (ExportQuality) -> Unit,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val srtText = SubtitleFileIo.readSrtText(context, uri)
                onEvent(EditorEvent.ImportSrtCaptions(srtText))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                onEvent(EditorEvent.ReportCaptionError)
            } catch (_: SecurityException) {
                onEvent(EditorEvent.ReportCaptionError)
            }
        }
    }
    val exportSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip"),
    ) { uri ->
        val doc = state.captionDocument ?: return@rememberLauncherForActivityResult
        if (uri == null || doc.lines.isEmpty()) return@rememberLauncherForActivityResult
        val srt = SrtCaptionCodec.exportSrt(doc)
        if (srt.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                SubtitleFileIo.writeSrtText(context, uri, srt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                onEvent(EditorEvent.ReportCaptionError)
            } catch (_: SecurityException) {
                onEvent(EditorEvent.ReportCaptionError)
            }
        }
    }
    var seekRequest by remember(state.project.id) { mutableStateOf<PreviewSeekRequest?>(null) }
    var seekRequestCounter by remember(state.project.id) { mutableLongStateOf(0L) }
    var timelinePositionMs by remember(state.project.id) { mutableLongStateOf(0L) }
    val clips = state.project.videoClips()
    val audioClips = state.project.audioClips()
    val overlayClips = state.project.overlayClips()
    val textClips = state.project.textClips()
    val videoDurationMs = ProjectTimeline.videoDurationMs(state.project)
    val editsEnabled = !state.isBusy
    val selectedLocation = state.project.findClipLocation(state.selectedClipId)
    val previewAspectRatio = ExportGeometry.previewAspectRatio(state.project.canvasAspect)
    val previewBackground = canvasBackgroundColor(state.project.canvasBackground)
    val emptyPreviewTextColor = if (state.project.canvasBackground == CanvasBackground.WHITE) {
        Color.Black.copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.78f)
    }
    AudioPreviewPlayer(
        audioPreview = state.audioPreview,
        onPlaybackEnded = { onEvent(EditorEvent.StopAudioPreview) },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MediaProSpacing.Medium),
    ) {
        Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
        EditorPreviewStage(
            videoClips = clips,
            previewClips = state.previewClips,
            previewAudioClips = state.previewAudioClips,
            overlayClips = overlayClips,
            overlayFilePaths = state.overlayFilePaths,
            textClips = textClips,
            captionDocument = state.captionDocument,
            timelinePositionMs = timelinePositionMs,
            onTimelinePositionChanged = { timelinePositionMs = it },
            seekRequest = seekRequest,
            previewAspectRatio = previewAspectRatio,
            previewBackground = previewBackground,
            emptyPreviewTextColor = emptyPreviewTextColor,
        )

        if (clips.isNotEmpty() || audioClips.isNotEmpty() || overlayClips.isNotEmpty() ||
            textClips.isNotEmpty() || state.canUndo || state.canRedo
        ) {
            TimelineStrip(
                clips = clips,
                audioClips = audioClips,
                overlayClips = overlayClips,
                textClips = textClips,
                clipFilePaths = state.clipFilePaths,
                timelinePositionMs = timelinePositionMs,
                totalDurationMs = state.project.durationMs,
                selectedClipId = state.selectedClipId,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                isEnabled = editsEnabled,
                onSelectClip = { clipId -> onEvent(EditorEvent.SelectClip(clipId)) },
                onSeekTimeline = { targetMs ->
                    seekRequestCounter += 1L
                    seekRequest = PreviewSeekRequest(
                        timelinePositionMs = targetMs.coerceAtLeast(0L),
                        requestId = seekRequestCounter,
                    )
                },
                onTrimClip = { _, startMs, endMs ->
                    onEvent(EditorEvent.ApplyTrim(startMs = startMs, endMs = endMs))
                },
                onUndo = { onEvent(EditorEvent.Undo) },
                onRedo = { onEvent(EditorEvent.Redo) },
            )
            if (selectedLocation != null) {
                val selectedClip = selectedLocation.clip
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                ClipTools(
                    isEnabled = editsEnabled,
                    canTrim = !selectedLocation.isFreeTimed && ProjectTimeline.canTrimClip(selectedClip),
                    canSplit = selectedLocation.isVideo && ProjectTimeline.canSplitClip(selectedClip),
                    canMoveEarlier = selectedLocation.canMoveEarlier,
                    canMoveLater = selectedLocation.canMoveLater,
                    isAudioClip = selectedLocation.isAudio,
                    isOverlayClip = selectedLocation.isOverlay,
                    isTextClip = selectedLocation.isText,
                    hasActiveEffects = selectedLocation.isVideo &&
                        selectedClip.visualEffects?.isIdentity == false,
                    hasActiveSpeed = !selectedLocation.isFreeTimed &&
                        ProjectTimeline.effectiveSpeed(selectedClip) != Clip.DEFAULT_PLAYBACK_SPEED,
                    hasActiveTransform = selectedLocation.isVideo && selectedClip.hasActiveTransform,
                    hasActiveCrop = selectedLocation.isVideo && selectedClip.hasActiveCrop,
                    canFreezeFrame = selectedLocation.isVideo && !selectedClip.isStillImage,
                    canAdjustSpeed = !selectedLocation.isFreeTimed && !selectedClip.isStillImage,
                    canAdjustVolume = selectedLocation.isAudio || (selectedLocation.isVideo && !selectedClip.isStillImage),
                    hasAdjustedVolume = (selectedLocation.isVideo || selectedLocation.isAudio) &&
                        selectedClip.volume < 1.0f,
                    canEditOverlayTiming = videoDurationMs >= ProjectTimeline.MIN_CLIP_DURATION_MS,
                    isAudioPlaying = state.audioPreview?.clipId == selectedClip.id,
                    onTrimClick = { onEvent(EditorEvent.OpenTrim) },
                    onSplitClick = { onEvent(EditorEvent.OpenSplit) },
                    onSpeedClick = { onEvent(EditorEvent.OpenSpeed) },
                    onTransformClick = { onEvent(EditorEvent.OpenTransform) },
                    onCropClick = { onEvent(EditorEvent.OpenCrop) },
                    onFreezeFrameClick = { onEvent(EditorEvent.OpenFreezeFrame) },
                    onDuplicateClick = { onEvent(EditorEvent.DuplicateSelected) },
                    onEffectsClick = { onEvent(EditorEvent.OpenClipEffects) },
                    onVolumeClick = { onEvent(EditorEvent.OpenVolume) },
                    onOverlayPositionClick = { onEvent(EditorEvent.OpenOverlayPosition) },
                    onOverlayTimingClick = { onEvent(EditorEvent.OpenOverlayTiming) },
                    onEditTextClick = { onEvent(EditorEvent.OpenEditText) },
                    onTextPositionClick = { onEvent(EditorEvent.OpenTextPosition) },
                    onTextTimingClick = { onEvent(EditorEvent.OpenTextTiming) },
                    onMoveEarlierClick = { onEvent(EditorEvent.MoveSelected(-1)) },
                    onMoveLaterClick = { onEvent(EditorEvent.MoveSelected(1)) },
                    onTogglePlayClick = { onEvent(EditorEvent.ToggleAudioPreview) },
                    onRemoveClick = { onEvent(EditorEvent.RequestRemoveClip(selectedClip.id)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onPickVideo,
                enabled = editsEnabled,
                shape = MediaProShapes.Small,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
            ) {
                if (state.importingTrack == TrackType.VIDEO) {
                    CircularProgressIndicator(
                        strokeWidth = MediaProSpacing.BorderSelected,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                    Text(
                        text = stringResource(R.string.editor_adding_video),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.editor_add_video),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            EditorOutlinedButton(
                label = if (state.isImportingAudio) {
                    stringResource(R.string.editor_adding_audio)
                } else {
                    stringResource(R.string.editor_add_audio)
                },
                enabled = editsEnabled,
                onClick = onPickAudio,
                modifier = Modifier.weight(1f),
                leadingContent = if (state.isImportingAudio) {
                    {
                        CircularProgressIndicator(
                            strokeWidth = MediaProSpacing.BorderSelected,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                    }
                } else {
                    null
                },
            )
        }

        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier.fillMaxWidth(),
        ) {
            EditorOutlinedButton(
                label = if (state.isImportingOverlay) {
                    stringResource(R.string.editor_adding_overlay)
                } else {
                    stringResource(R.string.editor_add_overlay)
                },
                enabled = editsEnabled,
                onClick = onPickOverlay,
                modifier = Modifier.weight(1f),
                leadingContent = if (state.isImportingOverlay) {
                    {
                        CircularProgressIndicator(
                            strokeWidth = MediaProSpacing.BorderSelected,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                    }
                } else {
                    null
                },
            )
            EditorOutlinedButton(
                label = stringResource(R.string.editor_add_text),
                enabled = editsEnabled,
                onClick = { onEvent(EditorEvent.OpenAddText) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier.fillMaxWidth(),
        ) {
            EditorOutlinedButton(
                label = stringResource(R.string.editor_canvas_button),
                enabled = editsEnabled,
                onClick = { onEvent(EditorEvent.OpenCanvas) },
                modifier = Modifier.weight(1f),
            )
            EditorOutlinedButton(
                label = stringResource(R.string.editor_captions_button),
                enabled = editsEnabled,
                onClick = { onEvent(EditorEvent.OpenCaptions) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        ExportSection(
            exportState = state.export,
            isExportEnabled = clips.isNotEmpty() && editsEnabled,
            onExportClick = { onEvent(EditorEvent.OpenExportQuality) },
            onCancelExport = { onEvent(EditorEvent.CancelExport) },
            onDismissDone = { onEvent(EditorEvent.DismissExportDone) },
        )

        if (state.error != null) {
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Surface(
                shape = MediaProShapes.Small,
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(
                    MediaProSpacing.BorderThin,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = stringResource(editorErrorStringRes(state.error)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(
                        horizontal = MediaProSpacing.MediumSmall,
                        vertical = MediaProSpacing.Small,
                    ),
                )
            }
        }

        if (clips.isNotEmpty()) {
            ClipList(
                clips = clips,
                selectedClipId = state.selectedClipId,
                isRemoveEnabled = editsEnabled,
                onSelectClip = { clipId -> onEvent(EditorEvent.SelectClip(clipId)) },
                onRequestRemoveClip = { clipId ->
                    onEvent(EditorEvent.RequestRemoveClip(clipId))
                },
            )
        }
        Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
    }

    when (val dialog = state.activeDialog) {
        is EditorDialog.Trim -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && !location.isFreeTimed) {
                TrimDialog(
                    clip = location.clip,
                    onConfirm = { startMs, endMs ->
                        onEvent(EditorEvent.ApplyTrim(startMs = startMs, endMs = endMs))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.Split -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isVideo) {
                SplitDialog(
                    clip = location.clip,
                    onConfirm = { offsetMs ->
                        onEvent(EditorEvent.ApplySplit(offsetMs = offsetMs))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.Speed -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && !location.isFreeTimed) {
                ClipSpeedDialog(
                    clip = location.clip,
                    onConfirm = { speed ->
                        onEvent(EditorEvent.ApplySpeed(speed = speed))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.Volume -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && (location.isAudio || location.isVideo)) {
                VolumeDialog(
                    clip = location.clip,
                    onConfirm = { volume ->
                        onEvent(EditorEvent.ApplyVolume(volume = volume))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.Transform -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isVideo) {
                ClipTransformDialog(
                    clip = location.clip,
                    onConfirm = { rotationDegrees, flipHorizontal, flipVertical ->
                        onEvent(
                            EditorEvent.ApplyTransform(
                                rotationDegrees = rotationDegrees,
                                flipHorizontal = flipHorizontal,
                                flipVertical = flipVertical,
                            ),
                        )
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.Crop -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isVideo) {
                ClipCropDialog(
                    clip = location.clip,
                    onConfirm = { crop ->
                        onEvent(EditorEvent.ApplyCrop(crop = crop))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.FreezeFrame -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isVideo && !location.clip.isStillImage) {
                FreezeFrameDialog(
                    clip = location.clip,
                    onConfirm = { sourceTimeMs, freezeDurationMs ->
                        onEvent(
                            EditorEvent.ApplyFreezeFrame(
                                sourceTimeMs = sourceTimeMs,
                                durationMs = freezeDurationMs,
                            ),
                        )
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.ClipEffects -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isVideo) {
                ClipEffectsDialog(
                    clip = location.clip,
                    onConfirm = { effects ->
                        onEvent(EditorEvent.ApplyClipEffects(effects))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.OverlayPosition -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isOverlay) {
                OverlayPositionDialog(
                    clip = location.clip,
                    canvasAspect = state.project.canvasAspect,
                    canvasBackground = state.project.canvasBackground,
                    onConfirm = { centerX, centerY, widthFraction ->
                        onEvent(
                            EditorEvent.ApplyOverlayPosition(
                                centerX = centerX,
                                centerY = centerY,
                                widthFraction = widthFraction,
                            ),
                        )
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.OverlayTiming -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isOverlay &&
                videoDurationMs >= ProjectTimeline.MIN_CLIP_DURATION_MS
            ) {
                OverlayTimingDialog(
                    clip = location.clip,
                    videoDurationMs = videoDurationMs,
                    onConfirm = { startTimeMs, durationMs ->
                        onEvent(
                            EditorEvent.ApplyOverlayTiming(
                                startTimeMs = startTimeMs,
                                durationMs = durationMs,
                            ),
                        )
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.TextEdit -> {
            val existingClip = dialog.clipId?.let { state.project.findClipLocation(it) }
                ?.takeIf { it.isText }
                ?.clip
            TextStyleDialog(
                clipId = existingClip?.id,
                initialText = existingClip?.textContent.orEmpty(),
                initialStyle = existingClip?.textStyle ?: TextOverlayStyle(),
                canvasBackground = state.project.canvasBackground,
                isEditing = existingClip != null,
                onConfirm = { text, style ->
                    onEvent(EditorEvent.ApplyText(text = text, style = style))
                },
                onDismiss = { onEvent(EditorEvent.DismissDialog) },
            )
        }
        is EditorDialog.TextPosition -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isText) {
                OverlayPositionDialog(
                    clip = location.clip,
                    canvasAspect = state.project.canvasAspect,
                    canvasBackground = state.project.canvasBackground,
                    titleRes = R.string.editor_text_position_title,
                    isText = true,
                    onConfirm = { centerX, centerY, widthFraction ->
                        onEvent(
                            EditorEvent.ApplyTextPosition(
                                centerX = centerX,
                                centerY = centerY,
                                widthFraction = widthFraction,
                            ),
                        )
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.TextTiming -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isText &&
                videoDurationMs >= ProjectTimeline.MIN_CLIP_DURATION_MS
            ) {
                OverlayTimingDialog(
                    clip = location.clip,
                    videoDurationMs = videoDurationMs,
                    titleRes = R.string.editor_text_timing_title,
                    onConfirm = { startTimeMs, durationMs ->
                        onEvent(
                            EditorEvent.ApplyTextTiming(
                                startTimeMs = startTimeMs,
                                durationMs = durationMs,
                            ),
                        )
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        EditorDialog.Captions -> {
            CaptionsEditorSheet(
                document = state.captionDocument,
                isTranscribing = state.isTranscribingCaptions,
                hasClips = clips.isNotEmpty() || audioClips.isNotEmpty(),
                callbacks = CaptionSheetCallbacks(
                    onGenerateCaptions = { onEvent(EditorEvent.GenerateCaptions()) },
                    onSelectPreset = { presetId -> onEvent(EditorEvent.SelectCaptionPreset(presetId)) },
                    onSelectTemplate = { templateId -> onEvent(EditorEvent.SelectCaptionTemplate(templateId)) },
                    onUpdateOffsetUs = { offsetUs -> onEvent(EditorEvent.UpdateCaptionOffsetUs(offsetUs)) },
                    onUpdateStyle = { style -> onEvent(EditorEvent.UpdateCaptionStyle(style)) },
                    onUpdateWordText = { wordId, newText ->
                        onEvent(EditorEvent.UpdateCaptionWordText(wordId, newText))
                    },
                    onSeekToUs = { positionUs ->
                        seekRequestCounter += 1L
                        seekRequest = PreviewSeekRequest(
                            timelinePositionMs = (positionUs / CaptionTime.MICROS_PER_MS).coerceAtLeast(0L),
                            requestId = seekRequestCounter,
                        )
                    },
                    onClearCaptions = { onEvent(EditorEvent.ClearCaptions) },
                    onGenerateWithScript = { script -> onEvent(EditorEvent.GenerateCaptions(script)) },
                    onImportSrt = { importSrtLauncher.launch(arrayOf("*/*")) },
                    onExportSrt = {
                        exportSrtLauncher.launch("${sanitizeExportName(state.project.title)}.srt")
                    },
                ),
                onDismiss = { onEvent(EditorEvent.DismissDialog) },
            )
        }
        EditorDialog.Canvas -> {
            CanvasDialog(
                initialAspect = state.project.canvasAspect,
                initialBackground = state.project.canvasBackground,
                onConfirm = { aspect, background ->
                    onEvent(EditorEvent.ApplyCanvas(aspect = aspect, background = background))
                },
                onDismiss = { onEvent(EditorEvent.DismissDialog) },
            )
        }
        EditorDialog.Quality -> {
            ExportQualityDialog(
                initialQuality = state.project.exportQuality,
                onConfirm = onConfirmExportQuality,
                onDismiss = { onEvent(EditorEvent.DismissDialog) },
            )
        }
        null -> Unit
    }

    val pendingLocation = state.project.findClipLocation(state.pendingRemoveClipId)
    if (pendingLocation != null) {
        RemoveClipDialog(
            clipNumber = pendingLocation.clipNumber,
            onConfirm = { onEvent(EditorEvent.ConfirmRemoveClip) },
            onDismiss = { onEvent(EditorEvent.CancelRemoveClip) },
        )
    }
}

@Composable
private fun CenteredEditorStatus(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MediaProSpacing.Medium),
    ) {
        content()
    }
}

@Composable
private fun EditorPreviewStage(
    videoClips: List<Clip>,
    previewClips: List<PreviewClip>,
    previewAudioClips: List<PreviewClip>,
    overlayClips: List<Clip>,
    overlayFilePaths: Map<String, String>,
    textClips: List<Clip>,
    captionDocument: CaptionDocument?,
    timelinePositionMs: Long,
    onTimelinePositionChanged: (Long) -> Unit,
    seekRequest: PreviewSeekRequest?,
    previewAspectRatio: Float,
    previewBackground: Color,
    emptyPreviewTextColor: Color,
) {
    val hasVideoClips = videoClips.isNotEmpty()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .heightIn(max = 360.dp)
                .aspectRatio(previewAspectRatio)
                .clip(MediaProShapes.Medium)
                .background(previewBackground)
                .border(
                    width = MediaProSpacing.BorderThin,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MediaProShapes.Medium,
                ),
        ) {
            if (!hasVideoClips && textClips.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = emptyPreviewTextColor,
                )
            } else {
                if (hasVideoClips) {
                    PreviewPlayer(
                        previewClips = previewClips,
                        audioClips = previewAudioClips,
                        backgroundColor = previewBackground,
                        seekRequest = seekRequest,
                        onTimelinePositionChanged = onTimelinePositionChanged,
                        modifier = Modifier.fillMaxSize(),
                    )
                    PreviewEffectsOverlay(
                        videoClips = videoClips,
                        timelinePositionMs = timelinePositionMs,
                        modifier = Modifier.fillMaxSize(),
                    )
                    OverlayPreviewLayer(
                        overlays = overlayClips,
                        overlayFilePaths = overlayFilePaths,
                        timelinePositionMs = timelinePositionMs,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                TextPreviewLayer(
                    textClips = textClips,
                    timelinePositionMs = timelinePositionMs,
                    modifier = Modifier.fillMaxSize(),
                )
                CaptionPreviewOverlay(
                    document = captionDocument,
                    currentPositionUs = timelinePositionMs * CaptionTime.MICROS_PER_MS,
                    videoWidth = 0,
                    videoHeight = 0,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}
