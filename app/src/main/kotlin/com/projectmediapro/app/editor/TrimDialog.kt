package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
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

private const val TRIM_NUDGE_MS = 100L

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

    EditorSliderDialog(
        title = stringResource(R.string.editor_trim_title),
        readoutText = stringResource(
            R.string.editor_trim_range,
            ProjectTimeline.formatPreciseDuration(startMs),
            ProjectTimeline.formatPreciseDuration(endMs),
        ),
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = endMs - startMs >= minDurationMs,
        onConfirm = { onConfirm(startMs, endMs) },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            EditorDialogSectionLabel(text = stringResource(R.string.editor_precision_start_nudge))
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_precision_minus_100ms),
                    enabled = startMs > 0L,
                    onClick = { startMs = (startMs - TRIM_NUDGE_MS).coerceAtLeast(0L) },
                    modifier = Modifier.weight(1f),
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_precision_plus_100ms),
                    enabled = startMs + minDurationMs < endMs,
                    onClick = { startMs = (startMs + TRIM_NUDGE_MS).coerceAtMost(endMs - minDurationMs) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            EditorDialogSectionLabel(text = stringResource(R.string.editor_precision_end_nudge))
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_precision_minus_100ms),
                    enabled = endMs - minDurationMs > startMs,
                    onClick = { endMs = (endMs - TRIM_NUDGE_MS).coerceAtLeast(startMs + minDurationMs) },
                    modifier = Modifier.weight(1f),
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_precision_plus_100ms),
                    enabled = endMs < sourceDurationMs,
                    onClick = { endMs = (endMs + TRIM_NUDGE_MS).coerceAtMost(sourceDurationMs) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
