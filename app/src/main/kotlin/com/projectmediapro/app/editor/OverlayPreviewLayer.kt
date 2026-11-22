package com.projectmediapro.app.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import com.projectmediapro.core.model.Clip
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREVIEW_MAX_DECODE_SIDE_PX = 1024

@Composable
internal fun OverlayPreviewLayer(
    overlays: List<Clip>,
    overlayFilePaths: Map<String, String>,
    timelinePositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val visibleOverlays = overlays.filter { TimelineClock.isOverlayVisibleAt(it, timelinePositionMs) }
    if (visibleOverlays.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()
        val canvasWidthDp = maxWidth

        visibleOverlays.forEach { clip ->
            key(clip.id) {
                val path = overlayFilePaths[clip.id] ?: return@key
                val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
                    value = withContext(Dispatchers.IO) {
                        decodePreviewImageBitmap(path)
                    }
                }
                val loaded = bitmap ?: return@key
            val widthFraction = clip.overlayWidthFraction.coerceIn(
                Clip.MIN_OVERLAY_WIDTH_FRACTION,
                Clip.MAX_OVERLAY_WIDTH_FRACTION,
            )
            val overlayWidthPx = canvasWidthPx * widthFraction
            val imageAspect = if (loaded.height > 0) {
                loaded.width.toFloat() / loaded.height.toFloat()
            } else {
                1f
            }
            val overlayHeightPx = overlayWidthPx / imageAspect
            val leftPx = (clip.overlayCenterX.coerceIn(0f, 1f) * canvasWidthPx) - (overlayWidthPx / 2f)
            val topPx = (clip.overlayCenterY.coerceIn(0f, 1f) * canvasHeightPx) - (overlayHeightPx / 2f)

            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .width(canvasWidthDp * widthFraction),
                )
            }
        }
    }
}

private fun decodePreviewImageBitmap(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longSide / (sampleSize * 2) >= PREVIEW_MAX_DECODE_SIDE_PX) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
