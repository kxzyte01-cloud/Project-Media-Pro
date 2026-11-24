package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CanvasAspect(val displayLabel: String) {
    ORIGINAL("Auto"),
    RATIO_16_9("16:9"),
    RATIO_9_16("9:16"),
    RATIO_1_1("1:1"),
    RATIO_4_5("4:5"),
}

@Serializable
enum class CanvasBackground {
    BLACK,
    WHITE,
    GREY,
}

@Serializable
enum class ExportQuality {
    HD_720P,
    FULL_HD_1080P,
    QHD_1440P,
    UHD_4K,
}

@Serializable
enum class ExportFrameRate {
    AUTO,
    FPS_30,
    FPS_60,
}

@Serializable
enum class ExportBitrate {
    FAST_SMALL,
    STANDARD,
    HIGH,
}

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
    val exportFrameRate: ExportFrameRate = ExportFrameRate.AUTO,
    val exportBitrate: ExportBitrate = ExportBitrate.STANDARD,
    val beatMarkers: List<BeatMarker> = emptyList(),
    val soundReactiveEnabled: Boolean = false,
    val opticalFlowEnabled: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
