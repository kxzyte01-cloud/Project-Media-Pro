package com.projectmediapro.app.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.overlayClips
import com.projectmediapro.core.model.videoClips
import java.util.Locale

private data class TemplateSlotOption(
    val clipId: String,
    val label: String,
    val durationLabel: String,
    val defaultReplaceable: Boolean,
)

private fun formatShortDuration(ms: Long): String {
    val secs = (ms.coerceAtLeast(0L) / 1_000.0)
    return String.format(Locale.ROOT, "%.1fs", secs)
}

@Composable
fun SaveAsTemplateDialog(
    project: Project,
    onConfirm: (name: String, description: String, replaceableClipIds: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val slotOptions = remember(project) {
        buildList {
            project.videoClips().forEachIndexed { idx, clip ->
                add(TemplateSlotOption(clip.id, "Video ${idx + 1}", formatShortDuration(clip.durationMs), defaultReplaceable = true))
            }
            project.audioClips().forEachIndexed { idx, clip ->
                add(TemplateSlotOption(clip.id, "Audio ${idx + 1}", formatShortDuration(clip.durationMs), defaultReplaceable = false))
            }
            project.overlayClips().forEachIndexed { idx, clip ->
                add(TemplateSlotOption(clip.id, "Overlay ${idx + 1}", formatShortDuration(clip.durationMs), defaultReplaceable = false))
            }
        }
    }

    var templateName by remember(project.id) {
        mutableStateOf("${project.title.trim()} Template".take(TemplateRepository.MAX_NAME_LENGTH))
    }
    var description by remember(project.id) { mutableStateOf("") }
    var replaceableIds by remember(project.id) {
        mutableStateOf(slotOptions.filter { it.defaultReplaceable }.map { it.clipId }.toSet())
    }
    val canSave = templateName.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.editor_save_template_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it.take(TemplateRepository.MAX_NAME_LENGTH) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSave) onConfirm(templateName, description, replaceableIds)
                        },
                    ),
                    label = { Text(text = stringResource(R.string.editor_save_template_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(160) },
                    maxLines = 2,
                    label = { Text(text = stringResource(R.string.editor_save_template_desc_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.editor_save_template_preserves),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Text(
                    text = stringResource(R.string.editor_save_template_slots_heading),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.editor_save_template_slots_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                slotOptions.forEach { option ->
                    val isReplaceable = option.clipId in replaceableIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget)
                            .clickable(
                                role = Role.Switch,
                                onClick = {
                                    replaceableIds = if (isReplaceable) {
                                        replaceableIds - option.clipId
                                    } else {
                                        replaceableIds + option.clipId
                                    }
                                },
                            ),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${option.label} • ${option.durationLabel}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(
                                    if (isReplaceable) {
                                        R.string.editor_save_template_replaceable
                                    } else {
                                        R.string.editor_save_template_locked
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isReplaceable,
                            onCheckedChange = null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(templateName, description, replaceableIds) },
                enabled = canSave,
            ) {
                Text(text = stringResource(R.string.editor_save_template_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_cancel))
            }
        },
    )
}
