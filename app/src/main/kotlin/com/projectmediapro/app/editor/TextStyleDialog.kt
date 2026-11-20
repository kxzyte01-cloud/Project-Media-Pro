package com.projectmediapro.app.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasBackground
import com.projectmediapro.core.model.TextAlignment
import com.projectmediapro.core.model.TextFontFamily
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.TextStylePreset
import kotlin.math.roundToInt

private const val DIALOG_PREVIEW_WIDTH_PX = 720
private const val DIALOG_PREVIEW_HEIGHT_PX = 405

private data class ColorSwatchOption(
    val argb: Long,
    @StringRes val labelRes: Int,
)

private val TEXT_COLOR_SWATCHES = listOf(
    ColorSwatchOption(TextOverlayStyle.COLOR_WHITE, R.string.cd_color_white),
    ColorSwatchOption(TextOverlayStyle.COLOR_CREAM, R.string.cd_color_cream),
    ColorSwatchOption(TextOverlayStyle.COLOR_AMBER, R.string.cd_color_amber),
    ColorSwatchOption(TextOverlayStyle.COLOR_CORAL, R.string.cd_color_coral),
    ColorSwatchOption(TextOverlayStyle.COLOR_CYAN, R.string.cd_color_cyan),
    ColorSwatchOption(TextOverlayStyle.COLOR_MINT, R.string.cd_color_mint),
    ColorSwatchOption(TextOverlayStyle.COLOR_VIOLET, R.string.cd_color_violet),
    ColorSwatchOption(TextOverlayStyle.COLOR_BLACK, R.string.cd_color_black),
)

private val BACKGROUND_COLOR_SWATCHES = listOf(
    ColorSwatchOption(TextOverlayStyle.COLOR_BG_DARK_GLASS, R.string.cd_color_bg_dark_glass),
    ColorSwatchOption(TextOverlayStyle.COLOR_BG_SOLID_BLACK, R.string.cd_color_bg_solid_black),
    ColorSwatchOption(TextOverlayStyle.COLOR_BG_WARM_TINT, R.string.cd_color_bg_warm_tint),
    ColorSwatchOption(TextOverlayStyle.COLOR_BG_LIGHT_FROST, R.string.cd_color_bg_light_frost),
)

private val TextOverlayStyleSaver: Saver<TextOverlayStyle, Any> = listSaver(
    save = { s ->
        listOf(
            s.fontFamily.name,
            s.isBold,
            s.isItalic,
            s.textSizeFraction,
            s.textColorArgb,
            s.strokeColorArgb,
            s.strokeWidthFraction,
            s.backgroundColorArgb,
            s.hasBackground,
            s.hasShadow,
            s.alignment.name,
            s.letterSpacingEm,
        )
    },
    restore = { raw ->
        val font = runCatching { TextFontFamily.valueOf(raw[0] as String) }.getOrDefault(TextFontFamily.SANS)
        val align = runCatching { TextAlignment.valueOf(raw[10] as String) }.getOrDefault(TextAlignment.CENTER)
        TextOverlayStyle(
            fontFamily = font,
            isBold = raw[1] as Boolean,
            isItalic = raw[2] as Boolean,
            textSizeFraction = raw[3] as Float,
            textColorArgb = raw[4] as Long,
            strokeColorArgb = raw[5] as Long,
            strokeWidthFraction = raw[6] as Float,
            backgroundColorArgb = raw[7] as Long,
            hasBackground = raw[8] as Boolean,
            hasShadow = raw[9] as Boolean,
            alignment = align,
            letterSpacingEm = raw[11] as Float,
        ).sanitized()
    },
)

