package com.projectmediapro.app.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.model.Clip
import kotlin.math.abs

@Stable
internal class PreviewCanvasDragState(
    initialCenterX: Float,
    initialCenterY: Float,
    initialWidthFraction: Float,
) {
    var isDragging by mutableStateOf(false)
    var centerX by mutableFloatStateOf(initialCenterX)
    var centerY by mutableFloatStateOf(initialCenterY)
    var widthFraction by mutableFloatStateOf(initialWidthFraction)
}

@Composable
internal fun rememberPreviewCanvasDragState(
    clipId: String,
    centerX: Float,
    centerY: Float,
    widthFraction: Float,
): PreviewCanvasDragState = remember(clipId) {
    PreviewCanvasDragState(centerX, centerY, widthFraction)
}

@Composable
internal fun Modifier.previewCanvasSelectionBorder(
    isSelected: Boolean,
    isInteractive: Boolean,
): Modifier = if (isSelected && isInteractive) {
    this.border(
        width = 1.5.dp,
        color = MaterialTheme.colorScheme.primary,
        shape = MediaProShapes.Small,
    )
} else {
    this
}

@Composable
internal fun Modifier.previewCanvasTransformGesture(
    clipId: String,
    isInteractive: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    baseCenterX: Float,
    baseCenterY: Float,
    baseWidthFraction: Float,
    dragState: PreviewCanvasDragState,
    onSelect: (String) -> Unit,
    onCommit: (clipId: String, centerX: Float, centerY: Float, widthFraction: Float) -> Unit,
): Modifier {
    if (!isInteractive || canvasWidthPx <= 0f || canvasHeightPx <= 0f) return this

    val latestCenterX by rememberUpdatedState(baseCenterX)
    val latestCenterY by rememberUpdatedState(baseCenterY)
    val latestWidthFraction by rememberUpdatedState(baseWidthFraction)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnCommit by rememberUpdatedState(onCommit)

    return this.pointerInput(clipId, canvasWidthPx, canvasHeightPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            currentOnSelect(clipId)
            dragState.centerX = latestCenterX
            dragState.centerY = latestCenterY
            dragState.widthFraction = latestWidthFraction
            var moved = false
            try {
                do {
                    val event = awaitPointerEvent()
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    if (abs(pan.x) > 0.5f || abs(pan.y) > 0.5f || abs(zoom - 1f) > 0.005f) {
                        moved = true
                        dragState.isDragging = true
                        dragState.centerX = (dragState.centerX + pan.x / canvasWidthPx).coerceIn(0f, 1f)
                        dragState.centerY = (dragState.centerY + pan.y / canvasHeightPx).coerceIn(0f, 1f)
                        dragState.widthFraction = (dragState.widthFraction * zoom).coerceIn(
                            Clip.MIN_OVERLAY_WIDTH_FRACTION,
                            Clip.MAX_OVERLAY_WIDTH_FRACTION,
                        )
                    }
                    event.changes.forEach { change ->
                        if (change.positionChanged() || !change.pressed) {
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                dragState.isDragging = false
            }
            if (moved) {
                currentOnCommit(
                    clipId,
                    dragState.centerX,
                    dragState.centerY,
                    dragState.widthFraction,
                )
            }
        }
    }
}
