package com.projectmediapro.app.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.TrackType

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
                CenteredEditorStatus(
                    modifier = Modifier.padding(innerPadding),
                ) {
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
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clips = state.project.tracks
        .firstOrNull { it.type == TrackType.VIDEO }
        ?.clips
        .orEmpty()

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
                .background(Color.Black),
        ) {
            if (clips.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            } else {
                PreviewPlayer(
                    previewPaths = state.previewPaths,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
        Button(
            onClick = onPickVideo,
            enabled = !state.isImporting,
            shape = MediaProShapes.Small,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
        ) {
            if (state.isImporting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                Text(text = stringResource(R.string.editor_adding_video))
            } else {
                Text(text = stringResource(R.string.editor_add_video))
            }
        }

        if (state.error != null) {
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Text(
                text = stringResource(editorErrorStringRes(state.error)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (clips.isNotEmpty()) {
            ClipList(
                clips = clips,
                onRequestRemoveClip = { clipId ->
                    onEvent(EditorEvent.RequestRemoveClip(clipId))
                },
            )
        }
        Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
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
