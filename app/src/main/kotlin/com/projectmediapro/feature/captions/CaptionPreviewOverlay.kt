package com.projectmediapro.feature.captions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.projectmediapro.core.model.OccupiedBand
import kotlin.math.roundToInt

data class FittedVideoRect(
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
)

fun computeFittedVideoRect(
    containerWidthPx: Int,
    containerHeightPx: Int,
    videoWidth: Int,
    videoHeight: Int,
): FittedVideoRect {
    if (containerWidthPx <= 0 || containerHeightPx <= 0) {
        return FittedVideoRect(0, 0, 0, 0)
    }
    if (videoWidth <= 0 || videoHeight <= 0) {
        return FittedVideoRect(0, 0, containerWidthPx, containerHeightPx)
    }

    val containerAspect = containerWidthPx.toFloat() / containerHeightPx.toFloat()
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()

    return if (videoAspect > containerAspect) {
        val fittedHeight = (containerWidthPx / videoAspect).roundToInt().coerceIn(1, containerHeightPx)
        val top = (containerHeightPx - fittedHeight) / 2
        FittedVideoRect(0, top, containerWidthPx, fittedHeight)
    } else {
        val fittedWidth = (containerHeightPx * videoAspect).roundToInt().coerceIn(1, containerWidthPx)
        val left = (containerWidthPx - fittedWidth) / 2
        FittedVideoRect(left, 0, fittedWidth, containerHeightPx)
    }
}

@Composable
fun CaptionPreviewOverlay(
    document: CaptionDocument?,
    currentPositionUs: Long,
    videoWidth: Int,
    videoHeight: Int,
    modifier: Modifier = Modifier,
    occupiedBands: List<OccupiedBand> = emptyList(),
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier.onSizeChanged { containerSize = it },
    ) {
        if (document != null && containerSize.width > 0 && containerSize.height > 0) {
            val rect = remember(containerSize, videoWidth, videoHeight) {
                computeFittedVideoRect(
                    containerWidthPx = containerSize.width,
                    containerHeightPx = containerSize.height,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                )
            }
            val frameState = remember(document, currentPositionUs) {
                CaptionFrameEvaluator.evaluate(document, currentPositionUs)
            }
            if (frameState.hasVisibleContent && rect.widthPx > 0 && rect.heightPx > 0) {
                val widthDp = with(density) { rect.widthPx.toDp() }
                val heightDp = with(density) { rect.heightPx.toDp() }

                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(rect.leftPx, rect.topPx) }
                        .size(width = widthDp, height = heightDp),
                ) {
                    drawIntoCanvas { canvas ->
                        CaptionCanvasRenderer.render(
                            canvas = canvas.nativeCanvas,
                            widthPx = size.width,
                            heightPx = size.height,
                            frameState = frameState,
                            occupiedBands = occupiedBands,
                        )
                    }
                }
            }
        }
    }
}
