package com.projectmediapro.app.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.MediaProTopBar

@Composable
fun EditorScreen(
    uiState: EditorUiState,
    onImportVideo: (Uri) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onImportVideo) },
    )
    val latestVideo = uiState.project?.tracks
        ?.firstOrNull { track -> track.kind.name == "VIDEO" }
        ?.clips?.lastOrNull()?.sourceUri

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaProTopBar(
                title = uiState.project?.title ?: stringResource(R.string.editor_title_fallback),
                navigationIcon = { Text(stringResource(R.string.cd_navigate_back)) },
                onNavigationClick = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.project == null -> Text(stringResource(R.string.editor_project_missing))
                latestVideo != null -> VideoPreview(latestVideo)
                else -> Text(
                    stringResource(R.string.editor_preview_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Button(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                enabled = !uiState.isLoading && uiState.project != null && !uiState.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isImporting) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.editor_import_video))
                }
            }
            uiState.project?.let { project ->
                Text(
                    stringResource(
                        R.string.editor_timeline_clip_count,
                        project.tracks.sumOf { it.clips.size },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (uiState.error) {
                EditorError.PROJECT_NOT_FOUND -> Text(
                    stringResource(R.string.editor_project_missing),
                    color = MaterialTheme.colorScheme.error,
                )
                EditorError.IMPORT_FAILED -> Text(
                    stringResource(R.string.editor_import_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun VideoPreview(sourceUri: String) {
    val context = LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }

    LaunchedEffect(player, sourceUri) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(sourceUri)))
        player.prepare()
        player.playWhenReady = false
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
        update = { it.player = player },
        modifier = Modifier.fillMaxWidth().height(220.dp),
    )
}
