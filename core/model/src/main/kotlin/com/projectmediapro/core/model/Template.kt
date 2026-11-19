package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Template(
    val id: String,
    val name: String,
    val description: String = "",
    val slots: List<Slot> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val canvasAspect: CanvasAspect = CanvasAspect.ORIGINAL,
    val canvasBackground: CanvasBackground = CanvasBackground.BLACK,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
