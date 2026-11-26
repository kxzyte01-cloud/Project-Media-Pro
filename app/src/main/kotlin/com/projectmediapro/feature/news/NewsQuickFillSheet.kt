package com.projectmediapro.feature.news

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.component.SectionHeader
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.NewsBrandPreset
import com.projectmediapro.core.model.NewsCalloutMode
import com.projectmediapro.core.model.NewsClockMode
import com.projectmediapro.core.model.NewsContentConfig
import com.projectmediapro.core.model.NewsCorner
import com.projectmediapro.core.model.NewsMotionConfig
import com.projectmediapro.core.model.NewsMotionStyle
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.NewsSfxCue
import com.projectmediapro.core.model.NewsStyleConfig
import com.projectmediapro.core.model.NewsTemplateId
import com.projectmediapro.core.model.NewsThemeVariant
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val BRAND_SWATCHES: List<Long> = listOf(
    0xFFD91E2AL, // Broadcast Crimson
    0xFF1D4ED8L, // Global Cobalt
    0xFFF59E0BL, // Amber Gold
    0xFF16A34AL, // Emerald Green
    0xFF7C3AEDL, // Studio Violet
    0xFF22D3EEL, // Broadcast Cyan
    0xFF121620L, // Dark Noir
    0xFFF5EFE0L, // Cream Paper
    0xFFFFFFFFL, // Pure White
    0xFF0A0A0CL, // Ink Black
)

private val SHEET_EFFECT_LIST: List<NewsTemplateId> = listOf(
    NewsTemplateId.BREAKING_DESK_16_9,
    NewsTemplateId.DATA_TICKER_DESK,
    NewsTemplateId.LIVE_BUG_CLOCK,
    NewsTemplateId.OTS_TOPIC_CARD,
    NewsTemplateId.STINGER_WIPE,
    NewsTemplateId.EDITORIAL_QUOTE_CARD,
    NewsTemplateId.CALLOUT_ANNOTATOR,
    NewsTemplateId.SOURCE_ARCHIVE_HUD,
    NewsTemplateId.SPEAKER_LOWER_THIRD,
    NewsTemplateId.STAT_PROOF_CARD,
)

