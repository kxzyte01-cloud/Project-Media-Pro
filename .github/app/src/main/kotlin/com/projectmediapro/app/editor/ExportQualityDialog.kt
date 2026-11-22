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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality

@Composable
fun ExportQualityDialog(
    initialQuality: ExportQuality,
    onConfirm: (ExportQuality, ExportFrameRate, ExportBitrate, Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialFrameRate: ExportFrameRate = ExportFrameRate.AUTO,
    initialBitrate: ExportBitrate = ExportBitrate.STANDARD,
    initialOpticalFlowEnabled: Boolean = false,
) {
    var selectedQuality by rememberSaveable { mutableStateOf(initialQuality) }
    var selectedFrameRate by rememberSaveable { mutableStateOf(initialFrameRate) }
    var selectedBitrate by rememberSaveable { mutableStateOf(initialBitrate) }
    var opticalFlowEnabled by rememberSaveable { mutableStateOf(initialOpticalFlowEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.editor_quality_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                EditorDialogSectionLabel(text = stringResource(R.string.editor_export_resolution_label))
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
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val noteRes = qualityNoteRes(quality)
                            if (noteRes != null) {
                                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                                Text(
                                    text = stringResource(noteRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                EditorDialogSectionLabel(text = stringResource(R.string.editor_export_fps_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    ExportFrameRate.entries.forEach { fps ->
                        ExportSettingChip(
                            label = stringResource(frameRateLabelRes(fps)),
                            isSelected = fps == selectedFrameRate,
                            onClick = { selectedFrameRate = fps },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                EditorDialogSectionLabel(text = stringResource(R.string.editor_export_bitrate_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    ExportBitrate.entries.forEach { bitrate ->
                        ExportSettingChip(
                            label = stringResource(bitrateLabelRes(bitrate)),
                            isSelected = bitrate == selectedBitrate,
                            onClick = { selectedBitrate = bitrate },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Surface(
                    shape = MediaProShapes.Medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MediaProSpacing.MediumSmall,
                                vertical = MediaProSpacing.Small,
                            ),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.editor_export_optical_flow_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.editor_export_optical_flow_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = opticalFlowEnabled,
                            onCheckedChange = { opticalFlowEnabled = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(R.string.editor_export_confirm),
                onClick = { onConfirm(selectedQuality, selectedFrameRate, selectedBitrate, opticalFlowEnabled) },
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

@Composable
private fun ExportSettingChip(
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

private fun qualityLabelRes(quality: ExportQuality): Int = when (quality) {
    ExportQuality.HD_720P -> R.string.editor_quality_720p_fast
    ExportQuality.FULL_HD_1080P -> R.string.editor_quality_1080p
    ExportQuality.QHD_1440P -> R.string.editor_quality_2k
    ExportQuality.UHD_4K -> R.string.editor_quality_4k
}

private fun qualityNoteRes(quality: ExportQuality): Int? = when (quality) {
    ExportQuality.HD_720P -> R.string.editor_quality_720p_note
    ExportQuality.UHD_4K -> R.string.editor_quality_4k_note
    else -> null
}

private fun frameRateLabelRes(fps: ExportFrameRate): Int = when (fps) {
    ExportFrameRate.AUTO -> R.string.editor_export_fps_auto
    ExportFrameRate.FPS_30 -> R.string.editor_export_fps_30
    ExportFrameRate.FPS_60 -> R.string.editor_export_fps_60
}

private fun bitrateLabelRes(bitrate: ExportBitrate): Int = when (bitrate) {
    ExportBitrate.FAST_SMALL -> R.string.editor_export_bitrate_fast
    ExportBitrate.STANDARD -> R.string.editor_export_bitrate_standard
    ExportBitrate.HIGH -> R.string.editor_export_bitrate_high
}
