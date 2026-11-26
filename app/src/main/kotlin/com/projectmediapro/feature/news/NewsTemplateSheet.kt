package com.projectmediapro.feature.news

import androidx.compose.runtime.Composable
import com.projectmediapro.core.model.NewsBrandPreset
import com.projectmediapro.core.model.NewsOverlayItem

/**
 * Backward-compatible entry point delegating to the unified 3-tab [NewsQuickFillSheet].
 */
@Composable
fun NewsTemplateSheet(
    overlays: List<NewsOverlayItem>,
    timelinePositionMs: Long,
    onAddOverlay: (NewsOverlayItem) -> Unit,
    onUpdateOverlay: (NewsOverlayItem) -> Unit,
    onRemoveOverlay: (String) -> Unit,
    onApplyBrandPresetToAll: (NewsBrandPreset) -> Unit = {},
    onDismiss: () -> Unit,
) {
    NewsQuickFillSheet(
        overlays = overlays,
        timelinePositionMs = timelinePositionMs,
        onAddOverlay = onAddOverlay,
        onUpdateOverlay = onUpdateOverlay,
        onRemoveOverlay = onRemoveOverlay,
        onApplyBrandPresetToAll = onApplyBrandPresetToAll,
        onDismiss = onDismiss,
    )
}
