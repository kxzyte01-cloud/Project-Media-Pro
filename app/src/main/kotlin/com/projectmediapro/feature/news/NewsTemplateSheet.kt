package com.projectmediapro.feature.news

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.components.SectionHeader
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.NewsTemplateId
import com.projectmediapro.core.model.NewsThemeVariant
import com.projectmediapro.core.model.ProjectTimeline
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsTemplateSheet(
    overlays: List<NewsOverlayItem>,
    timelinePositionMs: Long,
    onAddOverlay: (NewsOverlayItem) -> Unit,
    onUpdateOverlay: (NewsOverlayItem) -> Unit,
    onRemoveOverlay: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingId by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf(NewsTemplateId.BREAKING_DESK_16_9) }
    var selectedTheme by remember { mutableStateOf(NewsOverlayItem.defaultThemeFor(NewsTemplateId.BREAKING_DESK_16_9)) }
    var headline by remember { mutableStateOf("") }
    var subline by remember { mutableStateOf("") }
    var badgeLabel by remember { mutableStateOf(NewsOverlayItem.defaultBadgeFor(NewsTemplateId.BREAKING_DESK_16_9)) }
    var tickerText by remember { mutableStateOf("") }
    var startTimeMs by remember(timelinePositionMs) { mutableLongStateOf(timelinePositionMs.coerceAtLeast(0L)) }
    var durationMs by remember { mutableLongStateOf(NewsOverlayItem.defaultDurationMsFor(NewsTemplateId.BREAKING_DESK_16_9)) }

    val showTickerField = selectedTemplate == NewsTemplateId.BREAKING_DESK_16_9 ||
        selectedTemplate == NewsTemplateId.DATA_TICKER_DESK

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MediaProSpacing.Medium)
                .navigationBarsPadding()
                .padding(bottom = MediaProSpacing.Medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.news_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                        contentDescription = stringResource(R.string.cd_close_news_sheet),
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.news_section_templates))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = NewsTemplateId.values().toList(), key = { it.name }) { template ->
                    val isSelected = template == selectedTemplate
                    Surface(
                        shape = MediaProShapes.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else MediaProSpacing.BorderThin,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget)
                            .clickable {
                                selectedTemplate = template
                                if (editingId == null) {
                                    selectedTheme = NewsOverlayItem.defaultThemeFor(template)
                                    badgeLabel = NewsOverlayItem.defaultBadgeFor(template)
                                    durationMs = NewsOverlayItem.defaultDurationMsFor(template)
                                }
                            },
                    ) {
                        Text(
                            text = stringResource(labelForTemplate(template)),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(
                                horizontal = MediaProSpacing.Medium,
                                vertical = MediaProSpacing.Small,
                            ),
                        )
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.news_section_quick_fill))
            OutlinedTextField(
                value = headline,
                onValueChange = { headline = it.take(180) },
                label = { Text(text = stringResource(R.string.news_field_headline)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subline,
                onValueChange = { subline = it.take(180) },
                label = { Text(text = stringResource(R.string.news_field_subline)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = badgeLabel,
                onValueChange = { badgeLabel = it.take(48) },
                label = { Text(text = stringResource(R.string.news_field_badge)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showTickerField) {
                OutlinedTextField(
                    value = tickerText,
                    onValueChange = { tickerText = it.take(400) },
                    label = { Text(text = stringResource(R.string.news_field_ticker)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(title = stringResource(R.string.news_section_theme))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = NewsThemeVariant.values().toList(), key = { it.name }) { variant ->
                    FilterChip(
                        selected = variant == selectedTheme,
                        onClick = { selectedTheme = variant },
                        label = { Text(text = stringResource(labelForTheme(variant))) },
                        modifier = Modifier.defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.news_section_timing))
            Text(
                text = stringResource(R.string.news_timing_start, ProjectTimeline.formatDuration(startTimeMs)),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.news_timing_duration, ProjectTimeline.formatDuration(durationMs)),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
            Slider(
                value = (durationMs / 1_000f).coerceIn(1f, 30f),
                onValueChange = { secs ->
                    durationMs = (secs.roundToLong() * 1_000L).coerceIn(
                        NewsOverlayItem.MIN_DURATION_MS,
                        30_000L,
                    )
                },
                valueRange = 1f..30f,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (editingId != null) {
                    TextButton(
                        onClick = {
                            editingId = null
                            headline = ""
                            subline = ""
                            tickerText = ""
                            badgeLabel = NewsOverlayItem.defaultBadgeFor(selectedTemplate)
                            startTimeMs = timelinePositionMs.coerceAtLeast(0L)
                            durationMs = NewsOverlayItem.defaultDurationMsFor(selectedTemplate)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                    ) {
                        Text(text = stringResource(R.string.news_action_new))
                    }
                }
                Button(
                    onClick = {
                        val effectiveHeadline = headline.trim().ifEmpty {
                            badgeLabel.trim().ifEmpty { NewsOverlayItem.defaultBadgeFor(selectedTemplate) }
                        }
                        val item = NewsOverlayItem(
                            id = editingId ?: "",
                            templateId = selectedTemplate,
                            headline = effectiveHeadline,
                            subline = subline.trim(),
                            badgeLabel = badgeLabel.trim(),
                            tickerText = tickerText.trim(),
                            theme = selectedTheme,
                            startTimeMs = startTimeMs,
                            durationMs = durationMs,
                        )
                        if (editingId != null) {
                            onUpdateOverlay(item)
                        } else {
                            onAddOverlay(item)
                        }
                        editingId = null
                        headline = ""
                        subline = ""
                        tickerText = ""
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = MediaProSpacing.MinTouchTarget),
                ) {
                    Text(
                        text = stringResource(
                            if (editingId != null) R.string.news_action_update else R.string.news_action_add,
                        ),
                    )
                }
            }

            if (overlays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                SectionHeader(title = stringResource(R.string.news_section_active, overlays.size))
                overlays.forEach { item ->
                    Surface(
                        shape = MediaProShapes.Medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MediaProSpacing.Small, vertical = MediaProSpacing.ExtraSmall),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.headline,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${stringResource(labelForTemplate(item.templateId))} • ${ProjectTimeline.formatDuration(item.startTimeMs)}–${ProjectTimeline.formatDuration(item.endTimeMs)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingId = item.id
                                    selectedTemplate = item.templateId
                                    selectedTheme = item.theme
                                    headline = item.headline
                                    subline = item.subline
                                    badgeLabel = item.badgeLabel
                                    tickerText = item.tickerText
                                    startTimeMs = item.startTimeMs
                                    durationMs = item.durationMs
                                },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = MediaProSpacing.MinTouchTarget,
                                    minHeight = MediaProSpacing.MinTouchTarget,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_news_overlay),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (editingId == item.id) editingId = null
                                    onRemoveOverlay(item.id)
                                },
                                modifier = Modifier.defaultMinSize(
                                    minWidth = MediaProSpacing.MinTouchTarget,
                                    minHeight = MediaProSpacing.MinTouchTarget,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_news_overlay),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun labelForTemplate(templateId: NewsTemplateId): Int = when (templateId) {
    NewsTemplateId.BREAKING_DESK_16_9 -> R.string.news_tpl_breaking_desk
    NewsTemplateId.BREAKING_SHORTS_9_16 -> R.string.news_tpl_breaking_shorts
    NewsTemplateId.EDITORIAL_QUOTE_CARD -> R.string.news_tpl_editorial_quote
    NewsTemplateId.SPEAKER_LOWER_THIRD -> R.string.news_tpl_speaker_l3
    NewsTemplateId.STAT_PROOF_CARD -> R.string.news_tpl_stat_proof
    NewsTemplateId.DATA_TICKER_DESK -> R.string.news_tpl_data_ticker
}

private fun labelForTheme(theme: NewsThemeVariant): Int = when (theme) {
    NewsThemeVariant.BROADCAST_RED -> R.string.news_theme_broadcast_red
    NewsThemeVariant.CREAM_PAPER -> R.string.news_theme_cream_paper
    NewsThemeVariant.DARK_NOIR -> R.string.news_theme_dark_noir
    NewsThemeVariant.SLATE_BLUE -> R.string.news_theme_slate_blue
}
