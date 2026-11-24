package com.projectmediapro.app.home

import com.projectmediapro.core.model.CanvasAspect
import java.util.Locale

/**
 * Pure formatting helpers for Home project cards (duration and aspect badges).
 */
object HomeMetadataFormatter {

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    fun formatAspect(aspect: CanvasAspect): String = when (aspect) {
        CanvasAspect.RATIO_9_16 -> "9:16"
        CanvasAspect.RATIO_16_9 -> "16:9"
        CanvasAspect.RATIO_1_1 -> "1:1"
        CanvasAspect.RATIO_4_5 -> "4:5"
        CanvasAspect.ORIGINAL -> "Auto"
    }
}
