package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Minimal representation of a media segment placed on a [Track].
 */
@Serializable
data class Clip(
    val id: String,
    val sourceUri: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val linkedSlotId: String? = null,
)

