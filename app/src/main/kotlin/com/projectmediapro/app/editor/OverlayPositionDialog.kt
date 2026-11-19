package com.projectmediapro.app.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.data.export.ExportGeometry
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.CanvasBackground
import com.projectmediapro.core.model.Clip
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayPositionDialog(
    clip: Clip,
    canvasAspect: CanvasAspect,
    canvasBackground: CanvasBackground,
    onConfirm: (centerX: Float, centerY: Float, widthFraction: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var centerX by rememberSaveable(clip.id, clip.overlayCenterX) {
        mutableFloatStateOf(clip.overlayCenterX.coerceIn(0f, 1f))
    }
    var centerY by rememberSaveable(clip.id, clip.overlayCenterY) {
        mutableFloatStateOf(clip.overlayCenterY.coerceIn(0f, 1f))
    }
    var widthFraction by rememberSaveable(clip.id, clip.overlayWidthFraction) {
        mutableFloatStateOf(
            clip.overlayWidthFraction.coerceIn(
                Clip.MIN_OVERLAY_WIDTH_FRACTION,
                Clip.MAX_OVERLAY_WIDTH_FRACTION,
            ),
        )
    }

    val aspectRatio = ExportGeometry.previewAspectRatio(canvasAspect)
    val previewBg = canvasBackgroundColor(canvasBackground)
    val guideColor = if (canvasBackground == CanvasBackground.WHITE) {
        Color.Black.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    val xPercent = (centerX * 100f).roundToInt()
    val yPercent = (centerY * 100f).roundToInt()
    val sizePercent = (widthFraction * 100f).roundToInt()
    val previewDescription = stringResource(
        R.string.cd_overlay_position_preview,
        xPercent,
        yPercent,
        sizePercent,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.editor_overlay_position_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .heightIn(max = 128.dp)
                        .aspectRatio(aspectRatio)
                        .clip(MediaProShapes.Small)
                        .background(previewBg)
                        .border(
                            width = MediaProSpacing.BorderThin,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MediaProShapes.Small,
                        )
                        .semantics { contentDescription = previewDescription },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = guideColor,
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawLine(
                            color = guideColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    val boxWidthPx = constraints.maxWidth.toFloat()
                    val boxHeightPx = constraints.maxHeight.toFloat()
                    val overlayWidthDp = maxWidth * widthFraction
                    val overlayWidthPx = boxWidthPx * widthFraction
                    val leftPx = (centerX * boxWidthPx) - (overlayWidthPx / 2f)
                    val topPx = (centerY * boxHeightPx) - (overlayWidthPx / 2f)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                            .size(width = overlayWidthDp, height = overlayWidthDp)
                            .clip(MediaProShapes.Small)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f))
                            .border(
                                width = MediaProSpacing.BorderSelected,
                                color = MaterialTheme.colorScheme.onTertiary,
                                shape = MediaProShapes.Small,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.MediumSmall))
                LabeledSliderRow(
                    label = stringResource(R.string.editor_overlay_x_value, xPercent),
                    value = centerX,
                    valueRange = 0f..1f,
                    onValueChange = { centerX = it.coerceIn(0f, 1f) },
                )
                LabeledSliderRow(
                    label = stringResource(R.string.editor_overlay_y_value, yPercent),
                    value = centerY,
                    valueRange = 0f..1f,
                    onValueChange = { centerY = it.coerceIn(0f, 1f) },
                )
                LabeledSliderRow(
                    label = stringResource(R.string.editor_overlay_size_value, sizePercent),
                    value = widthFraction,
                    valueRange = Clip.MIN_OVERLAY_WIDTH_FRACTION..Clip.MAX_OVERLAY_WIDTH_FRACTION,
                    onValueChange = {
                        widthFraction = it.coerceIn(
                            Clip.MIN_OVERLAY_WIDTH_FRACTION,
                            Clip.MAX_OVERLAY_WIDTH_FRACTION,
                        )
                    },
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(R.string.editor_apply),
                onClick = { onConfirm(centerX, centerY, widthFraction) },
            )
        },
        dismissButton = {
            DialogActionButton(
                label = stringResource(R.string.home_cancel),
                onClick = onDismiss,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
