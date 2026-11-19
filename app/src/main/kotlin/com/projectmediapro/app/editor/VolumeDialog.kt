package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.model.Clip
import kotlin.math.roundToInt

private const val STEP_PERCENT = 5
private const val SLIDER_STEPS = (100 / STEP_PERCENT) - 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeDialog(
    clip: Clip,
    onConfirm: (volume: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialPercent = ((clip.volume.coerceIn(0f, 1f) * 100f / STEP_PERCENT).roundToInt() * STEP_PERCENT)
        .coerceIn(0, 100)
    var percent by rememberSaveable(clip.id, initialPercent) { mutableIntStateOf(initialPercent) }

    EditorSliderDialog(
        title = stringResource(R.string.editor_volume_title),
        readoutText = stringResource(R.string.editor_volume_value, percent),
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = true,
        onConfirm = { onConfirm((percent / 100f).coerceIn(0f, 1f)) },
        onDismiss = onDismiss,
    ) {
        Slider(
            value = percent.toFloat(),
            onValueChange = { raw ->
                percent = ((raw / STEP_PERCENT).roundToInt() * STEP_PERCENT).coerceIn(0, 100)
            },
            valueRange = 0f..100f,
            steps = SLIDER_STEPS,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
