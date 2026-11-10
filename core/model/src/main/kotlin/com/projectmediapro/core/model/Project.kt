package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Root serializable data model for a Project Media Pro editing project.
 */
@Serializable
data class Project(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val tracks: List<Track> = emptyList(),
    val template: Template? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

