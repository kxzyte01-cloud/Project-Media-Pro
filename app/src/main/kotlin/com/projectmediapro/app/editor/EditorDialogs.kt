package com.projectmediapro.app.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R

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
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.editor_remove_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_cancel))
            }
        },
    )
}

internal fun editorErrorStringRes(error: EditorError): Int = when (error) {
    EditorError.IMPORT_FAILED -> R.string.error_import_failed
    EditorError.IMPORT_NOT_A_VIDEO -> R.string.error_import_not_video
    EditorError.IMPORT_NO_SPACE -> R.string.error_import_no_space
    EditorError.SAVE_FAILED -> R.string.error_save_failed
}
