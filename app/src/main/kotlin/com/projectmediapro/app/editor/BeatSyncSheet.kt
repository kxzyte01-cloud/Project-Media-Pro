package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.BeatEffectKind
import com.projectmediapro.core.model.BeatMarker
import com.projectmediapro.core.model.ProjectTimeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BeatSyncSheet(
    beatMarkers: List<BeatMarker>,
    soundReactiveEnabled: Boolean,
    timelinePositionMs: Long,
    hasClips: Boolean,
    isDetectingBeats: Boolean,
    onToggleSoundReactive: (Boolean) -> Unit,
    onAutoDetectBeats: () -> Unit,
    onTapBeat: (BeatEffectKind) -> Unit,
    onAutoCutOnBeats: () -> Unit,
    onSeekToBeat: (Long) -> Unit,
    onRemoveBeat: (String) -> Unit,
    onClearBeats: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MediaProSpacing.Medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.editor_beats_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(
                            R.string.editor_beats_subtitle,
                            beatMarkers.size,
                            ProjectTimeline.formatDuration(timelinePositionMs),
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.editor_beats_close),
                    )
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Small))

            // Top action row: Auto-Detect Beats + Tap Plain Beat
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onAutoDetectBeats,
                    enabled = hasClips && !isDetectingBeats,
                    shape = MediaProShapes.Small,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                ) {
                    if (isDetectingBeats) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                        Text(text = stringResource(R.string.editor_beats_detecting))
                    } else {
                        Text(text = stringResource(R.string.editor_beats_auto_detect))
                    }
                }
                OutlinedButton(
                    onClick = { onTapBeat(BeatEffectKind.NONE) },
                    enabled = hasClips && !isDetectingBeats,
                    shape = MediaProShapes.Small,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                ) {
                    Text(text = stringResource(R.string.editor_beats_tap_mark))
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Medium))

            Surface(
                shape = MediaProShapes.Medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MediaProSpacing.Medium, vertical = MediaProSpacing.Small),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editor_beats_sound_reactive_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.editor_beats_sound_reactive_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = soundReactiveEnabled,
                        onCheckedChange = onToggleSoundReactive,
                    )
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Medium))

            // DJ-Style Live Perform Pads
            Text(
                text = stringResource(R.string.editor_beats_perform_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.editor_beats_perform_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))

            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LivePerformPad(
                    label = stringResource(R.string.editor_beats_fx_zoom),
                    accentColor = Color(0xFF38BDF8),
                    enabled = hasClips && !isDetectingBeats,
                    onClick = { onTapBeat(BeatEffectKind.ZOOM_PUNCH) },
                    modifier = Modifier.weight(1f),
                )
                LivePerformPad(
                    label = stringResource(R.string.editor_beats_fx_shake),
                    accentColor = Color(0xFFFB7185),
                    enabled = hasClips && !isDetectingBeats,
                    onClick = { onTapBeat(BeatEffectKind.SHAKE) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LivePerformPad(
                    label = stringResource(R.string.editor_beats_fx_flash),
                    accentColor = Color(0xFFFACC15),
                    enabled = hasClips && !isDetectingBeats,
                    onClick = { onTapBeat(BeatEffectKind.FLASH) },
                    modifier = Modifier.weight(1f),
                )
                LivePerformPad(
                    label = stringResource(R.string.editor_beats_fx_glitch),
                    accentColor = Color(0xFFC084FC),
                    enabled = hasClips && !isDetectingBeats,
                    onClick = { onTapBeat(BeatEffectKind.GLITCH) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (beatMarkers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Medium))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onAutoCutOnBeats,
                        enabled = hasClips && !isDetectingBeats,
                        shape = MediaProShapes.Small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.editor_beats_auto_cut))
                    }
                    TextButton(
                        onClick = onClearBeats,
                        enabled = !isDetectingBeats,
                    ) {
                        Text(
                            text = stringResource(R.string.editor_beats_clear),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.Small))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(items = beatMarkers, key = { it.id }) { marker ->
                        val chipColor = beatMarkerColor(marker.effect)
                        Surface(
                            shape = MediaProShapes.Small,
                            color = chipColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, chipColor.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .clip(MediaProShapes.Small)
                                .clickable { onSeekToBeat(marker.timeMs) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    start = MediaProSpacing.Small,
                                    end = MediaProSpacing.ExtraSmall,
                                    top = MediaProSpacing.ExtraSmall,
                                    bottom = MediaProSpacing.ExtraSmall,
                                ),
                            ) {
                                Text(
                                    text = formatBeatChipLabel(marker),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chipColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.editor_remove_confirm),
                                    tint = chipColor,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveBeat(marker.id) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Large))
        }
    }
}

@Composable
private fun LivePerformPad(
    label: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MediaProShapes.Medium,
        color = accentColor.copy(alpha = if (enabled) 0.18f else 0.06f),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = if (enabled) 0.72f else 0.25f)),
        modifier = modifier
            .clip(MediaProShapes.Medium)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp)
                .padding(MediaProSpacing.Small),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        }
    }
}

internal fun beatMarkerColor(effect: BeatEffectKind): Color = when (effect) {
    BeatEffectKind.NONE -> Color(0xFFFBBF24)
    BeatEffectKind.ZOOM_PUNCH -> Color(0xFF38BDF8)
    BeatEffectKind.SHAKE -> Color(0xFFFB7185)
    BeatEffectKind.FLASH -> Color(0xFFFACC15)
    BeatEffectKind.GLITCH -> Color(0xFFC084FC)
}

@Composable
private fun formatBeatChipLabel(marker: BeatMarker): String {
    val timeText = ProjectTimeline.formatDuration(marker.timeMs)
    val fxTag = when (marker.effect) {
        BeatEffectKind.NONE -> null
        BeatEffectKind.ZOOM_PUNCH -> stringResource(R.string.editor_beats_fx_zoom)
        BeatEffectKind.SHAKE -> stringResource(R.string.editor_beats_fx_shake)
        BeatEffectKind.FLASH -> stringResource(R.string.editor_beats_fx_flash)
        BeatEffectKind.GLITCH -> stringResource(R.string.editor_beats_fx_glitch)
    }
    return if (fxTag == null) timeText else "$timeText · $fxTag"
}
