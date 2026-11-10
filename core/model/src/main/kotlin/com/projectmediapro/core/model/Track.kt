package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Categorizes the kind of media a [Track] carries.
 */
@Serializable
enum class TrackKind {
    VIDEO,
    OVERLAY,
    AUDIO,
}

/**
 * An ordered sequence of [Clip] items in a [Project].
 */
@Serializable
data class Track(
    val id: String,
    val kind: TrackKind,
    val clips: List<Clip> = emptyList(),
)

