package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

internal enum class EditorButtonTone {
    Default,
    Primary,
    Destructive,
}

/**
 * Shared 48dp-minimum outlined action button used across [ClipTools] and [ExportSection].
 */
@Composable
internal fun EditorOutlinedButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: EditorButtonTone = EditorButtonTone.Default,
) {
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        tone == EditorButtonTone.Destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        tone == EditorButtonTone.Primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        tone == EditorButtonTone.Destructive -> MaterialTheme.colorScheme.error
        tone == EditorButtonTone.Primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MediaProShapes.Small,
        border = BorderStroke(MediaProSpacing.BorderThin, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            disabledContentColor = contentColor,
        ),
        modifier = modifier.defaultMinSize(
            minWidth = MediaProSpacing.MinTouchTarget,
            minHeight = MediaProSpacing.MinTouchTarget,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Shared dialog scaffold for slider-driven clip edits ([TrimDialog] and [SplitDialog]), combining
 * a polite live-region tabular timecode readout pill with 48x48dp dialog actions.
 */
@Composable
internal fun EditorSliderDialog(
    title: String,
    readoutText: String,
    confirmLabel: String,
    isConfirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    sliderContent: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = MediaProShapes.Small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = readoutText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(
                                horizontal = MediaProSpacing.MediumSmall,
                                vertical = MediaProSpacing.Small,
                            )
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Spacer(modifier = Modifier.height(MediaProSpacing.MediumSmall))
                sliderContent()
            }
        },
        confirmButton = {
            DialogActionButton(
                label = confirmLabel,
                enabled = isConfirmEnabled,
                onClick = onConfirm,
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
internal fun DialogActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    textColor: Color = Color.Unspecified,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.defaultMinSize(
            minWidth = MediaProSpacing.MinTouchTarget,
            minHeight = MediaProSpacing.MinTouchTarget,
        ),
    ) {
        Text(
            text = label,
            color = textColor,
        )
    }
}

/**
 * Confirmation dialog shown before removing a clip from the project.
 */
@Composable
fun RemoveClipDialog(
    clipNumber: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.editor_remove_title))
        },
        text = {
            Text(text = stringResource(R.string.editor_remove_message, clipNumber))
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(R.string.editor_remove_confirm),
                textColor = MaterialTheme.colorScheme.error,
                onClick = onConfirm,
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

internal fun editorErrorStringRes(error: EditorError): Int = when (error) {
    EditorError.IMPORT_FAILED -> R.string.error_import_failed
    EditorError.IMPORT_NOT_A_VIDEO -> R.string.error_import_not_video
    EditorError.IMPORT_NO_SPACE -> R.string.error_import_no_space
    EditorError.SAVE_FAILED -> R.string.error_save_failed
    EditorError.EXPORT_FAILED -> R.string.error_export_failed
    EditorError.EXPORT_UNSUPPORTED -> R.string.error_export_unsupported
    EditorError.EXPORT_NO_SPACE -> R.string.error_export_no_space
    EditorError.EXPORT_SAVE_FAILED -> R.string.error_export_save_failed
    EditorError.EXPORT_NOTHING -> R.string.error_export_nothing
}
