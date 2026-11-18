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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.projectmediapro.app.data.export.sanitizeExportName
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.ProjectTimeline
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

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        if (uri != null) {
            onEvent(EditorEvent.StartExport(uri.toString()))
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
                    onRequestExport = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            onEvent(EditorEvent.StartExport())
                        } else {
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
    onRequestExport: () -> Unit,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clips = state.project.videoClips()
    val isExporting = state.export is ExportUiState.Running
    val editsEnabled = !state.isImporting && !isExporting
    val selectedIndex = clips.indexOfFirst { it.id == state.selectedClipId }
    val selectedClip = clips.getOrNull(selectedIndex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MediaProSpacing.Medium),
    ) {
        Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MediaProShapes.Medium)
                .background(Color.Black)
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
                    color = Color.White.copy(alpha = 0.78f),
                )
            } else {
                PreviewPlayer(
                    previewClips = state.previewClips,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (clips.isNotEmpty() || state.canUndo || state.canRedo) {
            TimelineStrip(
                clips = clips,
                selectedClipId = state.selectedClipId,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                isEnabled = editsEnabled,
                onSelectClip = { clipId -> onEvent(EditorEvent.SelectClip(clipId)) },
                onUndo = { onEvent(EditorEvent.Undo) },
                onRedo = { onEvent(EditorEvent.Redo) },
            )
            if (selectedClip != null) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                ClipTools(
                    isEnabled = editsEnabled,
                    canTrim = ProjectTimeline.canTrimClip(selectedClip),
                    canSplit = ProjectTimeline.canSplitClip(selectedClip),
                    canMoveEarlier = selectedIndex > 0,
                    canMoveLater = selectedIndex < clips.lastIndex,
                    onTrimClick = { onEvent(EditorEvent.OpenTrim) },
                    onSplitClick = { onEvent(EditorEvent.OpenSplit) },
                    onMoveEarlierClick = { onEvent(EditorEvent.MoveSelected(-1)) },
                    onMoveLaterClick = { onEvent(EditorEvent.MoveSelected(1)) },
                    onRemoveClick = { onEvent(EditorEvent.RequestRemoveClip(selectedClip.id)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
        Button(
            onClick = onPickVideo,
            enabled = editsEnabled,
            shape = MediaProShapes.Small,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
        ) {
            if (state.isImporting) {
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

        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        ExportSection(
            exportState = state.export,
            isExportEnabled = clips.isNotEmpty() && editsEnabled,
            onExportClick = onRequestExport,
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
            val clip = clips.firstOrNull { it.id == dialog.clipId }
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
            val clip = clips.firstOrNull { it.id == dialog.clipId }
            if (clip != null) {
                SplitDialog(
                    clip = clip,
                    onConfirm = { offsetMs ->
                        onEvent(EditorEvent.ApplySplit(offsetMs = offsetMs))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        null -> Unit
    }

    val pendingIndex = clips.indexOfFirst { it.id == state.pendingRemoveClipId }
    if (pendingIndex >= 0) {
        RemoveClipDialog(
            clipNumber = pendingIndex + 1,
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
