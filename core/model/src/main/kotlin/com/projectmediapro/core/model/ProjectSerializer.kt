package com.projectmediapro.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object ProjectSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(project: Project): String {
        if (project.schemaVersion != Project.CURRENT_SCHEMA_VERSION) {
            throw SerializationException("Unsupported project schema version: ${project.schemaVersion}")
        }
        return json.encodeToString(Project.serializer(), project)
    }

    fun deserialize(rawJson: String): Project {
        val project = json.decodeFromString(Project.serializer(), rawJson)
        if (project.schemaVersion != Project.CURRENT_SCHEMA_VERSION) {
            throw SerializationException("Unsupported project schema version: ${project.schemaVersion}")
        }
        return project
    }
}
