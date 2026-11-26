package com.projectmediapro.app.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import androidx.compose.ui.graphics.Path
import com.projectmediapro.core.model.BeatMarker
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.SpeedRampEvaluator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val RULER_HEIGHT_DP = 28.dp
private val VIDEO_LANE_HEIGHT_DP = 56.dp
private val AUDIO_LANE_HEIGHT_DP = 48.dp
private val TIMED_LANE_HEIGHT_DP = 44.dp
private val TRIM_HANDLE_WIDTH_DP = 14.dp
private val TIMELINE_TRAILING_PAD_DP = 64.dp
private const val ZOOM_STEP_FACTOR = 1.35f

@Composable
fun TimelineStrip(
    clips: List<Clip>,
    selectedClipId: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    isEnabled: Boolean,
    onSelectClip: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    audioClips: List<Clip> = emptyList(),
    overlayClips: List<Clip> = emptyList(),
    textClips: List<Clip> = emptyList(),
    beatMarkers: List<BeatMarker> = emptyList(),
    clipFilePaths: Map<String, String> = emptyMap(),
    timelinePositionMs: Long = 0L,
    totalDurationMs: Long = 0L,
    onSeekTimeline: (Long) -> Unit = {},
    onScrubTimeline: (Long, Boolean) -> Unit = { ms, _ -> onSeekTimeline(ms) },
    onSplitAtPlayhead: (() -> Unit)? = null,
    onTrimLeftToPlayhead: (() -> Unit)? = null,
    onTrimRightToPlayhead: (() -> Unit)? = null,
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
    onResizeTimedClip: (clipId: String, startTimeMs: Long, durationMs: Long) -> Unit = { _, _, _ -> },
    showHistoryControls: Boolean = true,
) {
    var dpPerSecond by rememberSaveable { mutableFloatStateOf(TimelineScale.DEFAULT_DP_PER_SECOND) }
    val scrollState = rememberScrollState()
    val effectiveTotalMs = remember(totalDurationMs, clips, audioClips, overlayClips, textClips) {
        val computed = maxOf(
            totalDurationMs,
            clips.sumOf { it.durationMs.coerceAtLeast(0L) },
            audioClips.sumOf { it.durationMs.coerceAtLeast(0L) },
            overlayClips.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L,
            textClips.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L,
        )
        computed.coerceAtLeast(1_000L)
    }
    val density = LocalDensity.current
    val contentWidthDp = remember(effectiveTotalMs, dpPerSecond) {
        (TimelineScale.msToDp(effectiveTotalMs, dpPerSecond).coerceAtLeast(240f)).dp + TIMELINE_TRAILING_PAD_DP
    }
    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()
    var isUserScrubbing by remember { mutableStateOf(false) }
    val latestOnScrub by rememberUpdatedState(onScrubTimeline)

    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserScrubbing = true
        }
    }

    LaunchedEffect(scrollState, dpPerSecond, effectiveTotalMs, isEnabled) {
        var lastEmittedMs = -1L
        snapshotFlow { Triple(scrollState.value, scrollState.isScrollInProgress, isUserScrubbing) }
            .collect { (scrollPx, inProgress, userScrubbing) ->
                if (!isEnabled || !userScrubbing) return@collect
                val scrolledDp = with(density) { scrollPx.toDp().value }
                val targetMs = TimelineScale.dpToMs(scrolledDp, dpPerSecond).coerceIn(0L, effectiveTotalMs)
                if (inProgress) {
                    if (lastEmittedMs < 0L || abs(targetMs - lastEmittedMs) >= 16L) {
                        lastEmittedMs = targetMs
                        latestOnScrub(targetMs, true)
                    }
                } else {
                    isUserScrubbing = false
                    lastEmittedMs = targetMs
                    latestOnScrub(targetMs, false)
                }
            }
    }

    LaunchedEffect(timelinePositionMs, dpPerSecond, effectiveTotalMs, isUserScrubbing) {
        if (isUserScrubbing) return@LaunchedEffect
        val targetPx = with(density) {
            TimelineScale.msToDp(timelinePositionMs.coerceIn(0L, effectiveTotalMs), dpPerSecond).dp.toPx()
        }.roundToInt().coerceIn(0, scrollState.maxValue)
        if (abs(scrollState.value - targetPx) > 2) {
            scrollState.scrollTo(targetPx)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MediaProSpacing.Medium, bottom = MediaProSpacing.ExtraSmall),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.editor_timeline_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        R.string.editor_timeline_timecode,
                        ProjectTimeline.formatDuration(timelinePositionMs),
                        ProjectTimeline.formatDuration(effectiveTotalMs),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TimelineZoomButton(
                label = stringResource(R.string.editor_timeline_zoom_out),
                enabled = isEnabled && dpPerSecond > TimelineScale.MIN_DP_PER_SECOND + 0.5f,
                onClick = { dpPerSecond = TimelineScale.clampZoom(dpPerSecond / ZOOM_STEP_FACTOR) },
            )
            Text(
                text = stringResource(R.string.editor_timeline_zoom_percent, TimelineScale.zoomPercent(dpPerSecond)),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimelineZoomButton(
                label = stringResource(R.string.editor_timeline_zoom_in),
                enabled = isEnabled && dpPerSecond < TimelineScale.MAX_DP_PER_SECOND - 0.5f,
                onClick = { dpPerSecond = TimelineScale.clampZoom(dpPerSecond * ZOOM_STEP_FACTOR) },
            )
            if (showHistoryControls) {
            IconButton(
                onClick = onUndo,
                enabled = isEnabled && canUndo,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = stringResource(R.string.cd_undo),
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = isEnabled && canRedo,
                modifier = Modifier.defaultMinSize(
                    minWidth = MediaProSpacing.MinTouchTarget,
                    minHeight = MediaProSpacing.MinTouchTarget,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_redo),
                    contentDescription = stringResource(R.string.cd_redo),
                )
            }
            }
        }

        if (clips.isNotEmpty() && (onSplitAtPlayhead != null || onTrimLeftToPlayhead != null || onTrimRightToPlayhead != null)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MediaProSpacing.ExtraSmall),
            ) {
                if (onSplitAtPlayhead != null) {
                    TimelineQuickCutChip(
                        label = stringResource(R.string.editor_tool_split),
                        contentDesc = stringResource(R.string.cd_timeline_split_playhead),
                        enabled = isEnabled,
                        accent = true,
                        onClick = onSplitAtPlayhead,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onTrimLeftToPlayhead != null) {
                    TimelineQuickCutChip(
                        label = stringResource(R.string.editor_tool_cut_left),
                        contentDesc = stringResource(R.string.cd_timeline_cut_left),
                        enabled = isEnabled,
                        onClick = onTrimLeftToPlayhead,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onTrimRightToPlayhead != null) {
                    TimelineQuickCutChip(
                        label = stringResource(R.string.editor_tool_cut_right),
                        contentDesc = stringResource(R.string.cd_timeline_cut_right),
                        enabled = isEnabled,
                        onClick = onTrimRightToPlayhead,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Surface(
            shape = MediaProShapes.Medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .systemGestureExclusion()
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            dpPerSecond = TimelineScale.clampZoom(dpPerSecond * zoom)
                        }
                    }
                },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val halfViewportDp = (maxWidth / 2f).coerceAtLeast(1.dp)
                val halfViewportPx = with(density) { halfViewportDp.toPx() }
                val visibleStartMs = TimelineScale.dpToMs(
                    with(density) { (scrollState.value - halfViewportPx).coerceAtLeast(0f).toDp().value },
                    dpPerSecond,
                ).coerceIn(0L, effectiveTotalMs)
                val visibleEndMs = TimelineScale.dpToMs(
                    with(density) { (scrollState.value + halfViewportPx).coerceAtLeast(0f).toDp().value },
                    dpPerSecond,
                ).coerceIn(visibleStartMs, effectiveTotalMs)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(
                            start = halfViewportDp,
                            end = halfViewportDp,
                            top = MediaProSpacing.Small,
                            bottom = MediaProSpacing.Small,
                        ),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                        modifier = Modifier.width(contentWidthDp),
                    ) {
                        TimelineRuler(
                            totalDurationMs = effectiveTotalMs,
                            dpPerSecond = dpPerSecond,
                            isEnabled = isEnabled,
                            visibleStartMs = visibleStartMs,
                            visibleEndMs = visibleEndMs,
                            beatMarkers = beatMarkers,
                            onSeekTimeline = { targetMs ->
                                isUserScrubbing = false
                                latestOnScrub(targetMs, false)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RULER_HEIGHT_DP),
                        )

                    if (clips.isNotEmpty()) {
                        TimelineLaneHeader(text = stringResource(R.string.editor_lane_video))
                        TimelineSequentialLane(
                            clips = clips,
                            dpPerSecond = dpPerSecond,
                            laneHeight = VIDEO_LANE_HEIGHT_DP,
                            selectedClipId = selectedClipId,
                            isEnabled = isEnabled,
                            isAudioLane = false,
                            clipFilePaths = clipFilePaths,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedContainerColor = MaterialTheme.colorScheme.surface,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            blockLabel = { number, clip ->
                                when {
                                    clip.isStillImage -> "$number · Freeze"
                                    ProjectTimeline.effectiveSpeed(clip) != Clip.DEFAULT_PLAYBACK_SPEED ->
                                        "$number · ${String.format(Locale.US, "%.1fx", ProjectTimeline.effectiveSpeed(clip))}"
                                    else -> number.toString()
                                }
                            },
                            accessibilityLabel = { number, formattedDuration ->
                                stringResource(R.string.cd_timeline_clip, number, formattedDuration)
                            },
                            onSelectClip = onSelectClip,
                            onTrimClip = onTrimClip,
                        )
                    }

                    if (audioClips.isNotEmpty()) {
                        TimelineLaneHeader(text = stringResource(R.string.editor_lane_music))
                        TimelineSequentialLane(
                            clips = audioClips,
                            dpPerSecond = dpPerSecond,
                            laneHeight = AUDIO_LANE_HEIGHT_DP,
                            selectedClipId = selectedClipId,
                            isEnabled = isEnabled,
                            isAudioLane = true,
                            clipFilePaths = clipFilePaths,
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedContentColor = MaterialTheme.colorScheme.onSecondary,
                            unselectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            blockLabel = { number, _ -> "A$number" },
                            accessibilityLabel = { number, formattedDuration ->
                                stringResource(R.string.cd_timeline_audio_clip, number, formattedDuration)
                            },
                            onSelectClip = onSelectClip,
                            onTrimClip = onTrimClip,
                        )
                    }

                    if (overlayClips.isNotEmpty()) {
                        TimelineLaneHeader(text = stringResource(R.string.editor_lane_overlays))
                        TimelineTimedLane(
                            clips = overlayClips,
                            dpPerSecond = dpPerSecond,
                            totalWidthDp = contentWidthDp,
                            selectedClipId = selectedClipId,
                            isEnabled = isEnabled,
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                            selectedContentColor = MaterialTheme.colorScheme.onTertiary,
                            unselectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            unselectedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            blockLabel = { number, _ -> "O$number" },
                            accessibilityLabel = { number, _, formattedDuration ->
                                stringResource(R.string.cd_timeline_overlay_clip, number, formattedDuration)
                            },
                            totalVideoDurationMs = effectiveTotalMs,
                            onSelectClip = onSelectClip,
                            onResizeTimedClip = onResizeTimedClip,
                        )
                    }

                    if (textClips.isNotEmpty()) {
                        TimelineLaneHeader(text = stringResource(R.string.editor_lane_text))
                        TimelineTimedLane(
                            clips = textClips,
                            dpPerSecond = dpPerSecond,
                            totalWidthDp = contentWidthDp,
                            selectedClipId = selectedClipId,
                            isEnabled = isEnabled,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            blockLabel = { number, clip ->
                                val snippet = clip.textContent.trim().replace('\n', ' ').take(10)
                                if (snippet.isNotEmpty()) "T$number · $snippet" else "T$number"
                            },
                            accessibilityLabel = { number, clip, formattedDuration ->
                                stringResource(
                                    R.string.cd_timeline_text_clip,
                                    number,
                                    clip.textContent.trim().take(24),
                                    formattedDuration,
                                )
                            },
                            totalVideoDurationMs = effectiveTotalMs,
                            onSelectClip = onSelectClip,
                            onResizeTimedClip = onResizeTimedClip,
                        )
                    }
                }

                }

                // CapCut / Vita Fixed Center Playhead Indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .matchParentSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .weight(1f)
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineQuickCutChip(
    label: String,
    contentDesc: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        accent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MediaProShapes.Small,
        color = containerColor,
        border = BorderStroke(MediaProSpacing.BorderThin, borderColor),
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = contentDesc
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TimelineZoomButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MediaProShapes.Small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outline),
        modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
        }
    }
}

