package com.projectmediapro.app.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import java.util.Locale

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
    clipFilePaths: Map<String, String> = emptyMap(),
    timelinePositionMs: Long = 0L,
    totalDurationMs: Long = 0L,
    onSeekTimeline: (Long) -> Unit = {},
    onTrimClip: (clipId: String, startMs: Long, endMs: Long) -> Unit = { _, _, _ -> },
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
    val contentWidthDp = remember(effectiveTotalMs, dpPerSecond) {
        (TimelineScale.msToDp(effectiveTotalMs, dpPerSecond).coerceAtLeast(240f)).dp + TIMELINE_TRAILING_PAD_DP
    }
    val playheadOffsetDp = remember(timelinePositionMs, effectiveTotalMs, dpPerSecond) {
        TimelineScale.msToDp(timelinePositionMs.coerceIn(0L, effectiveTotalMs), dpPerSecond).dp
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(MediaProSpacing.Small),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.width(contentWidthDp),
                ) {
                    TimelineRuler(
                        totalDurationMs = effectiveTotalMs,
                        dpPerSecond = dpPerSecond,
                        isEnabled = isEnabled,
                        onSeekTimeline = onSeekTimeline,
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
                            onSelectClip = onSelectClip,
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
                            onSelectClip = onSelectClip,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = playheadOffsetDp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
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
        modifier = Modifier.defaultMinSize(minWidth = 34.dp, minHeight = 34.dp),
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
    onSeekTimeline: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val stepMs = remember(dpPerSecond, totalDurationMs) { TimelineScale.rulerStepMs(dpPerSecond, totalDurationMs) }
    val latestOnSeek by rememberUpdatedState(onSeekTimeline)

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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minorStepMs = (stepMs / 2L).coerceAtLeast(500L)
            var tMs = 0L
            while (tMs <= totalDurationMs) {
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
        }

        var labelMs = 0L
        while (labelMs <= totalDurationMs) {
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
        val frameCount = TimelineScale.desiredThumbnailCount(blockWidth.value)
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
                Row(modifier = Modifier.fillMaxSize()) {
                    filmstrip.forEach { bitmap ->
                        if (!bitmap.isRecycled) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isSelected) 0.28f else 0.42f)),
                )
            }

            if (isAudioLane && waveform.isNotEmpty()) {
                val barColor = contentColor.copy(alpha = 0.65f)
                val volumeGain = ProjectTimeline.sanitizeVolume(clip.volume)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    val stepX = size.width / waveform.size.coerceAtLeast(1)
                    val centerY = size.height / 2f
                    val maxHalfHeight = size.height * 0.45f
                    val stroke = (stepX * 0.6f).coerceIn(1.5f, 4f)
                    waveform.forEachIndexed { idx, peak ->
                        val amplitude = (peak * volumeGain).coerceIn(0f, 1f)
                        val half = (amplitude * maxHalfHeight).coerceAtLeast(1.5f)
                        val x = idx * stepX + stepX / 2f
                        drawLine(
                            color = barColor,
                            start = Offset(x, centerY - half),
                            end = Offset(x, centerY + half),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
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
                    val previewTimelineMs = ProjectTimeline.scaledDurationMs(
                        previewEnd - previewStart,
                        clip.playbackSpeed,
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
    onSelectClip: (String) -> Unit,
) {
    val rows = remember(clips, dpPerSecond) { packOverlayRows(clips, dpPerSecond) }
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
                    val formattedDuration = ProjectTimeline.formatDuration(clip.durationMs)
                    val clipContentDescription = accessibilityLabel(clipNumber, clip, formattedDuration)
                    val startOffsetDp = TimelineScale.msToDp(clip.startTimeMs, dpPerSecond).dp
                    val blockWidthDp = TimelineScale.msToDp(clip.durationMs, dpPerSecond)
                        .coerceAtLeast(TimelineScale.MIN_BLOCK_WIDTH_DP)
                        .dp
                    val borderStroke = if (isSelected) {
                        BorderStroke(MediaProSpacing.BorderSelected, selectedContentColor.copy(alpha = 0.85f))
                    } else {
                        BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outline)
                    }
                    Surface(
                        onClick = { onSelectClip(clip.id) },
                        enabled = isEnabled,
                        shape = MediaProShapes.Small,
                        color = if (isSelected) selectedContainerColor else unselectedContainerColor,
                        contentColor = if (isSelected) selectedContentColor else unselectedContentColor,
                        border = borderStroke,
                        modifier = Modifier
                            .offset(x = startOffsetDp)
                            .width(blockWidthDp)
                            .height(TIMED_LANE_HEIGHT_DP)
                            .clearAndSetSemantics {
                                contentDescription = clipContentDescription
                                role = Role.Button
                                selected = isSelected
                            },
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = MediaProSpacing.ExtraSmall),
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
