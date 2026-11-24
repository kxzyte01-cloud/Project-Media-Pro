package com.projectmediapro.app.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.projectmediapro.app.data.export.sanitizeExportName
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.projectmediapro.app.R
import com.projectmediapro.app.editor.EditorButtonTone
import com.projectmediapro.app.editor.EditorOutlinedButton
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.mainVideoSlots
import com.projectmediapro.core.model.overlaySlots

private enum class TemplateCategory {
    ALL,
    MINE,
    LYRICS,
    MONTAGE,
    YOUTUBE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    uiState: TemplatesUiState,
    onEvent: (TemplatesEvent) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onUseTemplate: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingExportTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf(TemplateCategory.ALL) }
    val importPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onEvent(
                TemplatesEvent.ImportTemplatePack {
                    context.contentResolver.openInputStream(uri)
                },
            )
        }
    }
    val exportPackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val templateId = pendingExportTemplateId
        pendingExportTemplateId = null
        if (uri != null && templateId != null) {
            onEvent(
                TemplatesEvent.ExportTemplatePack(templateId = templateId) {
                    context.contentResolver.openOutputStream(uri)
                },
            )
        }
    }

    val defaultName = stringResource(
        R.string.templates_default_name,
        uiState.templates.size + 1,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaProTopBar(
                title = stringResource(R.string.templates_title),
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                },
                onNavigationClick = onNavigateBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MediaProSpacing.Medium),
        ) {
            item(key = "header-actions") {
                Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
                if (uiState.error != null) {
                    Text(
                        text = stringResource(templatesErrorStringRes(uiState.error)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { onEvent(TemplatesEvent.ShowCreateDialog(defaultName)) },
                        shape = MediaProShapes.Small,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                    ) {
                        Text(text = stringResource(R.string.templates_new_button))
                    }
                    OutlinedButton(
                        onClick = { importPackLauncher.launch(arrayOf("application/zip", "*/*")) },
                        shape = MediaProShapes.Small,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                    ) {
                        Text(text = stringResource(R.string.templates_import_pack))
                    }
                }
            }

            item(key = "category-filters") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val categories = listOf(
                        TemplateCategory.ALL to R.string.templates_filter_all,
                        TemplateCategory.MINE to R.string.templates_filter_mine,
                        TemplateCategory.LYRICS to R.string.templates_filter_lyrics,
                        TemplateCategory.MONTAGE to R.string.templates_filter_montage,
                        TemplateCategory.YOUTUBE to R.string.templates_filter_youtube,
                    )
                    items(items = categories, key = { it.first.name }) { (category, labelRes) ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(text = stringResource(labelRes)) },
                        )
                    }
                }
            }

            val showMine = selectedCategory == TemplateCategory.ALL || selectedCategory == TemplateCategory.MINE
            val filteredStarters = uiState.starterTemplates.filter { starter ->
                when (selectedCategory) {
                    TemplateCategory.ALL -> true
                    TemplateCategory.MINE -> false
                    TemplateCategory.LYRICS -> !starter.captionStyleJson.isNullOrBlank() ||
                        starter.name.contains("Lyric", ignoreCase = true)
                    TemplateCategory.MONTAGE -> starter.name.contains("Montage", ignoreCase = true) ||
                        starter.mainVideoSlots().size >= 3
                    TemplateCategory.YOUTUBE -> starter.canvasAspect == CanvasAspect.RATIO_16_9
                }
            }

            if (selectedCategory == TemplateCategory.MINE && uiState.templates.isEmpty()) {
                item(key = "my-templates-empty") {
                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                    Surface(
                        shape = MediaProShapes.Medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.templates_my_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(MediaProSpacing.Medium),
                        )
                    }
                }
            }

            if (showMine && uiState.templates.isNotEmpty()) {
                item(key = "my-templates-heading") {
                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                    Text(
                        text = stringResource(R.string.templates_my_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(
                    items = uiState.templates,
                    key = { it.id },
                ) { template ->
                    TemplateRowCard(
                        template = template,
                        onOpenBuilder = { onOpenTemplate(template.id) },
                        onUseClick = { onUseTemplate(template.id) },
                        onExportPackClick = {
                            pendingExportTemplateId = template.id
                            exportPackLauncher.launch("${sanitizeExportName(template.name)}.zip")
                        },
                        onDeleteClick = { onEvent(TemplatesEvent.RequestDelete(template.id)) },
                    )
                }
            }

            if (filteredStarters.isNotEmpty()) {
                item(key = "starter-templates-heading") {
                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                    Text(
                        text = stringResource(R.string.templates_starter_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                items(
                    items = filteredStarters,
                    key = { it.id },
                ) { starter ->
                    StarterTemplateCard(
                        template = starter,
                        onUseClick = { onUseTemplate(starter.id) },
                        onCustomizeClick = { onEvent(TemplatesEvent.CustomizeStarter(starter)) },
                    )
                }
            }

            item(key = "bottom-spacer") {
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
            }
        }
    }

    if (uiState.isCreateDialogVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(TemplatesEvent.DismissCreateDialog) },
            title = { Text(text = stringResource(R.string.templates_new_button)) },
            text = {
                OutlinedTextField(
                    value = uiState.draftName,
                    onValueChange = { onEvent(TemplatesEvent.UpdateDraftName(it)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { onEvent(TemplatesEvent.ConfirmCreate(defaultName)) },
                    ),
                    label = { Text(text = stringResource(R.string.templates_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(TemplatesEvent.ConfirmCreate(defaultName)) },
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Text(text = stringResource(R.string.home_create_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(TemplatesEvent.DismissCreateDialog) },
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Text(text = stringResource(R.string.home_cancel))
                }
            },
        )
    }

    val pendingDelete = uiState.templates.firstOrNull { it.id == uiState.pendingDeleteId }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { onEvent(TemplatesEvent.CancelDelete) },
            title = { Text(text = stringResource(R.string.templates_delete_title)) },
            text = { Text(text = stringResource(R.string.templates_delete_message, pendingDelete.name)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(TemplatesEvent.ConfirmDelete) },
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(TemplatesEvent.CancelDelete) },
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Text(text = stringResource(R.string.home_cancel))
                }
            },
        )
    }
}

@Composable
private fun TemplateRowCard(
    template: Template,
    onOpenBuilder: () -> Unit,
    onUseClick: () -> Unit,
    onExportPackClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenBuilder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MediaProSpacing.Medium),
        ) {
            TemplatePreviewBanner(template = template)
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (template.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Text(
                text = stringResource(
                    R.string.templates_slot_summary,
                    template.mainVideoSlots().size,
                    template.overlaySlots().size,
                    template.audioSlots().size,
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditorOutlinedButton(
                    label = stringResource(R.string.templates_use_button),
                    enabled = true,
                    tone = EditorButtonTone.Primary,
                    onClick = onUseClick,
                    modifier = Modifier.weight(1f),
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.templates_export_pack),
                    enabled = true,
                    onClick = onExportPackClick,
                )
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.defaultMinSize(
                        minWidth = MediaProSpacing.MinTouchTarget,
                        minHeight = MediaProSpacing.MinTouchTarget,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.cd_delete_template),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StarterTemplateCard(
    template: Template,
    onUseClick: () -> Unit,
    onCustomizeClick: () -> Unit,
) {
    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MediaProSpacing.Medium),
        ) {
            TemplatePreviewBanner(template = template)
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (template.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
            Text(
                text = stringResource(
                    R.string.templates_slot_summary,
                    template.mainVideoSlots().size,
                    template.overlaySlots().size,
                    template.audioSlots().size,
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditorOutlinedButton(
                    label = stringResource(R.string.templates_use_button),
                    enabled = true,
                    tone = EditorButtonTone.Primary,
                    onClick = onUseClick,
                    modifier = Modifier.weight(1f),
                )
                EditorOutlinedButton(
                    label = stringResource(R.string.templates_customize_button),
                    enabled = true,
                    onClick = onCustomizeClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun templatesErrorStringRes(error: TemplatesError): Int = when (error) {
    TemplatesError.LOAD_FAILED -> R.string.error_load_failed
    TemplatesError.SAVE_FAILED -> R.string.error_save_failed
    TemplatesError.DELETE_FAILED -> R.string.error_delete_failed
}