@Composable
private fun TimelineLaneHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimelineRuler(
    totalDurationMs: Long,
    dpPerSecond: Float,
    isEnabled: Boolean,
    visibleStartMs: Long = 0L,
    visibleEndMs: Long = totalDurationMs,
    beatMarkers: List<BeatMarker> = emptyList(),
    onSeekTimeline: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val stepMs = remember(dpPerSecond, totalDurationMs) { TimelineScale.rulerStepMs(dpPerSecond, totalDurationMs) }
    val latestOnSeek by rememberUpdatedState(onSeekTimeline)
    val diamondPath = remember { Path() }

    fun seekForPx(xPx: Float) {
        val xDp = with(density) { xPx.toDp().value }
        val ms = TimelineScale.dpToMs(xDp, dpPerSecond).coerceIn(0L, totalDurationMs)
        latestOnSeek(ms)
    }

    Box(
        modifier = modifier
            .pointerInput(isEnabled, dpPerSecond, totalDurationMs) {
                if (!isEnabled) return@pointerInput
                detectTapGestures { offset -> seekForPx(offset.x) }
            }
            .pointerInput(isEnabled, dpPerSecond, totalDurationMs) {
                if (!isEnabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    seekForPx(change.position.x)
                }
            },
    ) {
        val cullStartMs = (visibleStartMs - stepMs).coerceAtLeast(0L)
        val cullEndMs = (visibleEndMs + stepMs).coerceAtMost(totalDurationMs)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val minorStepMs = (stepMs / 2L).coerceAtLeast(500L)
            var tMs = (cullStartMs / minorStepMs) * minorStepMs
            while (tMs <= cullEndMs) {
                val xPx = with(density) { TimelineScale.msToDp(tMs, dpPerSecond).dp.toPx() }
                if (xPx > size.width) break
                val isMajor = tMs % stepMs == 0L
                val tickHeight = if (isMajor) size.height * 0.45f else size.height * 0.25f
                drawLine(
                    color = tickColor,
                    start = Offset(xPx, size.height - tickHeight),
                    end = Offset(xPx, size.height),
                    strokeWidth = if (isMajor) 2f else 1f,
                )
                tMs += minorStepMs
            }

            val diamondRadius = 5.dp.toPx()
            val diamondY = size.height - diamondRadius - 2f
            if (beatMarkers.isNotEmpty()) {
                var lo = 0
                var hi = beatMarkers.size
                while (lo < hi) {
                    val mid = (lo + hi) ushr 1
                    if (beatMarkers[mid].timeMs < cullStartMs) lo = mid + 1 else hi = mid
                }
                for (idx in lo until beatMarkers.size) {
                    val marker = beatMarkers[idx]
                    if (marker.timeMs > cullEndMs) break
                    val bx = with(density) { TimelineScale.msToDp(marker.timeMs, dpPerSecond).dp.toPx() }
                    if (bx in 0f..size.width) {
                        diamondPath.reset()
                        diamondPath.moveTo(bx, diamondY - diamondRadius)
                        diamondPath.lineTo(bx + diamondRadius, diamondY)
                        diamondPath.lineTo(bx, diamondY + diamondRadius)
                        diamondPath.lineTo(bx - diamondRadius, diamondY)
                        diamondPath.close()
                        drawPath(diamondPath, color = beatMarkerColor(marker.effect))
                    }
                }
            }
        }

        var labelMs = (cullStartMs / stepMs) * stepMs
        while (labelMs <= cullEndMs) {
            val xDp = TimelineScale.msToDp(labelMs, dpPerSecond).dp
            Text(
                text = ProjectTimeline.formatDuration(labelMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = labelColor,
                modifier = Modifier.offset(x = xDp + 2.dp),
            )
            labelMs += stepMs
        }
    }
}

