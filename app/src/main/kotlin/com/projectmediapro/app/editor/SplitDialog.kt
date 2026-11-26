package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.SpeedRampEvaluator
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitDialog(
    clip: Clip,
    onConfirm: (offsetMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val speed = ProjectTimeline.effectiveSpeed(clip)
    val sourceSpanMs = ProjectTimeline.effectiveSourceSpanMs(clip)
    val timelineDurationMs = ProjectTimeline.effectiveDurationMs(clip)
    val minTimelinePartMs = ProjectTimeline.scaledDurationMs(ProjectTimeline.MIN_CLIP_DURATION_MS, speed)
    val safeTimelineDurationMs = timelineDurationMs.coerceAtLeast(minTimelinePartMs * 2L)
    val maxOffsetMs = (safeTimelineDurationMs - minTimelinePartMs).coerceAtLeast(minTimelinePartMs)
    val initialOffsetMs = (safeTimelineDurationMs / 2L).coerceIn(minTimelinePartMs, maxOffsetMs)

    var offsetMs by rememberSaveable(clip.id, safeTimelineDurationMs) { mutableLongStateOf(initialOffsetMs) }
    val sourceOffsetMs = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
        timelineOffsetMs = offsetMs,
        sourceSpanMs = sourceSpanMs,
        baseSpeed = clip.playbackSpeed,
        preset = clip.speedRamp,
    )
    val canSplitHere = sourceOffsetMs >= ProjectTimeline.MIN_CLIP_DURATION_MS &&
        (sourceSpanMs - sourceOffsetMs) >= ProjectTimeline.MIN_CLIP_DURATION_MS

    EditorSliderDialog(
        title = stringResource(R.string.editor_split_title),
        readoutText = stringResource(
            R.string.editor_split_at,
            ProjectTimeline.formatDuration(offsetMs),
        ),
        confirmLabel = stringResource(R.string.editor_split_confirm),
        isConfirmEnabled = canSplitHere,
        onConfirm = { onConfirm(sourceOffsetMs) },
        onDismiss = onDismiss,
    ) {
        Slider(
            value = offsetMs.toFloat(),
            onValueChange = { value ->
                offsetMs = value.roundToLong().coerceIn(minTimelinePartMs, maxOffsetMs)
            },
            valueRange = 0f..safeTimelineDurationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
