package com.projectmediapro.feature.news

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.projectmediapro.core.model.NewsOverlayItem

/**
 * Live Compose preview layer for all 10 animated news & broadcast overlays, sharing
 * [NewsOverlayRenderer] 1:1 with the Media3 Transformer export pipeline.
 */
@Composable
fun NewsPreviewOverlay(
    overlays: List<NewsOverlayItem>,
    timelinePositionMs: Long,
    captionsActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (overlays.isEmpty()) return
    val context = LocalContext.current
    val bitmapCache = remember(context) { NewsOverlayBitmapCache(context) }
    DisposableEffect(bitmapCache) {
        onDispose { bitmapCache.clear() }
    }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                NewsOverlayRenderer.renderAll(
                    canvas = canvas.nativeCanvas,
                    widthPx = size.width,
                    heightPx = size.height,
                    overlays = overlays,
                    timeMs = timelinePositionMs,
                    captionsActive = captionsActive,
                    bitmapLoader = { uri -> bitmapCache.load(uri) },
                )
            }
        }
    }
}
