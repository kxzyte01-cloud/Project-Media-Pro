package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayTimingDialog(
    clip: Clip,
    videoDurationMs: Long,
    onConfirm: (startTimeMs: Long, durationMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val minDurationMs = ProjectTimeline.MIN_CLIP_DURATION_MS
    val safeVideoMs = videoDurationMs.coerceAtLeast(minDurationMs)
    val maxStartMs = (safeVideoMs - minDurationMs).coerceAtLeast(0L)

    val initialStartMs = clip.startTimeMs.coerceIn(0L, maxStartMs)
    val initialMaxDurationMs = (safeVideoMs - initialStartMs).coerceAtLeast(minDurationMs)
    val initialDurationMs = clip.durationMs.coerceIn(minDurationMs, initialMaxDurationMs)

    var startMs by rememberSaveable(clip.id, initialStartMs) { mutableLongStateOf(initialStartMs) }
    var durationMs by rememberSaveable(clip.id, initialDurationMs) {
        mutableLongStateOf(initialDurationMs)
    }

    val currentMaxDurationMs = (safeVideoMs - startMs).coerceAtLeast(minDurationMs)
    val clampedDurationMs = durationMs.coerceIn(minDurationMs, currentMaxDurationMs)

    EditorSliderDialog(
        title = stringResource(R.string.editor_overlay_timing_title),
        readoutText = stringResource(
            R.string.editor_overlay_timing_summary,
            ProjectTimeline.formatDuration(startMs),
            ProjectTimeline.formatDuration(clampedDurationMs),
        ),
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = videoDurationMs >= minDurationMs,
        onConfirm = { onConfirm(startMs, clampedDurationMs) },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    R.string.editor_overlay_start_label,
                    ProjectTimeline.formatDuration(startMs),
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = startMs.toFloat(),
                onValueChange = { raw ->
                    val nextStart = raw.roundToLong().coerceIn(0L, maxStartMs)
                    startMs = nextStart
                    val nextMaxDuration = (safeVideoMs - nextStart).coerceAtLeast(minDurationMs)
                    if (durationMs > nextMaxDuration) {
                        durationMs = nextMaxDuration
                    }
                },
                valueRange = 0f..maxStartMs.toFloat().coerceAtLeast(1f),
                enabled = maxStartMs > 0L,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Text(
                text = stringResource(
                    R.string.editor_overlay_duration_label,
                    ProjectTimeline.formatDuration(clampedDurationMs),
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = clampedDurationMs.toFloat(),
                onValueChange = { raw ->
                    durationMs = raw.roundToLong().coerceIn(minDurationMs, currentMaxDurationMs)
                },
                valueRange = minDurationMs.toFloat()..currentMaxDurationMs.toFloat().coerceAtLeast(minDurationMs.toFloat() + 1f),
                enabled = currentMaxDurationMs > minDurationMs,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
