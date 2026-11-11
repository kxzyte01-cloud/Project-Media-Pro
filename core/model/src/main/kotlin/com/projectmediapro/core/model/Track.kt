package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val type: TrackType,
    val clips: List<Clip> = emptyList()
)

@Serializable
enum class TrackType {
    VIDEO, AUDIO, TEXT
}