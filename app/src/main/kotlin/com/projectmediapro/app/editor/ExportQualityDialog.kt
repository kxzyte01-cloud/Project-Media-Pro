package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.ExportQuality

@Composable
fun ExportQualityDialog(
    initialQuality: ExportQuality,
    onConfirm: (ExportQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedQuality by rememberSaveable { mutableStateOf(initialQuality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.editor_quality_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ExportQuality.entries.forEach { quality ->
                    EditorOptionCard(
                        isSelected = quality == selectedQuality,
                        onClick = { selectedQuality = quality },
                        modifier = Modifier.fillMaxWidth(),
                    ) { contentColor ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MediaProSpacing.MediumSmall,
                                    vertical = MediaProSpacing.Small,
                                ),
                        ) {
                            Text(
                                text = stringResource(qualityLabelRes(quality)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (quality == ExportQuality.UHD_4K) {
                                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                                Text(
                                    text = stringResource(R.string.editor_quality_4k_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(R.string.editor_export_confirm),
                onClick = { onConfirm(selectedQuality) },
            )
        },
        dismissButton = {
            DialogActionButton(
                label = stringResource(R.string.home_cancel),
                onClick = onDismiss,
            )
        },
    )
}

private fun qualityLabelRes(quality: ExportQuality): Int = when (quality) {
    ExportQuality.HD_720P -> R.string.editor_quality_720p
    ExportQuality.FULL_HD_1080P -> R.string.editor_quality_1080p
    ExportQuality.UHD_4K -> R.string.editor_quality_4k
}
