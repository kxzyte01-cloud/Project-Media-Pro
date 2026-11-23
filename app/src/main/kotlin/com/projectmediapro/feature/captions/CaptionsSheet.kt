package com.projectmediapro.feature.captions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.SectionHeader
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

data class CaptionSheetCallbacks(
    val onGenerateCaptions: () -> Unit,
    val onSelectPreset: (String) -> Unit,
    val onSelectTemplate: (CaptionTemplateId) -> Unit,
    val onUpdateOffsetUs: (Long) -> Unit,
    val onUpdateStyle: (CaptionStyle) -> Unit,
    val onUpdateWordText: (wordId: String, newText: String) -> Unit,
    val onSeekToUs: (Long) -> Unit,
    val onClearCaptions: () -> Unit,
    val onGenerateWithScript: ((String) -> Unit)? = null,
    val onImportSrt: (() -> Unit)? = null,
    val onExportSrt: (() -> Unit)? = null,
    val onImportCustomFont: (() -> Unit)? = null,
)

private data class ColorSwatch(
    val label: String,
    val primaryArgb: Long,
    val highlightArgb: Long,
    val strokeArgb: Long,
)

private val CAPTION_COLOR_SWATCHES = listOf(
    ColorSwatch("Classic Gold", 0xFFFFFFFFL, 0xFFFACC15L, 0xFF0F172AL),
    ColorSwatch("Electric Cyan", 0xFFFFFFFFL, 0xFF22D3EEL, 0xFF0F172AL),
    ColorSwatch("Crimson Pop", 0xFFFFFFFFL, 0xFFFB7185L, 0xFF090D16L),
    ColorSwatch("Emerald", 0xFFF8FAFCL, 0xFF34D399L, 0xFF064E3BL),
    ColorSwatch("Mono Dark", 0xFF0F172AL, 0xFF2563EBL, 0xFFFFFFFFL),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptionsEditorSheet(
    document: CaptionDocument?,
    isTranscribing: Boolean,
    hasClips: Boolean,
    callbacks: CaptionSheetCallbacks,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingWord by remember { mutableStateOf<WordTiming?>(null) }
    var showScriptDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MediaProSpacing.Medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.captions_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_captions),
                    )
                }
            }

            Spacer(modifier = Modifier.height(MediaProSpacing.Small))

            Button(
                onClick = callbacks.onGenerateCaptions,
                enabled = hasClips && !isTranscribing,
                shape = MediaProShapes.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
            ) {
                if (isTranscribing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                    Text(text = stringResource(R.string.captions_generating))
                } else {
                    Text(
                        text = stringResource(
                            if (document == null) R.string.captions_generate else R.string.captions_regenerate,
                        ),
                    )
                }
            }

            val hasSecondaryActions = callbacks.onGenerateWithScript != null ||
                callbacks.onImportSrt != null ||
                (callbacks.onExportSrt != null && document != null)
            if (hasSecondaryActions) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (callbacks.onGenerateWithScript != null) {
                        OutlinedButton(
                            onClick = { showScriptDialog = true },
                            enabled = hasClips && !isTranscribing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.captions_sync_script))
                        }
                    }
                    if (callbacks.onImportSrt != null) {
                        OutlinedButton(
                            onClick = callbacks.onImportSrt,
                            enabled = !isTranscribing,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.captions_import_srt))
                        }
                    }
                    if (callbacks.onExportSrt != null && document != null) {
                        OutlinedButton(
                            onClick = callbacks.onExportSrt,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.captions_export_srt))
                        }
                    }
                }
            }

            if (document == null) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
                Text(
                    text = stringResource(R.string.captions_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
            } else {
                SectionHeader(title = stringResource(R.string.captions_section_preset))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    LyricsPresetRegistry.all.forEach { preset ->
                        FilterChip(
                            selected = document.presetId == preset.id,
                            onClick = { callbacks.onSelectPreset(preset.id) },
                            label = { Text(text = preset.displayName) },
                        )
                    }
                }

                SectionHeader(title = stringResource(R.string.captions_section_template))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(
                        items = CaptionTemplateRegistry.all,
                        key = { it.id.name },
                    ) { template ->
                        CaptionTemplatePreviewCard(
                            template = template,
                            isSelected = document.style.templateId == template.id,
                            onClick = { callbacks.onSelectTemplate(template.id) },
                        )
                    }
                }

                SectionHeader(title = stringResource(R.string.captions_section_sync))
                val offsetMs = (document.globalOffsetUs / CaptionTime.MICROS_PER_MS).toFloat()
                Text(
                    text = stringResource(R.string.captions_offset_label, offsetMs.toLong()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                )
                Slider(
                    value = offsetMs.coerceIn(CaptionStyleBounds.OFFSET_MS_RANGE),
                    onValueChange = { value ->
                        val snappedMs = (value / 10f).roundToLong() * 10L
                        callbacks.onUpdateOffsetUs(snappedMs * CaptionTime.MICROS_PER_MS)
                    },
                    valueRange = CaptionStyleBounds.OFFSET_MS_RANGE,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { callbacks.onUpdateOffsetUs(document.globalOffsetUs - CaptionTime.NUDGE_STEP_US) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.captions_nudge_earlier))
                    }
                    OutlinedButton(
                        onClick = { callbacks.onUpdateOffsetUs(0L) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.captions_nudge_reset))
                    }
                    OutlinedButton(
                        onClick = { callbacks.onUpdateOffsetUs(document.globalOffsetUs + CaptionTime.NUDGE_STEP_US) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.captions_nudge_later))
                    }
                }

                SectionHeader(title = stringResource(R.string.captions_section_style))
                StyleCustomizationSection(
                    style = document.style,
                    onUpdateStyle = callbacks.onUpdateStyle,
                    onImportCustomFont = callbacks.onImportCustomFont,
                )

                SectionHeader(title = stringResource(R.string.captions_section_words))
                Column(
                    verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    document.lines.forEach { line ->
                        Surface(
                            shape = MediaProShapes.Medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(MediaProSpacing.Small)) {
                                Text(
                                    text = formatCaptionTimestamp(line.startUs / CaptionTime.MICROS_PER_MS),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
                                    verticalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall),
                                ) {
                                    line.words.forEach { word ->
                                        Surface(
                                            shape = MediaProShapes.Small,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.clickable {
                                                callbacks.onSeekToUs(
                                                    (word.startUs + document.globalOffsetUs).coerceAtLeast(0L),
                                                )
                                                editingWord = word
                                            },
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(
                                                    horizontal = MediaProSpacing.Small,
                                                    vertical = MediaProSpacing.ExtraSmall,
                                                ),
                                            ) {
                                                Text(
                                                    text = word.text,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Spacer(modifier = Modifier.width(MediaProSpacing.ExtraSmall))
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.cd_edit_word),
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                TextButton(
                    onClick = callbacks.onClearCaptions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.captions_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
            }
        }
    }

    val targetWord = editingWord
    if (targetWord != null) {
        EditWordDialog(
            word = targetWord,
            onConfirm = { updatedText ->
                callbacks.onUpdateWordText(targetWord.id, updatedText)
                editingWord = null
            },
            onDismiss = { editingWord = null },
        )
    }

    val scriptCallback = callbacks.onGenerateWithScript
    if (showScriptDialog && scriptCallback != null) {
        PasteScriptDialog(
            onConfirm = { scriptText ->
                showScriptDialog = false
                scriptCallback(scriptText)
            },
            onDismiss = { showScriptDialog = false },
        )
    }
}

private fun formatCaptionTimestamp(durationMs: Long): String {
    val totalSeconds = max(0L, durationMs) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
private fun StyleCustomizationSection(
    style: CaptionStyle,
    onUpdateStyle: (CaptionStyle) -> Unit,
    onImportCustomFont: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            CaptionFontFamily.entries.forEach { family ->
                FilterChip(
                    selected = style.customFontPath == null && style.fontFamily == family,
                    onClick = {
                        onUpdateStyle(
                            style.copy(
                                fontFamily = family,
                                customFontPath = null,
                                rainFontEnabled = if (family == CaptionFontFamily.RAIN_GLASS) true else style.rainFontEnabled,
                            ),
                        )
                    },
                    label = { Text(text = family.displayName) },
                )
            }
            if (onImportCustomFont != null) {
                FilterChip(
                    selected = style.customFontPath != null,
                    onClick = onImportCustomFont,
                    label = {
                        Text(
                            text = stringResource(
                                if (style.customFontPath != null) {
                                    R.string.captions_custom_font_active
                                } else {
                                    R.string.captions_import_font
                                },
                            ),
                        )
                    },
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CAPTION_COLOR_SWATCHES.forEach { swatch ->
                val isSelected = style.primaryColorArgb == swatch.primaryArgb &&
                    style.highlightColorArgb == swatch.highlightArgb
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(swatch.highlightArgb))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        )
                        .clickable {
                            onUpdateStyle(
                                style.copy(
                                    primaryColorArgb = swatch.primaryArgb,
                                    highlightColorArgb = swatch.highlightArgb,
                                    strokeColorArgb = swatch.strokeArgb,
                                ),
                            )
                        },
                )
            }
        }

        Text(
            text = stringResource(R.string.captions_size_label, (style.fontSizeScale * 100).toInt()),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = style.fontSizeScale.coerceIn(CaptionStyleBounds.FONT_SCALE_RANGE),
            onValueChange = { onUpdateStyle(style.copy(fontSizeScale = it)) },
            valueRange = CaptionStyleBounds.FONT_SCALE_RANGE,
        )

        Text(
            text = stringResource(R.string.captions_position_y_label, (style.position.yFraction * 100).toInt()),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = style.position.yFraction.coerceIn(CaptionStyleBounds.POSITION_Y_RANGE),
            onValueChange = { y ->
                onUpdateStyle(style.copy(position = style.position.copy(yFraction = y)))
            },
            valueRange = CaptionStyleBounds.POSITION_Y_RANGE,
        )

        Text(
            text = stringResource(R.string.captions_stroke_label, (style.strokeWidthFraction * 100).toInt()),
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = style.strokeWidthFraction.coerceIn(CaptionStyleBounds.STROKE_WIDTH_RANGE),
            onValueChange = { w -> onUpdateStyle(style.copy(strokeWidthFraction = w)) },
            valueRange = CaptionStyleBounds.STROKE_WIDTH_RANGE,
        )

        StyleToggleRow(
            label = stringResource(R.string.captions_shadow_toggle),
            checked = style.shadowEnabled,
            onCheckedChange = { onUpdateStyle(style.copy(shadowEnabled = it)) },
        )
        StyleToggleRow(
            label = stringResource(R.string.captions_box_toggle),
            checked = style.backgroundBoxEnabled,
            onCheckedChange = { onUpdateStyle(style.copy(backgroundBoxEnabled = it)) },
        )
        StyleToggleRow(
            label = stringResource(R.string.captions_uppercase_toggle),
            checked = style.uppercase,
            onCheckedChange = { onUpdateStyle(style.copy(uppercase = it)) },
        )
        StyleToggleRow(
            label = stringResource(R.string.captions_rain_font_toggle),
            checked = style.rainFontEnabled,
            onCheckedChange = { onUpdateStyle(style.copy(rainFontEnabled = it)) },
        )
    }
}

