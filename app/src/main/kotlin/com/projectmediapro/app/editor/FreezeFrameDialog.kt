package com.projectmediapro.app.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
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

private val FREEZE_DURATION_PRESETS_MS: List<Long> = listOf(1_000L, 2_000L, 3_000L, 5_000L)
private const val FREEZE_STEP_MS: Long = 500L

@Composable
fun FreezeFrameDialog(
    clip: Clip,
    onConfirm: (sourceTimeMs: Long, freezeDurationMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val speed = ProjectTimeline.effectiveSpeed(clip)
    val timelineDurationMs = ProjectTimeline.effectiveDurationMs(clip).coerceAtLeast(1L)
    val trimStartMs = ProjectTimeline.effectiveTrimStartMs(clip)
    val trimEndMs = ProjectTimeline.effectiveTrimEndMs(clip)

    var timelineOffsetMs by rememberSaveable(clip.id, timelineDurationMs) {
        mutableLongStateOf(timelineDurationMs / 2L)
    }
    var freezeDurationMs by rememberSaveable(clip.id) {
        mutableLongStateOf(ProjectTimeline.DEFAULT_FREEZE_DURATION_MS)
    }

    val sourceTimeMs = (trimStartMs + (timelineOffsetMs * speed).roundToLong())
        .coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))

    EditorSliderDialog(
        title = stringResource(R.string.editor_freeze_title),
        readoutText = stringResource(
            R.string.editor_freeze_readout,
            ProjectTimeline.formatDuration(timelineOffsetMs),
            ProjectTimeline.formatDuration(freezeDurationMs),
        ),
        confirmLabel = stringResource(R.string.editor_freeze_confirm),
        isConfirmEnabled = true,
        onConfirm = { onConfirm(sourceTimeMs, freezeDurationMs) },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EditorDialogSliderRow(
                label = stringResource(
                    R.string.editor_freeze_frame_at,
                    ProjectTimeline.formatDuration(timelineOffsetMs),
                ),
                value = timelineOffsetMs.toFloat(),
                valueRange = 0f..timelineDurationMs.toFloat(),
                onValueChange = { raw ->
                    timelineOffsetMs = raw.roundToLong().coerceIn(0L, timelineDurationMs)
                },
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            EditorDialogSectionLabel(text = stringResource(R.string.editor_freeze_duration_label))
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                FREEZE_DURATION_PRESETS_MS.forEach { presetMs ->
                    val isSelected = freezeDurationMs == presetMs
                    EditorOptionCard(
                        isSelected = isSelected,
                        onClick = { freezeDurationMs = presetMs },
                    ) { contentColor ->
                        Text(
                            text = stringResource(R.string.editor_freeze_preset_seconds, (presetMs / 1_000L).toInt()),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = contentColor,
                            modifier = Modifier.padding(
                                horizontal = MediaProSpacing.MediumSmall,
                                vertical = MediaProSpacing.Small,
                            ),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            EditorDialogSliderRow(
                label = stringResource(
                    R.string.editor_freeze_duration_value,
                    ProjectTimeline.formatDuration(freezeDurationMs),
                ),
                value = freezeDurationMs.toFloat(),
                valueRange = ProjectTimeline.MIN_CLIP_DURATION_MS.toFloat()..ProjectTimeline.MAX_FREEZE_DURATION_MS.toFloat(),
                onValueChange = { raw ->
                    val snapped = ((raw / FREEZE_STEP_MS).roundToLong() * FREEZE_STEP_MS)
                    freezeDurationMs = snapped.coerceIn(
                        ProjectTimeline.MIN_CLIP_DURATION_MS,
                        ProjectTimeline.MAX_FREEZE_DURATION_MS,
                    )
                },
            )
        }
    }
}
