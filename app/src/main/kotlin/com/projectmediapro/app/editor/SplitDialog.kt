package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import kotlin.math.roundToLong

/**
 * Dialog with a [Slider] for choosing a split offset inside [clip]'s current length such that
 * both resulting parts are at least [ProjectTimeline.MIN_CLIP_DURATION_MS].
 */
@Composable
fun SplitDialog(
    clip: Clip,
    onConfirm: (offsetMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val minPartMs = ProjectTimeline.MIN_CLIP_DURATION_MS
    val clipDurationMs = clip.durationMs.coerceAtLeast(minPartMs * 2L)
    val maxOffsetMs = (clipDurationMs - minPartMs).coerceAtLeast(minPartMs)
    val initialOffsetMs = (clipDurationMs / 2L).coerceIn(minPartMs, maxOffsetMs)

    var offsetMs by rememberSaveable(clip.id, clipDurationMs) { mutableLongStateOf(initialOffsetMs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.editor_split_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.editor_split_at,
                        ProjectTimeline.formatDuration(offsetMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Slider(
                    value = offsetMs.toFloat(),
                    onValueChange = { value ->
                        offsetMs = value.roundToLong().coerceIn(minPartMs, maxOffsetMs)
                    },
                    valueRange = 0f..clipDurationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(offsetMs) },
                enabled = offsetMs >= minPartMs && (clip.durationMs - offsetMs) >= minPartMs,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Text(text = stringResource(R.string.editor_split_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Text(text = stringResource(R.string.home_cancel))
            }
        },
    )
}
