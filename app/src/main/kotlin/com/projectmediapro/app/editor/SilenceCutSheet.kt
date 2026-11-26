package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.SilenceSegment
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Interactive offline Auto-Cut Silences preview sheet:
 * scans clip audio, previews red-hatched silent spans, lets creators adjust dB threshold &
 * min pause length or uncheck any pause, and cuts all selected silences in one undoable tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SilenceCutSheet(
    state: SilenceScanUiState,
    onThresholdChange: (Float) -> Unit,
    onMinSilenceMsChange: (Long) -> Unit,
    onToggleSegment: (String) -> Unit,
    onApplyCut: () -> Unit,
    onCancelScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedSegments = state.segments.filter { it.selected }
    val totalSavedMs = selectedSegments.sumOf { it.durationMs }

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
                        text = stringResource(R.string.editor_silence_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (state.isScanning) {
                            stringResource(R.string.editor_silence_scanning)
                        } else if (state.segments.isEmpty()) {
                            stringResource(R.string.editor_silence_none_found)
                        } else {
                            stringResource(
                                R.string.editor_silence_summary,
                                selectedSegments.size,
                                formatSecondsShort(totalSavedMs),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.editor_silence_close),
                    )
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Small))

            if (state.isScanning) {
                Surface(
                    shape = MediaProShapes.Medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Medium),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MediaProSpacing.Medium),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                        Text(
                            text = stringResource(R.string.editor_silence_scanning),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = onCancelScan,
                            shape = MediaProShapes.Small,
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                        ) {
                            Text(text = stringResource(R.string.home_cancel))
                        }
                    }
                }
            } else {
                // Visual Timeline Silence Preview Bar (red-hatched silent regions)
                SilenceTimelinePreviewBar(
                    clipDurationMs = state.clipDurationMs,
                    segments = state.segments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )

                Spacer(modifier = Modifier.height(MediaProSpacing.Medium))

                // Threshold Slider (-40 dB to -25 dB)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.editor_silence_threshold_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.editor_silence_db_value, state.thresholdDb.roundToInt()),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = state.thresholdDb.coerceIn(-40f, -25f),
                    onValueChange = { onThresholdChange(it.roundToInt().toFloat()) },
                    valueRange = -40f..-25f,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Min Silence Duration Slider (300 ms to 1500 ms)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.editor_silence_min_pause_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.editor_silence_ms_value, state.minSilenceMs),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = state.minSilenceMs.coerceIn(300L, 1_500L).toFloat(),
                    onValueChange = { raw ->
                        val snapped = ((raw / 50f).roundToLong() * 50L).coerceIn(300L, 1_500L)
                        onMinSilenceMsChange(snapped)
                    },
                    valueRange = 300f..1500f,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.segments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                    Text(
                        text = stringResource(R.string.editor_silence_tap_toggle_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(items = state.segments, key = { it.id }) { seg ->
                            val accent = if (seg.selected) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                            Surface(
                                shape = MediaProShapes.Small,
                                color = if (seg.selected) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget)
                                    .clip(MediaProShapes.Small)
                                    .clickable { onToggleSegment(seg.id) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        start = MediaProSpacing.ExtraSmall,
                                        end = MediaProSpacing.Small,
                                        top = MediaProSpacing.ExtraSmall,
                                        bottom = MediaProSpacing.ExtraSmall,
                                    ),
                                ) {
                                    Checkbox(
                                        checked = seg.selected,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${ProjectTimeline.formatDuration(seg.startMs)} (${formatSecondsShort(seg.durationMs)})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (seg.selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.Medium))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = MediaProShapes.Small,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                    ) {
                        Text(text = stringResource(R.string.home_cancel))
                    }
                    Button(
                        onClick = onApplyCut,
                        enabled = selectedSegments.isNotEmpty(),
                        shape = MediaProShapes.Small,
                        modifier = Modifier
                            .weight(1.4f)
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                    ) {
                        Text(text = stringResource(R.string.editor_silence_apply_cut))
                    }
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Large))
        }
    }
}

@Composable
private fun SilenceTimelinePreviewBar(
    clipDurationMs: Long,
    segments: List<SilenceSegment>,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeSpeechColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val selectedCutColor = MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
    val hatchColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
    val unselectedCutColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = MediaProShapes.Small,
        color = trackColor,
        border = BorderStroke(MediaProSpacing.BorderThin, borderColor),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = activeSpeechColor, size = size)
                val safeTotal = clipDurationMs.coerceAtLeast(1L).toFloat()
                segments.forEach { seg ->
                    val leftX = ((seg.startMs.coerceAtLeast(0L) / safeTotal) * size.width).coerceIn(0f, size.width)
                    val rightX = ((seg.endMs.coerceAtLeast(seg.startMs) / safeTotal) * size.width).coerceIn(leftX, size.width)
                    val w = (rightX - leftX).coerceAtLeast(2f)
                    drawRect(
                        color = if (seg.selected) selectedCutColor else unselectedCutColor,
                        topLeft = Offset(leftX, 0f),
                        size = Size(w, size.height),
                    )
                    if (seg.selected) {
                        val step = 8.dp.toPx()
                        var hx = leftX
                        while (hx < rightX) {
                            val endX = (hx + size.height * 0.5f).coerceAtMost(rightX)
                            drawLine(
                                color = hatchColor,
                                start = Offset(hx, size.height),
                                end = Offset(endX, 0f),
                                strokeWidth = 1.5f,
                            )
                            hx += step
                        }
                    }
                }
            }
        }
    }
}

private fun formatSecondsShort(ms: Long): String {
    val sec = ms.coerceAtLeast(0L) / 1_000.0
    return String.format(Locale.US, "%.1fs", sec)
}
