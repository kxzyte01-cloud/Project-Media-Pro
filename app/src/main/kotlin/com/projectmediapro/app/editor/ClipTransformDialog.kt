package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline

@Composable
fun ClipTransformDialog(
    clip: Clip,
    onConfirm: (rotationDegrees: Int, flipHorizontal: Boolean, flipVertical: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialRotation = ProjectTimeline.normalizeRotationDegrees(clip.rotationDegrees)
    var rotationDegrees by rememberSaveable(clip.id, initialRotation) { mutableIntStateOf(initialRotation) }
    var flipHorizontal by rememberSaveable(clip.id, clip.flipHorizontal) { mutableStateOf(clip.flipHorizontal) }
    var flipVertical by rememberSaveable(clip.id, clip.flipVertical) { mutableStateOf(clip.flipVertical) }

    val flipSummary = buildList {
        if (flipHorizontal) add(stringResource(R.string.editor_transform_flip_h))
        if (flipVertical) add(stringResource(R.string.editor_transform_flip_v))
    }.joinToString(" · ")
    val readoutText = if (flipSummary.isEmpty()) {
        stringResource(R.string.editor_transform_readout_rotation, rotationDegrees)
    } else {
        stringResource(R.string.editor_transform_readout_full, rotationDegrees, flipSummary)
    }

    EditorSliderDialog(
        title = stringResource(R.string.editor_transform_title),
        readoutText = readoutText,
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = true,
        onConfirm = { onConfirm(rotationDegrees, flipHorizontal, flipVertical) },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EditorOutlinedButton(
                label = stringResource(R.string.editor_transform_rotate_90),
                enabled = true,
                tone = if (rotationDegrees != 0) EditorButtonTone.Primary else EditorButtonTone.Default,
                onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_transform_flip_h),
                    enabled = true,
                    tone = if (flipHorizontal) EditorButtonTone.Primary else EditorButtonTone.Default,
                    onClick = { flipHorizontal = !flipHorizontal },
                    modifier = Modifier.weight(1f),
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_transform_flip_v),
                    enabled = true,
                    tone = if (flipVertical) EditorButtonTone.Primary else EditorButtonTone.Default,
                    onClick = { flipVertical = !flipVertical },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            EditorOutlinedButton(
                label = stringResource(R.string.editor_transform_reset),
                enabled = rotationDegrees != 0 || flipHorizontal || flipVertical,
                onClick = {
                    rotationDegrees = 0
                    flipHorizontal = false
                    flipVertical = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
