package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Output and preview frame aspect ratio for a [Project].
 * Later features (overlays, text) place elements as fractions (`0..1`) of this canvas.
 */
@Serializable
enum class CanvasAspect {
    ORIGINAL,
    RATIO_16_9,
    RATIO_9_16,
    RATIO_1_1,
    RATIO_4_5,
}

/**
 * Background fill shown behind fitted clips when the clip aspect ratio differs from [CanvasAspect].
 */
@Serializable
enum class CanvasBackground {
    BLACK,
    WHITE,
    GREY,
}

/**
 * Target export resolution remembered per [Project].
 */
@Serializable
enum class ExportQuality {
    HD_720P,
    FULL_HD_1080P,
    UHD_4K,
}

/**
 * Root project model persisted as JSON.
 * Later features (overlays, text) place elements as fractions (`0..1`) of the canvas defined by
 * [canvasAspect].
 */
@Serializable
data class Project(
    val id: String,
    val title: String,
    val durationMs: Long = 0L,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val canvasAspect: CanvasAspect = CanvasAspect.ORIGINAL,
    val canvasBackground: CanvasBackground = CanvasBackground.BLACK,
    val exportQuality: ExportQuality = ExportQuality.FULL_HD_1080P,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
