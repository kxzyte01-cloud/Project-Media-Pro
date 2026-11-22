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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.KeyframeEasing
import com.projectmediapro.core.model.KeyframeInterpolator
import com.projectmediapro.core.model.KeyframePresetId
import com.projectmediapro.core.model.KeyframePresets
import com.projectmediapro.core.model.OverlayKeyframe
import kotlin.math.roundToInt

@Composable
fun KeyframeEditorDialog(
    clip: Clip,
    onConfirm: (keyframes: List<OverlayKeyframe>) -> Unit,
    onDismiss: () -> Unit,
) {
    val baseCenterX = OverlayKeyframe.sanitizeCoordinate(clip.overlayCenterX, 0.5f)
    val baseCenterY = OverlayKeyframe.sanitizeCoordinate(clip.overlayCenterY, 0.5f)
    var keyframes by remember(clip.id) {
        mutableStateOf(
            KeyframeInterpolator.sanitizeList(clip.keyframes, baseCenterX, baseCenterY),
        )
    }
    var selectedIndex by remember(clip.id) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (keyframes.size - 1).coerceAtLeast(0))
    val activeKeyframe = keyframes.getOrNull(safeIndex)
    val matchedPreset = KeyframePresets.matchingPreset(keyframes, baseCenterX, baseCenterY)

    val readoutText = if (keyframes.isEmpty()) {
        stringResource(R.string.editor_keyframes_readout_static)
    } else {
        stringResource(
            R.string.editor_keyframes_readout_count,
            keyframes.size,
            ((activeKeyframe?.timeFraction ?: 0f) * 100f).roundToInt(),
        )
    }

    fun updateSelected(transform: (OverlayKeyframe) -> OverlayKeyframe) {
        if (safeIndex !in keyframes.indices) return
        val updated = keyframes.toMutableList()
        updated[safeIndex] = transform(updated[safeIndex]).sanitized(baseCenterX, baseCenterY)
        keyframes = updated
    }

    EditorSliderDialog(
        title = stringResource(R.string.editor_keyframes_title),
        readoutText = readoutText,
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = true,
        onConfirm = {
            onConfirm(KeyframeInterpolator.sanitizeList(keyframes, baseCenterX, baseCenterY))
        },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EditorDialogSectionLabel(text = stringResource(R.string.editor_keyframes_presets_label))
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                KeyframePresetId.entries.forEach { preset ->
                    PresetChip(
                        label = stringResource(presetLabelRes(preset)),
                        isSelected = matchedPreset == preset,
                        onClick = {
                            keyframes = KeyframePresets.build(preset, baseCenterX, baseCenterY)
                            selectedIndex = 0
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_keyframes_add),
                    enabled = keyframes.size < OverlayKeyframe.MAX_KEYFRAMES_PER_CLIP,
                    onClick = {
                        val nextTime = KeyframeInterpolator.suggestNextTimeFraction(keyframes)
                        val seed = KeyframeInterpolator.evaluate(
                            baseCenterX = baseCenterX,
                            baseCenterY = baseCenterY,
                            keyframes = keyframes,
                            progress = nextTime,
                        )
                        val nextKf = OverlayKeyframe(
                            timeFraction = nextTime,
                            centerX = seed.centerX,
                            centerY = seed.centerY,
                            scale = seed.scale,
                            rotationDegrees = seed.rotationDegrees,
                            opacity = seed.opacity,
                        ).sanitized(baseCenterX, baseCenterY)
                        val sorted = KeyframeInterpolator.sanitizeList(
                            keyframes = keyframes + nextKf,
                            defaultCenterX = baseCenterX,
                            defaultCenterY = baseCenterY,
                        )
                        keyframes = sorted
                        selectedIndex = sorted.indexOfLast { it == nextKf }.coerceAtLeast(0)
                    },
                    modifier = Modifier.weight(1f),
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_keyframes_remove),
                    enabled = keyframes.isNotEmpty(),
                    tone = EditorButtonTone.Destructive,
                    onClick = {
                        if (safeIndex in keyframes.indices) {
                            keyframes = keyframes.toMutableList().apply { removeAt(safeIndex) }
                            selectedIndex = (safeIndex - 1).coerceAtLeast(0)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (keyframes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    keyframes.forEachIndexed { idx, kf ->
                        PresetChip(
                            label = stringResource(
                                R.string.editor_keyframes_point_label,
                                idx + 1,
                                (kf.timeFraction * 100f).roundToInt(),
                            ),
                            isSelected = idx == safeIndex,
                            onClick = { selectedIndex = idx },
                        )
                    }
                }
            }

            if (activeKeyframe != null) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_keyframes_time_value,
                        (activeKeyframe.timeFraction * 100f).roundToInt(),
                    ),
                    value = activeKeyframe.timeFraction,
                    valueRange = 0f..1f,
                    onValueChange = { v -> updateSelected { it.copy(timeFraction = v) } },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_keyframes_pos_x_value,
                        (activeKeyframe.centerX * 100f).roundToInt(),
                    ),
                    value = activeKeyframe.centerX,
                    valueRange = 0f..1f,
                    onValueChange = { v -> updateSelected { it.copy(centerX = v) } },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_keyframes_pos_y_value,
                        (activeKeyframe.centerY * 100f).roundToInt(),
                    ),
                    value = activeKeyframe.centerY,
                    valueRange = 0f..1f,
                    onValueChange = { v -> updateSelected { it.copy(centerY = v) } },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_keyframes_scale_value,
                        (activeKeyframe.scale * 100f).roundToInt(),
                    ),
                    value = activeKeyframe.scale,
                    valueRange = OverlayKeyframe.MIN_SCALE..OverlayKeyframe.MAX_SCALE,
                    onValueChange = { v -> updateSelected { it.copy(scale = v) } },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_keyframes_rotation_value,
                        activeKeyframe.rotationDegrees.roundToInt(),
                    ),
                    value = activeKeyframe.rotationDegrees,
                    valueRange = OverlayKeyframe.MIN_ROTATION_DEG..OverlayKeyframe.MAX_ROTATION_DEG,
                    onValueChange = { v -> updateSelected { it.copy(rotationDegrees = v) } },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_keyframes_opacity_value,
                        (activeKeyframe.opacity * 100f).roundToInt(),
                    ),
                    value = activeKeyframe.opacity,
                    valueRange = 0f..1f,
                    onValueChange = { v -> updateSelected { it.copy(opacity = v) } },
                )

                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                EditorDialogSectionLabel(text = stringResource(R.string.editor_keyframes_easing_label))
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    KeyframeEasing.entries.forEach { easing ->
                        PresetChip(
                            label = stringResource(easingLabelRes(easing)),
                            isSelected = activeKeyframe.easing == easing,
                            onClick = { updateSelected { it.copy(easing = easing) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    EditorOptionCard(
        isSelected = isSelected,
        onClick = onClick,
    ) { contentColor ->
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(
                horizontal = MediaProSpacing.MediumSmall,
                vertical = MediaProSpacing.Small,
            ),
        )
    }
}

private fun presetLabelRes(preset: KeyframePresetId): Int = when (preset) {
    KeyframePresetId.NONE -> R.string.editor_keyframes_preset_none
    KeyframePresetId.FADE_IN_OUT -> R.string.editor_keyframes_preset_fade
    KeyframePresetId.ZOOM_IN -> R.string.editor_keyframes_preset_zoom
    KeyframePresetId.SLIDE_UP -> R.string.editor_keyframes_preset_slide
    KeyframePresetId.POP_SPIN -> R.string.editor_keyframes_preset_pop
}

private fun easingLabelRes(easing: KeyframeEasing): Int = when (easing) {
    KeyframeEasing.LINEAR -> R.string.editor_keyframes_easing_linear
    KeyframeEasing.EASE_IN -> R.string.editor_keyframes_easing_in
    KeyframeEasing.EASE_OUT -> R.string.editor_keyframes_easing_out
    KeyframeEasing.EASE_IN_OUT -> R.string.editor_keyframes_easing_smooth
}
