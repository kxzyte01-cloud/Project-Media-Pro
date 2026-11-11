package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Slot(
    val id: String,
    val label: String,
    val requiredType: TrackType,
    val filledMediaUri: String? = null
)