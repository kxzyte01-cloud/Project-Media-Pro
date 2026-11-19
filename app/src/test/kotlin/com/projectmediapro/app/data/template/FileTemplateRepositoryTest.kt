package com.projectmediapro.app.data.template

import com.projectmediapro.app.data.media.ProjectMediaStorage
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTemplateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun createListSaveAndDelete_persistsAtomicallyAndCleansUpMedia() = runTest {
        var nextId = 0
        var now = 1_000L
        val mediaStorage = ProjectMediaStorage(File(tempFolder.root, "template_media"))
        val repo = FileTemplateRepository(
            directory = File(tempFolder.root, "templates"),
            idProvider = { "id-${++nextId}" },
            clock = { now },
            mediaStorage = mediaStorage,
        )

        val created = repo.createTemplate("  My Intro Template  ")
        assertEquals("My Intro Template", created.name)
        assertEquals(3, created.slots.size)

        val loaded = repo.getTemplate(created.id)
        assertNotNull(loaded)
        assertEquals(created.id, loaded?.id)

        val mediaRel = mediaStorage.clipRelativePath(created.id, "intro", "mp4")
        val mediaFile = mediaStorage.resolve(mediaRel)
        mediaFile?.parentFile?.mkdirs()
        mediaFile?.writeText("bytes")
        assertTrue(mediaFile?.isFile == true)

        now = 2_000L
        repo.saveTemplate(created.copy(name = "Updated Template"))
        val list = repo.listTemplates()
        assertEquals(1, list.size)
        assertEquals("Updated Template", list[0].name)

        repo.deleteTemplate(created.id)
        assertNull(repo.getTemplate(created.id))
        assertFalse(mediaFile?.exists() == true)
    }
}
