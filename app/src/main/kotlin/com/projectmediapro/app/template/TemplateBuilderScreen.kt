package com.projectmediapro.app.template

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.app.editor.EditorButtonTone
import com.projectmediapro.app.editor.EditorOutlinedButton
import com.projectmediapro.app.editor.EditorSliderDialog
import com.projectmediapro.app.editor.editorErrorStringRes
import com.projectmediapro.core.designsystem.component.MediaProTopBar
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.Slot
import com.projectmediapro.core.model.SlotRole
import com.projectmediapro.core.model.TemplateBuilder
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.introSlot
import com.projectmediapro.core.model.mainVideoSlots
import com.projectmediapro.core.model.outroSlot
import com.projectmediapro.core.model.overlaySlots
import kotlin.math.roundToLong

@Composable
fun TemplateBuilderScreen(
    uiState: TemplateBuilderUiState,
    onEvent: (TemplateBuilderEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingPickSlotId by remember { mutableStateOf<String?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val slotId = pendingPickSlotId
        pendingPickSlotId = null
        if (slotId != null) {
            onEvent(TemplateBuilderEvent.SlotMediaPicked(slotId, uri?.toString()))
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val slotId = pendingPickSlotId
        pendingPickSlotId = null
        if (slotId != null) {
            onEvent(TemplateBuilderEvent.SlotMediaPicked(slotId, uri?.toString()))
        }
    }

    val overlayPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val slotId = pendingPickSlotId
        pendingPickSlotId = null
        if (slotId != null) {
            onEvent(TemplateBuilderEvent.SlotMediaPicked(slotId, uri?.toString()))
        } else {
            onEvent(TemplateBuilderEvent.OverlayPicked(uri?.toString()))
        }
    }

    val topBarTitle = when (uiState) {
        is TemplateBuilderUiState.Loaded -> uiState.template.name
        TemplateBuilderUiState.Loading -> stringResource(R.string.templates_loading_title)
        TemplateBuilderUiState.NotFound -> stringResource(R.string.templates_not_found_title)
        TemplateBuilderUiState.Error -> stringResource(R.string.templates_error_title)
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
            TemplateBuilderUiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    CircularProgressIndicator()
                }
            }
            TemplateBuilderUiState.NotFound -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Text(
                        text = stringResource(R.string.templates_not_found_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TemplateBuilderUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Text(
                        text = stringResource(R.string.error_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is TemplateBuilderUiState.Loaded -> {
                LoadedTemplateBuilder(
                    state = uiState,
                    onPickVideoForSlot = { slotId ->
                        pendingPickSlotId = slotId
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                    onPickAudioForSlot = { slotId ->
                        pendingPickSlotId = slotId
                        audioPickerLauncher.launch("audio/*")
                    },
                    onPickOverlay = { slotId ->
                        pendingPickSlotId = slotId
                        overlayPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onEvent = onEvent,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun LoadedTemplateBuilder(
    state: TemplateBuilderUiState.Loaded,
    onPickVideoForSlot: (String) -> Unit,
    onPickAudioForSlot: (String) -> Unit,
    onPickOverlay: (String?) -> Unit,
    onEvent: (TemplateBuilderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = state.template
    val intro = template.introSlot()
    val mainVideos = template.mainVideoSlots()
    val outro = template.outroSlot()
    val overlays = template.overlaySlots()
    val audios = template.audioSlots()
    val selectedSlot = template.slots.firstOrNull { it.id == state.selectedSlotId }
    val enabled = !state.isImporting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MediaProSpacing.Medium),
    ) {
        Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
        Text(
            text = stringResource(R.string.editor_lane_video),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MediaProSpacing.ExtraSmall),
        )
        Surface(
            shape = MediaProShapes.Medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .systemGestureExclusion(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(MediaProSpacing.Small),
            ) {
                if (intro != null) {
                    TemplateSlotBlock(
                        title = stringResource(R.string.template_slot_intro),
                        subtitle = slotSubtitle(intro),
                        isSelected = intro.id == state.selectedSlotId,
                        isEnabled = enabled,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = { onEvent(TemplateBuilderEvent.SelectSlot(intro.id)) },
                    )
                }
                mainVideos.forEachIndexed { idx, slot ->
                    TemplateSlotBlock(
                        title = stringResource(R.string.template_slot_video_n, idx + 1),
                        subtitle = slotSubtitle(slot),
                        isSelected = slot.id == state.selectedSlotId,
                        isEnabled = enabled,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = { onEvent(TemplateBuilderEvent.SelectSlot(slot.id)) },
                    )
                }
                if (outro != null) {
                    TemplateSlotBlock(
                        title = stringResource(R.string.template_slot_outro),
                        subtitle = slotSubtitle(outro),
                        isSelected = outro.id == state.selectedSlotId,
                        isEnabled = enabled,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        onClick = { onEvent(TemplateBuilderEvent.SelectSlot(outro.id)) },
                    )
                }
            }
        }

        if (overlays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Text(
                text = stringResource(R.string.editor_lane_overlays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MediaProSpacing.ExtraSmall),
            )
            Surface(
                shape = MediaProShapes.Medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .systemGestureExclusion(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(MediaProSpacing.Small),
                ) {
                    overlays.forEachIndexed { idx, slot ->
                        TemplateSlotBlock(
                            title = "O${idx + 1}",
                            subtitle = ProjectTimeline.formatDuration(slot.durationMs),
                            isSelected = slot.id == state.selectedSlotId,
                            isEnabled = enabled,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { onEvent(TemplateBuilderEvent.SelectSlot(slot.id)) },
                        )
                    }
                }
            }
        }

        if (audios.isNotEmpty()) {
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
            Text(
                text = stringResource(R.string.editor_lane_music),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MediaProSpacing.ExtraSmall),
            )
            Surface(
                shape = MediaProShapes.Medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .systemGestureExclusion(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(MediaProSpacing.Small),
                ) {
                    audios.forEachIndexed { idx, slot ->
                        TemplateSlotBlock(
                            title = "${idx + 1}",
                            subtitle = slotSubtitle(slot),
                            isSelected = slot.id == state.selectedSlotId,
                            isEnabled = enabled,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { onEvent(TemplateBuilderEvent.SelectSlot(slot.id)) },
                        )
                    }
                }
            }
        }

        if (selectedSlot != null) {
            Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
            SlotActionBar(
                slot = selectedSlot,
                audioIndex = audios.indexOfFirst { it.id == selectedSlot.id },
                audioCount = audios.size,
                canRemove = selectedSlot.role == SlotRole.OVERLAY ||
                    selectedSlot.role == SlotRole.AUDIO ||
                    (selectedSlot.role == SlotRole.MAIN_VIDEO && mainVideos.size > 1),
                enabled = enabled,
                onPickMedia = {
                    when (selectedSlot.role) {
                        SlotRole.INTRO, SlotRole.MAIN_VIDEO, SlotRole.OUTRO -> onPickVideoForSlot(selectedSlot.id)
                        SlotRole.AUDIO -> onPickAudioForSlot(selectedSlot.id)
                        SlotRole.OVERLAY -> onPickOverlay(selectedSlot.id)
                    }
                },
                onEditDuration = { onEvent(TemplateBuilderEvent.OpenOverlayDuration) },
                onMoveAudio = { delta -> onEvent(TemplateBuilderEvent.MoveAudioSlot(selectedSlot.id, delta)) },
                onToggleReplaceable = { onEvent(TemplateBuilderEvent.ToggleReplaceable(selectedSlot.id)) },
                onClearMedia = { onEvent(TemplateBuilderEvent.ClearSlotMedia(selectedSlot.id)) },
                onRemoveSlot = { onEvent(TemplateBuilderEvent.RemoveSlot(selectedSlot.id)) },
            )
        }

        Spacer(modifier = Modifier.height(MediaProSpacing.Medium))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier.fillMaxWidth(),
        ) {
            EditorOutlinedButton(
                label = stringResource(R.string.template_add_video_slot),
                enabled = enabled,
                onClick = { onEvent(TemplateBuilderEvent.AddVideoSlot) },
                modifier = Modifier.weight(1f),
            )
            EditorOutlinedButton(
                label = stringResource(R.string.template_add_audio_slot),
                enabled = enabled,
                onClick = { onEvent(TemplateBuilderEvent.AddAudioSlot) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(MediaProSpacing.Small))
        EditorOutlinedButton(
            label = stringResource(R.string.editor_add_overlay),
            enabled = enabled,
            onClick = { onPickOverlay(null) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Spacer(modifier = Modifier.height(MediaProSpacing.Small))
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
                    text = stringResource(editorErrorStringRes(state.error)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(
                        horizontal = MediaProSpacing.MediumSmall,
                        vertical = MediaProSpacing.Small,
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(MediaProSpacing.ExtraLarge))
    }

    if (state.isEditingOverlayDuration && selectedSlot?.role == SlotRole.OVERLAY) {
        TemplateOverlayDurationDialog(
            initialDurationMs = selectedSlot.durationMs,
            onConfirm = { durationMs ->
                onEvent(TemplateBuilderEvent.ApplyOverlayDuration(durationMs))
            },
            onDismiss = { onEvent(TemplateBuilderEvent.DismissDialog) },
        )
    }
}

@Composable
private fun SlotActionBar(
    slot: Slot,
    audioIndex: Int,
    audioCount: Int,
    canRemove: Boolean,
    enabled: Boolean,
    onPickMedia: () -> Unit,
    onEditDuration: () -> Unit,
    onMoveAudio: (Int) -> Unit,
    onToggleReplaceable: () -> Unit,
    onClearMedia: () -> Unit,
    onRemoveSlot: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        EditorOutlinedButton(
            label = if (slot.filledMediaUri == null) {
                stringResource(R.string.template_slot_pick_media)
            } else {
                stringResource(R.string.template_slot_replace_media)
            },
            enabled = enabled,
            tone = EditorButtonTone.Primary,
            onClick = onPickMedia,
        )
        if (slot.role == SlotRole.OVERLAY) {
            EditorOutlinedButton(
                label = stringResource(R.string.template_slot_duration),
                enabled = enabled,
                onClick = onEditDuration,
            )
        }
        if (slot.role == SlotRole.AUDIO) {
            EditorOutlinedButton(
                label = stringResource(R.string.editor_tool_earlier),
                enabled = enabled && audioIndex > 0,
                onClick = { onMoveAudio(-1) },
            )
            EditorOutlinedButton(
                label = stringResource(R.string.editor_tool_later),
                enabled = enabled && audioIndex in 0 until (audioCount - 1),
                onClick = { onMoveAudio(1) },
            )
        }
        if (slot.role == SlotRole.MAIN_VIDEO || slot.role == SlotRole.AUDIO) {
            EditorOutlinedButton(
                label = if (slot.isReplaceable) {
                    stringResource(R.string.template_slot_swap_on_use)
                } else {
                    stringResource(R.string.template_slot_locked)
                },
                enabled = enabled,
                onClick = onToggleReplaceable,
            )
        }
        if (slot.filledMediaUri != null && slot.role != SlotRole.OVERLAY) {
            EditorOutlinedButton(
                label = stringResource(R.string.template_slot_clear),
                enabled = enabled,
                onClick = onClearMedia,
            )
        }
        if (canRemove) {
            EditorOutlinedButton(
                label = stringResource(R.string.editor_remove_confirm),
                enabled = enabled,
                tone = EditorButtonTone.Destructive,
                onClick = onRemoveSlot,
            )
        }
    }
}

@Composable
private fun TemplateSlotBlock(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val borderStroke = if (isSelected) {
        BorderStroke(MediaProSpacing.BorderSelected, contentColor)
    } else {
        BorderStroke(MediaProSpacing.BorderThin, MaterialTheme.colorScheme.outline)
    }
    val description = "$title, $subtitle"

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = MediaProShapes.Small,
        color = containerColor,
        contentColor = contentColor,
        border = borderStroke,
        modifier = Modifier
            .widthIn(min = 76.dp)
            .defaultMinSize(
                minWidth = MediaProSpacing.MinTouchTarget,
                minHeight = MediaProSpacing.MinTouchTarget,
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                selected = isSelected
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = MediaProSpacing.MediumSmall,
                vertical = MediaProSpacing.Small,
            ),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun slotSubtitle(slot: Slot): String {
    return when {
        slot.filledMediaUri != null && slot.durationMs > 0L ->
            ProjectTimeline.formatDuration(slot.durationMs)
        slot.filledMediaUri != null ->
            stringResource(R.string.template_slot_set)
        slot.isReplaceable ->
            stringResource(R.string.template_slot_swap_short)
        else ->
            stringResource(R.string.template_slot_empty)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateOverlayDurationDialog(
    initialDurationMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val minMs = TemplateBuilder.MIN_OVERLAY_DURATION_MS
    val maxMs = TemplateBuilder.MAX_OVERLAY_DURATION_MS
    var durationMs by rememberSaveable(initialDurationMs) {
        mutableLongStateOf(initialDurationMs.coerceIn(minMs, maxMs))
    }

    EditorSliderDialog(
        title = stringResource(R.string.template_overlay_duration_title),
        readoutText = ProjectTimeline.formatDuration(durationMs),
        confirmLabel = stringResource(R.string.editor_apply),
        isConfirmEnabled = true,
        onConfirm = { onConfirm(durationMs) },
        onDismiss = onDismiss,
    ) {
        Slider(
            value = durationMs.toFloat(),
            onValueChange = { raw ->
                durationMs = raw.roundToLong().coerceIn(minMs, maxMs)
            },
            valueRange = minMs.toFloat()..maxMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
