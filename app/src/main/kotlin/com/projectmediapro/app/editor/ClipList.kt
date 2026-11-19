package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.SectionHeader
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline

@Composable
fun ClipList(
    clips: List<Clip>,
    onRequestRemoveClip: (String) -> Unit,
    modifier: Modifier = Modifier,
    isRemoveEnabled: Boolean = true,
    selectedClipId: String? = null,
    onSelectClip: (String) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionHeader(title = stringResource(R.string.editor_clips_heading))
        Column(
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier.fillMaxWidth(),
        ) {
            clips.forEachIndexed { index, clip ->
                ClipRowCard(
                    clipNumber = index + 1,
                    durationText = ProjectTimeline.formatDuration(clip.durationMs),
                    isSelected = clip.id == selectedClipId,
                    isRemoveEnabled = isRemoveEnabled,
                    onCardClick = { onSelectClip(clip.id) },
                    onRemoveClick = { onRequestRemoveClip(clip.id) },
                )
            }
        }
    }
}

@Composable
private fun ClipRowCard(
    clipNumber: Int,
    durationText: String,
    isSelected: Boolean,
    isRemoveEnabled: Boolean,
    onCardClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val borderStroke = if (isSelected) {
        BorderStroke(MediaProSpacing.BorderSelected, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant)
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onCardClick,
        enabled = isRemoveEnabled,
        shape = MediaProShapes.Medium,
        color = containerColor,
        border = borderStroke,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MediaProSpacing.Medium,
                    top = MediaProSpacing.Small,
                    bottom = MediaProSpacing.Small,
                    end = MediaProSpacing.ExtraSmall,
                ),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.editor_clip_title, clipNumber),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemoveClick,
                enabled = isRemoveEnabled,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.cd_remove_clip),
                    tint = if (isRemoveEnabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}
