package com.projectmediapro.app.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Formats an elapsed millisecond duration into a high-precision stopwatch string:
 * `MM:SS.cc` (e.g. `00:08.42`) or `H:MM:SS.cc` for exports over an hour.
 */
internal fun formatExportStopwatch(elapsedMs: Long): String {
    val safeMs = elapsedMs.coerceAtLeast(0L)
    val centis = (safeMs % 1_000L) / 10L
    val totalSeconds = safeMs / 1_000L
    val seconds = totalSeconds % 60L
    val totalMinutes = totalSeconds / 60L
    val minutes = totalMinutes % 60L
    val hours = totalMinutes / 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    } else {
        String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }
}

/**
 * Full-screen CapCut-style export loading & completion overlay with a monotonic
 * stopwatch that ticks live during export and locks at the exact completion time.
 */
@Composable
internal fun ExportProgressOverlay(
    exportState: ExportUiState,
    startedAtNanos: Long,
    finalElapsedMs: Long,
    finishedAtEpochMs: Long,
    onCancelExport: () -> Unit,
    onDismissDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (exportState is ExportUiState.Idle) return

    val view = LocalView.current
    DisposableEffect(view, exportState is ExportUiState.Running) {
        if (exportState is ExportUiState.Running) {
            view.keepScreenOn = true
        }
        onDispose { view.keepScreenOn = false }
    }

    var liveElapsedMs by remember(startedAtNanos) { mutableLongStateOf(0L) }
    LaunchedEffect(exportState is ExportUiState.Running, startedAtNanos) {
        if (exportState !is ExportUiState.Running) return@LaunchedEffect
        val anchorNanos = if (startedAtNanos > 0L) startedAtNanos else System.nanoTime()
        while (true) {
            liveElapsedMs = ((System.nanoTime() - anchorNanos) / 1_000_000L).coerceAtLeast(0L)
            delay(40L)
        }
    }

    // Consume all background taps while the full-screen export overlay is visible
    val blockerInteraction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.88f))
            .systemBarsPadding()
            .clickable(
                interactionSource = blockerInteraction,
                indication = null,
                onClick = {},
            )
            .padding(MediaProSpacing.Large),
    ) {
        Surface(
            shape = MediaProShapes.Large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                MediaProSpacing.BorderThin,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MediaProSpacing.MediumSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MediaProSpacing.Large),
            ) {
                when (exportState) {
                    is ExportUiState.Running -> {
                        val percent = exportState.percent.coerceIn(0, 100)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(124.dp),
                        ) {
                            CircularProgressIndicator(
                                progress = { percent / 100f },
                                strokeWidth = 8.dp,
                                strokeCap = StrokeCap.Round,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = stringResource(R.string.editor_exporting, percent),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )

                        Surface(
                            shape = MediaProShapes.Small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.editor_export_stopwatch_elapsed,
                                    formatExportStopwatch(liveElapsedMs),
                                ),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFeatureSettings = "tnum",
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    horizontal = MediaProSpacing.Medium,
                                    vertical = MediaProSpacing.ExtraSmall,
                                ),
                            )
                        }

                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            text = stringResource(R.string.editor_export_keep_open),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))

                        OutlinedButton(
                            onClick = onCancelExport,
                            shape = MediaProShapes.Small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                        ) {
                            Text(text = stringResource(R.string.home_cancel))
                        }
                    }

                    is ExportUiState.Done -> {
                        val doneMessage = if (exportState.savedToGallery) {
                            stringResource(R.string.editor_export_done_gallery)
                        } else {
                            stringResource(R.string.editor_export_done_file)
                        }
                        val formattedClock = remember(finishedAtEpochMs) {
                            if (finishedAtEpochMs > 0L) {
                                DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(finishedAtEpochMs))
                            } else {
                                ""
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.editor_export_complete_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                        }

                        Text(
                            text = stringResource(R.string.editor_export_complete_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )

                        Surface(
                            shape = MediaProShapes.Small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(
                                    horizontal = MediaProSpacing.Medium,
                                    vertical = MediaProSpacing.Small,
                                ),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.editor_export_finished_in,
                                        formatExportStopwatch(finalElapsedMs),
                                    ),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFeatureSettings = "tnum",
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (formattedClock.isNotEmpty()) {
                                    Text(
                                        text = stringResource(
                                            R.string.editor_export_finished_at,
                                            formattedClock,
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Text(
                            text = doneMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))

                        Button(
                            onClick = onDismissDone,
                            shape = MediaProShapes.Small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                        ) {
                            Text(text = stringResource(R.string.editor_export_done_button))
                        }
                    }

                    ExportUiState.Idle -> Unit
                }
            }
        }
    }
}
