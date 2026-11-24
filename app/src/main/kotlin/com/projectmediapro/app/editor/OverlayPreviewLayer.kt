package com.projectmediapro.app.editor

import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.KeyframeInterpolator
import com.projectmediapro.app.data.export.OverlayBitmapStyler
import com.projectmediapro.core.model.OverlayKeyframe
import com.projectmediapro.core.model.OverlayVisualStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREVIEW_MAX_DECODE_SIDE_PX = 1024
private const val OVERLAY_PREVIEW_CACHE_MAX_BYTES = 16 * 1024 * 1024

private data class OverlayPreviewCacheKey(
    val path: String,
    val fileLength: Long,
    val lastModifiedMs: Long,
    val style: OverlayVisualStyle?,
)

private val overlayPreviewCache = object : LruCache<OverlayPreviewCacheKey, ImageBitmap>(
    OVERLAY_PREVIEW_CACHE_MAX_BYTES,
) {
    override fun sizeOf(key: OverlayPreviewCacheKey, value: ImageBitmap): Int {
        val estimatedBytes = value.width.toLong() * value.height.toLong() * 4L
        return estimatedBytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
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
    onSelectOverlay: (String) -> Unit = {},
    onCommitOverlayTransform: (clipId: String, centerX: Float, centerY: Float, widthFraction: Float) -> Unit = { _, _, _, _ -> },
) {
    DisposableEffect(Unit) {
        onDispose { overlayPreviewCache.evictAll() }
    }
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
                    val effectiveStyle = clip.overlayStyle?.sanitized()
                    val styleOpacity = effectiveStyle?.opacity ?: OverlayVisualStyle.DEFAULT_OPACITY
                    val bitmapStyleKey = effectiveStyle?.takeIf { it.requiresBitmapProcessing }
                    val bitmap by produceState<ImageBitmap?>(
                        initialValue = null,
                        key1 = path,
                        key2 = bitmapStyleKey,
                    ) {
                        value = withContext(Dispatchers.IO) {
                            val fileMeta = File(path)
                            val cacheKey = OverlayPreviewCacheKey(
                                path = path,
                                fileLength = fileMeta.length(),
                                lastModifiedMs = fileMeta.lastModified(),
                                style = bitmapStyleKey,
                            )
                            overlayPreviewCache.get(cacheKey)
                                ?: decodePreviewImageBitmap(path, bitmapStyleKey)?.also { decoded ->
                                    overlayPreviewCache.put(cacheKey, decoded)
                                }
                        }
                    }
                    val loaded = bitmap
                    if (loaded != null) {
                        val baseWidthFraction = OverlayKeyframe.sanitizeFinite(
                            clip.overlayWidthFraction,
                            Clip.DEFAULT_OVERLAY_WIDTH_FRACTION,
                        ).coerceIn(
                            Clip.MIN_OVERLAY_WIDTH_FRACTION,
                            Clip.MAX_OVERLAY_WIDTH_FRACTION,
                        )
                        val resolved = KeyframeInterpolator.evaluateAtTime(
                            baseCenterX = clip.overlayCenterX,
                            baseCenterY = clip.overlayCenterY,
                            keyframes = clip.keyframes,
                            elapsedTime = timelinePositionMs - clip.startTimeMs,
                            totalDuration = clip.durationMs,
                        )
                        val dragState = rememberPreviewCanvasDragState(
                            clipId = clip.id,
                            centerX = resolved.centerX,
                            centerY = resolved.centerY,
                            widthFraction = baseWidthFraction,
                        )

                        val displayCenterX = if (dragState.isDragging) dragState.centerX else resolved.centerX
                        val displayCenterY = if (dragState.isDragging) dragState.centerY else resolved.centerY
                        val displayWidthFraction = if (dragState.isDragging) dragState.widthFraction else baseWidthFraction

                        val overlayWidthPx = canvasWidthPx * displayWidthFraction
                        val imageAspect = if (loaded.height > 0) {
                            loaded.width.toFloat() / loaded.height.toFloat()
                        } else {
                            1f
                        }
                        val overlayHeightPx = overlayWidthPx / imageAspect
                        val leftPx = (displayCenterX * canvasWidthPx) - (overlayWidthPx / 2f)
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
                                .previewCanvasSelectionBorder(
                                    isSelected = selectedClipId == clip.id,
                                    isInteractive = isInteractive,
                                )
                                .previewCanvasTransformGesture(
                                    clipId = clip.id,
                                    isInteractive = isInteractive,
                                    canvasWidthPx = canvasWidthPx,
                                    canvasHeightPx = canvasHeightPx,
                                    baseCenterX = resolved.centerX,
                                    baseCenterY = resolved.centerY,
                                    baseWidthFraction = baseWidthFraction,
                                    dragState = dragState,
                                    onSelect = onSelectOverlay,
                                    onCommit = onCommitOverlayTransform,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private fun decodePreviewImageBitmap(
    path: String,
    style: OverlayVisualStyle?,
): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longSide / (sampleSize * 2) >= PREVIEW_MAX_DECODE_SIDE_PX) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = BitmapFactory.decodeFile(path, options) ?: return null
    val styled = OverlayBitmapStyler.applyStyle(decoded, style)
    if (styled !== decoded && !decoded.isRecycled) {
        decoded.recycle()
    }
    return styled.asImageBitmap()
}
