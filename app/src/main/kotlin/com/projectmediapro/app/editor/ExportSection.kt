package com.projectmediapro.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

/**
 * The "Export video" button, the running-export progress with a Cancel button (the screen stays on
 * while exporting), and the notice shown after a finished export.
 */
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
        OutlinedButton(
            onClick = onExportClick,
            enabled = isExportEnabled,
            shape = MediaProShapes.Small,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.editor_export_video))
        }

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
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editor_exporting, percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(R.string.editor_export_keep_open),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = onCancelExport,
                        modifier = Modifier.defaultMinSize(
                            minWidth = MediaProSpacing.MinTouchTarget,
                            minHeight = MediaProSpacing.MinTouchTarget,
                        ),
                    ) {
                        Text(text = stringResource(R.string.home_cancel))
                    }
                }
            }
            is ExportUiState.Done -> {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val messageRes = if (exportState.savedToGallery) {
                        R.string.editor_export_done_gallery
                    } else {
                        R.string.editor_export_done_file
                    }
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismissDone,
                        modifier = Modifier.defaultMinSize(
                            minWidth = MediaProSpacing.MinTouchTarget,
                            minHeight = MediaProSpacing.MinTouchTarget,
                        ),
                    ) {
                        Text(text = stringResource(R.string.editor_export_dismiss))
                    }
                }
            }
        }
    }
}
