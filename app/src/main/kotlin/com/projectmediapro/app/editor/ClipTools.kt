package com.projectmediapro.app.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

/**
 * Horizontally scrollable row of editing actions for the currently selected timeline clip.
 */
@Composable
fun ClipTools(
    isEnabled: Boolean,
    canTrim: Boolean,
    canSplit: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onTrimClick: () -> Unit,
    onSplitClick: () -> Unit,
    onMoveEarlierClick: () -> Unit,
    onMoveLaterClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        ToolButton(
            label = stringResource(R.string.editor_tool_trim),
            enabled = isEnabled && canTrim,
            onClick = onTrimClick,
        )
        ToolButton(
            label = stringResource(R.string.editor_tool_split),
            enabled = isEnabled && canSplit,
            onClick = onSplitClick,
        )
        ToolButton(
            label = stringResource(R.string.editor_tool_earlier),
            enabled = isEnabled && canMoveEarlier,
            onClick = onMoveEarlierClick,
        )
        ToolButton(
            label = stringResource(R.string.editor_tool_later),
            enabled = isEnabled && canMoveLater,
            onClick = onMoveLaterClick,
        )
        ToolButton(
            label = stringResource(R.string.editor_remove_confirm),
            enabled = isEnabled,
            labelColor = if (isEnabled) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = onRemoveClick,
        )
    }
}

@Composable
private fun ToolButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    labelColor: Color = Color.Unspecified,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MediaProShapes.Small,
        modifier = Modifier.defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
    ) {
        Text(text = label, color = labelColor)
    }
}