/**
 * Dark-studio 3-tab Quick-Fill Inspector Bottom Sheet (Content | Style | Motion & Audio)
 * with live animated Canvas preview and 1-tap Channel Brand Presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsQuickFillSheet(
    overlays: List<NewsOverlayItem>,
    timelinePositionMs: Long,
    onAddOverlay: (NewsOverlayItem) -> Unit,
    onUpdateOverlay: (NewsOverlayItem) -> Unit,
    onRemoveOverlay: (String) -> Unit,
    onApplyBrandPresetToAll: (NewsBrandPreset) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presetStore = remember(context) { NewsBrandPresetStore(context) }
    val bitmapCache = remember(context) { NewsOverlayBitmapCache(context) }
    DisposableEffect(bitmapCache) {
        onDispose { bitmapCache.clear() }
    }

    var presets by remember { mutableStateOf(presetStore.loadAll()) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    var editingId by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var draft by remember {
        mutableStateOf(
            createDefaultDraft(
                templateId = NewsTemplateId.BREAKING_DESK_16_9,
                startTimeMs = timelinePositionMs,
            ),
        )
    }

    // Live animated preview clock (runs ~25 fps while sheet is open)
    var previewTickMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(draft.templateId, draft.durationMs) {
        previewTickMs = 0L
        while (true) {
            delay(40L)
            val cycleMs = draft.durationMs.coerceIn(NewsOverlayItem.MIN_DURATION_MS, 5_000L)
            previewTickMs = (previewTickMs + 40L) % cycleMs
        }
    }

    val otsImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            draft = draft.copy(content = draft.content.copy(imageUri = uri.toString()))
        }
    }
    val logoImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            draft = draft.copy(content = draft.content.copy(logoUri = uri.toString()))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MediaProSpacing.Medium)
                .padding(bottom = MediaProSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.news_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close_news_sheet),
                    )
                }
            }

            // Live Scaled Canvas Preview Box
            Surface(
                shape = MediaProShapes.Medium,
                color = Color(0xFF0D1119),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (draft.content.aspectModePortrait) 9f / 11f else 16f / 9f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val previewItem = draft.copy(startTimeMs = 0L)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            NewsOverlayRenderer.renderAll(
                                canvas = canvas.nativeCanvas,
                                widthPx = size.width,
                                heightPx = size.height,
                                overlays = listOf(previewItem),
                                timeMs = previewTickMs,
                                captionsActive = false,
                                bitmapLoader = { uri -> bitmapCache.load(uri) },
                            )
                        }
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = MediaProShapes.Small,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.news_preview_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Channel Brand Presets Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SectionHeader(title = stringResource(R.string.news_section_presets))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (overlays.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val currentBrand = NewsBrandPreset(
                                    id = "active_brand",
                                    name = "Active Brand",
                                    logoUri = draft.content.logoUri,
                                    primaryColorArgb = draft.style.primaryColorArgb,
                                    secondaryColorArgb = draft.style.secondaryColorArgb,
                                    textColorArgb = draft.style.textColorArgb,
                                    highlightColorArgb = draft.style.highlightColorArgb,
                                    fontScale = draft.style.fontScale,
                                    cornerRadiusDp = draft.style.cornerRadiusDp,
                                    themeVariant = draft.theme,
                                )
                                onApplyBrandPresetToAll(currentBrand)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.news_preset_apply_all),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            newPresetName = ""
                            showSavePresetDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.news_preset_save),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                items(items = presets, key = { it.id }) { preset ->
                    Surface(
                        shape = MediaProShapes.Small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, Color(preset.primaryColorArgb).copy(alpha = 0.7f)),
                        modifier = Modifier.clickable {
                            draft = draft.withBrandPreset(preset)
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.primaryColorArgb)),
                            )
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!preset.builtIn) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.cd_delete_news_overlay),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            presets = presetStore.deleteCustom(preset.id)
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // 10-Effect Selector Strip
            SectionHeader(title = stringResource(R.string.news_section_templates))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                items(items = SHEET_EFFECT_LIST, key = { it.name }) { template ->
                    val selected = draft.templateId == template
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (editingId == null) {
                                draft = createDefaultDraft(
                                    templateId = template,
                                    startTimeMs = timelinePositionMs,
                                    preserveStyle = if (draft.style.useCustomColors) draft.style else null,
                                )
                            } else {
                                draft = draft.copy(
                                    templateId = template,
                                    badgeLabel = NewsOverlayItem.defaultBadgeFor(template),
                                    durationMs = if (template == NewsTemplateId.STINGER_WIPE) {
                                        draft.durationMs.coerceIn(
                                            NewsOverlayItem.STINGER_MIN_DURATION_MS,
                                            NewsOverlayItem.STINGER_MAX_DURATION_MS,
                                        )
                                    } else {
                                        draft.durationMs
                                    },
                                )
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(labelForTemplate(template)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            // 3-Tab Inspector: Content | Style | Motion & Audio
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.news_tab_content), maxLines = 1) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.news_tab_style), maxLines = 1) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.news_tab_motion), maxLines = 1) },
                )
            }

            when (selectedTab) {
                0 -> ContentTabSection(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onPickOtsImage = { otsImagePicker.launch("image/*") },
                    onPickLogo = { logoImagePicker.launch("image/*") },
                )
                1 -> StyleTabSection(
                    draft = draft,
                    onDraftChange = { draft = it },
                )
                else -> MotionAudioTabSection(
                    draft = draft,
                    timelinePositionMs = timelinePositionMs,
                    onDraftChange = { draft = it },
                )
            }

            // Primary Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            ) {
                if (editingId != null) {
                    OutlinedButton(
                        onClick = {
                            editingId = null
                            draft = createDefaultDraft(draft.templateId, timelinePositionMs)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.news_action_new))
                    }
                }
                Button(
                    onClick = {
                        val finalItem = draft.copy(
                            id = editingId.orEmpty(),
                            headline = draft.headline.trim().ifEmpty { NewsOverlayItem.defaultBadgeFor(draft.templateId) },
                            subline = draft.subline.trim(),
                            badgeLabel = draft.badgeLabel.trim().ifEmpty { NewsOverlayItem.defaultBadgeFor(draft.templateId) },
                            tickerText = draft.tickerText.trim(),
                            startTimeMs = if (editingId != null) draft.startTimeMs else timelinePositionMs.coerceAtLeast(0L),
                        )
                        if (editingId == null) {
                            onAddOverlay(finalItem)
                        } else {
                            onUpdateOverlay(finalItem)
                        }
                        editingId = null
                    },
                    modifier = Modifier.weight(1.4f),
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (editingId == null) R.string.news_action_add else R.string.news_action_update,
                        ),
                    )
                }
            }

            // Active Overlays on Timeline
            if (overlays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.Small))
                SectionHeader(title = stringResource(R.string.news_section_active, overlays.size))
                Column(verticalArrangement = Arrangement.spacedBy(MediaProSpacing.ExtraSmall)) {
                    overlays.forEach { item ->
                        Surface(
                            shape = MediaProShapes.Medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = if (editingId == item.id) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MediaProSpacing.Medium, vertical = MediaProSpacing.Small),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.headline,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${stringResource(labelForTemplate(item.templateId))} · ${formatSec(item.startTimeMs)}–${formatSec(item.endTimeMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingId = item.id
                                            draft = item
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.cd_edit_news_overlay),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onAddOverlay(
                                                item.copy(
                                                    id = "",
                                                    startTimeMs = timelinePositionMs.coerceAtLeast(0L),
                                                ),
                                            )
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = stringResource(R.string.cd_duplicate_news_overlay),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (editingId == item.id) editingId = null
                                            onRemoveOverlay(item.id)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.cd_delete_news_overlay),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(stringResource(R.string.news_preset_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text(stringResource(R.string.news_preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val custom = NewsBrandPreset(
                            id = "custom_${System.currentTimeMillis()}",
                            name = newPresetName.trim().ifBlank { "Channel Preset" },
                            logoUri = draft.content.logoUri,
                            primaryColorArgb = draft.style.primaryColorArgb,
                            secondaryColorArgb = draft.style.secondaryColorArgb,
                            textColorArgb = draft.style.textColorArgb,
                            highlightColorArgb = draft.style.highlightColorArgb,
                            fontScale = draft.style.fontScale,
                            cornerRadiusDp = draft.style.cornerRadiusDp,
                            themeVariant = draft.theme,
                            builtIn = false,
                        )
                        presets = presetStore.saveCustom(custom)
                        showSavePresetDialog = false
                    },
                ) {
                    Text(stringResource(R.string.news_preset_save_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text(stringResource(R.string.news_preset_cancel))
                }
            },
        )
    }
}

@Composable
private fun ContentTabSection(
    draft: NewsOverlayItem,
    onDraftChange: (NewsOverlayItem) -> Unit,
    onPickOtsImage: () -> Unit,
    onPickLogo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
        if (draft.templateId != NewsTemplateId.CALLOUT_ANNOTATOR && draft.templateId != NewsTemplateId.LIVE_BUG_CLOCK) {
            OutlinedTextField(
                value = draft.headline,
                onValueChange = { onDraftChange(draft.copy(headline = it)) },
                label = { Text(stringResource(R.string.news_field_headline)) },
                maxLines = if (draft.templateId == NewsTemplateId.EDITORIAL_QUOTE_CARD) 3 else 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (draft.templateId in setOf(
                NewsTemplateId.BREAKING_DESK_16_9,
                NewsTemplateId.OTS_TOPIC_CARD,
                NewsTemplateId.EDITORIAL_QUOTE_CARD,
                NewsTemplateId.SPEAKER_LOWER_THIRD,
                NewsTemplateId.STAT_PROOF_CARD,
            )
        ) {
            OutlinedTextField(
                value = draft.subline,
                onValueChange = { onDraftChange(draft.copy(subline = it)) },
                label = { Text(stringResource(R.string.news_field_subline)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = draft.badgeLabel,
            onValueChange = { onDraftChange(draft.copy(badgeLabel = it)) },
            label = { Text(stringResource(R.string.news_field_badge)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Breaking Lower-Third dual tabs & toggles
        if (draft.templateId == NewsTemplateId.BREAKING_DESK_16_9) {
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                OutlinedTextField(
                    value = draft.content.tab1Label,
                    onValueChange = { onDraftChange(draft.copy(content = draft.content.copy(tab1Label = it))) },
                    label = { Text(stringResource(R.string.news_field_tab1)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.content.tab2Label,
                    onValueChange = { onDraftChange(draft.copy(content = draft.content.copy(tab2Label = it))) },
                    label = { Text(stringResource(R.string.news_field_tab2)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            ToggleRow(
                label = stringResource(R.string.news_toggle_chevrons),
                checked = draft.content.showChevrons,
                onCheckedChange = { onDraftChange(draft.copy(content = draft.content.copy(showChevrons = it))) },
            )
            ToggleRow(
                label = stringResource(R.string.news_toggle_intro_pill),
                checked = draft.content.introPillReveal,
                onCheckedChange = { onDraftChange(draft.copy(content = draft.content.copy(introPillReveal = it))) },
            )
            ToggleRow(
                label = stringResource(R.string.news_toggle_portrait_mode),
                checked = draft.content.aspectModePortrait,
                onCheckedChange = { onDraftChange(draft.copy(content = draft.content.copy(aspectModePortrait = it))) },
            )
        }

        // Ticker fields
        if (draft.templateId == NewsTemplateId.BREAKING_DESK_16_9 || draft.templateId == NewsTemplateId.DATA_TICKER_DESK) {
            OutlinedTextField(
                value = draft.tickerText,
                onValueChange = { onDraftChange(draft.copy(tickerText = it)) },
                label = { Text(stringResource(R.string.news_field_ticker)) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.templateId == NewsTemplateId.DATA_TICKER_DESK) {
                ToggleRow(
                    label = stringResource(R.string.news_toggle_chip_mode),
                    checked = draft.content.tickerChipMode,
                    onCheckedChange = { onDraftChange(draft.copy(content = draft.content.copy(tickerChipMode = it))) },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.news_field_separator),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    listOf("•", "|", "///").forEach { sep ->
                        FilterChip(
                            selected = draft.content.tickerSeparator == sep,
                            onClick = { onDraftChange(draft.copy(content = draft.content.copy(tickerSeparator = sep))) },
                            label = { Text(sep) },
                        )
                    }
                }
            }
        }

        // LIVE Bug & Clock fields
        if (draft.templateId == NewsTemplateId.LIVE_BUG_CLOCK) {
            OutlinedTextField(
                value = draft.content.locationLabel,
                onValueChange = { onDraftChange(draft.copy(content = draft.content.copy(locationLabel = it))) },
                label = { Text(stringResource(R.string.news_field_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(
                label = stringResource(R.string.news_toggle_live_dot),
                checked = draft.content.showLiveDot,
                onCheckedChange = { onDraftChange(draft.copy(content = draft.content.copy(showLiveDot = it))) },
            )
            Text(text = stringResource(R.string.news_clock_mode), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                listOf(
                    NewsClockMode.OFF to R.string.news_clock_off,
                    NewsClockMode.SYSTEM_TIME to R.string.news_clock_system,
                    NewsClockMode.CUSTOM_START to R.string.news_clock_custom,
                ).forEach { (mode, labelId) ->
                    FilterChip(
                        selected = draft.content.clockMode == mode,
                        onClick = { onDraftChange(draft.copy(content = draft.content.copy(clockMode = mode))) },
                        label = { Text(stringResource(labelId)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                OutlinedButton(onClick = onPickLogo) {
                    Text(stringResource(R.string.news_pick_logo))
                }
                if (draft.content.logoUri.isNotBlank()) {
                    TextButton(onClick = { onDraftChange(draft.copy(content = draft.content.copy(logoUri = ""))) }) {
                        Text(stringResource(R.string.news_clear_image))
                    }
                }
            }
        }

        // OTS Topic Card image picker
        if (draft.templateId == NewsTemplateId.OTS_TOPIC_CARD) {
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                OutlinedButton(onClick = onPickOtsImage) {
                    Text(stringResource(R.string.news_pick_image))
                }
                if (draft.content.imageUri.isNotBlank()) {
                    TextButton(onClick = { onDraftChange(draft.copy(content = draft.content.copy(imageUri = ""))) }) {
                        Text(stringResource(R.string.news_clear_image))
                    }
                }
            }
        }

        // Stinger Wipe direction
        if (draft.templateId == NewsTemplateId.STINGER_WIPE) {
            ToggleRow(
                label = stringResource(R.string.news_toggle_wipe_rtl),
                checked = draft.content.wipeRightToLeft,
                onCheckedChange = { onDraftChange(draft.copy(content = draft.content.copy(wipeRightToLeft = it))) },
            )
        }

        // Callout Annotator mode
        if (draft.templateId == NewsTemplateId.CALLOUT_ANNOTATOR) {
            Text(text = stringResource(R.string.news_callout_mode), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                listOf(
                    NewsCalloutMode.CIRCLE to R.string.news_callout_circle,
                    NewsCalloutMode.UNDERLINE to R.string.news_callout_underline,
                    NewsCalloutMode.REDACTION_BAR to R.string.news_callout_redaction,
                ).forEach { (mode, labelId) ->
                    FilterChip(
                        selected = draft.content.calloutMode == mode,
                        onClick = { onDraftChange(draft.copy(content = draft.content.copy(calloutMode = mode))) },
                        label = { Text(stringResource(labelId)) },
                    )
                }
            }
        }

        // Source & Archive HUD
        if (draft.templateId == NewsTemplateId.SOURCE_ARCHIVE_HUD) {
            OutlinedTextField(
                value = draft.content.yearTag,
                onValueChange = { onDraftChange(draft.copy(content = draft.content.copy(yearTag = it))) },
                label = { Text(stringResource(R.string.news_field_year)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow(
                label = stringResource(R.string.news_toggle_scanlines),
                checked = draft.content.archiveScanlines,
                onCheckedChange = { onDraftChange(draft.copy(content = draft.content.copy(archiveScanlines = it))) },
            )
        }
    }
}

@Composable
private fun StyleTabSection(
    draft: NewsOverlayItem,
    onDraftChange: (NewsOverlayItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
        // Quick Theme Variants (Cream Paper / Dark Noir / Broadcast Red / Slate Blue)
        Text(text = stringResource(R.string.news_section_theme), style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
            items(items = NewsThemeVariant.values().toList(), key = { it.name }) { variant ->
                FilterChip(
                    selected = draft.theme == variant && !draft.style.useCustomColors,
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                theme = variant,
                                style = draft.style.copy(useCustomColors = false),
                            ),
                        )
                    },
                    label = { Text(stringResource(labelForTheme(variant))) },
                )
            }
        }

        ColorSwatchRow(
            title = stringResource(R.string.news_color_primary),
            selectedArgb = draft.style.primaryColorArgb,
            onSelect = { argb ->
                onDraftChange(draft.copy(style = draft.style.copy(primaryColorArgb = argb, useCustomColors = true)))
            },
        )
        ColorSwatchRow(
            title = stringResource(R.string.news_color_secondary),
            selectedArgb = draft.style.secondaryColorArgb,
            onSelect = { argb ->
                onDraftChange(draft.copy(style = draft.style.copy(secondaryColorArgb = argb, useCustomColors = true)))
            },
        )
        ColorSwatchRow(
            title = stringResource(R.string.news_color_text),
            selectedArgb = draft.style.textColorArgb,
            onSelect = { argb ->
                onDraftChange(draft.copy(style = draft.style.copy(textColorArgb = argb, useCustomColors = true)))
            },
        )
        ColorSwatchRow(
            title = stringResource(R.string.news_color_highlight),
            selectedArgb = draft.style.highlightColorArgb,
            onSelect = { argb ->
                onDraftChange(draft.copy(style = draft.style.copy(highlightColorArgb = argb, useCustomColors = true)))
            },
        )

        SliderRow(
            label = stringResource(R.string.news_slider_font_scale, (draft.style.fontScale * 100f).roundToInt()),
            value = draft.style.fontScale,
            range = 0.75f..1.50f,
            onValueChange = { onDraftChange(draft.copy(style = draft.style.copy(fontScale = it))) },
        )
        SliderRow(
            label = stringResource(R.string.news_slider_opacity, (draft.style.backgroundOpacity * 100f).roundToInt()),
            value = draft.style.backgroundOpacity,
            range = 0.25f..1.0f,
            onValueChange = { onDraftChange(draft.copy(style = draft.style.copy(backgroundOpacity = it))) },
        )
        SliderRow(
            label = stringResource(R.string.news_slider_radius, draft.style.cornerRadiusDp.roundToInt()),
            value = draft.style.cornerRadiusDp,
            range = 0f..24f,
            onValueChange = { onDraftChange(draft.copy(style = draft.style.copy(cornerRadiusDp = it))) },
        )
        if (draft.templateId == NewsTemplateId.EDITORIAL_QUOTE_CARD) {
            SliderRow(
                label = stringResource(R.string.news_slider_roughness, (draft.style.roughEdgeIntensity * 100f).roundToInt()),
                value = draft.style.roughEdgeIntensity,
                range = 0f..1f,
                onValueChange = { onDraftChange(draft.copy(style = draft.style.copy(roughEdgeIntensity = it))) },
            )
        }
        if (draft.templateId == NewsTemplateId.CALLOUT_ANNOTATOR) {
            SliderRow(
                label = stringResource(R.string.news_slider_stroke, draft.style.strokeWidthDp.roundToInt()),
                value = draft.style.strokeWidthDp,
                range = 2f..18f,
                onValueChange = { onDraftChange(draft.copy(style = draft.style.copy(strokeWidthDp = it))) },
            )
        }
    }
}

@Composable
private fun MotionAudioTabSection(
    draft: NewsOverlayItem,
    timelinePositionMs: Long,
    onDraftChange: (NewsOverlayItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
        Text(text = stringResource(R.string.news_motion_style), style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
            val styles = listOf(
                NewsMotionStyle.SLIDE to R.string.news_motion_slide,
                NewsMotionStyle.WIPE to R.string.news_motion_wipe,
                NewsMotionStyle.POP to R.string.news_motion_pop,
                NewsMotionStyle.FADE to R.string.news_motion_fade,
                NewsMotionStyle.NONE to R.string.news_motion_none,
            )
            items(items = styles, key = { it.first.name }) { (style, labelId) ->
                FilterChip(
                    selected = draft.motion.inOutStyle == style,
                    onClick = { onDraftChange(draft.copy(motion = draft.motion.copy(inOutStyle = style))) },
                    label = { Text(stringResource(labelId)) },
                )
            }
        }

        SliderRow(
            label = stringResource(R.string.news_slider_anim_speed, draft.motion.animationSpeed),
            value = draft.motion.animationSpeed,
            range = 0.5f..2.2f,
            onValueChange = { onDraftChange(draft.copy(motion = draft.motion.copy(animationSpeed = it))) },
        )

        if (draft.templateId == NewsTemplateId.DATA_TICKER_DESK || draft.templateId == NewsTemplateId.BREAKING_DESK_16_9) {
            SliderRow(
                label = stringResource(R.string.news_slider_ticker_speed, draft.motion.tickerSpeedPxPerSec.roundToInt()),
                value = draft.motion.tickerSpeedPxPerSec,
                range = 40f..280f,
                onValueChange = { onDraftChange(draft.copy(motion = draft.motion.copy(tickerSpeedPxPerSec = it))) },
            )
        }

        if (draft.templateId == NewsTemplateId.EDITORIAL_QUOTE_CARD) {
            SliderRow(
                label = stringResource(R.string.news_slider_stagger, draft.motion.lineStaggerMs.toInt()),
                value = draft.motion.lineStaggerMs.toFloat(),
                range = 0f..600f,
                onValueChange = { onDraftChange(draft.copy(motion = draft.motion.copy(lineStaggerMs = it.toLong()))) },
            )
        }

        // Start & Duration
        Text(
            text = stringResource(R.string.news_timing_start, formatSec(timelinePositionMs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val isStinger = draft.templateId == NewsTemplateId.STINGER_WIPE
        val durRange = if (isStinger) {
            NewsOverlayItem.STINGER_MIN_DURATION_MS.toFloat()..NewsOverlayItem.STINGER_MAX_DURATION_MS.toFloat()
        } else {
            1_000f..30_000f
        }
        SliderRow(
            label = stringResource(R.string.news_timing_duration, formatSec(draft.durationMs)),
            value = draft.durationMs.toFloat().coerceIn(durRange.start, durRange.endInclusive),
            range = durRange,
            onValueChange = { raw ->
                val snapped = if (isStinger) ((raw / 50f).roundToInt() * 50L) else ((raw / 250f).roundToInt() * 250L)
                onDraftChange(draft.copy(durationMs = snapped.coerceIn(durRange.start.toLong(), durRange.endInclusive.toLong())))
            },
        )

        // Corner placement for LIVE Bug, OTS Card, Source HUD
        if (draft.templateId in setOf(
                NewsTemplateId.LIVE_BUG_CLOCK,
                NewsTemplateId.OTS_TOPIC_CARD,
                NewsTemplateId.SOURCE_ARCHIVE_HUD,
            )
        ) {
            Text(text = stringResource(R.string.news_corner_label), style = MaterialTheme.typography.labelMedium)
            val corners = if (draft.templateId == NewsTemplateId.OTS_TOPIC_CARD) {
                listOf(NewsCorner.TOP_LEFT to R.string.news_corner_tl, NewsCorner.TOP_RIGHT to R.string.news_corner_tr)
            } else {
                listOf(
                    NewsCorner.TOP_LEFT to R.string.news_corner_tl,
                    NewsCorner.TOP_RIGHT to R.string.news_corner_tr,
                    NewsCorner.BOTTOM_LEFT to R.string.news_corner_bl,
                    NewsCorner.BOTTOM_RIGHT to R.string.news_corner_br,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                items(items = corners, key = { it.first.name }) { (corner, labelId) ->
                    FilterChip(
                        selected = draft.motion.corner == corner,
                        onClick = { onDraftChange(draft.copy(motion = draft.motion.copy(corner = corner))) },
                        label = { Text(stringResource(labelId)) },
                    )
                }
            }
        }

        // Normalized X/Y anchor + scale for Callout & OTS
        if (draft.templateId == NewsTemplateId.CALLOUT_ANNOTATOR) {
            SliderRow(
                label = stringResource(R.string.news_slider_anchor_x, (draft.motion.anchorX * 100f).roundToInt()),
                value = draft.motion.anchorX,
                range = 0.10f..0.90f,
                onValueChange = { onDraftChange(draft.copy(motion = draft.motion.copy(anchorX = it))) },
            )
            SliderRow(
                label = stringResource(R.string.news_slider_anchor_y, (draft.motion.anchorY * 100f).roundToInt()),
                value = draft.motion.anchorY,
                range = 0.10f..0.90f,
                onValueChange = { onDraftChange(draft.copy(motion = draft.motion.copy(anchorY = it))) },
            )
        }
        if (draft.templateId == NewsTemplateId.CALLOUT_ANNOTATOR || draft.templateId == NewsTemplateId.OTS_TOPIC_CARD) {
            SliderRow(
                label = stringResource(R.string.news_slider_scale, (draft.motion.scale * 100f).roundToInt()),
                value = draft.motion.scale,
                range = 0.5f..1.8f,
                onValueChange = { onDraftChange(draft.copy(motion = draft.motion.copy(scale = it))) },
            )
        }

        ToggleRow(
            label = stringResource(R.string.news_toggle_safe_zone),
            checked = draft.motion.safeZoneLock,
            onCheckedChange = { onDraftChange(draft.copy(motion = draft.motion.copy(safeZoneLock = it))) },
        )

        // Bundled Offline SFX Cue + Volume
        Text(text = stringResource(R.string.news_sfx_label), style = MaterialTheme.typography.labelMedium)
        val cues = listOf(
            NewsSfxCue.NONE to R.string.news_sfx_none,
            NewsSfxCue.NEWSROOM_HIT to R.string.news_sfx_hit,
            NewsSfxCue.TRANSITION_WHOOSH to R.string.news_sfx_whoosh,
            NewsSfxCue.MARKER_SCRATCH to R.string.news_sfx_scratch,
            NewsSfxCue.TELETYPE_TICK to R.string.news_sfx_teletype,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
            items(items = cues, key = { it.first.name }) { (cue, labelId) ->
                FilterChip(
                    selected = draft.motion.sfxCue == cue,
                    onClick = { onDraftChange(draft.copy(motion = draft.motion.copy(sfxCue = cue))) },
                    label = { Text(stringResource(labelId)) },
                )
            }
        }
        if (draft.motion.sfxCue != NewsSfxCue.NONE) {
            SliderRow(
                label = stringResource(R.string.news_slider_sfx_vol, (draft.motion.sfxVolume * 100f).roundToInt()),
                value = draft.motion.sfxVolume,
                range = 0f..1f,
                onValueChange = { onDraftChange(draft.copy(motion = draft.motion.copy(sfxVolume = it))) },
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(
    title: String,
    selectedArgb: Long,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items = BRAND_SWATCHES, key = { it }) { argb ->
                val isSelected = selectedArgb == argb
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(argb) },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

private fun createDefaultDraft(
    templateId: NewsTemplateId,
    startTimeMs: Long,
    preserveStyle: NewsStyleConfig? = null,
): NewsOverlayItem {
    val defaultHeadline = when (templateId) {
        NewsTemplateId.BREAKING_DESK_16_9 -> "GLOBAL SUMMIT OPENS WITH HISTORIC DEAL"
        NewsTemplateId.DATA_TICKER_DESK -> "NIFTY +1.4% | S&P 500 +0.8% | GOLD -0.3%"
        NewsTemplateId.LIVE_BUG_CLOCK -> "LIVE"
        NewsTemplateId.OTS_TOPIC_CARD -> "SPECIAL REPORT"
        NewsTemplateId.STINGER_WIPE -> "BREAKING NEWS"
        NewsTemplateId.EDITORIAL_QUOTE_CARD -> "\"Every second counts when a story breaks live.\""
        NewsTemplateId.CALLOUT_ANNOTATOR -> "KEY DETAIL"
        NewsTemplateId.SOURCE_ARCHIVE_HUD -> "CITY ARCHIVE"
        NewsTemplateId.SPEAKER_LOWER_THIRD -> "ALEX RIVERA"
        NewsTemplateId.STAT_PROOF_CARD -> "+42%"
        NewsTemplateId.BREAKING_SHORTS_9_16 -> "BREAKING UPDATE"
    }
    val defaultSubline = when (templateId) {
        NewsTemplateId.BREAKING_DESK_16_9 -> "Live coverage from the studio desk"
        NewsTemplateId.OTS_TOPIC_CARD -> "Key visual breakdown"
        NewsTemplateId.EDITORIAL_QUOTE_CARD -> "Editorial Desk · Report"
        NewsTemplateId.SPEAKER_LOWER_THIRD -> "Senior Correspondent · Studio"
        NewsTemplateId.STAT_PROOF_CARD -> "Year-over-year viewer growth"
        else -> ""
    }
    val defaultTicker = if (templateId == NewsTemplateId.DATA_TICKER_DESK || templateId == NewsTemplateId.BREAKING_DESK_16_9) {
        "NIFTY +1.4% | NASDAQ +0.9% | GOLD -0.2% | LIVE UPDATES"
    } else {
        ""
    }
    return NewsOverlayItem(
        id = "",
        templateId = templateId,
        headline = defaultHeadline,
        subline = defaultSubline,
        badgeLabel = NewsOverlayItem.defaultBadgeFor(templateId),
        tickerText = defaultTicker,
        theme = NewsOverlayItem.defaultThemeFor(templateId),
        startTimeMs = startTimeMs.coerceAtLeast(0L),
        durationMs = NewsOverlayItem.defaultDurationMsFor(templateId),
        content = NewsContentConfig(
            tickerChipMode = templateId == NewsTemplateId.DATA_TICKER_DESK,
        ),
        style = preserveStyle ?: NewsStyleConfig(),
        motion = NewsMotionConfig(
            sfxCue = NewsOverlayItem.defaultSfxFor(templateId),
        ),
    )
}

private fun labelForTemplate(templateId: NewsTemplateId): Int = when (templateId) {
    NewsTemplateId.BREAKING_DESK_16_9 -> R.string.news_tpl_breaking_desk
    NewsTemplateId.BREAKING_SHORTS_9_16 -> R.string.news_tpl_breaking_shorts
    NewsTemplateId.DATA_TICKER_DESK -> R.string.news_tpl_data_ticker
    NewsTemplateId.LIVE_BUG_CLOCK -> R.string.news_tpl_live_bug
    NewsTemplateId.OTS_TOPIC_CARD -> R.string.news_tpl_ots_card
    NewsTemplateId.STINGER_WIPE -> R.string.news_tpl_stinger_wipe
    NewsTemplateId.EDITORIAL_QUOTE_CARD -> R.string.news_tpl_editorial_quote
    NewsTemplateId.CALLOUT_ANNOTATOR -> R.string.news_tpl_callout
    NewsTemplateId.SOURCE_ARCHIVE_HUD -> R.string.news_tpl_source_hud
    NewsTemplateId.SPEAKER_LOWER_THIRD -> R.string.news_tpl_speaker_l3
    NewsTemplateId.STAT_PROOF_CARD -> R.string.news_tpl_stat_proof
}

private fun labelForTheme(variant: NewsThemeVariant): Int = when (variant) {
    NewsThemeVariant.BROADCAST_RED -> R.string.news_theme_broadcast_red
    NewsThemeVariant.CREAM_PAPER -> R.string.news_theme_cream_paper
    NewsThemeVariant.DARK_NOIR -> R.string.news_theme_dark_noir
    NewsThemeVariant.SLATE_BLUE -> R.string.news_theme_slate_blue
}

private fun formatSec(ms: Long): String = String.format(Locale.US, "%.1fs", ms.coerceAtLeast(0L) / 1_000f)
