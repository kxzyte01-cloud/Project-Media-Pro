package com.projectmediapro.core.model

import kotlinx.serialization.json.Json

object ProjectSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun serialize(project: Project): String {
        return json.encodeToString(Project.serializer(), project)
    }

    fun deserialize(rawJson: String): Project {
        return json.decodeFromString(Project.serializer(), rawJson)
    }
}