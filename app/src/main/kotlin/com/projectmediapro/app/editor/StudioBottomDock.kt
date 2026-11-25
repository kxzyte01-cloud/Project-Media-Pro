package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TrackType

@Composable
internal fun StudioBottomDock(
    state: EditorUiState.Loaded,
    selectedLocation: ClipLocation?,
    videoDurationMs: Long,
    timelinePositionMs: Long,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickOverlay: () -> Unit,
    onReplaceVideo: () -> Unit,
    onReplaceAudio: () -> Unit,
    onSaveSnapshot: (Long) -> Unit,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editsEnabled = !state.isBusy

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = MediaProSpacing.BorderThin,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        horizontal = MediaProSpacing.Small,
                        vertical = MediaProSpacing.Small,
                    ),
            ) {
                if (selectedLocation == null) {
                    MainStudioDockItems(
                        state = state,
                        editsEnabled = editsEnabled,
                        onPickVideo = onPickVideo,
                        onPickAudio = onPickAudio,
                        onPickOverlay = onPickOverlay,
                        onEvent = onEvent,
                    )
                } else {
                    SelectedClipDockItems(
                        state = state,
                        location = selectedLocation,
                        videoDurationMs = videoDurationMs,
                        timelinePositionMs = timelinePositionMs,
                        editsEnabled = editsEnabled,
                        onReplaceVideo = onReplaceVideo,
                        onReplaceAudio = onReplaceAudio,
                        onSaveSnapshot = onSaveSnapshot,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainStudioDockItems(
    state: EditorUiState.Loaded,
    editsEnabled: Boolean,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickOverlay: () -> Unit,
    onEvent: (EditorEvent) -> Unit,
) {
    StudioDockTile(
        icon = StudioToolIconKind.VIDEO,
        label = stringResource(R.string.editor_studio_dock_video),
        enabled = editsEnabled,
        isHighlighted = true,
        isLoading = state.importingTrack == TrackType.VIDEO,
        onClick = onPickVideo,
    )
    StudioDockTile(
        icon = StudioToolIconKind.AUDIO,
        label = stringResource(R.string.editor_studio_dock_audio),
        enabled = editsEnabled,
        isLoading = state.isImportingAudio,
        onClick = onPickAudio,
    )
    StudioDockTile(
        icon = StudioToolIconKind.OVERLAY,
        label = stringResource(R.string.editor_studio_dock_overlay),
        enabled = editsEnabled,
        isLoading = state.isImportingOverlay,
        onClick = onPickOverlay,
    )
    StudioDockTile(
        icon = StudioToolIconKind.TEXT,
        label = stringResource(R.string.editor_studio_dock_text),
        enabled = editsEnabled,
        onClick = { onEvent(EditorEvent.OpenAddText) },
    )
    StudioDockTile(
        icon = StudioToolIconKind.CAPTIONS,
        label = stringResource(R.string.editor_studio_dock_captions),
        enabled = editsEnabled,
        isActive = state.captionDocument?.lines?.isNotEmpty() == true,
        onClick = { onEvent(EditorEvent.OpenCaptions) },
    )
    StudioDockTile(
        icon = StudioToolIconKind.BEATS,
        label = stringResource(R.string.editor_studio_dock_beats),
        enabled = editsEnabled,
        isActive = state.project.beatMarkers.isNotEmpty(),
        isLoading = state.isDetectingBeats,
        onClick = { onEvent(EditorEvent.OpenBeats) },
    )
    StudioDockTile(
        icon = StudioToolIconKind.CANVAS,
        label = stringResource(R.string.editor_studio_dock_canvas),
        enabled = editsEnabled,
        onClick = { onEvent(EditorEvent.OpenCanvas) },
    )
}

@Composable
private fun SelectedClipDockItems(
    state: EditorUiState.Loaded,
    location: ClipLocation,
    videoDurationMs: Long,
    timelinePositionMs: Long,
    editsEnabled: Boolean,
    onReplaceVideo: () -> Unit,
    onReplaceAudio: () -> Unit,
    onSaveSnapshot: (Long) -> Unit,
    onEvent: (EditorEvent) -> Unit,
) {
    val clip = location.clip
    val canEditOverlayTiming = videoDurationMs >= ProjectTimeline.MIN_CLIP_DURATION_MS

    Surface(
        shape = MediaProShapes.Small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            MediaProSpacing.BorderThin,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .clip(MediaProShapes.Small)
            .clickable(enabled = editsEnabled) { onEvent(EditorEvent.ClearSelection) },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 56.dp)
                .padding(horizontal = MediaProSpacing.Small, vertical = MediaProSpacing.ExtraSmall),
        ) {
            StudioToolIcon(
                kind = StudioToolIconKind.BACK,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 18.dp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.editor_studio_dock_back),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

    Spacer(modifier = Modifier.width(MediaProSpacing.ExtraSmall))

    when {
        location.isText -> {
            StudioDockTile(
                icon = StudioToolIconKind.TEXT,
                label = stringResource(R.string.editor_tool_text_style),
                enabled = editsEnabled,
                isHighlighted = true,
                onClick = { onEvent(EditorEvent.OpenEditText) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.POSITION,
                label = stringResource(R.string.editor_tool_position),
                enabled = editsEnabled,
                onClick = { onEvent(EditorEvent.OpenTextPosition) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.TIMING,
                label = stringResource(R.string.editor_tool_timing),
                enabled = editsEnabled && canEditOverlayTiming,
                onClick = { onEvent(EditorEvent.OpenTextTiming) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.KEYFRAMES,
                label = stringResource(R.string.editor_tool_keyframes),
                enabled = editsEnabled,
                isActive = clip.hasKeyframes,
                onClick = { onEvent(EditorEvent.OpenKeyframes) },
            )
        }
        location.isOverlay -> {
            StudioDockTile(
                icon = StudioToolIconKind.POSITION,
                label = stringResource(R.string.editor_tool_position),
                enabled = editsEnabled,
                isActive = clip.hasCustomOverlayStyle,
                onClick = { onEvent(EditorEvent.OpenOverlayPosition) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.TIMING,
                label = stringResource(R.string.editor_tool_timing),
                enabled = editsEnabled && canEditOverlayTiming,
                onClick = { onEvent(EditorEvent.OpenOverlayTiming) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.KEYFRAMES,
                label = stringResource(R.string.editor_tool_keyframes),
                enabled = editsEnabled,
                isActive = clip.hasKeyframes,
                onClick = { onEvent(EditorEvent.OpenKeyframes) },
            )
        }
        location.isAudio -> {
            val isAudioPlaying = state.audioPreview?.clipId == clip.id
            StudioDockTile(
                icon = if (isAudioPlaying) StudioToolIconKind.PAUSE else StudioToolIconKind.PLAY,
                label = stringResource(
                    if (isAudioPlaying) R.string.editor_tool_pause else R.string.editor_tool_play,
                ),
                enabled = editsEnabled,
                isActive = isAudioPlaying,
                onClick = { onEvent(EditorEvent.ToggleAudioPreview) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.REPLACE,
                label = stringResource(R.string.editor_tool_replace),
                enabled = editsEnabled,
                onClick = onReplaceAudio,
            )
            StudioDockTile(
                icon = StudioToolIconKind.TRIM,
                label = stringResource(R.string.editor_tool_trim),
                enabled = editsEnabled && ProjectTimeline.canTrimClip(clip),
                onClick = { onEvent(EditorEvent.OpenTrim) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.SPEED,
                label = stringResource(R.string.editor_tool_speed),
                enabled = editsEnabled && !clip.isStillImage,
                isActive = ProjectTimeline.effectiveSpeed(clip) != Clip.DEFAULT_PLAYBACK_SPEED,
                onClick = { onEvent(EditorEvent.OpenSpeed) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.VOLUME,
                label = stringResource(R.string.editor_tool_volume),
                enabled = editsEnabled,
                isActive = clip.hasCustomAudioMix,
                onClick = { onEvent(EditorEvent.OpenVolume) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.EARLIER,
                label = stringResource(R.string.editor_tool_earlier),
                enabled = editsEnabled && location.canMoveEarlier,
                onClick = { onEvent(EditorEvent.MoveSelected(-1)) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.LATER,
                label = stringResource(R.string.editor_tool_later),
                enabled = editsEnabled && location.canMoveLater,
                onClick = { onEvent(EditorEvent.MoveSelected(1)) },
            )
        }
        else -> {
            StudioDockTile(
                icon = StudioToolIconKind.REPLACE,
                label = stringResource(R.string.editor_tool_replace),
                enabled = editsEnabled,
                onClick = onReplaceVideo,
            )
            StudioDockTile(
                icon = StudioToolIconKind.TRIM,
                label = stringResource(R.string.editor_tool_trim),
                enabled = editsEnabled && ProjectTimeline.canTrimClip(clip),
                onClick = { onEvent(EditorEvent.OpenTrim) },
            )
            val clipEndMs = ProjectTimeline.saturatingAdd(
                clip.startTimeMs,
                ProjectTimeline.effectiveDurationMs(clip),
            )
            val canSplitHere = timelinePositionMs >= clip.startTimeMs + ProjectTimeline.MIN_CLIP_DURATION_MS &&
                timelinePositionMs <= clipEndMs - ProjectTimeline.MIN_CLIP_DURATION_MS
            StudioDockTile(
                icon = StudioToolIconKind.SPLIT,
                label = stringResource(R.string.editor_tool_split),
                enabled = editsEnabled && ProjectTimeline.canSplitClip(clip),
                onClick = {
                    if (canSplitHere) {
                        onEvent(EditorEvent.SplitAtPlayhead(timelinePositionMs))
                    } else {
                        onEvent(EditorEvent.OpenSplit)
                    }
                },
            )
            StudioDockTile(
                icon = StudioToolIconKind.SPEED,
                label = stringResource(R.string.editor_tool_speed),
                enabled = editsEnabled && !clip.isStillImage,
                isActive = ProjectTimeline.effectiveSpeed(clip) != Clip.DEFAULT_PLAYBACK_SPEED,
                onClick = { onEvent(EditorEvent.OpenSpeed) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.VOLUME,
                label = stringResource(R.string.editor_tool_volume),
                enabled = editsEnabled && !clip.isStillImage,
                isActive = clip.hasCustomAudioMix,
                onClick = { onEvent(EditorEvent.OpenVolume) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.EXTRACT_AUDIO,
                label = stringResource(R.string.editor_tool_extract_audio),
                enabled = editsEnabled && !clip.isStillImage,
                onClick = { onEvent(EditorEvent.ExtractAudioFromSelected) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.TRANSFORM,
                label = stringResource(R.string.editor_tool_transform),
                enabled = editsEnabled,
                isActive = clip.hasActiveTransform,
                onClick = { onEvent(EditorEvent.OpenTransform) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.CROP,
                label = stringResource(R.string.editor_tool_crop),
                enabled = editsEnabled,
                isActive = clip.hasActiveCrop,
                onClick = { onEvent(EditorEvent.OpenCrop) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.FREEZE,
                label = stringResource(R.string.editor_tool_freeze),
                enabled = editsEnabled && !clip.isStillImage,
                onClick = { onEvent(EditorEvent.OpenFreezeFrame) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.SNAPSHOT,
                label = stringResource(R.string.editor_tool_snapshot),
                enabled = editsEnabled && !clip.isStillImage,
                onClick = { onSaveSnapshot(timelinePositionMs) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.EFFECTS,
                label = stringResource(R.string.editor_tool_effects),
                enabled = editsEnabled,
                isActive = clip.visualEffects?.isIdentity == false,
                onClick = { onEvent(EditorEvent.OpenClipEffects) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.EARLIER,
                label = stringResource(R.string.editor_tool_earlier),
                enabled = editsEnabled && location.canMoveEarlier,
                onClick = { onEvent(EditorEvent.MoveSelected(-1)) },
            )
            StudioDockTile(
                icon = StudioToolIconKind.LATER,
                label = stringResource(R.string.editor_tool_later),
                enabled = editsEnabled && location.canMoveLater,
                onClick = { onEvent(EditorEvent.MoveSelected(1)) },
            )
        }
    }

    StudioDockTile(
        icon = StudioToolIconKind.DUPLICATE,
        label = stringResource(
            if (location.isOverlay) R.string.editor_tool_copy_overlay else R.string.editor_tool_duplicate,
        ),
        enabled = editsEnabled,
        onClick = { onEvent(EditorEvent.DuplicateSelected) },
    )
    StudioDockTile(
        icon = StudioToolIconKind.COPY_FROM,
        label = stringResource(R.string.editor_tool_copy_from_here),
        enabled = editsEnabled,
        onClick = { onEvent(EditorEvent.CopyFromHere(timelinePositionMs)) },
    )
    StudioDockTile(
        icon = StudioToolIconKind.COPY_TILL,
        label = stringResource(R.string.editor_tool_copy_till_here),
        enabled = editsEnabled,
        onClick = { onEvent(EditorEvent.CopyTillHere(timelinePositionMs)) },
    )
    StudioDockTile(
        icon = StudioToolIconKind.COPY_TO,
        label = stringResource(R.string.editor_tool_copy_to_here),
        enabled = editsEnabled,
        onClick = { onEvent(EditorEvent.CopyToHere(timelinePositionMs)) },
    )
    StudioDockTile(
        icon = StudioToolIconKind.DELETE,
        label = stringResource(R.string.editor_remove_confirm),
        enabled = editsEnabled,
        isDestructive = true,
        onClick = { onEvent(EditorEvent.RequestRemoveClip(clip.id)) },
    )
}

@Composable
private fun StudioDockTile(
    icon: StudioToolIconKind,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isHighlighted: Boolean = false,
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)
        isDestructive -> MaterialTheme.colorScheme.error
        isActive || isHighlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val containerColor = when {
        isHighlighted && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isActive && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(MediaProShapes.Small)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minWidth = 58.dp, minHeight = 56.dp)
            .padding(horizontal = MediaProSpacing.Small, vertical = MediaProSpacing.ExtraSmall),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                StudioToolIcon(
                    kind = icon,
                    tint = contentColor,
                    size = 20.dp,
                )
            }
            if (isActive && enabled && !isLoading) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
