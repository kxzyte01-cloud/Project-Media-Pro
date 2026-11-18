package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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

/**
 * Displays the Timeline header with 8dp-spaced Undo/Redo actions and a recessed, horizontally
 * scrollable studio track strip of proportional clip blocks.
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
            Surface(
                shape = MediaProShapes.Medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
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
    val borderStroke = if (isSelected) {
        BorderStroke(MediaProSpacing.BorderSelected, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = MediaProSpacing.ExtraSmall,
                vertical = MediaProSpacing.ExtraSmall,
            ),
        ) {
            Text(
                text = clipNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = if (isSelected) 0.92f else 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
