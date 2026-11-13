package com.projectmediapro.app.data.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectMediaStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun clipRelativePath_validatesIdsAndNormalizesExtension() {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media"))

        assertEquals(
            "proj-1/clip-1.mp4",
            storage.clipRelativePath("proj-1", "clip-1", "mp4"),
        )
        assertEquals(
            "proj-1/clip-1.webm",
            storage.clipRelativePath("proj-1", "clip-1", "webm"),
        )
        assertEquals(
            "proj-1/clip-1.mp4",
            storage.clipRelativePath("proj-1", "clip-1", "MP4"),
        )
        assertEquals(
            "proj-1/clip-1.mp4",
            storage.clipRelativePath("proj-1", "clip-1", "bad_ext"),
        )
        assertEquals(
            "proj-1/clip-1.mp4",
            storage.clipRelativePath("proj-1", "clip-1", ""),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun clipRelativePath_rejectsInvalidProjectId() {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media"))
        storage.clipRelativePath("../escape", "clip-1", "mp4")
    }

    @Test(expected = IllegalArgumentException::class)
    fun clipRelativePath_rejectsInvalidClipId() {
        val storage = ProjectMediaStorage(File(tempFolder.root, "media"))
        storage.clipRelativePath("proj-1", "bad/clip", "mp4")
    }

    @Test
    fun resolve_blocksTraversalAndAllowsValidRelativePaths() {
        val root = File(tempFolder.root, "media")
        val storage = ProjectMediaStorage(root)

        assertNull(storage.resolve(""))
        assertNull(storage.resolve("/etc/passwd"))
        assertNull(storage.resolve("../secret.mp4"))
        assertNull(storage.resolve("proj-1/../secret.mp4"))
        assertNull(storage.resolve("proj-1\\clip-1.mp4"))

        val resolved = storage.resolve("proj-1/clip-1.mp4")
        assertNotNull(resolved)
        assertTrue(resolved?.path?.startsWith(root.canonicalFile.path) == true)
    }

    @Test
    fun deleteClipFile_andDeleteProjectMedia_removeTargetsSafely() {
        val root = tempFolder.newFolder("media")
        val storage = ProjectMediaStorage(root)

        val rel1 = storage.clipRelativePath("proj-1", "clip-1", "mp4")
        val rel2 = storage.clipRelativePath("proj-1", "clip-2", "mp4")
        val otherRel = storage.clipRelativePath("proj-2", "clip-1", "mp4")

        val file1 = storage.resolve(rel1)
        val file2 = storage.resolve(rel2)
        val otherFile = storage.resolve(otherRel)
        assertNotNull(file1)
        assertNotNull(file2)
        assertNotNull(otherFile)

        file1?.parentFile?.mkdirs()
        file1?.writeText("one")
        file2?.writeText("two")
        otherFile?.parentFile?.mkdirs()
        otherFile?.writeText("other")

        storage.deleteClipFile(rel1)
        assertFalse(file1?.exists() == true)
        assertTrue(file2?.exists() == true)

        storage.deleteClipFile("proj-1/non-existent.mp4")
        storage.deleteProjectMedia("proj-1")
        assertFalse(File(root, "proj-1").exists())
        assertTrue(otherFile?.exists() == true)

        storage.deleteProjectMedia("non-existent-project")
    }

    @Test
    fun availableBytes_usesNearestExistingAncestorWhenRootDoesNotExist() {
        val nonExistentRoot = File(tempFolder.root, "deep/nested/media")
        val storage = ProjectMediaStorage(nonExistentRoot)

        assertTrue(storage.availableBytes() > 0L)
    }
}
