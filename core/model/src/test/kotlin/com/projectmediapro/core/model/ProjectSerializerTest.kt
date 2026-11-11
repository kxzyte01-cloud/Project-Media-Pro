package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
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