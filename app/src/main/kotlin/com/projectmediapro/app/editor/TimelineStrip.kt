package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline

private const val DP_PER_SECOND = 12f

@Composable
fun TimelineStrip(
    clips: List<Clip>,
    selectedClipId: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    isEnabled: Boolean,
    onSelectClip: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    audioClips: List<Clip> = emptyList(),
    overlayClips: List<Clip> = emptyList(),
    textClips: List<Clip> = emptyList(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MediaProSpacing.Medium, bottom = MediaProSpacing.ExtraSmall),
        ) {
            Text(
                text = stringResource(R.string.editor_timeline_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onUndo,
                enabled = isEnabled && canUndo,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = stringResource(R.string.cd_undo),
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = isEnabled && canRedo,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_redo),
                    contentDescription = stringResource(R.string.cd_redo),
                )
            }
        }

        if (clips.isNotEmpty()) {
            TimelineTrackLane(
                laneTitle = stringResource(R.string.editor_lane_video),
                clips = clips,
                selectedClipId = selectedClipId,
                isEnabled = isEnabled,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                blockLabel = { number -> number.toString() },
                accessibilityLabel = { number, formattedDuration ->
                    stringResource(R.string.cd_timeline_clip, number, formattedDuration)
                },
                onSelectClip = onSelectClip,
            )
        }

        if (audioClips.isNotEmpty()) {
            if (clips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            }
            TimelineTrackLane(
                laneTitle = stringResource(R.string.editor_lane_music),
                clips = audioClips,
                selectedClipId = selectedClipId,
                isEnabled = isEnabled,
                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                selectedContentColor = MaterialTheme.colorScheme.onSecondary,
                unselectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                blockLabel = { number -> "A$number" },
                accessibilityLabel = { number, formattedDuration ->
                    stringResource(R.string.cd_timeline_audio_clip, number, formattedDuration)
                },
                onSelectClip = onSelectClip,
            )
        }

        if (overlayClips.isNotEmpty()) {
            if (clips.isNotEmpty() || audioClips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            }
            TimelineTimedLane(
                laneTitle = stringResource(R.string.editor_lane_overlays),
                clips = overlayClips,
                selectedClipId = selectedClipId,
                isEnabled = isEnabled,
                selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                selectedContentColor = MaterialTheme.colorScheme.onTertiary,
                unselectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                unselectedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                blockLabel = { number, _ -> "O$number" },
                accessibilityLabel = { number, _, formattedDuration ->
                    stringResource(R.string.cd_timeline_overlay_clip, number, formattedDuration)
                },
                onSelectClip = onSelectClip,
            )
        }

        if (textClips.isNotEmpty()) {
            if (clips.isNotEmpty() || audioClips.isNotEmpty() || overlayClips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            }
            TimelineTimedLane(
                laneTitle = stringResource(R.string.editor_lane_text),
                clips = textClips,
                selectedClipId = selectedClipId,
                isEnabled = isEnabled,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                blockLabel = { number, clip ->
                    val snippet = clip.textContent.trim().replace('\n', ' ').take(10)
                    if (snippet.isNotEmpty()) "T$number · $snippet" else "T$number"
                },
                accessibilityLabel = { number, clip, formattedDuration ->
                    stringResource(
                        R.string.cd_timeline_text_clip,
                        number,
                        clip.textContent.trim().take(24),
                        formattedDuration,
                    )
                },
                onSelectClip = onSelectClip,
            )
        }
    }
}

@Composable
private fun TimelineTrackLane(
    laneTitle: String,
    clips: List<Clip>,
    selectedClipId: String?,
    isEnabled: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContainerColor: Color,
    unselectedContentColor: Color,
    blockLabel: (Int) -> String,
    accessibilityLabel: @Composable (Int, String) -> String,
    onSelectClip: (String) -> Unit,
) {
    Text(
        text = laneTitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = MediaProSpacing.ExtraSmall),
    )
    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .systemGestureExclusion(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(MediaProSpacing.Small),
        ) {
            clips.forEachIndexed { index, clip ->
                val clipNumber = index + 1
                val isSelected = clip.id == selectedClipId
                val formattedDuration = ProjectTimeline.formatDuration(clip.durationMs)
                TimelineClipBlock(
                    label = blockLabel(clipNumber),
                    formattedDuration = formattedDuration,
                    accessibilityLabel = accessibilityLabel(clipNumber, formattedDuration),
                    durationMs = clip.durationMs,
                    isSelected = isSelected,
                    isEnabled = isEnabled,
                    containerColor = if (isSelected) selectedContainerColor else unselectedContainerColor,
                    contentColor = if (isSelected) selectedContentColor else unselectedContentColor,
                    onClick = { onSelectClip(clip.id) },
                )
            }
        }
    }
}

