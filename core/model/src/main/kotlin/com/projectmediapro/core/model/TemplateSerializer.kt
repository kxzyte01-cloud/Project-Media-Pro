package com.projectmediapro.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object TemplateSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(template: Template): String {
        if (template.schemaVersion != Template.CURRENT_SCHEMA_VERSION) {
            throw SerializationException("Unsupported template schema version: ${template.schemaVersion}")
        }
        return json.encodeToString(Template.serializer(), template)
    }

    fun deserialize(rawJson: String): Template {
        val template = json.decodeFromString(Template.serializer(), rawJson)
        if (template.schemaVersion != Template.CURRENT_SCHEMA_VERSION) {
            throw SerializationException("Unsupported template schema version: ${template.schemaVersion}")
        }
        return template
    }
}
