package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
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
 * Dialog with a [RangeSlider] over `[0, effectiveSourceDurationMs(clip)]` enforcing a minimum
 * remaining length of [ProjectTimeline.MIN_CLIP_DURATION_MS].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimDialog(
    clip: Clip,
    onConfirm: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val minDurationMs = ProjectTimeline.MIN_CLIP_DURATION_MS
    val sourceDurationMs = ProjectTimeline.effectiveSourceDurationMs(clip)
        .coerceAtLeast(minDurationMs)
    val initialStartMs = ProjectTimeline.effectiveTrimStartMs(clip)
        .coerceIn(0L, sourceDurationMs - minDurationMs)
    val initialEndMs = ProjectTimeline.effectiveTrimEndMs(clip)
        .coerceIn(initialStartMs + minDurationMs, sourceDurationMs)

    var startMs by rememberSaveable(clip.id, sourceDurationMs) { mutableLongStateOf(initialStartMs) }
    var endMs by rememberSaveable(clip.id, sourceDurationMs) { mutableLongStateOf(initialEndMs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.editor_trim_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.editor_trim_range,
                        ProjectTimeline.formatDuration(startMs),
                        ProjectTimeline.formatDuration(endMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                RangeSlider(
                    value = startMs.toFloat()..endMs.toFloat(),
                    onValueChange = { range ->
                        if (range.endInclusive - range.start >= minDurationMs.toFloat()) {
                            val candidateStart = range.start.roundToLong()
                                .coerceIn(0L, sourceDurationMs - minDurationMs)
                            val candidateEnd = range.endInclusive.roundToLong()
                                .coerceIn(candidateStart + minDurationMs, sourceDurationMs)
                            startMs = candidateStart
                            endMs = candidateEnd
                        }
                    },
                    valueRange = 0f..sourceDurationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(startMs, endMs) },
                enabled = endMs - startMs >= minDurationMs,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Text(text = stringResource(R.string.editor_apply))
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
