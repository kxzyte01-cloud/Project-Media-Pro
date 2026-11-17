package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline

private const val DP_PER_SECOND = 12f

/**
 * Displays the Timeline header with Undo/Redo actions and a horizontally scrollable row of clip blocks.
 */
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
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            clips.forEachIndexed { index, clip ->
                TimelineClipBlock(
                    clipNumber = index + 1,
                    durationMs = clip.durationMs,
                    isSelected = clip.id == selectedClipId,
                    isEnabled = isEnabled,
                    onClick = { onSelectClip(clip.id) },
                )
            }
        }
    }
}

@Composable
private fun TimelineClipBlock(
    clipNumber: Int,
    durationMs: Long,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val blockWidth: Dp = ((durationMs.coerceAtLeast(0L) / 1_000f) * DP_PER_SECOND).dp
        .coerceAtLeast(MediaProSpacing.MinTouchTarget)
    val formattedDuration = ProjectTimeline.formatDuration(durationMs)
    val accessibilityLabel = stringResource(R.string.cd_timeline_clip, clipNumber, formattedDuration)

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    }

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = MediaProShapes.Small,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = MediaProSpacing.Small),
        ) {
            Text(
                text = clipNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
