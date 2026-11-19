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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.data.export.ExportGeometry
import com.projectmediapro.app.data.export.sanitizeExportName
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasBackground
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.videoClips

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
    onConfirmExportQuality: (ExportQuality) -> Unit,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clips = state.project.videoClips()
    val audioClips = state.project.audioClips()
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
                if (clips.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = emptyPreviewTextColor,
                    )
                } else {
                    PreviewPlayer(
                        previewClips = state.previewClips,
                        backgroundColor = previewBackground,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (clips.isNotEmpty() || audioClips.isNotEmpty() || state.canUndo || state.canRedo) {
            TimelineStrip(
                clips = clips,
                audioClips = audioClips,
                selectedClipId = state.selectedClipId,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                isEnabled = editsEnabled,
                onSelectClip = { clipId -> onEvent(EditorEvent.SelectClip(clipId)) },
                onUndo = { onEvent(EditorEvent.Undo) },
                onRedo = { onEvent(EditorEvent.Redo) },
            )
            if (selectedLocation != null) {
                val selectedClip = selectedLocation.clip
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                ClipTools(
                    isEnabled = editsEnabled,
                    canTrim = ProjectTimeline.canTrimClip(selectedClip),
                    canSplit = !selectedLocation.isAudio && ProjectTimeline.canSplitClip(selectedClip),
                    canMoveEarlier = selectedLocation.canMoveEarlier,
                    canMoveLater = selectedLocation.canMoveLater,
                    isAudioClip = selectedLocation.isAudio,
                    isAudioPlaying = state.audioPreview?.clipId == selectedClip.id,
                    onTrimClick = { onEvent(EditorEvent.OpenTrim) },
                    onSplitClick = { onEvent(EditorEvent.OpenSplit) },
                    onVolumeClick = { onEvent(EditorEvent.OpenVolume) },
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
                if (state.isImporting && !state.isImportingAudio) {
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
        EditorOutlinedButton(
            label = stringResource(R.string.editor_canvas_button),
            enabled = editsEnabled,
            onClick = { onEvent(EditorEvent.OpenCanvas) },
            modifier = Modifier.fillMaxWidth(),
        )

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
            val clip = state.project.findClipLocation(dialog.clipId)?.clip
            if (clip != null) {
                TrimDialog(
                    clip = clip,
                    onConfirm = { startMs, endMs ->
                        onEvent(EditorEvent.ApplyTrim(startMs = startMs, endMs = endMs))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.Split -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && !location.isAudio) {
                SplitDialog(
                    clip = location.clip,
                    onConfirm = { offsetMs ->
                        onEvent(EditorEvent.ApplySplit(offsetMs = offsetMs))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        is EditorDialog.Volume -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && location.isAudio) {
                VolumeDialog(
                    clip = location.clip,
                    onConfirm = { volume ->
                        onEvent(EditorEvent.ApplyVolume(volume = volume))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
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
