package com.projectmediapro.app.template

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.editor.EditorButtonTone
import com.projectmediapro.app.editor.EditorOutlinedButton
import com.projectmediapro.app.editor.editorErrorStringRes
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Slot
import com.projectmediapro.core.model.TrackType

@Composable
fun UseTemplateScreen(
    uiState: UseTemplateUiState,
    onEvent: (UseTemplateEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingSlotId by rememberSaveable { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val slotId = pendingSlotId
        pendingSlotId = null
        if (slotId != null) {
            onEvent(UseTemplateEvent.SlotMediaPicked(slotId, uri?.toString()))
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val slotId = pendingSlotId
        pendingSlotId = null
        if (slotId != null) {
            onEvent(UseTemplateEvent.SlotMediaPicked(slotId, uri?.toString()))
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val slotId = pendingSlotId
        pendingSlotId = null
        if (slotId != null) {
            onEvent(UseTemplateEvent.SlotMediaPicked(slotId, uri?.toString()))
        }
    }

    val topBarTitle = when (uiState) {
        is UseTemplateUiState.Loaded -> stringResource(R.string.use_template_title)
        UseTemplateUiState.Loading -> stringResource(R.string.templates_loading_title)
        UseTemplateUiState.NotFound -> stringResource(R.string.templates_not_found_title)
        UseTemplateUiState.Error -> stringResource(R.string.templates_error_title)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaProTopBar(
                title = topBarTitle,
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
        when (uiState) {
            UseTemplateUiState.Loading -> {
                CenteredTemplateStatus(modifier = Modifier.padding(innerPadding)) {
                    CircularProgressIndicator()
                }
            }
            UseTemplateUiState.NotFound -> {
                CenteredTemplateStatus(modifier = Modifier.padding(innerPadding)) {
                    Text(
                        text = stringResource(R.string.templates_not_found_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            UseTemplateUiState.Error -> {
                CenteredTemplateStatus(modifier = Modifier.padding(innerPadding)) {
                    Text(
                        text = stringResource(R.string.error_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is UseTemplateUiState.Loaded -> {
                val replaceableSlots = uiState.template.slots.filter { it.isReplaceable }
                val lockedFilledCount = uiState.template.slots.count { !it.isReplaceable && it.filledMediaUri != null }
                val enabled = !uiState.isCreating

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = MediaProSpacing.Medium),
                ) {
                    Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
                    OutlinedTextField(
                        value = uiState.projectTitle,
                        onValueChange = { onEvent(UseTemplateEvent.UpdateProjectTitle(it)) },
                        enabled = enabled,
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.home_project_title_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (lockedFilledCount > 0) {
                        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                        Surface(
                            shape = MediaProShapes.Small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.use_template_locked_kept, lockedFilledCount),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = MediaProSpacing.MediumSmall,
                                    vertical = MediaProSpacing.Small,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
                    Text(
                        text = stringResource(R.string.use_template_swap_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))

                    replaceableSlots.forEach { slot ->
                        val isPicked = uiState.pickedUrisBySlotId[slot.id] != null
                        UseTemplateSlotRow(
                            slot = slot,
                            isPicked = isPicked,
                            enabled = enabled,
                            onPick = {
                                pendingSlotId = slot.id
                                when (slot.requiredType) {
                                    TrackType.AUDIO -> audioPicker.launch("audio/*")
                                    TrackType.OVERLAY -> imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                    else -> videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                    )
                                }
                            },
                            onClear = { onEvent(UseTemplateEvent.ClearPickedSlot(slot.id)) },
                        )
                        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                    }

                    if (uiState.error != null) {
                        Surface(
                            shape = MediaProShapes.Small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(
                                MediaProSpacing.BorderThin,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Text(
                                text = stringResource(editorErrorStringRes(uiState.error)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(
                                    horizontal = MediaProSpacing.MediumSmall,
                                    vertical = MediaProSpacing.Small,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                    }

                    Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                    Button(
                        onClick = { onEvent(UseTemplateEvent.CreateProject) },
                        enabled = enabled && uiState.hasAtLeastOneVideoReady,
                        shape = MediaProShapes.Small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                    ) {
                        if (uiState.isCreating) {
                            CircularProgressIndicator(
                                strokeWidth = MediaProSpacing.BorderSelected,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(MediaProSpacing.Small))
                            Text(text = stringResource(R.string.use_template_creating))
                        } else {
                            Text(text = stringResource(R.string.use_template_create_button))
                        }
                    }
                    Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
                }
            }
        }
    }
}

@Composable
private fun CenteredTemplateStatus(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MediaProSpacing.Medium),
    ) {
        content()
    }
}

@Composable
private fun UseTemplateSlotRow(
    slot: Slot,
    isPicked: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val statusText = when {
        isPicked -> stringResource(R.string.use_template_slot_selected)
        slot.filledMediaUri != null -> stringResource(R.string.use_template_slot_default)
        else -> stringResource(R.string.template_slot_empty)
    }

    Surface(
        shape = MediaProShapes.Medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isPicked) MediaProSpacing.BorderSelected else MediaProSpacing.BorderThin,
            color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .padding(MediaProSpacing.MediumSmall),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPicked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (isPicked) {
                EditorOutlinedButton(
                    label = stringResource(R.string.template_slot_clear),
                    enabled = enabled,
                    onClick = onClear,
                )
            }
            EditorOutlinedButton(
                label = if (isPicked) {
                    stringResource(R.string.template_slot_replace_media)
                } else {
                    stringResource(R.string.template_slot_pick_media)
                },
                enabled = enabled,
                tone = EditorButtonTone.Primary,
                onClick = onPick,
            )
        }
    }
}