@Composable
fun TextStyleDialog(
    clipId: String?,
    initialText: String,
    initialStyle: TextOverlayStyle,
    canvasBackground: CanvasBackground,
    isEditing: Boolean,
    onConfirm: (text: String, style: TextOverlayStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val stateKey = clipId ?: "new-text"
    var text by rememberSaveable(stateKey) { mutableStateOf(initialText) }
    var currentStyle by rememberSaveable(stateKey, stateSaver = TextOverlayStyleSaver) {
        mutableStateOf(initialStyle.sanitized())
    }

    val sampleFallback = stringResource(R.string.editor_text_preview_sample)
    val previewString = text.trim().ifEmpty { sampleFallback }
    val previewBg = canvasBackgroundColor(canvasBackground)
    val previewBitmap = rememberRenderedTextBitmap(
        text = previewString,
        style = currentStyle,
        canvasWidthPx = DIALOG_PREVIEW_WIDTH_PX,
        canvasHeightPx = DIALOG_PREVIEW_HEIGHT_PX,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEditing) R.string.editor_text_edit_title else R.string.editor_text_add_title,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .clip(MediaProShapes.Small)
                        .background(previewBg)
                        .border(
                            width = MediaProSpacing.BorderThin,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MediaProShapes.Small,
                        )
                        .padding(MediaProSpacing.Small),
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = stringResource(R.string.cd_text_style_preview),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(text = stringResource(R.string.editor_text_input_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                EditorDialogSectionLabel(text = stringResource(R.string.editor_text_presets_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemGestureExclusion()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    TextStylePreset.entries.forEach { preset ->
                        val presetStyle = TextOverlayStyle.preset(preset)
                        EditorOptionCard(
                            isSelected = currentStyle == presetStyle,
                            onClick = { currentStyle = presetStyle },
                        ) { contentColor ->
                            Text(
                                text = stringResource(textPresetLabelRes(preset)),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                modifier = Modifier.padding(
                                    horizontal = MediaProSpacing.MediumSmall,
                                    vertical = MediaProSpacing.Small,
                                ),
                            )
                        }
                    }
                }

                EditorDialogSectionLabel(text = stringResource(R.string.editor_text_font_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemGestureExclusion()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    TextFontFamily.entries.forEach { family ->
                        EditorOptionCard(
                            isSelected = currentStyle.fontFamily == family,
                            onClick = { currentStyle = currentStyle.copy(fontFamily = family) },
                        ) { contentColor ->
                            Text(
                                text = stringResource(fontFamilyLabelRes(family)),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                modifier = Modifier.padding(
                                    horizontal = MediaProSpacing.MediumSmall,
                                    vertical = MediaProSpacing.Small,
                                ),
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    EditorOptionCard(
                        isSelected = currentStyle.isBold,
                        onClick = { currentStyle = currentStyle.copy(isBold = !currentStyle.isBold) },
                        modifier = Modifier.weight(1f),
                    ) { contentColor ->
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.editor_text_bold),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                modifier = Modifier.padding(vertical = MediaProSpacing.Small),
                            )
                        }
                    }
                    EditorOptionCard(
                        isSelected = currentStyle.isItalic,
                        onClick = { currentStyle = currentStyle.copy(isItalic = !currentStyle.isItalic) },
                        modifier = Modifier.weight(1f),
                    ) { contentColor ->
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.editor_text_italic),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                modifier = Modifier.padding(vertical = MediaProSpacing.Small),
                            )
                        }
                    }
                }

                EditorDialogSectionLabel(text = stringResource(R.string.editor_text_align_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextAlignment.entries.forEach { item ->
                        EditorOptionCard(
                            isSelected = currentStyle.alignment == item,
                            onClick = { currentStyle = currentStyle.copy(alignment = item) },
                            modifier = Modifier.weight(1f),
                        ) { contentColor ->
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(textAlignmentLabelRes(item)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor,
                                    modifier = Modifier.padding(vertical = MediaProSpacing.Small),
                                )
                            }
                        }
                    }
                }

                EditorDialogSectionLabel(text = stringResource(R.string.editor_text_color_label))
                ColorSwatchRow(
                    swatches = TEXT_COLOR_SWATCHES,
                    selectedArgb = currentStyle.textColorArgb,
                    onSelect = { chosenArgb ->
                        val nextStroke = if (currentStyle.strokeColorArgb == chosenArgb) {
                            if (chosenArgb == TextOverlayStyle.COLOR_BLACK) {
                                TextOverlayStyle.COLOR_WHITE
                            } else {
                                TextOverlayStyle.COLOR_BLACK
                            }
                        } else {
                            currentStyle.strokeColorArgb
                        }
                        currentStyle = currentStyle.copy(
                            textColorArgb = chosenArgb,
                            strokeColorArgb = nextStroke,
                        )
                    },
                )

                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_text_size_value,
                        (currentStyle.textSizeFraction * 1000f).roundToInt(),
                    ),
                    value = currentStyle.textSizeFraction,
                    valueRange = TextOverlayStyle.MIN_TEXT_SIZE_FRACTION..TextOverlayStyle.MAX_TEXT_SIZE_FRACTION,
                    onValueChange = { currentStyle = currentStyle.copy(textSizeFraction = it).sanitized() },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_text_outline_value,
                        (currentStyle.strokeWidthFraction * 100f).roundToInt(),
                    ),
                    value = currentStyle.strokeWidthFraction,
                    valueRange = TextOverlayStyle.MIN_STROKE_WIDTH_FRACTION..TextOverlayStyle.MAX_STROKE_WIDTH_FRACTION,
                    onValueChange = { currentStyle = currentStyle.copy(strokeWidthFraction = it).sanitized() },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_text_spacing_value,
                        (currentStyle.letterSpacingEm * 100f).roundToInt(),
                    ),
                    value = currentStyle.letterSpacingEm,
                    valueRange = TextOverlayStyle.MIN_LETTER_SPACING_EM..TextOverlayStyle.MAX_LETTER_SPACING_EM,
                    onValueChange = { currentStyle = currentStyle.copy(letterSpacingEm = it).sanitized() },
                )

                ToggleOptionRow(
                    label = stringResource(R.string.editor_text_background_toggle),
                    checked = currentStyle.hasBackground,
                    onCheckedChange = { currentStyle = currentStyle.copy(hasBackground = it) },
                )
                if (currentStyle.hasBackground) {
                    ColorSwatchRow(
                        swatches = BACKGROUND_COLOR_SWATCHES,
                        selectedArgb = currentStyle.backgroundColorArgb,
                        onSelect = { currentStyle = currentStyle.copy(backgroundColorArgb = it) },
                    )
                }

                ToggleOptionRow(
                    label = stringResource(R.string.editor_text_shadow_toggle),
                    checked = currentStyle.hasShadow,
                    onCheckedChange = { currentStyle = currentStyle.copy(hasShadow = it) },
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(if (isEditing) R.string.editor_apply else R.string.editor_text_add_confirm),
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim(), currentStyle.sanitized()) },
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
private fun ColorSwatchRow(
    swatches: List<ColorSwatchOption>,
    selectedArgb: Long,
    onSelect: (Long) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
        modifier = Modifier
            .fillMaxWidth()
            .systemGestureExclusion()
            .horizontalScroll(rememberScrollState()),
    ) {
        swatches.forEach { option ->
            val isSelected = selectedArgb == option.argb
            val swatchDesc = stringResource(option.labelRes)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    )
                    .clip(CircleShape)
                    .clickable(onClick = { onSelect(option.argb) })
                    .clearAndSetSemantics {
                        contentDescription = swatchDesc
                        role = Role.RadioButton
                        selected = isSelected
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .border(
                            border = BorderStroke(
                                width = if (isSelected) 2.5.dp else MediaProSpacing.BorderThin,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                            shape = CircleShape,
                        )
                        .padding(if (isSelected) 3.dp else 1.dp)
                        .clip(CircleShape)
                        .background(Color(option.argb.toInt())),
                ) {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorDialogSliderRow(
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
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToggleOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.size(MediaProSpacing.Small))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun textPresetLabelRes(preset: TextStylePreset): Int = when (preset) {
    TextStylePreset.CLEAN_LYRIC -> R.string.editor_text_preset_clean_lyric
    TextStylePreset.CINEMA_TITLE -> R.string.editor_text_preset_cinema
    TextStylePreset.KARAOKE_PILL -> R.string.editor_text_preset_karaoke
    TextStylePreset.NEON_GLOW -> R.string.editor_text_preset_neon
    TextStylePreset.BOLD_OUTLINE -> R.string.editor_text_preset_bold_outline
    TextStylePreset.MINIMAL_CAPTION -> R.string.editor_text_preset_minimal
}

private fun fontFamilyLabelRes(family: TextFontFamily): Int = when (family) {
    TextFontFamily.SANS -> R.string.editor_text_font_sans
    TextFontFamily.SERIF -> R.string.editor_text_font_serif
    TextFontFamily.MONOSPACE -> R.string.editor_text_font_mono
    TextFontFamily.CONDENSED -> R.string.editor_text_font_condensed
}

private fun textAlignmentLabelRes(alignment: TextAlignment): Int = when (alignment) {
    TextAlignment.LEFT -> R.string.editor_text_align_left
    TextAlignment.CENTER -> R.string.editor_text_align_center
    TextAlignment.RIGHT -> R.string.editor_text_align_right
}
