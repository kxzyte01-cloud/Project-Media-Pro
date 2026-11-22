package com.projectmediapro.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class StudioToolIconKind {
    VIDEO,
    AUDIO,
    OVERLAY,
    TEXT,
    CAPTIONS,
    BEATS,
    CANVAS,
    TRIM,
    SPLIT,
    SPEED,
    VOLUME,
    TRANSFORM,
    CROP,
    FREEZE,
    EXTRACT_AUDIO,
    EFFECTS,
    POSITION,
    TIMING,
    KEYFRAMES,
    DUPLICATE,
    REPLACE,
    COPY_FROM,
    COPY_TILL,
    COPY_TO,
    EARLIER,
    LATER,
    PLAY,
    PAUSE,
    DELETE,
    BACK,
}

@Composable
internal fun StudioToolIcon(
    kind: StudioToolIconKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokePx = 1.75.dp.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pad = w * 0.14f
        val inner = w - pad * 2f

        when (kind) {
            StudioToolIconKind.VIDEO -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(pad, h * 0.22f),
                    size = Size(inner, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.5f, h * 0.36f), Offset(w * 0.5f, h * 0.64f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.5f), Offset(w * 0.64f, h * 0.5f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.AUDIO -> {
                val bars = listOf(0.32f, 0.72f, 0.50f, 0.84f, 0.38f)
                bars.forEachIndexed { idx, frac ->
                    val x = pad + inner * (idx.toFloat() / (bars.lastIndex.toFloat()))
                    val half = h * 0.36f * frac
                    drawLine(tint, Offset(x, h * 0.5f - half), Offset(x, h * 0.5f + half), strokePx, StrokeCap.Round)
                }
            }
            StudioToolIconKind.OVERLAY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(pad, h * 0.28f),
                    size = Size(inner * 0.72f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.36f, h * 0.16f),
                    size = Size(inner * 0.64f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke,
                )
            }
            StudioToolIconKind.TEXT -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.24f), Offset(w * 0.78f, h * 0.24f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.24f), Offset(w * 0.50f, h * 0.80f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.80f), Offset(w * 0.62f, h * 0.80f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.CAPTIONS -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(pad, h * 0.22f),
                    size = Size(inner, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.26f, h * 0.54f), Offset(w * 0.56f, h * 0.54f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.54f), Offset(w * 0.74f, h * 0.54f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.26f, h * 0.66f), Offset(w * 0.74f, h * 0.66f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.BEATS -> {
                val diamond = Path().apply {
                    moveTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.78f, h * 0.50f)
                    lineTo(w * 0.50f, h * 0.84f)
                    lineTo(w * 0.22f, h * 0.50f)
                    close()
                }
                drawPath(diamond, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.36f, h * 0.50f), Offset(w * 0.64f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.36f), Offset(w * 0.50f, h * 0.64f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.CANVAS -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(pad, h * 0.20f),
                    size = Size(inner, h * 0.60f),
                    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
                    style = stroke,
                )
                drawRect(
                    color = tint.copy(alpha = 0.55f),
                    topLeft = Offset(w * 0.30f, h * 0.33f),
                    size = Size(w * 0.40f, h * 0.34f),
                    style = stroke,
                )
            }
            StudioToolIconKind.TRIM -> {
                drawLine(tint, Offset(w * 0.24f, h * 0.22f), Offset(w * 0.24f, h * 0.78f), strokePx * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.76f, h * 0.22f), Offset(w * 0.76f, h * 0.78f), strokePx * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.24f, h * 0.32f), Offset(w * 0.76f, h * 0.32f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.24f, h * 0.68f), Offset(w * 0.76f, h * 0.68f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.SPLIT -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.86f), strokePx, StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(pad, h * 0.30f),
                    size = Size(w * 0.24f, h * 0.40f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.62f, h * 0.30f),
                    size = Size(w * 0.24f, h * 0.40f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = stroke,
                )
            }
            StudioToolIconKind.SPEED -> {
                drawArc(
                    color = tint,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(inner, inner),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.70f, h * 0.34f), strokePx, StrokeCap.Round)
                drawCircle(tint, radius = strokePx * 1.1f, center = Offset(w * 0.50f, h * 0.54f))
            }
            StudioToolIconKind.VOLUME -> {
                val speaker = Path().apply {
                    moveTo(w * 0.18f, h * 0.40f)
                    lineTo(w * 0.34f, h * 0.40f)
                    lineTo(w * 0.52f, h * 0.24f)
                    lineTo(w * 0.52f, h * 0.76f)
                    lineTo(w * 0.34f, h * 0.60f)
                    lineTo(w * 0.18f, h * 0.60f)
                    close()
                }
                drawPath(speaker, tint, style = stroke)
                drawArc(
                    color = tint,
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w * 0.48f, h * 0.30f),
                    size = Size(w * 0.32f, h * 0.40f),
                    style = stroke,
                )
            }
            StudioToolIconKind.TRANSFORM -> {
                drawArc(
                    color = tint,
                    startAngle = -60f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(inner, inner),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.68f, h * 0.16f), Offset(w * 0.80f, h * 0.30f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.80f, h * 0.30f), Offset(w * 0.64f, h * 0.34f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.CROP -> {
                drawLine(tint, Offset(w * 0.30f, h * 0.16f), Offset(w * 0.30f, h * 0.70f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.70f), Offset(w * 0.84f, h * 0.70f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.16f, h * 0.30f), Offset(w * 0.70f, h * 0.30f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.70f, h * 0.30f), Offset(w * 0.70f, h * 0.84f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.FREEZE -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.84f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.84f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.26f, h * 0.26f), Offset(w * 0.74f, h * 0.74f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.74f, h * 0.26f), Offset(w * 0.26f, h * 0.74f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.EFFECTS -> {
                val star = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.58f, h * 0.42f)
                    lineTo(w * 0.86f, h * 0.50f)
                    lineTo(w * 0.58f, h * 0.58f)
                    lineTo(w * 0.50f, h * 0.86f)
                    lineTo(w * 0.42f, h * 0.58f)
                    lineTo(w * 0.14f, h * 0.50f)
                    lineTo(w * 0.42f, h * 0.42f)
                    close()
                }
                drawPath(star, tint, style = stroke)
            }
            StudioToolIconKind.POSITION -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.84f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.84f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.40f, h * 0.26f), Offset(w * 0.50f, h * 0.16f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.60f, h * 0.26f), Offset(w * 0.50f, h * 0.16f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.40f, h * 0.74f), Offset(w * 0.50f, h * 0.84f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.60f, h * 0.74f), Offset(w * 0.50f, h * 0.84f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.TIMING -> {
                drawCircle(tint, radius = inner * 0.5f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.30f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.66f, h * 0.58f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.KEYFRAMES -> {
                val diamond = Path().apply {
                    moveTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.50f)
                    lineTo(w * 0.50f, h * 0.84f)
                    lineTo(w * 0.16f, h * 0.50f)
                    close()
                }
                drawPath(diamond, tint, style = stroke)
                drawCircle(tint, radius = strokePx * 1.15f, center = Offset(w * 0.5f, h * 0.5f))
            }
            StudioToolIconKind.DUPLICATE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.18f, h * 0.30f),
                    size = Size(w * 0.48f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.34f, h * 0.18f),
                    size = Size(w * 0.48f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke,
                )
            }
            StudioToolIconKind.REPLACE -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.36f), Offset(w * 0.78f, h * 0.36f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.22f), Offset(w * 0.78f, h * 0.36f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.78f, h * 0.64f), Offset(w * 0.22f, h * 0.64f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.78f), Offset(w * 0.22f, h * 0.64f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.COPY_FROM -> {
                drawLine(tint, Offset(w * 0.28f, h * 0.20f), Offset(w * 0.28f, h * 0.80f), strokePx * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.34f), Offset(w * 0.80f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.66f), Offset(w * 0.80f, h * 0.50f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.COPY_TILL -> {
                drawLine(tint, Offset(w * 0.72f, h * 0.20f), Offset(w * 0.72f, h * 0.80f), strokePx * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.62f, h * 0.50f), Offset(w * 0.20f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.34f), Offset(w * 0.20f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.66f), Offset(w * 0.20f, h * 0.50f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.COPY_TO -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.72f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.56f), Offset(w * 0.50f, h * 0.72f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.56f), Offset(w * 0.50f, h * 0.72f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.22f, h * 0.82f), Offset(w * 0.78f, h * 0.82f), strokePx * 1.2f, StrokeCap.Round)
            }
            StudioToolIconKind.EARLIER -> {
                drawLine(tint, Offset(w * 0.62f, h * 0.24f), Offset(w * 0.36f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.50f), Offset(w * 0.62f, h * 0.76f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.LATER -> {
                drawLine(tint, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.64f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.50f), Offset(w * 0.38f, h * 0.76f), strokePx, StrokeCap.Round)
            }
            StudioToolIconKind.PLAY -> {
                val tri = Path().apply {
                    moveTo(w * 0.30f, h * 0.20f)
                    lineTo(w * 0.78f, h * 0.50f)
                    lineTo(w * 0.30f, h * 0.80f)
                    close()
                }
                drawPath(tri, tint, style = stroke)
            }
            StudioToolIconKind.PAUSE -> {
                drawLine(tint, Offset(w * 0.36f, h * 0.22f), Offset(w * 0.36f, h * 0.78f), strokePx * 1.4f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.22f), Offset(w * 0.64f, h * 0.78f), strokePx * 1.4f, StrokeCap.Round)
            }
            StudioToolIconKind.DELETE -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.28f), Offset(w * 0.80f, h * 0.28f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.40f, h * 0.18f), Offset(w * 0.60f, h * 0.18f), strokePx, StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.28f, h * 0.28f),
                    size = Size(w * 0.44f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = stroke,
                )
            }
            StudioToolIconKind.EXTRACT_AUDIO -> {
                // Downward arrow from film bar into mini waveform bars
                drawLine(tint, Offset(w * 0.22f, h * 0.20f), Offset(w * 0.78f, h * 0.20f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.38f), Offset(w * 0.50f, h * 0.52f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.64f, h * 0.38f), Offset(w * 0.50f, h * 0.52f), strokePx, StrokeCap.Round)
                val wave = listOf(0.18f, 0.34f, 0.24f, 0.36f, 0.18f)
                wave.forEachIndexed { idx, amp ->
                    val x = w * 0.22f + (w * 0.56f) * (idx.toFloat() / wave.lastIndex.toFloat())
                    drawLine(tint, Offset(x, h * 0.74f - h * amp * 0.5f), Offset(x, h * 0.74f + h * amp * 0.5f), strokePx, StrokeCap.Round)
                }
            }
            StudioToolIconKind.BACK -> {
                drawLine(tint, Offset(w * 0.64f, h * 0.24f), Offset(w * 0.34f, h * 0.50f), strokePx, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.50f), Offset(w * 0.64f, h * 0.76f), strokePx, StrokeCap.Round)
            }
        }
    }
}
