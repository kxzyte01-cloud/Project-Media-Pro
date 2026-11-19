package com.projectmediapro.core.model

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateSerializerTest {

    @Test
    fun roundtrip_preservesSlotsRolesAndCanvasDefaults() {
        val template = Template(
            id = "tpl-1",
            name = "YouTube Short",
            slots = TemplateBuilder.createDefaultSlots("s-in", "s-v1", "s-out"),
            createdAt = 1_000L,
            updatedAt = 2_000L,
            canvasAspect = CanvasAspect.RATIO_9_16,
            canvasBackground = CanvasBackground.GREY,
        )

        val json = TemplateSerializer.serialize(template)
        val restored = TemplateSerializer.deserialize(json)
assertTrue(json.contains("\"schemaVersion\""))
        assertEquals(template.id, restored.id)
        assertEquals(template.name, restored.name)
        assertEquals(3, restored.slots.size)
        assertEquals(SlotRole.INTRO, restored.slots[0].role)
        assertEquals(SlotRole.MAIN_VIDEO, restored.slots[1].role)
        assertEquals(SlotRole.OUTRO, restored.slots[2].role)
        assertEquals(CanvasAspect.RATIO_9_16, restored.canvasAspect)
        assertEquals(CanvasBackground.GREY, restored.canvasBackground)
    }

    @Test(expected = SerializationException::class)
    fun deserialize_unsupportedSchemaVersion_throws() {
        TemplateSerializer.deserialize(
            """{"id":"tpl-2","name":"Future","schemaVersion":99}""",
        )
    }
}
