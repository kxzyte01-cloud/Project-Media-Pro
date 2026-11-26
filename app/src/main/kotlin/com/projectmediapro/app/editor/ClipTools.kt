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
    hasActiveSpeed: Boolean = false,
    hasActiveTransform: Boolean = false,
    hasActiveCrop: Boolean = false,
    hasKeyframes: Boolean = false,
    canFreezeFrame: Boolean = false,
    canAdjustSpeed: Boolean = true,
    canAdjustVolume: Boolean = true,
    hasAdjustedVolume: Boolean = false,
    canEditOverlayTiming: Boolean = false,
    isAudioPlaying: Boolean = false,
    isMuted: Boolean = false,
    isVideoOverlay: Boolean = false,
    hasChromaKey: Boolean = false,
    onSpeedClick: () -> Unit = {},
    onTransformClick: () -> Unit = {},
    onCropClick: () -> Unit = {},
    onFreezeFrameClick: () -> Unit = {},
    onDuplicateClick: () -> Unit = {},
    onEffectsClick: () -> Unit = {},
    onVolumeClick: () -> Unit = {},
    onTogglePlayClick: () -> Unit = {},
    onOverlayPositionClick: () -> Unit = {},
    onOverlayTimingClick: () -> Unit = {},
    onEditTextClick: () -> Unit = {},
    onTextPositionClick: () -> Unit = {},
    onTextTimingClick: () -> Unit = {},
    onKeyframesClick: () -> Unit = {},
    onToggleMuteClick: () -> Unit = {},
    onChromaKeyClick: () -> Unit = {},
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
                    text = stringResource(R.string.editor_tool_text_style),
                    enabled = isEnabled,
                    tone = EditorButtonTone.Primary,
                    onClick = onEditTextClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_position),
                    enabled = isEnabled,
                    onClick = onTextPositionClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_timing),
                    enabled = isEnabled && canEditOverlayTiming,
                    onClick = onTextTimingClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_keyframes),
                    enabled = isEnabled,
                    tone = if (hasKeyframes) EditorButtonTone.Primary else EditorButtonTone.Default,
                    onClick = onKeyframesClick,
                )
            }
            isOverlayClip -> {
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_position),
                    enabled = isEnabled,
                    onClick = onOverlayPositionClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_timing),
                    enabled = isEnabled && canEditOverlayTiming,
                    onClick = onOverlayTimingClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_keyframes),
                    enabled = isEnabled,
                    tone = if (hasKeyframes) EditorButtonTone.Primary else EditorButtonTone.Default,
                    onClick = onKeyframesClick,
                )
                if (isVideoOverlay) {
                    EditorOutlinedButton(
                        text = stringResource(R.string.editor_tool_chroma_key),
                        enabled = isEnabled,
                        tone = if (hasChromaKey) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onChromaKeyClick,
                    )
                    EditorOutlinedButton(
                        text = stringResource(if (isMuted) R.string.editor_tool_unmute else R.string.editor_tool_mute),
                        enabled = isEnabled,
                        tone = if (isMuted) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onToggleMuteClick,
                    )
                }
            }
            else -> {
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_trim),
                    enabled = isEnabled && canTrim,
                    onClick = onTrimClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_split),
                    enabled = isEnabled && canSplit,
                    onClick = onSplitClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_speed),
                    enabled = isEnabled && canAdjustSpeed,
                    tone = if (hasActiveSpeed) EditorButtonTone.Primary else EditorButtonTone.Default,
                    onClick = onSpeedClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_volume),
                    enabled = isEnabled && canAdjustVolume,
                    tone = if (hasAdjustedVolume) EditorButtonTone.Primary else EditorButtonTone.Default,
                    onClick = onVolumeClick,
                )
                EditorOutlinedButton(
                    text = stringResource(if (isMuted) R.string.editor_tool_unmute else R.string.editor_tool_mute),
                    enabled = isEnabled && canAdjustVolume,
                    tone = if (isMuted) EditorButtonTone.Primary else EditorButtonTone.Default,
                    onClick = onToggleMuteClick,
                )
                if (!isAudioClip) {
                    EditorOutlinedButton(
                        text = stringResource(R.string.editor_tool_transform),
                        enabled = isEnabled,
                        tone = if (hasActiveTransform) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onTransformClick,
                    )
                    EditorOutlinedButton(
                        text = stringResource(R.string.editor_tool_crop),
                        enabled = isEnabled,
                        tone = if (hasActiveCrop) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onCropClick,
                    )
                    EditorOutlinedButton(
                        text = stringResource(R.string.editor_tool_freeze),
                        enabled = isEnabled && canFreezeFrame,
                        onClick = onFreezeFrameClick,
                    )
                    EditorOutlinedButton(
                        text = stringResource(R.string.editor_tool_effects),
                        enabled = isEnabled,
                        tone = if (hasActiveEffects) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onEffectsClick,
                    )
                }
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_earlier),
                    enabled = isEnabled && canMoveEarlier,
                    onClick = onMoveEarlierClick,
                )
                EditorOutlinedButton(
                    text = stringResource(R.string.editor_tool_later),
                    enabled = isEnabled && canMoveLater,
                    onClick = onMoveLaterClick,
                )
                if (isAudioClip) {
                    EditorOutlinedButton(
                        text = stringResource(if (isAudioPlaying) R.string.editor_tool_pause else R.string.editor_tool_play),
                        enabled = isEnabled,
                        tone = if (isAudioPlaying) EditorButtonTone.Primary else EditorButtonTone.Default,
                        onClick = onTogglePlayClick,
                    )
                }
            }
        }
        EditorOutlinedButton(
            text = stringResource(R.string.editor_tool_duplicate),
            enabled = isEnabled,
            onClick = onDuplicateClick,
        )
        EditorOutlinedButton(
            text = stringResource(R.string.editor_remove_confirm),
            enabled = isEnabled,
            tone = EditorButtonTone.Destructive,
            onClick = onRemoveClick,
        )
    }
}
