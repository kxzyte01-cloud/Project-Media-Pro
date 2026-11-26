package com.projectmediapro.feature.news

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.projectmediapro.core.model.NewsOverlayItem

/**
 * Live Compose preview layer for animated news & broadcast overlays, sharing [NewsOverlayRenderer]
 * 1:1 with the Media3 Transformer export pipeline.
 */
@Composable
fun NewsPreviewOverlay(
    overlays: List<NewsOverlayItem>,
    timelinePositionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (overlays.isEmpty()) return
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                NewsOverlayRenderer.renderAll(
                    canvas = canvas.nativeCanvas,
                    widthPx = size.width,
                    heightPx = size.height,
                    overlays = overlays,
                    timeMs = timelinePositionMs,
                )
            }
        }
    }
}
