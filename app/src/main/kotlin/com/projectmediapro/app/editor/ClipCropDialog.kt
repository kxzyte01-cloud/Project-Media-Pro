package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipCrop
import kotlin.math.roundToInt

private data class CropPreset(
    val labelRes: Int,
    val crop: ClipCrop,
)

private val CROP_PRESETS: List<CropPreset> = listOf(
    CropPreset(R.string.editor_crop_preset_full, ClipCrop()),
    CropPreset(R.string.editor_crop_preset_9_16, ClipCrop.centeredForAspect(9f / 16f)),
    CropPreset(R.string.editor_crop_preset_1_1, ClipCrop.centeredForAspect(1f)),
    CropPreset(R.string.editor_crop_preset_4_5, ClipCrop.centeredForAspect(4f / 5f)),
    CropPreset(R.string.editor_crop_preset_16_9, ClipCrop.centeredForAspect(16f / 9f)),
)

@Composable
fun ClipCropDialog(
    clip: Clip,
    onConfirm: (crop: ClipCrop?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = (clip.crop ?: ClipCrop()).sanitized()
    var left by rememberSaveable(clip.id, initial.left) { mutableFloatStateOf(initial.left) }
    var top by rememberSaveable(clip.id, initial.top) { mutableFloatStateOf(initial.top) }
    var right by rememberSaveable(clip.id, initial.right) { mutableFloatStateOf(initial.right) }
    var bottom by rememberSaveable(clip.id, initial.bottom) { mutableFloatStateOf(initial.bottom) }

    val currentCrop = ClipCrop(left = left, top = top, right = right, bottom = bottom).sanitized()
    val widthPercent = (currentCrop.widthFraction * 100f).roundToInt()
    val heightPercent = (currentCrop.heightFraction * 100f).roundToInt()

    EditorSliderDialog(
        title = stringResource(R.string.editor_crop_title),
        readoutText = stringResource(R.string.editor_crop_readout, widthPercent, heightPercent),
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = true,
        onConfirm = {
            onConfirm(currentCrop.takeUnless { it.isIdentity })
        },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, MediaProShapes.Small)
                    .border(
                        BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
                        MediaProShapes.Small,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                )
                Box(
                    modifier = Modifier
                        .offset(
                            x = maxWidth * currentCrop.left,
                            y = maxHeight * currentCrop.top,
                        )
                        .size(
                            width = maxWidth * currentCrop.widthFraction,
                            height = maxHeight * currentCrop.heightFraction,
                        )
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                        .border(
                            BorderStroke(MediaProSpacing.BorderSelected, MaterialTheme.colorScheme.primary),
                        ),
                )
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                CROP_PRESETS.forEach { preset ->
                    val isSelected = currentCrop == preset.crop
                    EditorOptionCard(
                        isSelected = isSelected,
                        onClick = {
                            left = preset.crop.left
                            top = preset.crop.top
                            right = preset.crop.right
                            bottom = preset.crop.bottom
                        },
                    ) { contentColor ->
                        Text(
                            text = stringResource(preset.labelRes),
                            style = MaterialTheme.typography.labelLarge,
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
                label = stringResource(R.string.editor_crop_left, (left * 100f).roundToInt()),
                value = left,
                valueRange = 0f..(1f - ClipCrop.MIN_SPAN),
                onValueChange = { raw ->
                    left = raw.coerceAtMost(right - ClipCrop.MIN_SPAN).coerceAtLeast(0f)
                },
            )
            EditorDialogSliderRow(
                label = stringResource(R.string.editor_crop_right, (right * 100f).roundToInt()),
                value = right,
                valueRange = ClipCrop.MIN_SPAN..1f,
                onValueChange = { raw ->
                    right = raw.coerceAtLeast(left + ClipCrop.MIN_SPAN).coerceAtMost(1f)
                },
            )
            EditorDialogSliderRow(
                label = stringResource(R.string.editor_crop_top, (top * 100f).roundToInt()),
                value = top,
                valueRange = 0f..(1f - ClipCrop.MIN_SPAN),
                onValueChange = { raw ->
                    top = raw.coerceAtMost(bottom - ClipCrop.MIN_SPAN).coerceAtLeast(0f)
                },
            )
            EditorDialogSliderRow(
                label = stringResource(R.string.editor_crop_bottom, (bottom * 100f).roundToInt()),
                value = bottom,
                valueRange = ClipCrop.MIN_SPAN..1f,
                onValueChange = { raw ->
                    bottom = raw.coerceAtLeast(top + ClipCrop.MIN_SPAN).coerceAtMost(1f)
                },
            )
        }
    }
}
