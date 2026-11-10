package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSerializerTest {

    // Proves that a Project containing a Template and Tracks survives a JSON encode/decode round trip without data loss.
    @Test
    fun roundTrip_projectWithTemplate_preservesAllFields() {
        val original = Project(
            schemaVersion = Project.CURRENT_SCHEMA_VERSION,
            id = "proj-001",
            title = "Channel Episode 1",
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_050_000L,
            tracks = listOf(
                Track(
                    id = "track-v1",
                    kind = TrackKind.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "clip-1",
                            sourceUri = "file:///storage/emulated/0/Movies/intro.mp4",
                            startTimeMs = 0L,
                            durationMs = 5_000L,
                            linkedSlotId = "slot-intro",
                        ),
                    ),
                ),
            ),
            template = Template(
                id = "tpl-standard",
                name = "Standard Episode Template",
                slots = listOf(
                    Slot(id = "slot-intro", type = SlotType.INTRO, label = "Intro Clip", orderIndex = 0),
                    Slot(id = "slot-pip", type = SlotType.PICTURE_IN_PICTURE, label = "Reaction PiP", orderIndex = 1),
                    Slot(id = "slot-audio-1", type = SlotType.ORDERED_AUDIO, label = "Voiceover 1", orderIndex = 2),
                    Slot(id = "slot-outro", type = SlotType.OUTRO, label = "Outro Clip", orderIndex = 3),
                ),
            ),
        )

        val encoded = ProjectSerializer.encode(original)
        val decodedResult = ProjectSerializer.decode(encoded)

        assertTrue(decodedResult is ProjectSerializationResult.Success)
        val decoded = (decodedResult as ProjectSerializationResult.Success).project
        assertEquals(original, decoded)
    }

    // Proves that unrecognized JSON properties in a serialized project are safely ignored during decoding.
    @Test
    fun decode_withUnknownJsonFields_ignoresExtraFieldsAndSucceeds() {
        val jsonWithUnknowns = """
            {
              "schemaVersion": 1,
              "id": "proj-002",
              "title": "Forward Compatible Project",
              "createdAtEpochMs": 100,
              "updatedAtEpochMs": 200,
              "futureExperimentalFlag": true,
              "unexpectedObject": { "nested": "value" },
              "tracks": []
            }
        """.trimIndent()

        val result = ProjectSerializer.decode(jsonWithUnknowns)

        assertTrue(result is ProjectSerializationResult.Success)
        val project = (result as ProjectSerializationResult.Success).project
        assertEquals("proj-002", project.id)
        assertEquals("Forward Compatible Project", project.title)
    }

    // Proves that malformed or truncated JSON returns MalformedJson instead of throwing an uncaught exception.
    @Test
    fun decode_withInvalidJson_returnsMalformedJsonResultWithoutCrashing() {
        val corruptedJson = """{"schemaVersion": 1, "id": "broken", "tracks": ["""

        val result = ProjectSerializer.decode(corruptedJson)

        assertTrue(result is ProjectSerializationResult.MalformedJson)
    }

    // Proves that valid non-object JSON payloads ("[]" and "123") return MalformedJson without throwing an uncaught exception.
    @Test
    fun decode_withNonObjectJson_returnsMalformedJsonResult() {
        val arrayResult = ProjectSerializer.decode("[]")
        val primitiveResult = ProjectSerializer.decode("123")

        assertTrue(arrayResult is ProjectSerializationResult.MalformedJson)
        assertTrue(primitiveResult is ProjectSerializationResult.MalformedJson)
    }

    // Proves that schemaVersion 0 and -1 are rejected as InvalidSchemaVersion.
    @Test
    fun decode_withZeroOrNegativeSchemaVersion_returnsInvalidSchemaVersion() {
        val zeroVersionJson = """
            {
              "schemaVersion": 0,
              "id": "proj-zero",
              "title": "Zero Version",
              "createdAtEpochMs": 100,
              "updatedAtEpochMs": 200
            }
        """.trimIndent()
        val negativeVersionJson = """
            {
              "schemaVersion": -1,
              "id": "proj-neg",
              "title": "Negative Version",
              "createdAtEpochMs": 100,
              "updatedAtEpochMs": 200
            }
        """.trimIndent()

        assertEquals(
            ProjectSerializationResult.InvalidSchemaVersion(foundVersion = 0),
            ProjectSerializer.decode(zeroVersionJson),
        )
        assertEquals(
            ProjectSerializationResult.InvalidSchemaVersion(foundVersion = -1),
            ProjectSerializer.decode(negativeVersionJson),
        )
    }

    // Proves that a JSON object omitting schemaVersion defaults to schemaVersion 1 and succeeds.
    @Test
    fun decode_withMissingSchemaVersion_defaultsToVersionOneAndSucceeds() {
        val missingVersionJson = """
            {
              "id": "proj-default-ver",
              "title": "Default Version Project",
              "createdAtEpochMs": 500,
              "updatedAtEpochMs": 600
            }
        """.trimIndent()

        val result = ProjectSerializer.decode(missingVersionJson)

        assertTrue(result is ProjectSerializationResult.Success)
        val project = (result as ProjectSerializationResult.Success).project
        assertEquals(1, project.schemaVersion)
        assertEquals("proj-default-ver", project.id)
    }

    // Proves that a project saved with a newer schemaVersion is clearly reported as UnsupportedNewerSchema.
    @Test
    fun decode_withNewerSchemaVersion_returnsUnsupportedNewerSchemaResult() {
        val futureJson = """
            {
              "schemaVersion": 99,
              "id": "proj-future",
              "title": "Future Project",
              "createdAtEpochMs": 1000,
              "updatedAtEpochMs": 2000
            }
        """.trimIndent()

        val result = ProjectSerializer.decode(futureJson)

        assertEquals(
            ProjectSerializationResult.UnsupportedNewerSchema(
                foundVersion = 99,
                maxSupportedVersion = Project.CURRENT_SCHEMA_VERSION,
            ),
            result,
        )
    }
}

