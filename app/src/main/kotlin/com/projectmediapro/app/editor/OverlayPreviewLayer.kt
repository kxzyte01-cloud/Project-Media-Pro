package com.projectmediapro.app.editor

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.projectmediapro.app.R
import com.projectmediapro.app.data.export.ChromaKeyGlEffect
import com.projectmediapro.app.data.export.OverlayBitmapStyler
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.KeyframeInterpolator
import com.projectmediapro.core.model.OverlayKeyframe
import com.projectmediapro.core.model.OverlayVisualStyle
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREVIEW_MAX_DECODE_SIDE_PX = 1024
private const val OVERLAY_PREVIEW_CACHE_MAX_BYTES = 16 * 1024 * 1024
private const val PIP_SEEK_DRIFT_THRESHOLD_MS = 300L

private data class OverlayPreviewCacheKey(
    val path: String,
    val fileLength: Long,
    val lastModifiedMs: Long,
    val style: OverlayVisualStyle?,
)

private val overlayPreviewCache = object : LruCache<OverlayPreviewCacheKey, ImageBitmap>(OVERLAY_PREVIEW_CACHE_MAX_BYTES) {
    override fun sizeOf(key: OverlayPreviewCacheKey, value: ImageBitmap): Int =
        (value.width.toLong() * value.height.toLong() * 4L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

internal fun clearOverlayPreviewCache() {
    overlayPreviewCache.evictAll()
}

@Composable
internal fun OverlayPreviewLayer(
    overlays: List<Clip>,
    overlayFilePaths: Map<String, String>,
    timelinePositionMs: Long,
    modifier: Modifier = Modifier,
    selectedClipId: String? = null,
    isInteractive: Boolean = false,
    isPlaying: Boolean = false,
    onSelectOverlay: (String) -> Unit = {},
    onCommitOverlayTransform: (clipId: String, centerX: Float, centerY: Float, widthFraction: Float) -> Unit = { _, _, _, _ -> },
) {
    DisposableEffect(Unit) { onDispose { overlayPreviewCache.evictAll() } }
    val visibleOverlays = overlays.filter { TimelineClock.isOverlayVisibleAt(it, timelinePositionMs) }
    if (visibleOverlays.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()
        val canvasWidthDp = maxWidth
        visibleOverlays.forEach { clip ->
            key(clip.id) {
                val path = overlayFilePaths[clip.id]
                if (path != null) {
                    val baseWidthFraction = OverlayKeyframe.sanitizeFinite(
                        clip.overlayWidthFraction,
                        Clip.DEFAULT_OVERLAY_WIDTH_FRACTION,
                    ).coerceIn(Clip.MIN_OVERLAY_WIDTH_FRACTION, Clip.MAX_OVERLAY_WIDTH_FRACTION)
                    val resolved = KeyframeInterpolator.evaluateAtTime(
                        baseCenterX = clip.overlayCenterX,
                        baseCenterY = clip.overlayCenterY,
                        keyframes = clip.keyframes,
                        elapsedTime = timelinePositionMs - clip.startTimeMs,
                        totalDuration = clip.durationMs,
                    )
                    val dragState = rememberPreviewCanvasDragState(
                        clip.id,
                        resolved.centerX,
                        resolved.centerY,
                        baseWidthFraction,
                    )
                    val displayCenterX = if (dragState.isDragging) dragState.centerX else resolved.centerX
                    val displayCenterY = if (dragState.isDragging) dragState.centerY else resolved.centerY
                    val displayWidthFraction = if (dragState.isDragging) dragState.widthFraction else baseWidthFraction
                    val overlayWidthPx = canvasWidthPx * displayWidthFraction
                    val leftPx = (displayCenterX * canvasWidthPx) - (overlayWidthPx / 2f)
                    val styleOpacity = clip.overlayStyle?.sanitized()?.opacity ?: OverlayVisualStyle.DEFAULT_OPACITY

                    if (clip.isVideo) {
                        var videoAspect by remember(clip.id) { mutableFloatStateOf(16f / 9f) }
                        val safeAspect = videoAspect.coerceIn(0.35f, 3.0f)
                        val overlayHeightPx = overlayWidthPx / safeAspect
                        val topPx = (displayCenterY * canvasHeightPx) - (overlayHeightPx / 2f)
                        VideoOverlaySurface(
                            clip = clip,
                            path = path,
                            timelinePositionMs = timelinePositionMs,
                            isPlaying = isPlaying,
                            onAspectRatioKnown = { aspect -> videoAspect = aspect },
                            modifier = Modifier
                                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                                .width(canvasWidthDp * displayWidthFraction)
                                .aspectRatio(safeAspect)
                                .graphicsLayer {
                                    scaleX = resolved.scale
                                    scaleY = resolved.scale
                                    rotationZ = resolved.rotationDegrees
                                    alpha = (resolved.opacity * styleOpacity).coerceIn(0f, 1f)
                                }
                                .previewCanvasSelectionBorder(selectedClipId == clip.id, isInteractive)
                                .previewCanvasTransformGesture(
                                    clip.id,
                                    isInteractive,
                                    canvasWidthPx,
                                    canvasHeightPx,
                                    resolved.centerX,
                                    resolved.centerY,
                                    baseWidthFraction,
                                    dragState,
                                    onSelectOverlay,
                                    onCommitOverlayTransform,
                                ),
                        )
                    } else {
                        val effectiveStyle = clip.overlayStyle?.sanitized()
                        val bitmapStyleKey = effectiveStyle?.takeIf { it.requiresBitmapProcessing }
                        val bitmap by produceState<ImageBitmap?>(null, path, bitmapStyleKey) {
                            value = withContext(Dispatchers.IO) {
                                val meta = File(path)
                                val cacheKey = OverlayPreviewCacheKey(
                                    path,
                                    meta.length(),
                                    meta.lastModified(),
                                    bitmapStyleKey,
                                )
                                overlayPreviewCache.get(cacheKey)
                                    ?: decodePreviewImageBitmap(path, bitmapStyleKey)?.also {
                                        overlayPreviewCache.put(cacheKey, it)
                                    }
                            }
                        }
                        val loaded = bitmap
                        if (loaded != null) {
                            val imageAspect = if (loaded.height > 0) loaded.width.toFloat() / loaded.height else 1f
                            val overlayHeightPx = overlayWidthPx / imageAspect
                            val topPx = (displayCenterY * canvasHeightPx) - (overlayHeightPx / 2f)
                            Image(
                                bitmap = loaded,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                                    .width(canvasWidthDp * displayWidthFraction)
                                    .graphicsLayer {
                                        scaleX = resolved.scale
                                        scaleY = resolved.scale
                                        rotationZ = resolved.rotationDegrees
                                        alpha = (resolved.opacity * styleOpacity).coerceIn(0f, 1f)
                                    }
                                    .previewCanvasSelectionBorder(selectedClipId == clip.id, isInteractive)
                                    .previewCanvasTransformGesture(
                                        clip.id,
                                        isInteractive,
                                        canvasWidthPx,
                                        canvasHeightPx,
                                        resolved.centerX,
                                        resolved.centerY,
                                        baseWidthFraction,
                                        dragState,
                                        onSelectOverlay,
                                        onCommitOverlayTransform,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoOverlaySurface(
    clip: Clip,
    path: String,
    timelinePositionMs: Long,
    isPlaying: Boolean,
    onAspectRatioKnown: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(clip.id) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onAspectRatioKnown(videoSize.width.toFloat() / videoSize.height.toFloat())
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(path) {
        val file = File(path)
        if (!file.isFile) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
    }
    LaunchedEffect(clip.chromaKey) {
        val effects = if (clip.chromaKey.enabled) listOf(ChromaKeyGlEffect(clip.chromaKey)) else emptyList()
        player.setVideoEffects(effects)
    }
    LaunchedEffect(timelinePositionMs, clip.startTimeMs, isPlaying, clip.muted, clip.volume) {
        val localMs = (timelinePositionMs - clip.startTimeMs).coerceAtLeast(0L)
        if (!isPlaying || abs(player.currentPosition - localMs) > PIP_SEEK_DRIFT_THRESHOLD_MS) {
            player.seekTo(localMs)
        }
        player.volume = if (clip.muted) 0f else clip.volume.coerceIn(0f, 1f)
        player.playWhenReady = isPlaying
    }
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.view_preview_player, null, false) as PlayerView).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

private fun decodePreviewImageBitmap(path: String, style: OverlayVisualStyle?): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longSide / (sampleSize * 2) >= PREVIEW_MAX_DECODE_SIDE_PX) sampleSize *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null
    val styled = OverlayBitmapStyler.applyStyle(decoded, style)
    if (styled !== decoded && !decoded.isRecycled) decoded.recycle()
    return styled.asImageBitmap()
}
