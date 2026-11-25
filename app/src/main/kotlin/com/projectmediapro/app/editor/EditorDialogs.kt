package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

internal enum class EditorButtonTone {
    Default,
    Primary,
    Destructive,
}

@Composable
internal fun EditorOutlinedButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: EditorButtonTone = EditorButtonTone.Default,
    leadingContent: (@Composable () -> Unit)? = null,
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
        if (leadingContent != null) {
            leadingContent()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun EditorOptionCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String? = null,
    content: @Composable (contentColor: Color) -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val semanticsModifier = if (accessibilityLabel != null) {
        modifier.clearAndSetSemantics {
            contentDescription = accessibilityLabel
            role = Role.RadioButton
            selected = isSelected
        }
    } else {
        modifier.semantics {
            role = Role.RadioButton
            selected = isSelected
        }
    }

    Surface(
        onClick = onClick,
        shape = MediaProShapes.Small,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (isSelected) MediaProSpacing.BorderSelected else MediaProSpacing.BorderThin,
            color = borderColor,
        ),
        modifier = semanticsModifier.defaultMinSize(
            minWidth = MediaProSpacing.MinTouchTarget,
            minHeight = MediaProSpacing.MinTouchTarget,
        ),
    ) {
        content(contentColor)
    }
}

@Composable
internal fun EditorSliderDialog(
    title: String,
    readoutText: String,
    confirmLabel: String,
    isConfirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    secondaryConfirmLabel: String? = null,
    onSecondaryConfirm: (() -> Unit)? = null,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Surface(
                    shape = MediaProShapes.Small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = readoutText,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
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
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall)) {
                if (secondaryConfirmLabel != null && onSecondaryConfirm != null) {
                    DialogActionButton(
                        label = secondaryConfirmLabel,
                        enabled = isConfirmEnabled,
                        onClick = onSecondaryConfirm,
                    )
                }
                DialogActionButton(
                    label = confirmLabel,
                    enabled = isConfirmEnabled,
                    onClick = onConfirm,
                )
            }
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
    EditorError.IMPORT_NOT_AUDIO -> R.string.error_import_not_audio
    EditorError.IMPORT_NOT_IMAGE -> R.string.error_import_not_image
    EditorError.IMPORT_NO_SPACE -> R.string.error_import_no_space
    EditorError.SAVE_FAILED -> R.string.error_save_failed
    EditorError.EXPORT_FAILED -> R.string.error_export_failed
    EditorError.EXPORT_UNSUPPORTED -> R.string.error_export_unsupported
    EditorError.EXPORT_NO_SPACE -> R.string.error_export_no_space
    EditorError.EXPORT_SAVE_FAILED -> R.string.error_export_save_failed
    EditorError.EXPORT_NOTHING -> R.string.error_export_nothing
    EditorError.CAPTIONS_FAILED -> R.string.error_captions_failed
    EditorError.CAPTIONS_TRUNCATED -> R.string.error_captions_truncated
    EditorError.CAPTIONS_PLACEHOLDER_FALLBACK -> R.string.error_captions_placeholder_fallback
    EditorError.FREEZE_FRAME_FAILED -> R.string.error_freeze_frame_failed
    EditorError.EXTRACT_AUDIO_FAILED -> R.string.error_extract_audio_failed
    EditorError.BEATS_DETECT_FAILED -> R.string.error_beats_detect_failed
    EditorError.SNAPSHOT_FAILED -> R.string.error_snapshot_failed
}

@Composable
internal fun EditorDialogSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorDialogSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
