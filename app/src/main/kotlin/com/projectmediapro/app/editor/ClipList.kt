package com.projectmediapro.app.editor

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
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.SectionHeader
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline

/**
 * Renders the "Clips" section header and ordered clip cards for the project's video track.
 */
@Composable
fun ClipList(
    clips: List<Clip>,
    onRequestRemoveClip: (String) -> Unit,
    modifier: Modifier = Modifier,
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
    onRemoveClick: () -> Unit,
) {
    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surface,
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
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.cd_remove_clip),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
