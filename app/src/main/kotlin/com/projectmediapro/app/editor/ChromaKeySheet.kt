package com.projectmediapro.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.ChromaKeyConfig
import com.projectmediapro.core.model.ChromaKeyMath

/**
 * Opt-in chroma-key sheet for a selected video PiP overlay.
 * Green / Blue / Magenta / Custom hex swatches + similarity / smoothness / spill sliders + reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromaKeySheet(
    initial: ChromaKeyConfig,
    onApply: (ChromaKeyConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var config by remember { mutableStateOf(initial.sanitized()) }
    var customHex by remember { mutableStateOf(config.keyColorHex) }
    val customValid = ChromaKeyConfig.normalizeHex(customHex) != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MediaProSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        ) {
            Text(stringResource(R.string.chroma_title), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chroma_enable), modifier = Modifier.weight(1f))
                Switch(checked = config.enabled, onCheckedChange = { config = config.copy(enabled = it) })
            }
            Text(stringResource(R.string.chroma_key_color), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                ChromaKeyConfig.SWATCH_HEXES.forEach { hex ->
                    val argb = ChromaKeyMath.parseHexColor(hex) ?: 0xFF00FF00.toInt()
                    val isSelected = config.keyColorHex == hex
                    val swatchDesc = stringResource(
                        when (hex) {
                            ChromaKeyConfig.GREEN_HEX -> R.string.chroma_swatch_green
                            ChromaKeyConfig.BLUE_HEX -> R.string.chroma_swatch_blue
                            else -> R.string.chroma_swatch_magenta
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape,
                            )
                            .semantics {
                                contentDescription = swatchDesc
                                selected = isSelected
                            }
                            .clickable(role = Role.RadioButton) {
                                config = config.copy(keyColorHex = hex)
                                customHex = hex
                            },
                    )
                }
            }
            OutlinedTextField(
                value = customHex,
                onValueChange = {
                    customHex = it
                    ChromaKeyConfig.normalizeHex(it)?.let { hex ->
                        config = config.copy(keyColorHex = hex)
                    }
                },
                label = { Text(stringResource(R.string.chroma_custom_hex)) },
                isError = !customValid,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ChromaSlider(R.string.chroma_similarity, config.similarity) { config = config.copy(similarity = it) }
            ChromaSlider(R.string.chroma_smoothness, config.smoothness) { config = config.copy(smoothness = it) }
            ChromaSlider(R.string.chroma_spill, config.spillSuppression) { config = config.copy(spillSuppression = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                OutlinedButton(
                    onClick = {
                        config = ChromaKeyConfig()
                        customHex = ChromaKeyConfig.DEFAULT_KEY_HEX
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                ) { Text(stringResource(R.string.chroma_reset)) }
                Button(
                    onClick = {
                        onApply(config.sanitized())
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.chroma_apply))
                }
            }
            Spacer(Modifier.height(MediaProSpacing.Large))
        }
    }
}

@Composable
private fun ChromaSlider(labelRes: Int, value: Float, onChange: (Float) -> Unit) {
    Column {
        Text(
            stringResource(labelRes, (value * 100).toInt()),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
    }
}
