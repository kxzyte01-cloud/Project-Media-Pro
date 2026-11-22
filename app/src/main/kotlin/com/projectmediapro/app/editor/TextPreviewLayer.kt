package com.projectmediapro.app.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.projectmediapro.app.data.export.TextOverlayBitmapRenderer
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.TextOverlayStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TEXT_PREVIEW_MAX_SIDE_PX = 1024

@Composable
internal fun rememberRenderedTextBitmap(
    text: String,
    style: TextOverlayStyle,
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    maxWidthFraction: Float = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
): ImageBitmap? {
    val renderKey = TextRenderKey(
        text = text,
        style = style,
        renderWidthPx = canvasWidthPx,
        renderHeightPx = canvasHeightPx,
        widthFraction = maxWidthFraction,
    )
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = renderKey) {
        value = withContext(Dispatchers.Default) {
            TextOverlayBitmapRenderer.render(
                text = text,
                style = style,
                canvasWidthPx = canvasWidthPx,
                canvasHeightPx = canvasHeightPx,
                maxWidthFraction = maxWidthFraction,
            )?.asImageBitmap()
        }
    }
    return bitmap
}

@Composable
internal fun TextPreviewLayer(
    textClips: List<Clip>,
    timelinePositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val visibleTexts = textClips.filter {
        it.textContent.isNotBlank() && TimelineClock.isOverlayVisibleAt(it, timelinePositionMs)
    }
    if (visibleTexts.isEmpty()) return

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidthPx = constraints.maxWidth
        val canvasHeightPx = constraints.maxHeight
        if (canvasWidthPx <= 0 || canvasHeightPx <= 0) return@BoxWithConstraints

        val longSide = maxOf(canvasWidthPx, canvasHeightPx)
        val renderScale = if (longSide > TEXT_PREVIEW_MAX_SIDE_PX) {
            TEXT_PREVIEW_MAX_SIDE_PX.toFloat() / longSide.toFloat()
        } else {
            1f
        }
        val renderWidthPx = (canvasWidthPx * renderScale).roundToInt().coerceAtLeast(1)
        val renderHeightPx = (canvasHeightPx * renderScale).roundToInt().coerceAtLeast(1)

        visibleTexts.forEach { clip ->
            key(clip.id) {
                val style = clip.textStyle ?: TextOverlayStyle()
                val widthFraction = clip.overlayWidthFraction.coerceIn(
                    Clip.MIN_OVERLAY_WIDTH_FRACTION,
                    Clip.MAX_OVERLAY_WIDTH_FRACTION,
                )
                val loaded = rememberRenderedTextBitmap(
                    text = clip.textContent,
                    style = style,
                    canvasWidthPx = renderWidthPx,
                    canvasHeightPx = renderHeightPx,
                    maxWidthFraction = widthFraction,
                ) ?: return@key

            val displayWidthPx = loaded.width / renderScale
            val displayHeightPx = loaded.height / renderScale
            val leftPx = (clip.overlayCenterX.coerceIn(0f, 1f) * canvasWidthPx) - (displayWidthPx / 2f)
            val topPx = (clip.overlayCenterY.coerceIn(0f, 1f) * canvasHeightPx) - (displayHeightPx / 2f)

            val widthDp = with(density) { displayWidthPx.toDp() }
            val heightDp = with(density) { displayHeightPx.toDp() }

            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                    .size(width = widthDp, height = heightDp),
                )
            }
        }
    }
}

private data class TextRenderKey(
    val text: String,
    val style: TextOverlayStyle,
    val renderWidthPx: Int,
    val renderHeightPx: Int,
    val widthFraction: Float,
)
