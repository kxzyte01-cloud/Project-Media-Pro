package com.projectmediapro.app.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing

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
    isAudioClip: Boolean = false,
    isOverlayClip: Boolean = false,
    isTextClip: Boolean = false,
    hasActiveEffects: Boolean = false,
    canEditOverlayTiming: Boolean = false,
    isAudioPlaying: Boolean = false,
    onEffectsClick: () -> Unit = {},
    onVolumeClick: () -> Unit = {},
    onTogglePlayClick: () -> Unit = {},
    onOverlayPositionClick: () -> Unit = {},
    onOverlayTimingClick: () -> Unit = {},
    onEditTextClick: () -> Unit = {},
    onTextPositionClick: () -> Unit = {},
    onTextTimingClick: () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        when {
            isTextClip -> {
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_text_style),
                    enabled = isEnabled,
                    tone = EditorButtonTone.Primary,
                    onClick = onEditTextClick,
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_position),
                    enabled = isEnabled,
                    onClick = onTextPositionClick,
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_timing),
                    enabled = isEnabled && canEditOverlayTiming,
                    onClick = onTextTimingClick,
                )
            }
            isOverlayClip -> {
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_position),
                    enabled = isEnabled,
                    onClick = onOverlayPositionClick,
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_timing),
                    enabled = isEnabled && canEditOverlayTiming,
                    onClick = onOverlayTimingClick,
                )
            }
            else -> {
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_trim),
                    enabled = isEnabled && canTrim,
                    onClick = onTrimClick,
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_split),
                    enabled = isEnabled && !isAudioClip && canSplit,
                    onClick = onSplitClick,
                )
                if (!isAudioClip) {
                    EditorOutlinedButton(
                        label = stringResource(R.string.editor_tool_effects),
                        enabled = isEnabled,
                        tone = if (hasActiveEffects) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onEffectsClick,
                    )
                }
                if (isAudioClip) {
                    EditorOutlinedButton(
                        label = stringResource(R.string.editor_tool_volume),
                        enabled = isEnabled,
                        onClick = onVolumeClick,
                    )
                }
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_earlier),
                    enabled = isEnabled && canMoveEarlier,
                    onClick = onMoveEarlierClick,
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.editor_tool_later),
                    enabled = isEnabled && canMoveLater,
                    onClick = onMoveLaterClick,
                )
                if (isAudioClip) {
                    val playLabelRes = if (isAudioPlaying) {
                        R.string.editor_tool_pause
                    } else {
                        R.string.editor_tool_play
                    }
                    EditorOutlinedButton(
                        label = stringResource(playLabelRes),
                        enabled = isEnabled,
                        tone = if (isAudioPlaying) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onTogglePlayClick,
                    )
                }
            }
        }
        EditorOutlinedButton(
            label = stringResource(R.string.editor_remove_confirm),
            enabled = isEnabled,
            tone = EditorButtonTone.Destructive,
            onClick = onRemoveClick,
        )
    }
}
