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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.data.export.TextOverlayBitmapRenderer
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasBackground
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.TextAlignment
import com.projectmediapro.core.model.TextFontFamily
import com.projectmediapro.core.model.TextOverlayStyle
import com.projectmediapro.core.model.TextStylePreset
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val safeInitial = initialStyle.sanitized()
    val stateKey = clipId ?: "new-text"

    var text by rememberSaveable(stateKey) { mutableStateOf(initialText) }
    var fontFamilyName by rememberSaveable(stateKey) { mutableStateOf(safeInitial.fontFamily.name) }
    var isBold by rememberSaveable(stateKey) { mutableStateOf(safeInitial.isBold) }
    var isItalic by rememberSaveable(stateKey) { mutableStateOf(safeInitial.isItalic) }
    var textSizeFraction by rememberSaveable(stateKey) { mutableFloatStateOf(safeInitial.textSizeFraction) }
    var textColorArgb by rememberSaveable(stateKey) { mutableLongStateOf(safeInitial.textColorArgb) }
    var strokeColorArgb by rememberSaveable(stateKey) { mutableLongStateOf(safeInitial.strokeColorArgb) }
    var strokeWidthFraction by rememberSaveable(stateKey) {
        mutableFloatStateOf(safeInitial.strokeWidthFraction)
    }
    var backgroundColorArgb by rememberSaveable(stateKey) {
        mutableLongStateOf(safeInitial.backgroundColorArgb)
    }
    var hasBackground by rememberSaveable(stateKey) { mutableStateOf(safeInitial.hasBackground) }
    var hasShadow by rememberSaveable(stateKey) { mutableStateOf(safeInitial.hasShadow) }
    var alignmentName by rememberSaveable(stateKey) { mutableStateOf(safeInitial.alignment.name) }
    var letterSpacingEm by rememberSaveable(stateKey) { mutableFloatStateOf(safeInitial.letterSpacingEm) }

    val fontFamily = runCatching { TextFontFamily.valueOf(fontFamilyName) }.getOrDefault(TextFontFamily.SANS)
    val alignment = runCatching { TextAlignment.valueOf(alignmentName) }.getOrDefault(TextAlignment.CENTER)

    val currentStyle = TextOverlayStyle(
        fontFamily = fontFamily,
        isBold = isBold,
        isItalic = isItalic,
        textSizeFraction = textSizeFraction,
        textColorArgb = textColorArgb,
        strokeColorArgb = strokeColorArgb,
        strokeWidthFraction = strokeWidthFraction,
        backgroundColorArgb = backgroundColorArgb,
        hasBackground = hasBackground,
        hasShadow = hasShadow,
        alignment = alignment,
        letterSpacingEm = letterSpacingEm,
    ).sanitized()

    fun applyPreset(preset: TextStylePreset) {
        val next = TextOverlayStyle.preset(preset)
        fontFamilyName = next.fontFamily.name
        isBold = next.isBold
        isItalic = next.isItalic
        textSizeFraction = next.textSizeFraction
        textColorArgb = next.textColorArgb
        strokeColorArgb = next.strokeColorArgb
        strokeWidthFraction = next.strokeWidthFraction
        backgroundColorArgb = next.backgroundColorArgb
        hasBackground = next.hasBackground
        hasShadow = next.hasShadow
        alignmentName = next.alignment.name
        letterSpacingEm = next.letterSpacingEm
    }

    val sampleFallback = stringResource(R.string.editor_text_preview_sample)
    val previewString = text.trim().ifEmpty { sampleFallback }
    val previewBg = canvasBackgroundColor(canvasBackground)
    val previewBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = previewString,
        key2 = currentStyle,
    ) {
        value = withContext(Dispatchers.Default) {
            TextOverlayBitmapRenderer.render(
                text = previewString,
                style = currentStyle,
                canvasWidthPx = DIALOG_PREVIEW_WIDTH_PX,
                canvasHeightPx = DIALOG_PREVIEW_HEIGHT_PX,
                maxWidthFraction = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
            )?.asImageBitmap()
        }
    }

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
                    val rendered = previewBitmap
                    if (rendered != null) {
                        Image(
                            bitmap = rendered,
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

                SectionLabel(text = stringResource(R.string.editor_text_presets_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemGestureExclusion()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    TextStylePreset.entries.forEach { preset ->
                        val presetStyle = TextOverlayStyle.preset(preset)
                        val isMatch = currentStyle == presetStyle
                        EditorOptionCard(
                            isSelected = isMatch,
                            onClick = { applyPreset(preset) },
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

                SectionLabel(text = stringResource(R.string.editor_text_font_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemGestureExclusion()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    TextFontFamily.entries.forEach { family ->
                        EditorOptionCard(
                            isSelected = fontFamily == family,
                            onClick = { fontFamilyName = family.name },
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
                        isSelected = isBold,
                        onClick = { isBold = !isBold },
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
                        isSelected = isItalic,
                        onClick = { isItalic = !isItalic },
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

                SectionLabel(text = stringResource(R.string.editor_text_align_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextAlignment.entries.forEach { item ->
                        EditorOptionCard(
                            isSelected = alignment == item,
                            onClick = { alignmentName = item.name },
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

                SectionLabel(text = stringResource(R.string.editor_text_color_label))
                ColorSwatchRow(
                    swatches = TEXT_COLOR_SWATCHES,
                    selectedArgb = textColorArgb,
                    onSelect = { chosenArgb ->
                        textColorArgb = chosenArgb
                        if (strokeColorArgb == chosenArgb) {
                            strokeColorArgb = if (chosenArgb == TextOverlayStyle.COLOR_BLACK) {
                                TextOverlayStyle.COLOR_WHITE
                            } else {
                                TextOverlayStyle.COLOR_BLACK
                            }
                        }
                    },
                )

                TextStyleSliderRow(
                    label = stringResource(
                        R.string.editor_text_size_value,
                        (textSizeFraction * 1000f).roundToInt(),
                    ),
                    value = textSizeFraction,
                    valueRange = TextOverlayStyle.MIN_TEXT_SIZE_FRACTION..TextOverlayStyle.MAX_TEXT_SIZE_FRACTION,
                    onValueChange = { textSizeFraction = it },
                )
                TextStyleSliderRow(
                    label = stringResource(
                        R.string.editor_text_outline_value,
                        (strokeWidthFraction * 100f).roundToInt(),
                    ),
                    value = strokeWidthFraction,
                    valueRange = TextOverlayStyle.MIN_STROKE_WIDTH_FRACTION..TextOverlayStyle.MAX_STROKE_WIDTH_FRACTION,
                    onValueChange = { strokeWidthFraction = it },
                )
                TextStyleSliderRow(
                    label = stringResource(
                        R.string.editor_text_spacing_value,
                        (letterSpacingEm * 100f).roundToInt(),
                    ),
                    value = letterSpacingEm,
                    valueRange = TextOverlayStyle.MIN_LETTER_SPACING_EM..TextOverlayStyle.MAX_LETTER_SPACING_EM,
                    onValueChange = { letterSpacingEm = it },
                )

                ToggleOptionRow(
                    label = stringResource(R.string.editor_text_background_toggle),
                    checked = hasBackground,
                    onCheckedChange = { hasBackground = it },
                )
                if (hasBackground) {
                    ColorSwatchRow(
                        swatches = BACKGROUND_COLOR_SWATCHES,
                        selectedArgb = backgroundColorArgb,
                        onSelect = { backgroundColorArgb = it },
                    )
                }

                ToggleOptionRow(
                    label = stringResource(R.string.editor_text_shadow_toggle),
                    checked = hasShadow,
                    onCheckedChange = { hasShadow = it },
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(if (isEditing) R.string.editor_apply else R.string.editor_text_add_confirm),
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim(), currentStyle) },
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TextStyleSliderRow(
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
