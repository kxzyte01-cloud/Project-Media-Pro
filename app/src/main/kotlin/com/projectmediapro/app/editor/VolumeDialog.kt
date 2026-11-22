package com.projectmediapro.app.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import kotlin.math.roundToInt

private const val STEP_PERCENT = 5
private const val MAX_PERCENT = 200
private const val SLIDER_STEPS = (MAX_PERCENT / STEP_PERCENT) - 1
private val AUDIO_FADE_PRESETS_MS = listOf(0L, 500L, 1_000L, 2_000L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeDialog(
    clip: Clip,
    onConfirm: (volume: Float, audioBoost: Float, fadeInMs: Long, fadeOutMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val combinedGain = ProjectTimeline.sanitizeVolume(clip.volume) *
        ProjectTimeline.sanitizeAudioBoost(clip.audioBoost)
    val initialPercent = ((combinedGain * 100f / STEP_PERCENT).roundToInt() * STEP_PERCENT)
        .coerceIn(0, MAX_PERCENT)
    val maxFadeMs = (ProjectTimeline.effectiveDurationMs(clip) / 2L)
        .coerceIn(0L, Clip.MAX_AUDIO_FADE_MS)
    var percent by rememberSaveable(clip.id, initialPercent) { mutableIntStateOf(initialPercent) }
    var fadeInMs by rememberSaveable(clip.id, clip.audioFadeInMs) {
        mutableLongStateOf(clip.audioFadeInMs.coerceIn(0L, maxFadeMs))
    }
    var fadeOutMs by rememberSaveable(clip.id, clip.audioFadeOutMs) {
        mutableLongStateOf(clip.audioFadeOutMs.coerceIn(0L, maxFadeMs))
    }

    val isMuted = percent == 0
    val readoutText = when {
        isMuted -> stringResource(R.string.editor_volume_muted)
        percent > 100 -> stringResource(R.string.editor_volume_boost_value, percent)
        else -> stringResource(R.string.editor_volume_value, percent)
    }

    EditorSliderDialog(
        title = stringResource(R.string.editor_volume_title),
        readoutText = readoutText,
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = true,
        onConfirm = {
            val vol = (percent.coerceAtMost(100) / 100f).coerceIn(0f, 1f)
            val boost = if (percent > 100) (percent / 100f).coerceIn(1f, Clip.MAX_AUDIO_BOOST) else 1f
            onConfirm(vol, boost, fadeInMs.coerceIn(0L, maxFadeMs), fadeOutMs.coerceIn(0L, maxFadeMs))
        },
        onDismiss = onDismiss,
    ) {
        Slider(
            value = percent.toFloat(),
            onValueChange = { raw ->
                percent = ((raw / STEP_PERCENT).roundToInt() * STEP_PERCENT).coerceIn(0, MAX_PERCENT)
            },
            valueRange = 0f..MAX_PERCENT.toFloat(),
            steps = SLIDER_STEPS,
            modifier = Modifier.fillMaxWidth(),
        )
        if (percent > 100) {
            Text(
                text = stringResource(R.string.editor_volume_limiter_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
        }
        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        Text(
            text = stringResource(R.string.editor_audio_fade_in_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            AUDIO_FADE_PRESETS_MS.forEach { presetMs ->
                FilterChip(
                    selected = fadeInMs == presetMs,
                    enabled = presetMs == 0L || presetMs <= maxFadeMs,
                    onClick = { fadeInMs = presetMs },
                    label = { Text(text = formatFadeChipLabel(presetMs)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
        Text(
            text = stringResource(R.string.editor_audio_fade_out_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            AUDIO_FADE_PRESETS_MS.forEach { presetMs ->
                FilterChip(
                    selected = fadeOutMs == presetMs,
                    enabled = presetMs == 0L || presetMs <= maxFadeMs,
                    onClick = { fadeOutMs = presetMs },
                    label = { Text(text = formatFadeChipLabel(presetMs)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        EditorOutlinedButton(
            label = stringResource(
                if (isMuted) R.string.editor_volume_unmute else R.string.editor_volume_mute,
            ),
            enabled = true,
            tone = if (isMuted) EditorButtonTone.Primary else EditorButtonTone.Default,
            onClick = { percent = if (isMuted) 100 else 0 },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun formatFadeChipLabel(ms: Long): String = when (ms) {
    0L -> stringResource(R.string.editor_audio_fade_off)
    500L -> stringResource(R.string.editor_audio_fade_half_s)
    1_000L -> stringResource(R.string.editor_audio_fade_1s)
    else -> stringResource(R.string.editor_audio_fade_2s)
}
