package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Template(
    val id: String,
    val name: String,
    val description: String,
    val slots: List<Slot> = emptyList()
)