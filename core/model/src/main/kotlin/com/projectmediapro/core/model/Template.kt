package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * A reusable project structure containing ordered or typed [Slot] placeholders
 * that a user can populate and export.
 */
@Serializable
data class Template(
    val id: String,
    val name: String,
    val slots: List<Slot> = emptyList(),
)