@Composable
private fun StyleToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun EditWordDialog(
    word: WordTiming,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(word.id) { mutableStateOf(word.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.captions_edit_word_title)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text(text = stringResource(R.string.captions_edit_word_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) {
                Text(text = stringResource(R.string.captions_edit_word_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.captions_cancel))
            }
        },
    )
}

@Composable
private fun PasteScriptDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var script by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.captions_script_dialog_title)) },
        text = {
            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                minLines = 4,
                maxLines = 8,
                label = { Text(text = stringResource(R.string.captions_script_dialog_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(script) },
                enabled = script.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.captions_script_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.captions_cancel))
            }
        },
    )
}

@Composable
private fun CaptionTemplatePreviewCard(
    template: CaptionTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(108.dp)
            .clip(MediaProShapes.Medium)
            .background(containerColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = MediaProShapes.Medium,
            )
            .semantics(mergeDescendants = true) { selected = isSelected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
    ) {
        TemplateMiniSwatch(templateId = template.id)
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = template.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TemplateMiniSwatch(templateId: CaptionTemplateId) {
    val previewBrush = when (templateId) {
        CaptionTemplateId.KINETIC_COLLAGE -> Brush.verticalGradient(
            listOf(Color(0xFF0F291E), Color(0xFF07140F)),
        )
        CaptionTemplateId.KNOCKOUT_MASK -> Brush.verticalGradient(
            listOf(Color(0xFF000000), Color(0xFF0A0A0A)),
        )
        CaptionTemplateId.CRIMSON_ZINE -> Brush.verticalGradient(
            listOf(Color(0xFF3B0505), Color(0xFF8B0000)),
        )
        CaptionTemplateId.COLD_INVERT -> Brush.horizontalGradient(
            listOf(Color(0xFF060608), Color(0xFFF4F4F0)),
        )
        CaptionTemplateId.CODE_POEM -> Brush.verticalGradient(
            listOf(Color(0xFF0E1118), Color(0xFF141821)),
        )
        CaptionTemplateId.BLACKOUT_KINETIC -> Brush.verticalGradient(
            listOf(Color(0xFF000000), Color(0xFF000000)),
        )
        CaptionTemplateId.NEON_GLOW_STACK -> Brush.verticalGradient(
            listOf(Color(0xFF0F172A), Color(0xFF1E1B4B)),
        )
        CaptionTemplateId.MUSIC_PLAYER -> Brush.verticalGradient(
            listOf(Color(0xFF111827), Color(0xFF1E293B)),
        )
        CaptionTemplateId.EDITORIAL_SCRATCH -> Brush.verticalGradient(
            listOf(Color(0xFF1C1917), Color(0xFF292524)),
        )
        else -> Brush.verticalGradient(
            listOf(Color(0xFF111827), Color(0xFF0B0F17)),
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MediaProShapes.Small)
            .background(previewBrush),
    ) {
        when (templateId) {
            CaptionTemplateId.KINETIC_COLLAGE -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "BEG",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "YOU",
                        color = Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            CaptionTemplateId.KNOCKOUT_MASK -> {
                Text(
                    text = "DREAM",
                    color = Color(0xFFF59E0B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                )
            }
            CaptionTemplateId.CRIMSON_ZINE -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5EFE0))
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = "DIVINE",
                        color = Color(0xFF8B0000),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            CaptionTemplateId.NEON_GLOW_STACK -> {
                Text(
                    text = "NEON",
                    color = Color(0xFF22D3EE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            CaptionTemplateId.EDITORIAL_SCRATCH -> {
                Text(
                    text = "Cinema",
                    color = Color(0xFFFDE68A),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                )
            }
            CaptionTemplateId.MUSIC_PLAYER -> {
                Text(
                    text = "Lyrics ♪",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            CaptionTemplateId.TYPEWRITER -> {
                Text(
                    text = "type|",
                    color = Color(0xFFF8FAFC),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            CaptionTemplateId.KARAOKE_HIGHLIGHT -> {
                Text(
                    text = "Sing",
                    color = Color(0xFFFACC15),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            CaptionTemplateId.POP_IN -> {
                Text(
                    text = "POP!",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            CaptionTemplateId.KINETIC_GRUNGE -> {
                Text(
                    text = "KINETIC",
                    color = Color(0xFFFACC15),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            CaptionTemplateId.COLD_INVERT -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "CO",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "LD",
                        color = Color(0xFF0A0A0C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            CaptionTemplateId.CODE_POEM -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "me.",
                        color = Color(0xFFE06C75),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "js()",
                        color = Color(0xFF61AFEF),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            CaptionTemplateId.BLACKOUT_KINETIC -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PURE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "bold",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
            CaptionTemplateId.CLEAN_SUBTITLE -> {
                Text(
                    text = "Clean",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
