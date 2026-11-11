package com.projectmediapro.core.model

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSerializerTest {
    @Test
    fun testSerializationRoundtrip() {
        val project = Project(
            id = "test-1",
            title = "My Vacation",
            durationMs = 15000L,
            tracks = listOf(
                Track(
                    id = "track-1",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "clip-1",
                            mediaUri = "content://media/external/video/media/123",
                            startTimeMs = 0L,
                            durationMs = 5000L
                        )
                    )
                )
            )
        )

        val json = ProjectSerializer.serialize(project)
        val deserialized = ProjectSerializer.deserialize(json)

        assertEquals(project.id, deserialized.id)
        assertEquals(project.title, deserialized.title)
        assertEquals(project.tracks.size, deserialized.tracks.size)
        assertEquals(project.tracks[0].clips[0].mediaUri, deserialized.tracks[0].clips[0].mediaUri)
        assertEquals(Project.CURRENT_SCHEMA_VERSION, deserialized.schemaVersion)
    }

    @Test
    fun serialize_includesDefaultSchemaVersionInJson() {
        val project = Project(
            id = "test-schema",
            title = "Schema Check",
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        val json = ProjectSerializer.serialize(project)

        assertTrue(json.contains("\"schemaVersion\""))
        assertEquals(1, ProjectSerializer.deserialize(json).schemaVersion)
    }

    @Test
    fun deserialize_ignoresUnknownJsonFields() {
        val rawJson = """
            {
                "id": "proj-unknown",
                "title": "Future Project",
                "durationMs": 0,
                "tracks": [],
                "createdAt": 100,
                "updatedAt": 200,
                "schemaVersion": 1,
                "futureField": "ignored_value",
                "extraObject": { "nested": 42 }
            }
        """.trimIndent()

        val project = ProjectSerializer.deserialize(rawJson)

        assertEquals("proj-unknown", project.id)
        assertEquals("Future Project", project.title)
        assertEquals(1, project.schemaVersion)
    }

    @Test(expected = SerializationException::class)
    fun deserialize_malformedJson_throwsSerializationException() {
        ProjectSerializer.deserialize("{ \"id\": \"broken\", \"title\": ")
    }

    @Test(expected = SerializationException::class)
    fun deserialize_newerSchema_throwsSerializationException() {
        ProjectSerializer.deserialize(
            """{"id":"future","title":"Future","schemaVersion":2}""",
        )
    }

    @Test
    fun testUndoRedoManager() {
        val manager = UndoRedoManager("StateA")
        assertEquals("StateA", manager.currentState)
        assertEquals(false, manager.canUndo)

        manager.pushState("StateB")
        assertEquals("StateB", manager.currentState)
        assertEquals(true, manager.canUndo)

        manager.undo()
        assertEquals("StateA", manager.currentState)
        assertEquals(true, manager.canRedo)

        manager.redo()
        assertEquals("StateB", manager.currentState)
    }
}
