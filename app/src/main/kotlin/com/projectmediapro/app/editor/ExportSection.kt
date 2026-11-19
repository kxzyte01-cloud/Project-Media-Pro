package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

@Composable
fun ExportSection(
    exportState: ExportUiState,
    isExportEnabled: Boolean,
    onExportClick: () -> Unit,
    onCancelExport: () -> Unit,
    onDismissDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EditorOutlinedButton(
            label = stringResource(R.string.editor_export_video),
            enabled = isExportEnabled,
            tone = EditorButtonTone.Primary,
            onClick = onExportClick,
            modifier = Modifier.fillMaxWidth(),
        )

        when (exportState) {
            ExportUiState.Idle -> Unit
            is ExportUiState.Running -> {
                val view = LocalView.current
                DisposableEffect(view) {
                    view.keepScreenOn = true
                    onDispose { view.keepScreenOn = false }
                }
                val percent = exportState.percent.coerceIn(0, 100)

                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Surface(
                    shape = MediaProShapes.Medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MediaProSpacing.MediumSmall),
                    ) {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_exporting, percent),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                                Text(
                                    text = stringResource(R.string.editor_export_keep_open),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DialogActionButton(
                                label = stringResource(R.string.home_cancel),
                                onClick = onCancelExport,
                            )
                        }
                    }
                }
            }
            is ExportUiState.Done -> {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Surface(
                    shape = MediaProShapes.Medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(
                        MediaProSpacing.BorderThin,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = MediaProSpacing.Medium,
                                top = MediaProSpacing.ExtraSmall,
                                bottom = MediaProSpacing.ExtraSmall,
                                end = MediaProSpacing.ExtraSmall,
                            ),
                    ) {
                        val messageRes = if (exportState.savedToGallery) {
                            R.string.editor_export_done_gallery
                        } else {
                            R.string.editor_export_done_file
                        }
                        Text(
                            text = stringResource(messageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        DialogActionButton(
                            label = stringResource(R.string.editor_export_dismiss),
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = onDismissDone,
                        )
                    }
                }
            }
        }
    }
}
