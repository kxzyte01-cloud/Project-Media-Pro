package com.projectmediapro.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Result of decoding or encoding a [Project] JSON payload.
 */
sealed interface ProjectSerializationResult {
    data class Success(val project: Project) : ProjectSerializationResult

    data class UnsupportedNewerSchema(
        val foundVersion: Int,
        val maxSupportedVersion: Int,
    ) : ProjectSerializationResult

    data class InvalidSchemaVersion(val foundVersion: Int) : ProjectSerializationResult

    data class MalformedJson(val message: String) : ProjectSerializationResult
}

/**
 * Pure Kotlin JSON serializer for [Project] instances.
 * Ignores unknown fields for forward/backward resilience and explicitly checks `schemaVersion`.
 */
object ProjectSerializer {
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(project: Project): String {
        return jsonFormat.encodeToString(Project.serializer(), project)
    }

    fun decode(rawJson: String): ProjectSerializationResult {
        return try {
            val rootElement = jsonFormat.parseToJsonElement(rawJson)
            val versionElement = rootElement.jsonObject["schemaVersion"]?.jsonPrimitive
            val version = versionElement?.intOrNull ?: Project.CURRENT_SCHEMA_VERSION

            when {
                version <= 0 -> ProjectSerializationResult.InvalidSchemaVersion(foundVersion = version)
                version > Project.CURRENT_SCHEMA_VERSION -> {
                    ProjectSerializationResult.UnsupportedNewerSchema(
                        foundVersion = version,
                        maxSupportedVersion = Project.CURRENT_SCHEMA_VERSION,
                    )
                }
                else -> {
                    val project = jsonFormat.decodeFromJsonElement(Project.serializer(), rootElement)
                    ProjectSerializationResult.Success(project)
                }
            }
        } catch (exception: SerializationException) {
            ProjectSerializationResult.MalformedJson(exception.message ?: "Serialization error")
        } catch (exception: IllegalArgumentException) {
            ProjectSerializationResult.MalformedJson(exception.message ?: "Invalid JSON structure")
        }
    }
}

