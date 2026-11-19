package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SlotRole {
    INTRO,
    MAIN_VIDEO,
    OUTRO,
    OVERLAY,
    AUDIO,
}

@Serializable
data class Slot(
    val id: String,
    val label: String,
    val requiredType: TrackType,
    val filledMediaUri: String? = null,
    val role: SlotRole = SlotRole.MAIN_VIDEO,
    val isReplaceable: Boolean = true,
    val durationMs: Long = 0L,
    val startTimeMs: Long = 0L,
    val overlayCenterX: Float = Clip.DEFAULT_OVERLAY_CENTER_X,
    val overlayCenterY: Float = Clip.DEFAULT_OVERLAY_CENTER_Y,
    val overlayWidthFraction: Float = Clip.DEFAULT_OVERLAY_WIDTH_FRACTION,
)
