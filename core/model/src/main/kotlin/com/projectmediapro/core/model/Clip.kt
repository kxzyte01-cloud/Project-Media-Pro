package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * A single media or text segment on a [Track].
 */
@Serializable
data class Clip(
    val id: String,
    val mediaUri: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val playbackSpeed: Float = DEFAULT_PLAYBACK_SPEED,
    val volume: Float = 1.0f,
    val sourceDurationMs: Long = 0L,
    val overlayCenterX: Float = DEFAULT_OVERLAY_CENTER_X,
    val overlayCenterY: Float = DEFAULT_OVERLAY_CENTER_Y,
    val overlayWidthFraction: Float = DEFAULT_OVERLAY_WIDTH_FRACTION,
    val textContent: String = "",
    val textStyle: TextOverlayStyle? = null,
    val visualEffects: ClipVisualEffects? = null,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: ClipCrop? = null,
    val isStillImage: Boolean = false,
    val keyframes: List<OverlayKeyframe> = emptyList(),
    val audioBoost: Float = DEFAULT_AUDIO_BOOST,
    val audioFadeInMs: Long = 0L,
    val audioFadeOutMs: Long = 0L,
    val speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
) {
    val hasActiveTransform: Boolean
        get() = (rotationDegrees % 360) != 0 || flipHorizontal || flipVertical

    val hasActiveCrop: Boolean
        get() = crop != null && !crop.sanitized().isIdentity

    val hasKeyframes: Boolean
        get() = keyframes.isNotEmpty()

    val hasCustomAudioMix: Boolean
        get() = volume < 1.0f || audioBoost > DEFAULT_AUDIO_BOOST || audioFadeInMs > 0L || audioFadeOutMs > 0L

    companion object {
        const val MIN_PLAYBACK_SPEED: Float = 0.25f
        const val MAX_PLAYBACK_SPEED: Float = 4.0f
        const val DEFAULT_PLAYBACK_SPEED: Float = 1.0f

        const val DEFAULT_AUDIO_BOOST: Float = 1.0f
        const val MAX_AUDIO_BOOST: Float = 2.0f
        const val MAX_AUDIO_FADE_MS: Long = 5_000L

        const val DEFAULT_OVERLAY_CENTER_X: Float = 0.8f
        const val DEFAULT_OVERLAY_CENTER_Y: Float = 0.2f
        const val DEFAULT_OVERLAY_WIDTH_FRACTION: Float = 0.3f
        const val MIN_OVERLAY_WIDTH_FRACTION: Float = 0.1f
        const val MAX_OVERLAY_WIDTH_FRACTION: Float = 1.0f

        const val DEFAULT_TEXT_CENTER_X: Float = 0.5f
        const val DEFAULT_TEXT_CENTER_Y: Float = 0.8f
        const val DEFAULT_TEXT_WIDTH_FRACTION: Float = 0.85f
    }
}
