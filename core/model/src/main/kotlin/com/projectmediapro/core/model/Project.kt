package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val title: String,
    val durationMs: Long = 0L,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)