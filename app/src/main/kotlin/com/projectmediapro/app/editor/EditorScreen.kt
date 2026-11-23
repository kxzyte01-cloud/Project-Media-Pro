package com.projectmediapro.app.editor

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.data.export.ExportGeometry
import com.projectmediapro.app.data.export.ExportPlanner
import com.projectmediapro.app.data.export.sanitizeExportName
import com.projectmediapro.app.data.media.CustomFontStorage
import java.io.File
import com.projectmediapro.app.data.media.SubtitleFileIo
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasBackground
import androidx.compose.ui.graphics.graphicsLayer
import com.projectmediapro.core.model.BeatEffectEvaluator
import com.projectmediapro.core.model.BeatMarker
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TextOverlayStyle
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

    val replaceVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        onEvent(EditorEvent.ReplaceClipPicked(uri?.toString()))
    }

    val replaceAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        onEvent(EditorEvent.ReplaceClipPicked(uri?.toString()))
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        if (uri != null) {
            onEvent(EditorEvent.StartExport(destinationUri = uri.toString()))
        }
    }

    val loadedState = uiState as? EditorUiState.Loaded
    val topBarTitle = when (uiState) {
        is EditorUiState.Loaded -> uiState.project.title
        EditorUiState.Loading -> stringResource(R.string.editor_loading_title)
        EditorUiState.NotFound -> stringResource(R.string.editor_not_found_title)
        EditorUiState.Error -> stringResource(R.string.editor_error_title)
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StudioTopBar(
                title = topBarTitle,
                loadedState = loadedState,
                onNavigateBack = onNavigateBack,
                onUndo = { onEvent(EditorEvent.Undo) },
                onRedo = { onEvent(EditorEvent.Redo) },
                onSaveTemplateClick = { onEvent(EditorEvent.OpenSaveAsTemplate) },
                onApplyStyleClick = { onEvent(EditorEvent.OpenApplyStyle) },
                onExportClick = { onEvent(EditorEvent.OpenExportQuality) },
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState) {
                    EditorUiState.Loading -> {
                        CenteredEditorStatus {
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
                        CenteredEditorStatus {
                            Text(
                                text = stringResource(R.string.editor_not_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    EditorUiState.Error -> {
                        CenteredEditorStatus {
                            Text(
                                text = stringResource(R.string.editor_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    is EditorUiState.Loaded -> {
                        LoadedStudioWorkspace(
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
                            onReplaceVideo = {
                                replaceVideoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                )
                            },
                            onReplaceAudio = {
                                replaceAudioPickerLauncher.launch("audio/*")
                            },
                            onConfirmExportQuality = { quality, frameRate, bitrate, opticalFlow ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    onEvent(
                                        EditorEvent.StartExport(
                                            quality = quality,
                                            frameRate = frameRate,
                                            bitrate = bitrate,
                                            opticalFlowEnabled = opticalFlow,
                                        ),
                                    )
                                } else {
                                    onEvent(
                                        EditorEvent.SelectExportQuality(
                                            quality = quality,
                                            frameRate = frameRate,
                                            bitrate = bitrate,
                                            opticalFlowEnabled = opticalFlow,
                                        ),
                                    )
                                    createDocumentLauncher.launch("${sanitizeExportName(uiState.project.title)}.mp4")
                                }
                            },
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    title: String,
    loadedState: EditorUiState.Loaded?,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveTemplateClick: () -> Unit,
    onApplyStyleClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    var showTemplatesMenu by remember { mutableStateOf(false) }
    val editsEnabled = loadedState != null && !loadedState.isBusy
    val hasVideos = loadedState != null && loadedState.project.videoClips().isNotEmpty()
    val isExporting = loadedState?.export is ExportUiState.Running

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = MediaProSpacing.ExtraSmall),
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (loadedState != null) {
                    IconButton(
                        onClick = onUndo,
                        enabled = editsEnabled && loadedState.canUndo,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_undo),
                            contentDescription = stringResource(R.string.cd_undo),
                        )
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = editsEnabled && loadedState.canRedo,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_redo),
                            contentDescription = stringResource(R.string.cd_redo),
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showTemplatesMenu = true },
                            enabled = hasVideos && editsEnabled,
                        ) {
                            if (loadedState.isSavingTemplate) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_save_template),
                                    contentDescription = stringResource(R.string.cd_templates_menu),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showTemplatesMenu,
                            onDismissRequest = { showTemplatesMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.editor_menu_save_as_template)) },
                                onClick = {
                                    showTemplatesMenu = false
                                    onSaveTemplateClick()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.editor_menu_apply_style)) },
                                onClick = {
                                    showTemplatesMenu = false
                                    onApplyStyleClick()
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(MediaProSpacing.ExtraSmall))
                    Button(
                        onClick = onExportClick,
                        enabled = hasVideos && editsEnabled,
                        shape = MediaProShapes.Small,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .padding(end = MediaProSpacing.Small),
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.editor_studio_export_short),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                thickness = MediaProSpacing.BorderThin,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun LoadedStudioWorkspace(
    state: EditorUiState.Loaded,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickOverlay: () -> Unit,
    onReplaceVideo: () -> Unit,
    onReplaceAudio: () -> Unit,
    onConfirmExportQuality: (ExportQuality, ExportFrameRate, ExportBitrate, Boolean) -> Unit,
    onEvent: (EditorEvent) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSnapshotPositionMs by remember { mutableStateOf<Long?>(null) }
    val createSnapshotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val positionMs = pendingSnapshotPositionMs
        pendingSnapshotPositionMs = null
        if (uri != null && positionMs != null) {
            onEvent(
                EditorEvent.SaveFrameSnapshot(
                    timelinePositionMs = positionMs,
                    destinationUri = uri.toString(),
                ),
            )
        }
    }

    val importFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            scope.launch {
                val storage = CustomFontStorage(File(appContext.filesDir, "custom_fonts"))
                val importedFile = try {
                    storage.importFontFromUri(appContext, uri.toString())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                val currentDoc = state.captionDocument
                if (importedFile != null && currentDoc != null) {
                    onEvent(
                        EditorEvent.UpdateCaptionStyle(
                            currentDoc.style.copy(customFontPath = importedFile.absolutePath),
                        ),
                    )
                } else {
                    onEvent(EditorEvent.ReportCaptionError)
                }
            }
        }
    }

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

    Column(modifier = Modifier.fillMaxSize()) {
        StudioStatusBanners(
            exportState = state.export,
            savedTemplateName = state.savedTemplateName,
            appliedStyleTemplateName = state.appliedStyleTemplateName,
            error = state.error,
            onCancelExport = { onEvent(EditorEvent.CancelExport) },
            onDismissDone = { onEvent(EditorEvent.DismissExportDone) },
            onDismissTemplateSaved = { onEvent(EditorEvent.DismissTemplateSavedBanner) },
            onDismissStyleApplied = { onEvent(EditorEvent.DismissStyleAppliedBanner) },
        )

        // Top Zone: Fixed Cinema Preview Stage with direct overlay/text drag & pinch
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(0.47f)
                .fillMaxWidth()
                .background(Color(0xFF0A0C10))
                .clickable(
                    enabled = selectedLocation != null && editsEnabled,
                    onClick = { onEvent(EditorEvent.ClearSelection) },
                )
                .padding(horizontal = MediaProSpacing.Medium, vertical = MediaProSpacing.Small),
        ) {
            EditorPreviewStage(
                videoClips = clips,
                previewClips = state.previewClips,
                previewAudioClips = state.previewAudioClips,
                overlayClips = overlayClips,
                overlayFilePaths = state.overlayFilePaths,
                textClips = textClips,
                captionDocument = state.captionDocument,
                beatMarkers = state.project.beatMarkers,
                soundReactiveEnabled = state.project.soundReactiveEnabled,
                selectedClipId = state.selectedClipId,
                isInteractive = editsEnabled,
                timelinePositionMs = timelinePositionMs,
                onTimelinePositionChanged = { timelinePositionMs = it },
                seekRequest = seekRequest,
                previewAspectRatio = previewAspectRatio,
                previewBackground = previewBackground,
                emptyPreviewTextColor = emptyPreviewTextColor,
                onSelectClip = { clipId -> onEvent(EditorEvent.SelectClip(clipId)) },
                onCommitOverlayTransform = { clipId, cx, cy, wf ->
                    onEvent(EditorEvent.SelectClip(clipId))
                    onEvent(EditorEvent.ApplyOverlayPosition(centerX = cx, centerY = cy, widthFraction = wf))
                },
                onCommitTextTransform = { clipId, cx, cy, wf ->
                    onEvent(EditorEvent.SelectClip(clipId))
                    onEvent(EditorEvent.ApplyTextPosition(centerX = cx, centerY = cy, widthFraction = wf))
                },
            )
        }

        HorizontalDivider(
            thickness = MediaProSpacing.BorderThin,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Middle Zone: Scrollable Multi-Track Timeline Workspace
        Box(
            modifier = Modifier
                .weight(0.53f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (clips.isNotEmpty() || audioClips.isNotEmpty() || overlayClips.isNotEmpty() ||
                textClips.isNotEmpty() || state.canUndo || state.canRedo
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MediaProSpacing.Small, vertical = MediaProSpacing.ExtraSmall),
                ) {
                    TimelineStrip(
                        clips = clips,
                        audioClips = audioClips,
                        overlayClips = overlayClips,
                        textClips = textClips,
                        beatMarkers = state.project.beatMarkers,
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
                        onResizeTimedClip = { clipId, startTimeMs, durationMs ->
                            onEvent(
                                EditorEvent.ResizeTimedClip(
                                    clipId = clipId,
                                    startTimeMs = startTimeMs,
                                    durationMs = durationMs,
                                ),
                            )
                        },
                        onUndo = { onEvent(EditorEvent.Undo) },
                        onRedo = { onEvent(EditorEvent.Redo) },
                        showHistoryControls = false,
                    )
                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(MediaProSpacing.Medium),
                ) {
                    Text(
                        text = stringResource(R.string.editor_studio_empty_timeline_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Bottom Zone: Fixed CapCut / VN Context-Aware Tool Dock
        StudioBottomDock(
            state = state,
            selectedLocation = selectedLocation,
            videoDurationMs = videoDurationMs,
            timelinePositionMs = timelinePositionMs,
            onPickVideo = onPickVideo,
            onPickAudio = onPickAudio,
            onPickOverlay = onPickOverlay,
            onReplaceVideo = onReplaceVideo,
            onReplaceAudio = onReplaceAudio,
            onSaveSnapshot = { positionMs ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    onEvent(EditorEvent.SaveFrameSnapshot(timelinePositionMs = positionMs))
                } else {
                    pendingSnapshotPositionMs = positionMs
                    createSnapshotLauncher.launch("${sanitizeExportName(state.project.title)}_frame.png")
                }
            },
            onEvent = onEvent,
        )
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
                    onConfirm = { speed, speedRamp ->
                        onEvent(EditorEvent.ApplySpeed(speed = speed, speedRamp = speedRamp))
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
                    isAudioTrack = location.isAudio,
                    onConfirm = { volume, audioBoost, fadeInMs, fadeOutMs, audioEnhance ->
                        onEvent(
                            EditorEvent.ApplyVolume(
                                volume = volume,
                                audioBoost = audioBoost,
                                fadeInMs = fadeInMs,
                                fadeOutMs = fadeOutMs,
                                audioEnhance = audioEnhance,
                            ),
                        )
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
                    onConfirm = { centerX, centerY, widthFraction, style ->
                        onEvent(
                            EditorEvent.ApplyOverlayPosition(
                                centerX = centerX,
                                centerY = centerY,
                                widthFraction = widthFraction,
                                style = style,
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
                    onConfirm = { centerX, centerY, widthFraction, _ ->
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
        is EditorDialog.Keyframes -> {
            val location = state.project.findClipLocation(dialog.clipId)
            if (location != null && (location.isOverlay || location.isText)) {
                KeyframeEditorDialog(
                    clip = location.clip,
                    onConfirm = { keyframes ->
                        onEvent(EditorEvent.ApplyKeyframes(keyframes = keyframes))
                    },
                    onDismiss = { onEvent(EditorEvent.DismissDialog) },
                )
            }
        }
        EditorDialog.Beats -> {
            BeatSyncSheet(
                beatMarkers = state.project.beatMarkers,
                soundReactiveEnabled = state.project.soundReactiveEnabled,
                timelinePositionMs = timelinePositionMs,
                hasClips = clips.isNotEmpty() || audioClips.isNotEmpty(),
                isDetectingBeats = state.isDetectingBeats,
                onToggleSoundReactive = { enabled -> onEvent(EditorEvent.ToggleSoundReactive(enabled)) },
                onAutoDetectBeats = { onEvent(EditorEvent.AutoDetectBeats) },
                onTapBeat = { effect ->
                    onEvent(EditorEvent.TapBeatMarker(timeMs = timelinePositionMs, effect = effect))
                },
                onAutoCutOnBeats = { onEvent(EditorEvent.AutoCutOnBeats) },
                onSeekToBeat = { targetMs ->
                    seekRequestCounter += 1L
                    seekRequest = PreviewSeekRequest(
                        timelinePositionMs = targetMs.coerceAtLeast(0L),
                        requestId = seekRequestCounter,
                    )
                },
                onRemoveBeat = { markerId -> onEvent(EditorEvent.RemoveBeatMarker(markerId)) },
                onClearBeats = { onEvent(EditorEvent.ClearBeatMarkers) },
                onDismiss = { onEvent(EditorEvent.DismissDialog) },
            )
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
                    onImportCustomFont = { importFontLauncher.launch(arrayOf("*/*")) },
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
                initialFrameRate = state.project.exportFrameRate,
                initialBitrate = state.project.exportBitrate,
                initialOpticalFlowEnabled = state.project.opticalFlowEnabled,
                onConfirm = onConfirmExportQuality,
                onDismiss = { onEvent(EditorEvent.DismissDialog) },
            )
        }
        EditorDialog.SaveAsTemplate -> {
            SaveAsTemplateDialog(
                project = state.project,
                onConfirm = { name, description, replaceableClipIds ->
                    onEvent(
                        EditorEvent.ConfirmSaveAsTemplate(
                            name = name,
                            description = description,
                            replaceableClipIds = replaceableClipIds,
                        ),
                    )
                },
                onDismiss = { onEvent(EditorEvent.DismissDialog) },
            )
        }
        EditorDialog.ApplyStyle -> {
            ApplyStyleDialog(
                templates = state.availableTemplates,
                onSelectTemplate = { templateId ->
                    onEvent(EditorEvent.ApplyTemplateStyle(templateId))
                },
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
private fun StudioStatusBanners(
    exportState: ExportUiState,
    savedTemplateName: String?,
    appliedStyleTemplateName: String?,
    error: EditorError?,
    onCancelExport: () -> Unit,
    onDismissDone: () -> Unit,
    onDismissTemplateSaved: () -> Unit,
    onDismissStyleApplied: () -> Unit,
) {
    if (appliedStyleTemplateName != null) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MediaProSpacing.Medium, vertical = MediaProSpacing.ExtraSmall),
            ) {
                Text(
                    text = stringResource(R.string.editor_apply_style_applied, appliedStyleTemplateName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissStyleApplied) {
                    Text(text = stringResource(R.string.editor_save_template_dismiss))
                }
            }
        }
    }
    if (savedTemplateName != null) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MediaProSpacing.Medium, vertical = MediaProSpacing.ExtraSmall),
            ) {
                Text(
                    text = stringResource(R.string.editor_save_template_saved, savedTemplateName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissTemplateSaved) {
                    Text(text = stringResource(R.string.editor_save_template_dismiss))
                }
            }
        }
    }
    when (exportState) {
        is ExportUiState.Running -> {
            val percent = exportState.percent.coerceIn(0, 100)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MediaProSpacing.Medium, vertical = MediaProSpacing.ExtraSmall),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.editor_exporting, percent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancelExport) {
                            Text(text = stringResource(R.string.home_cancel))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        is ExportUiState.Done -> {
            val doneMessage = if (exportState.savedToGallery) {
                stringResource(R.string.editor_export_done_gallery)
            } else {
                stringResource(R.string.editor_export_done_file)
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MediaProSpacing.Medium, vertical = MediaProSpacing.ExtraSmall),
                ) {
                    Text(
                        text = doneMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissDone) {
                        Text(text = stringResource(R.string.editor_export_dismiss))
                    }
                }
            }
        }
        ExportUiState.Idle -> Unit
    }

    if (error != null) {
        Surface(
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
                text = stringResource(editorErrorStringRes(error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(
                    horizontal = MediaProSpacing.Medium,
                    vertical = MediaProSpacing.Small,
                ),
            )
        }
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
    beatMarkers: List<BeatMarker> = emptyList(),
    soundReactiveEnabled: Boolean = false,
    selectedClipId: String?,
    isInteractive: Boolean,
    timelinePositionMs: Long,
    onTimelinePositionChanged: (Long) -> Unit,
    seekRequest: PreviewSeekRequest?,
    previewAspectRatio: Float,
    previewBackground: Color,
    emptyPreviewTextColor: Color,
    onSelectClip: (String) -> Unit,
    onCommitOverlayTransform: (clipId: String, centerX: Float, centerY: Float, widthFraction: Float) -> Unit,
    onCommitTextTransform: (clipId: String, centerX: Float, centerY: Float, widthFraction: Float) -> Unit,
) {
    val hasVideoClips = videoClips.isNotEmpty()

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        val safeAspect = previewAspectRatio.coerceIn(0.25f, 4f)
        val availWidth = maxWidth
        val availHeight = maxHeight
        val stageModifier = if (availHeight.value > 0f && (availWidth.value / availHeight.value) > safeAspect) {
            Modifier.size(width = availHeight * safeAspect, height = availHeight)
        } else {
            Modifier.size(width = availWidth, height = availWidth / safeAspect)
        }
        val beatSample = BeatEffectEvaluator.evaluate(beatMarkers, timelinePositionMs, soundReactiveEnabled)
        val activeClipAtTime = TimelineClock.activeClipAt(videoClips, timelinePositionMs)
        val clipTransitionTransform = activeClipAtTime?.clip?.visualEffects?.transitionTransformAt(
            positionInClipMs = activeClipAtTime.positionInClipMs,
            clipDurationMs = activeClipAtTime.clipDurationMs,
        )
        val combinedScale = beatSample.scale * (clipTransitionTransform?.scale ?: 1f)
        val combinedTx = beatSample.translationXFraction + (clipTransitionTransform?.translationXFraction ?: 0f)
        val combinedTy = beatSample.translationYFraction + (clipTransitionTransform?.translationYFraction ?: 0f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = stageModifier
                .clip(MediaProShapes.Medium)
                .background(previewBackground)
                .border(
                    width = MediaProSpacing.BorderThin,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MediaProShapes.Medium,
                ),
        ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = combinedScale
                    scaleY = combinedScale
                    translationX = size.width * combinedTx
                    translationY = size.height * combinedTy
                },
        ) {
        if (!hasVideoClips && textClips.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = emptyPreviewTextColor,
            )
        } else {
            if (hasVideoClips) {
                val speechWindowsMs = remember(captionDocument) {
                    ExportPlanner.extractSpeechWindows(captionDocument)
                }
                PreviewPlayer(
                    previewClips = previewClips,
                    audioClips = previewAudioClips,
                    speechWindowsMs = speechWindowsMs,
                    backgroundColor = previewBackground,
                    seekRequest = seekRequest,
                    onTimelinePositionChanged = onTimelinePositionChanged,
                    modifier = Modifier.fillMaxSize(),
                )
                PreviewEffectsOverlay(
                    videoClips = videoClips,
                    timelinePositionMs = timelinePositionMs,
                    beatMarkers = beatMarkers,
                    soundReactiveEnabled = soundReactiveEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
                OverlayPreviewLayer(
                    overlays = overlayClips,
                    overlayFilePaths = overlayFilePaths,
                    timelinePositionMs = timelinePositionMs,
                    selectedClipId = selectedClipId,
                    isInteractive = isInteractive,
                    onSelectOverlay = onSelectClip,
                    onCommitOverlayTransform = onCommitOverlayTransform,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            TextPreviewLayer(
                textClips = textClips,
                timelinePositionMs = timelinePositionMs,
                selectedClipId = selectedClipId,
                isInteractive = isInteractive,
                onSelectText = onSelectClip,
                onCommitTextTransform = onCommitTextTransform,
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
    
        }}
    }
}