@Composable
private fun TimelineSequentialLane(
    clips: List<Clip>,
    dpPerSecond: Float,
    laneHeight: Dp,
    selectedClipId: String?,
    isEnabled: Boolean,
    isAudioLane: Boolean,
    clipFilePaths: Map<String, String>,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContainerColor: Color,
    unselectedContentColor: Color,
    blockLabel: (Int, Clip) -> String,
    accessibilityLabel: @Composable (Int, String) -> String,
    onSelectClip: (String) -> Unit,
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(laneHeight),
    ) {
        clips.forEachIndexed { index, clip ->
            val clipNumber = index + 1
            val isSelected = clip.id == selectedClipId
            val blockWidthDp = TimelineScale.msToDp(clip.durationMs, dpPerSecond)
                .coerceAtLeast(TimelineScale.MIN_BLOCK_WIDTH_DP)
                .dp
            val formattedDuration = ProjectTimeline.formatDuration(clip.durationMs)
            val filePath = clipFilePaths[clip.id]

            TimelineRichClipBlock(
                clip = clip,
                filePath = filePath,
                isAudioLane = isAudioLane,
                dpPerSecond = dpPerSecond,
                blockWidth = blockWidthDp,
                laneHeight = laneHeight,
                label = blockLabel(clipNumber, clip),
                formattedDuration = formattedDuration,
                accessibilityLabel = accessibilityLabel(clipNumber, formattedDuration),
                isSelected = isSelected,
                isEnabled = isEnabled,
                containerColor = if (isSelected) selectedContainerColor else unselectedContainerColor,
                contentColor = if (isSelected) selectedContentColor else unselectedContentColor,
                onClick = { onSelectClip(clip.id) },
                onTrimCommit = { startMs, endMs -> onTrimClip(clip.id, startMs, endMs) },
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

@Composable
private fun TimelineRichClipBlock(
    clip: Clip,
    filePath: String?,
    isAudioLane: Boolean,
    dpPerSecond: Float,
    blockWidth: Dp,
    laneHeight: Dp,
    label: String,
    formattedDuration: String,
    accessibilityLabel: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onTrimCommit: (startMs: Long, endMs: Long) -> Unit,
) {
    val canDragTrim = isSelected && isEnabled && ProjectTimeline.canTrimClip(clip)
    val density = LocalDensity.current
    val latestOnTrim by rememberUpdatedState(onTrimCommit)
    var dragDeltaDp by remember(clip.id) { mutableFloatStateOf(0f) }
    var activeTrimEdge by remember(clip.id) { mutableStateOf<TimelineTrimEdge?>(null) }

    var filmstrip by remember(filePath, clip.trimStartMs, clip.trimEndMs, clip.isStillImage) {
        mutableStateOf<List<Bitmap>>(emptyList())
    }
    var waveform by remember(filePath, clip.trimStartMs, clip.trimEndMs) {
        mutableStateOf<List<Float>>(emptyList())
    }

    if (!isAudioLane && filePath != null) {
        val frameCount = TimelineScale.desiredThumbnailCount(blockWidth.value, maxFrames = 6)
        LaunchedEffect(filePath, clip.isStillImage, clip.trimStartMs, clip.trimEndMs, frameCount) {
            filmstrip = TimelineMediaCache.loadVideoFilmstrip(
                filePath = filePath,
                isStillImage = clip.isStillImage,
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
                frameCount = frameCount,
            )
        }
    }

    if (isAudioLane && filePath != null) {
        LaunchedEffect(filePath, clip.trimStartMs, clip.trimEndMs) {
            waveform = TimelineMediaCache.loadAudioWaveform(
                filePath = filePath,
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
            )
        }
    }

    val borderStroke = if (isSelected) {
        BorderStroke(MediaProSpacing.BorderSelected, contentColor.copy(alpha = 0.9f))
    } else {
        BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outline)
    }

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = MediaProShapes.Small,
        color = containerColor,
        contentColor = contentColor,
        border = borderStroke,
        modifier = Modifier
            .width(blockWidth)
            .height(laneHeight)
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                role = Role.Button
                selected = isSelected
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isAudioLane && filmstrip.isNotEmpty()) {
                val imageBitmaps = remember(filmstrip) { filmstrip.map { it.asImageBitmap() } }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tileSize = size.height.toInt().coerceAtLeast(1)
                    val totalW = size.width.toInt().coerceAtLeast(tileSize)
                    val tileCount = ((totalW + tileSize - 1) / tileSize).coerceAtLeast(1)
                    val frameCount = imageBitmaps.size
                    for (i in 0 until tileCount) {
                        val frameIdx = ((i * frameCount) / tileCount).coerceIn(0, frameCount - 1)
                        val x = i * tileSize
                        val drawW = (totalW - x).coerceAtMost(tileSize)
                        if (drawW > 0) {
                            drawImage(
                                image = imageBitmaps[frameIdx],
                                dstOffset = IntOffset(x, 0),
                                dstSize = IntSize(tileSize, tileSize),
                            )
                        }
                    }
                }
            }

            val badgeTextColor = if (!isAudioLane && filmstrip.isNotEmpty()) Color.White else contentColor
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = if (canDragTrim) TRIM_HANDLE_WIDTH_DP + 2.dp else 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeTextColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val displayDuration = if (activeTrimEdge != null) {
                    val deltaMs = TimelineScale.signedDpToMs(dragDeltaDp, dpPerSecond)
                    val edge = activeTrimEdge ?: TimelineTrimEdge.END
                    val (previewStart, previewEnd) = TimelineScale.computeDragTrimBounds(clip, edge, deltaMs)
                    val previewTimelineMs = SpeedRampEvaluator.scaledDurationMs(
                        sourceSpanMs = previewEnd - previewStart,
                        baseSpeed = clip.playbackSpeed,
                        preset = clip.speedRamp,
                    )
                    ProjectTimeline.formatDuration(previewTimelineMs)
                } else {
                    formattedDuration
                }
                Text(
                    text = displayDuration,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = badgeTextColor.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }

            if (canDragTrim) {
                listOf(
                    TimelineTrimEdge.START to Alignment.CenterStart,
                    TimelineTrimEdge.END to Alignment.CenterEnd,
                ).forEach { (edge, alignment) ->
                    TrimDragHandle(
                        edge = edge,
                        handleColor = contentColor,
                        modifier = Modifier
                            .align(alignment)
                            .pointerInput(clip, dpPerSecond, edge) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        activeTrimEdge = edge
                                        dragDeltaDp = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmountPx ->
                                        change.consume()
                                        dragDeltaDp += with(density) { dragAmountPx.toDp().value }
                                    },
                                    onDragEnd = {
                                        val deltaMs = TimelineScale.signedDpToMs(dragDeltaDp, dpPerSecond)
                                        val (newStart, newEnd) = TimelineScale.computeDragTrimBounds(
                                            clip = clip,
                                            edge = edge,
                                            deltaTimelineMs = deltaMs,
                                        )
                                        activeTrimEdge = null
                                        dragDeltaDp = 0f
                                        latestOnTrim(newStart, newEnd)
                                    },
                                    onDragCancel = {
                                        activeTrimEdge = null
                                        dragDeltaDp = 0f
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrimDragHandle(
    edge: TimelineTrimEdge,
    handleColor: Color,
    modifier: Modifier = Modifier,
) {
    val shape = if (edge == TimelineTrimEdge.START) {
        RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
    } else {
        RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(TRIM_HANDLE_WIDTH_DP)
            .fillMaxHeight()
            .clip(shape)
            .background(handleColor.copy(alpha = 0.85f)),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(18.dp)
                .background(Color.Black.copy(alpha = 0.65f)),
        )
    }
}

@Composable
private fun TimelineTimedLane(
    clips: List<Clip>,
    dpPerSecond: Float,
    totalWidthDp: Dp,
    selectedClipId: String?,
    isEnabled: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContainerColor: Color,
    unselectedContentColor: Color,
    blockLabel: (Int, Clip) -> String,
    accessibilityLabel: @Composable (Int, Clip, String) -> String,
    totalVideoDurationMs: Long,
    onSelectClip: (String) -> Unit,
    onResizeTimedClip: (clipId: String, startTimeMs: Long, durationMs: Long) -> Unit,
) {
    val density = LocalDensity.current
    val rows = remember(clips, dpPerSecond) { packOverlayRows(clips, dpPerSecond) }
    val currentOnResize by rememberUpdatedState(onResizeTimedClip)
    val maxTimelineMs = totalVideoDurationMs.coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
    Column(
        verticalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
        modifier = Modifier.width(totalWidthDp),
    ) {
        rows.forEach { rowEntries ->
            Box(
                modifier = Modifier
                    .width(totalWidthDp)
                    .height(TIMED_LANE_HEIGHT_DP),
            ) {
                rowEntries.forEach { (clipNumber, clip) ->
                    val isSelected = clip.id == selectedClipId
                    var dragEdge by remember(clip.id) { mutableStateOf<TimelineTrimEdge?>(null) }
                    var dragStartMs by remember(clip.id, clip.startTimeMs) { mutableStateOf(clip.startTimeMs) }
                    var dragDurationMs by remember(clip.id, clip.durationMs) { mutableStateOf(clip.durationMs) }

                    val displayStartMs = if (dragEdge != null) dragStartMs else clip.startTimeMs
                    val displayDurationMs = if (dragEdge != null) dragDurationMs else clip.durationMs
                    val formattedDuration = ProjectTimeline.formatDuration(displayDurationMs)
                    val clipContentDescription = accessibilityLabel(clipNumber, clip, formattedDuration)
                    val startOffsetDp = TimelineScale.msToDp(displayStartMs, dpPerSecond).dp
                    val blockWidthDp = TimelineScale.msToDp(displayDurationMs, dpPerSecond)
                        .coerceAtLeast(TimelineScale.MIN_BLOCK_WIDTH_DP)
                        .dp
                    val borderStroke = if (isSelected) {
                        BorderStroke(MediaProSpacing.BorderSelected, selectedContentColor.copy(alpha = 0.85f))
                    } else {
                        BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outline)
                    }
                    val canDragResize = isSelected && isEnabled && totalVideoDurationMs >= ProjectTimeline.MIN_CLIP_DURATION_MS

                    Box(
                        modifier = Modifier
                            .offset(x = startOffsetDp)
                            .width(blockWidthDp)
                            .height(TIMED_LANE_HEIGHT_DP),
                    ) {
                        Surface(
                            onClick = { onSelectClip(clip.id) },
                            enabled = isEnabled,
                            shape = MediaProShapes.Small,
                            color = if (isSelected) selectedContainerColor else unselectedContainerColor,
                            contentColor = if (isSelected) selectedContentColor else unselectedContentColor,
                            border = borderStroke,
                            modifier = Modifier
                                .fillMaxSize()
                                .clearAndSetSemantics {
                                    contentDescription = clipContentDescription
                                    role = Role.Button
                                    selected = isSelected
                                },
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    horizontal = if (canDragResize) TRIM_HANDLE_WIDTH_DP + 2.dp else MediaProSpacing.ExtraSmall,
                                ),
                            ) {
                                Text(
                                    text = blockLabel(clipNumber, clip),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formattedDuration,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }

                        if (canDragResize) {
                            val clipEndMs = ProjectTimeline.saturatingAdd(clip.startTimeMs, clip.durationMs).coerceAtMost(maxTimelineMs)
                            TrimDragHandle(
                                edge = TimelineTrimEdge.START,
                                handleColor = selectedContentColor,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .pointerInput(clip.id, clip.startTimeMs, clip.durationMs, dpPerSecond, maxTimelineMs) {
                                        var accDp = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                dragEdge = TimelineTrimEdge.START
                                                dragStartMs = clip.startTimeMs
                                                dragDurationMs = clip.durationMs
                                                accDp = 0f
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                accDp += with(density) { dragAmount.toDp().value }
                                                val deltaMs = TimelineScale.signedDpToMs(accDp, dpPerSecond)
                                                val maxStart = (clipEndMs - ProjectTimeline.MIN_CLIP_DURATION_MS).coerceAtLeast(0L)
                                                val nextStart = if (deltaMs >= 0L) {
                                                    ProjectTimeline.saturatingAdd(clip.startTimeMs, deltaMs).coerceIn(0L, maxStart)
                                                } else {
                                                    (clip.startTimeMs + deltaMs).coerceIn(0L, maxStart)
                                                }
                                                dragStartMs = nextStart
                                                dragDurationMs = (clipEndMs - nextStart).coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
                                            },
                                            onDragEnd = {
                                                val finalStart = dragStartMs
                                                val finalDuration = dragDurationMs
                                                dragEdge = null
                                                if (finalStart != clip.startTimeMs || finalDuration != clip.durationMs) {
                                                    currentOnResize(clip.id, finalStart, finalDuration)
                                                }
                                            },
                                            onDragCancel = { dragEdge = null },
                                        )
                                    },
                            )
                            TrimDragHandle(
                                edge = TimelineTrimEdge.END,
                                handleColor = selectedContentColor,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .pointerInput(clip.id, clip.startTimeMs, clip.durationMs, dpPerSecond, maxTimelineMs) {
                                        var accDp = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                dragEdge = TimelineTrimEdge.END
                                                dragStartMs = clip.startTimeMs
                                                dragDurationMs = clip.durationMs
                                                accDp = 0f
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                accDp += with(density) { dragAmount.toDp().value }
                                                val deltaMs = TimelineScale.signedDpToMs(accDp, dpPerSecond)
                                                val maxDuration = (maxTimelineMs - clip.startTimeMs).coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
                                                dragDurationMs = if (deltaMs >= 0L) {
                                                    ProjectTimeline.saturatingAdd(clip.durationMs, deltaMs).coerceIn(ProjectTimeline.MIN_CLIP_DURATION_MS, maxDuration)
                                                } else {
                                                    (clip.durationMs + deltaMs).coerceIn(ProjectTimeline.MIN_CLIP_DURATION_MS, maxDuration)
                                                }
                                            },
                                            onDragEnd = {
                                                val finalStart = dragStartMs
                                                val finalDuration = dragDurationMs
                                                dragEdge = null
                                                if (finalDuration != clip.durationMs) {
                                                    currentOnResize(clip.id, finalStart, finalDuration)
                                                }
                                            },
                                            onDragCancel = { dragEdge = null },
                                        )
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun packOverlayRows(
    clips: List<Clip>,
    dpPerSecond: Float,
): List<List<Pair<Int, Clip>>> {
    val rows = mutableListOf<MutableList<Pair<Int, Clip>>>()
    val rowRightEdgesDp = mutableListOf<Float>()
    clips.forEachIndexed { index, clip ->
        val startDp = TimelineScale.msToDp(clip.startTimeMs, dpPerSecond)
        val widthDp = TimelineScale.msToDp(clip.durationMs, dpPerSecond)
            .coerceAtLeast(TimelineScale.MIN_BLOCK_WIDTH_DP)
        val endDp = startDp + widthDp
        val targetRow = rowRightEdgesDp.indexOfFirst { rightEdge -> startDp >= rightEdge + 6f }
        if (targetRow >= 0) {
            rows[targetRow].add((index + 1) to clip)
            rowRightEdgesDp[targetRow] = endDp
        } else {
            rows.add(mutableListOf((index + 1) to clip))
            rowRightEdgesDp.add(endDp)
        }
    }
    return rows
}
