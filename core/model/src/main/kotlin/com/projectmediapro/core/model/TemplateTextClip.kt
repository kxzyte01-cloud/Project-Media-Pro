package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Serializable editable text overlay stored inside a reusable [Template].
 */
@Serializable
data class TemplateTextClip(
    val id: String,
    val textContent: String,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3_000L,
    val centerX: Float = Clip.DEFAULT_TEXT_CENTER_X,
    val centerY: Float = Clip.DEFAULT_TEXT_CENTER_Y,
    val widthFraction: Float = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
    val textStyle: TextOverlayStyle? = null,
)
