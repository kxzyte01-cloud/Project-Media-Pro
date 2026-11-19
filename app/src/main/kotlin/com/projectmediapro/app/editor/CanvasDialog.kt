package com.projectmediapro.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.CanvasBackground

private val CanvasGreyColor = Color(0xFF475569)

internal fun canvasBackgroundColor(background: CanvasBackground): Color = when (background) {
    CanvasBackground.BLACK -> Color.Black
    CanvasBackground.WHITE -> Color.White
    CanvasBackground.GREY -> CanvasGreyColor
}

@Composable
fun CanvasDialog(
    initialAspect: CanvasAspect,
    initialBackground: CanvasBackground,
    onConfirm: (CanvasAspect, CanvasBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedAspect by rememberSaveable { mutableStateOf(initialAspect) }
    var selectedBackground by rememberSaveable { mutableStateOf(initialBackground) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.editor_canvas_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.editor_canvas_aspect_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    CanvasAspect.entries.forEach { aspect ->
                        AspectOptionTile(
                            aspect = aspect,
                            isSelected = aspect == selectedAspect,
                            onClick = { selectedAspect = aspect },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
                Text(
                    text = stringResource(R.string.editor_canvas_background_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CanvasBackground.entries.forEach { background ->
                        BackgroundOptionTile(
                            background = background,
                            isSelected = background == selectedBackground,
                            onClick = { selectedBackground = background },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(R.string.editor_apply),
                onClick = { onConfirm(selectedAspect, selectedBackground) },
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

@Composable
private fun AspectOptionTile(
    aspect: CanvasAspect,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(aspectLabelRes(aspect))
    val (rectWidth, rectHeight) = aspectPreviewDp(aspect)

    EditorOptionCard(
        isSelected = isSelected,
        onClick = onClick,
        accessibilityLabel = label,
    ) { contentColor ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(MediaProSpacing.Small),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(30.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = rectWidth, height = rectHeight)
                        .border(
                            width = MediaProSpacing.BorderSelected,
                            color = contentColor,
                            shape = MediaProShapes.Small,
                        ),
                )
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BackgroundOptionTile(
    background: CanvasBackground,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(backgroundLabelRes(background))
    val swatchColor = canvasBackgroundColor(background)

    EditorOptionCard(
        isSelected = isSelected,
        onClick = onClick,
        accessibilityLabel = label,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = MediaProSpacing.Small),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(MediaProShapes.Small)
                    .background(swatchColor)
                    .border(
                        width = MediaProSpacing.BorderThin,
                        color = MaterialTheme.colorScheme.outline,
                        shape = MediaProShapes.Small,
                    ),
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun aspectPreviewDp(aspect: CanvasAspect): Pair<Dp, Dp> = when (aspect) {
    CanvasAspect.ORIGINAL -> 24.dp to 18.dp
    CanvasAspect.RATIO_16_9 -> 28.dp to 16.dp
    CanvasAspect.RATIO_9_16 -> 16.dp to 28.dp
    CanvasAspect.RATIO_1_1 -> 22.dp to 22.dp
    CanvasAspect.RATIO_4_5 -> 20.dp to 25.dp
}

private fun aspectLabelRes(aspect: CanvasAspect): Int = when (aspect) {
    CanvasAspect.ORIGINAL -> R.string.editor_canvas_original
    CanvasAspect.RATIO_16_9 -> R.string.editor_canvas_16_9
    CanvasAspect.RATIO_9_16 -> R.string.editor_canvas_9_16
    CanvasAspect.RATIO_1_1 -> R.string.editor_canvas_1_1
    CanvasAspect.RATIO_4_5 -> R.string.editor_canvas_4_5
}

private fun backgroundLabelRes(background: CanvasBackground): Int = when (background) {
    CanvasBackground.BLACK -> R.string.editor_canvas_bg_black
    CanvasBackground.WHITE -> R.string.editor_canvas_bg_white
    CanvasBackground.GREY -> R.string.editor_canvas_bg_grey
}
