package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Clip(
    val id: String,
    val mediaUri: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f
)