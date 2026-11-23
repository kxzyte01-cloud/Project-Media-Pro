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
    val visualEffects: ClipVisualEffects? = null,
    val playbackSpeed: Float = Clip.DEFAULT_PLAYBACK_SPEED,
    val speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
    val volume: Float = 1.0f,
    val audioBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
    val audioFadeInMs: Long = 0L,
    val audioFadeOutMs: Long = 0L,
    val audioEnhance: AudioEnhanceConfig? = null,
    val overlayStyle: OverlayVisualStyle? = null,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: ClipCrop? = null,
)
