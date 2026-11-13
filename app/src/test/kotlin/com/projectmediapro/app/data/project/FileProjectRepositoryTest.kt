package com.projectmediapro.app.data.project

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.TrackType
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class FileProjectRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun createThenGet_roundTripPreservesProject() = runTest(testDispatcher) {
        val dir = File(tempFolder.root, "projects")
        var nextId = 1
        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
            idProvider = { "uuid-${nextId++}" },
            clock = { 123456L },
        )

        val created = repo.createProject("  My First Project  ")
        val loaded = repo.getProject(created.id)

        assertEquals("uuid-1", created.id)
        assertEquals("My First Project", created.title)
        assertEquals(0L, created.durationMs)
        assertEquals(1, created.tracks.size)
        assertEquals(TrackType.VIDEO, created.tracks[0].type)
        assertTrue(created.tracks[0].clips.isEmpty())
        assertEquals(123456L, created.createdAt)
        assertEquals(123456L, created.updatedAt)
        assertEquals(created, loaded)
    }

    @Test
    fun listProjects_returnsNewestUpdatedAtFirst() = runTest(testDispatcher) {
        val dir = File(tempFolder.root, "projects")
        var currentTime = 1000L
        var nextId = 1
        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
            idProvider = { "id-${nextId++}" },
            clock = { currentTime },
        )

        val older = repo.createProject("Older")
        currentTime = 2000L
        val middle = repo.createProject("Middle")
        currentTime = 3000L
        val newest = repo.createProject("Newest")

        val list = repo.listProjects()

        assertEquals(listOf(newest.id, middle.id, older.id), list.map { it.id })
    }

    @Test
    fun listProjects_skipsCorruptFileAndLeavesItUntouched() = runTest(testDispatcher) {
        val dir = tempFolder.newFolder("projects")
        val corruptFile = File(dir, "corrupt-id.pmp.json")
        val corruptContent = "{ not valid json "
        corruptFile.writeText(corruptContent, Charsets.UTF_8)

        var nextId = 1
        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
            idProvider = { "valid-${nextId++}" },
            clock = { 5000L },
        )
        val valid = repo.createProject("Valid")

        val list = repo.listProjects()

        assertEquals(1, list.size)
        assertEquals(valid.id, list[0].id)
        assertTrue(corruptFile.exists())
        assertEquals(corruptContent, corruptFile.readText(Charsets.UTF_8))
    }

    @Test
    fun listProjects_skipsNewerSchemaFileAndLeavesItUntouched() = runTest(testDispatcher) {
        val dir = tempFolder.newFolder("projects")
        val futureFile = File(dir, "future-id.pmp.json")
        val futureJson = """
            {
                "id": "future-id",
                "title": "From The Future",
                "durationMs": 0,
                "tracks": [],
                "createdAt": 100,
                "updatedAt": 999999,
                "schemaVersion": 2
            }
        """.trimIndent()
        futureFile.writeText(futureJson, Charsets.UTF_8)

        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
        )

        val list = repo.listProjects()

        assertTrue(list.isEmpty())
        assertNull(repo.getProject("future-id"))
        assertTrue(futureFile.exists())
        assertEquals(futureJson, futureFile.readText(Charsets.UTF_8))
    }

    @Test
    fun deleteProject_removesFileAndProjectMediaFolderWhileKeepingOtherProjectsMedia() = runTest(testDispatcher) {
        val projectsDir = tempFolder.newFolder("projects")
        val mediaDir = tempFolder.newFolder("media")
        val mediaStorage = ProjectMediaStorage(mediaDir)
        var nextId = 1
        val repo = FileProjectRepository(
            directory = projectsDir,
            ioDispatcher = testDispatcher,
            idProvider = { "del-${nextId++}" },
            mediaStorage = mediaStorage,
        )

        val first = repo.createProject("First")
        val second = repo.createProject("Second")

        val firstClip = mediaStorage.resolve(mediaStorage.clipRelativePath(first.id, "c1", "mp4"))
        val secondClip = mediaStorage.resolve(mediaStorage.clipRelativePath(second.id, "c1", "mp4"))
        firstClip?.parentFile?.mkdirs()
        firstClip?.writeText("video-1")
        secondClip?.parentFile?.mkdirs()
        secondClip?.writeText("video-2")

        repo.deleteProject(first.id)

        assertFalse(File(projectsDir, "${first.id}.pmp.json").exists())
        assertFalse(File(mediaDir, first.id).exists())
        assertTrue(File(mediaDir, second.id).exists())
        assertTrue(secondClip?.isFile == true)
    }

    @Test
    fun deleteProject_succeedsWhenProjectMediaFolderDoesNotExist() = runTest(testDispatcher) {
        val projectsDir = tempFolder.newFolder("projects")
        val mediaDir = File(tempFolder.root, "non-existent-media")
        val mediaStorage = ProjectMediaStorage(mediaDir)
        var nextId = 1
        val repo = FileProjectRepository(
            directory = projectsDir,
            ioDispatcher = testDispatcher,
            idProvider = { "nomedia-${nextId++}" },
            mediaStorage = mediaStorage,
        )

        val created = repo.createProject("No Media Folder")
        repo.deleteProject(created.id)

        assertNull(repo.getProject(created.id))
    }

    @Test
    fun invalidIds_areRejectedAndNeverTouchDisk() = runTest(testDispatcher) {
        val dir = File(tempFolder.root, "untouched-dir")
        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
        )

        assertNull(repo.getProject("../escape"))
        assertNull(repo.getProject("bad/id"))
        assertNull(repo.getProject(""))
        assertFalse(dir.exists())

        var saveThrew = false
        try {
            repo.saveProject(Project(id = "../invalid", title = "Bad"))
        } catch (_: IOException) {
            saveThrew = true
        }
        assertTrue(saveThrew)
        assertFalse(dir.exists())

        var deleteThrew = false
        try {
            repo.deleteProject("bad..id")
        } catch (_: IOException) {
            deleteThrew = true
        }
        assertTrue(deleteThrew)
        assertFalse(dir.exists())
    }

    @Test
    fun saveProject_replacesExistingAndLeavesNoTempFileBehind() = runTest(testDispatcher) {
        val dir = tempFolder.newFolder("projects")
        var currentTime = 1000L
        var nextId = 1
        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
            idProvider = { "save-${nextId++}" },
            clock = { currentTime },
        )

        val created = repo.createProject("Initial Title")
        currentTime = 4500L
        repo.saveProject(created.copy(title = "Updated Title", durationMs = 8000L))

        val loaded = repo.getProject(created.id)
        assertNotNull(loaded)
        assertEquals("Updated Title", loaded?.title)
        assertEquals(8000L, loaded?.durationMs)
        assertEquals(4500L, loaded?.updatedAt)

        val remainingFiles = dir.listFiles()?.map { it.name }.orEmpty()
        assertEquals(listOf("${created.id}.pmp.json"), remainingFiles)
    }

    @Test
    fun saveProject_rejectsUnsupportedSchemaWithoutOverwritingExistingFile() = runTest(testDispatcher) {
        val dir = tempFolder.newFolder("projects")
        var nextId = 1
        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
            idProvider = { "schema-${nextId++}" },
            clock = { 1000L },
        )
        val created = repo.createProject("Supported")
        val file = File(dir, "${created.id}.pmp.json")
        val originalContent = file.readText(Charsets.UTF_8)

        var thrown: IOException? = null
        try {
            repo.saveProject(created.copy(title = "Future", schemaVersion = Project.CURRENT_SCHEMA_VERSION + 1))
        } catch (failure: IOException) {
            thrown = failure
        }

        assertNotNull(thrown)
        assertEquals(originalContent, file.readText(Charsets.UTF_8))
        assertEquals(created, repo.getProject(created.id))
    }

    @Test
    fun createProject_doesNotOverwriteWhenGeneratedIdAlreadyExists() = runTest(testDispatcher) {
        val dir = tempFolder.newFolder("projects")
        val generatedIds = ArrayDeque(listOf("duplicate-id", "track-1", "duplicate-id", "track-2"))
        val repo = FileProjectRepository(
            directory = dir,
            ioDispatcher = testDispatcher,
            idProvider = { generatedIds.removeFirst() },
            clock = { 1000L },
        )
        val original = repo.createProject("Original")

        var thrown: IOException? = null
        try {
            repo.createProject("Should not replace")
        } catch (failure: IOException) {
            thrown = failure
        }

        assertNotNull(thrown)
        assertEquals(original, repo.getProject(original.id))
        assertEquals(listOf(original.id), repo.listProjects().map { it.id })
    }

    @Test
    fun listProjects_wrapsSecurityExceptionAsIOException() = runTest(testDispatcher) {
        val realDirectory = tempFolder.newFolder("projects")
        val protectedDirectory = object : File(realDirectory.absolutePath) {
            override fun listFiles(): Array<File>? = throw SecurityException("access denied")
        }
        val repo = FileProjectRepository(
            directory = protectedDirectory,
            ioDispatcher = testDispatcher,
        )

        var thrown: IOException? = null
        try {
            repo.listProjects()
        } catch (failure: IOException) {
            thrown = failure
        }

        assertNotNull(thrown)
        val causeChain = generateSequence<Throwable>(thrown) { it.cause }
        assertTrue(causeChain.any { it is SecurityException })
    }

    @Test
    fun listProjects_reportsStorageProblemAsIOException() = runTest(testDispatcher) {
        val notADirectory = tempFolder.newFile("projects")
        val repo = FileProjectRepository(
            directory = notADirectory,
            ioDispatcher = testDispatcher,
        )

        var thrown: IOException? = null
        try {
            repo.listProjects()
        } catch (failure: IOException) {
            thrown = failure
        }

        assertNotNull(thrown)
        assertTrue(notADirectory.isFile)
    }
}
