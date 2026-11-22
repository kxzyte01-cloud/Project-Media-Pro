package com.projectmediapro.app.data.template

import com.projectmediapro.app.data.media.FontHeaderValidator
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.TemplateSlot
import com.projectmediapro.core.model.TemplateSlotRole
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Step21CustomFontAndTemplatePackTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `fontHeaderValidator recognizes ttf and otf headers and rejects arbitrary bytes`() {
        assertTrue(FontHeaderValidator.isValidFontHeader(byteArrayOf(0x00, 0x01, 0x00, 0x00)))
        assertTrue(FontHeaderValidator.isValidFontHeader("OTTO".toByteArray()))
        assertTrue(FontHeaderValidator.isValidFontHeader("true".toByteArray()))
        assertTrue(FontHeaderValidator.isValidFontHeader("ttcf".toByteArray()))
        assertFalse(FontHeaderValidator.isValidFontHeader("PK\u0003\u0004".toByteArray()))
        assertFalse(FontHeaderValidator.isValidFontHeader(byteArrayOf(1, 2)))
    }

    @Test
    fun `templatePackCodec round-trips template metadata and bundled media cleanly`() {
        val storage = ProjectMediaStorage(tempFolder.newFolder("template_media"))
        val relMedia = storage.clipRelativePath("tpl-1", "audio-1", "m4a")
        val mediaFile = requireNotNull(storage.resolve(relMedia))
        mediaFile.parentFile?.mkdirs()
        mediaFile.writeBytes("sample-audio-bytes".toByteArray())

        val original = Template(
            id = "tpl-1",
            name = "Viral Beat Edit",
            description = "9:16 lyric template",
            canvasAspect = CanvasAspect.RATIO_9_16,
            slots = listOf(
                TemplateSlot(
                    id = "s1",
                    label = "Intro Clip",
                    role = TemplateSlotRole.MAIN_VIDEO,
                    targetDurationMs = 2_500L,
                ),
                TemplateSlot(
                    id = "s2",
                    label = "Beat Hook",
                    role = TemplateSlotRole.FIXED_AUDIO,
                    targetDurationMs = 15_000L,
                    fixedMediaUri = relMedia,
                ),
            ),
        )

        val zipOut = ByteArrayOutputStream()
        TemplatePackCodec.exportTemplatePack(original, storage, zipOut)

        var counter = 0
        val imported = TemplatePackCodec.importTemplatePack(
            inputStream = ByteArrayInputStream(zipOut.toByteArray()),
            mediaStorage = storage,
            idProvider = { "imp-${++counter}" },
        )

        assertEquals("Viral Beat Edit", imported.name)
        assertEquals(CanvasAspect.RATIO_9_16, imported.canvasAspect)
        assertEquals(2, imported.slots.size)
        val importedAudioUri = requireNotNull(imported.slots[1].fixedMediaUri)
        val importedFile = requireNotNull(storage.resolve(importedAudioUri))
        assertTrue(importedFile.isFile)
        assertEquals("sample-audio-bytes", importedFile.readText())
    }
}