@Composable
private fun TimelineTimedLane(
    laneTitle: String,
    clips: List<Clip>,
    selectedClipId: String?,
    isEnabled: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContainerColor: Color,
    unselectedContentColor: Color,
    blockLabel: (Int, Clip) -> String,
    accessibilityLabel: @Composable (Int, Clip, String) -> String,
    onSelectClip: (String) -> Unit,
) {
    val rows = remember(clips) { packOverlayRows(clips) }
    val totalWidthDp = remember(clips) {
        clips.maxOfOrNull { clip ->
            val startDp = (clip.startTimeMs.coerceAtLeast(0L) / 1_000f) * DP_PER_SECOND
            val blockDp = ((clip.durationMs.coerceAtLeast(0L) / 1_000f) * DP_PER_SECOND)
                .coerceAtLeast(MediaProSpacing.MinTouchTarget.value)
            startDp + blockDp
        }?.dp ?: MediaProSpacing.MinTouchTarget
    }

    Text(
        text = laneTitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = MediaProSpacing.ExtraSmall),
    )
    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .systemGestureExclusion(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(MediaProSpacing.Small),
        ) {
            rows.forEach { rowEntries ->
                Box(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                ) {
                    rowEntries.forEach { (clipNumber, clip) ->
                        val isSelected = clip.id == selectedClipId
                        val formattedDuration = ProjectTimeline.formatDuration(clip.durationMs)
                        val startOffsetDp = ((clip.startTimeMs.coerceAtLeast(0L) / 1_000f) * DP_PER_SECOND).dp
                        TimelineClipBlock(
                            label = blockLabel(clipNumber, clip),
                            formattedDuration = formattedDuration,
                            accessibilityLabel = accessibilityLabel(clipNumber, clip, formattedDuration),
                            durationMs = clip.durationMs,
                            isSelected = isSelected,
                            isEnabled = isEnabled,
                            containerColor = if (isSelected) {
                                selectedContainerColor
                            } else {
                                unselectedContainerColor
                            },
                            contentColor = if (isSelected) {
                                selectedContentColor
                            } else {
                                unselectedContentColor
                            },
                            onClick = { onSelectClip(clip.id) },
                            modifier = Modifier.offset(x = startOffsetDp),
                        )
                    }
                }
            }
        }
    }
}

private fun packOverlayRows(clips: List<Clip>): List<List<Pair<Int, Clip>>> {
    val rows = mutableListOf<MutableList<Pair<Int, Clip>>>()
    val rowRightEdgesDp = mutableListOf<Float>()
    clips.forEachIndexed { index, clip ->
        val startDp = (clip.startTimeMs.coerceAtLeast(0L) / 1_000f) * DP_PER_SECOND
        val widthDp = ((clip.durationMs.coerceAtLeast(0L) / 1_000f) * DP_PER_SECOND)
            .coerceAtLeast(MediaProSpacing.MinTouchTarget.value)
        val endDp = startDp + widthDp
        val targetRow = rowRightEdgesDp.indexOfFirst { rightEdge -> startDp >= rightEdge + 8f }
        if (targetRow >= 0) {
            rows[targetRow].add((index + 1) to clip)
            rowRightEdgesDp[targetRow] = endDp
        } else {
            rows.add(mutableListOf((index + 1) to clip))
            rowRightEdgesDp.add(endDp)
        }
    }
    return rows
}

@Composable
private fun TimelineClipBlock(
    label: String,
    formattedDuration: String,
    accessibilityLabel: String,
    durationMs: Long,
    isSelected: Boolean,
    isEnabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockWidth: Dp = ((durationMs.coerceAtLeast(0L) / 1_000f) * DP_PER_SECOND).dp
        .coerceAtLeast(MediaProSpacing.MinTouchTarget)
    val borderStroke = if (isSelected) {
        BorderStroke(MediaProSpacing.BorderSelected, contentColor.copy(alpha = 0.85f))
    } else {
        BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outline)
    }

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = MediaProShapes.Small,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (isSelected) MediaProSpacing.ExtraSmall else 0.dp,
        border = borderStroke,
        modifier = modifier
            .width(blockWidth)
            .defaultMinSize(
                minWidth = MediaProSpacing.MinTouchTarget,
                minHeight = MediaProSpacing.MinTouchTarget,
            )
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                role = Role.Button
                selected = isSelected
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = MediaProSpacing.ExtraSmall,
                vertical = MediaProSpacing.ExtraSmall,
            ),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = contentColor.copy(alpha = if (isSelected) 0.92f else 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
