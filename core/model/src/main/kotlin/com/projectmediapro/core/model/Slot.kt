package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Defines the supported slot roles in a reusable [Template].
 * Adding a new slot type requires adding one enum entry here.
 */
@Serializable
enum class SlotType {
    INTRO,
    OUTRO,
    PICTURE_IN_PICTURE,
    GIF,
    ORDERED_AUDIO,
}

/**
 * Represents a single fillable placeholder slot within a [Template].
 */
@Serializable
data class Slot(
    val id: String,
    val type: SlotType,
    val label: String,
    val orderIndex: Int = 0,
    val requiredDurationMs: Long? = null,
    val assignedMediaUri: String? = null,
)

