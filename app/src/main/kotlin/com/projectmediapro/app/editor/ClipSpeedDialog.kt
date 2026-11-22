package com.projectmediapro.app.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEED_PRESETS: List<Float> = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f)
private const val SPEED_STEP = 0.05f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipSpeedDialog(
    clip: Clip,
    onConfirm: (speed: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialSpeed = ProjectTimeline.effectiveSpeed(clip)
    var speed by rememberSaveable(clip.id, initialSpeed) { mutableFloatStateOf(initialSpeed) }
    val sourceSpanMs = ProjectTimeline.effectiveSourceSpanMs(clip)
    val previewDurationMs = ProjectTimeline.scaledDurationMs(sourceSpanMs, speed)
    val speedLabel = formatSpeedMultiplier(speed)

    EditorSliderDialog(
        title = stringResource(R.string.editor_speed_title),
        readoutText = stringResource(
            R.string.editor_speed_readout,
            speedLabel,
            ProjectTimeline.formatDuration(previewDurationMs),
        ),
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = true,
        onConfirm = { onConfirm(ProjectTimeline.sanitizeSpeed(speed)) },
        onDismiss = onDismiss,
    ) {
        Slider(
            value = speed,
            onValueChange = { raw ->
                val snapped = (raw / SPEED_STEP).roundToInt() * SPEED_STEP
                speed = ProjectTimeline.sanitizeSpeed(snapped)
            },
            valueRange = Clip.MIN_PLAYBACK_SPEED..Clip.MAX_PLAYBACK_SPEED,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            SPEED_PRESETS.forEach { preset ->
                val isSelected = abs(speed - preset) < 0.01f
                EditorOptionCard(
                    isSelected = isSelected,
                    onClick = { speed = preset },
                ) { contentColor ->
                    Text(
                        text = formatSpeedMultiplier(preset),
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
    }
}

private fun formatSpeedMultiplier(speed: Float): String {
    val rounded = (speed * 100f).roundToInt()
    return when {
        rounded % 100 == 0 -> String.format(Locale.US, "%dx", rounded / 100)
        rounded % 10 == 0 -> String.format(Locale.US, "%.1fx", speed)
        else -> String.format(Locale.US, "%.2fx", speed)
    }
}
