package com.projectmediapro.app.editor

import com.projectmediapro.app.data.media.ImportedVideo
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mediaStorage: ProjectMediaStorage

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mediaStorage = ProjectMediaStorage(File(tempFolder.root, "media"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_withExistingNotFoundOrRepositoryError_emitsExpectedState() = runTest(testDispatcher) {
        val existing = Project(id = "valid-id-1", title = "Demo Project")
        val repo = FakeEditorProjectRepository(listOf(existing))
        val importer = FakeVideoImporter(mediaStorage)

        val loadedVm = EditorViewModel("valid-id-1", repo, importer, mediaStorage)
        assertEquals(EditorUiState.Loaded(existing), loadedVm.uiState.value)

        val missingVm = EditorViewModel("missing-id", repo, importer, mediaStorage)
        assertEquals(EditorUiState.NotFound, missingVm.uiState.value)

        val failingRepo = FakeEditorProjectRepository(emptyList(), shouldFailLoad = true)
        val failingVm = EditorViewModel("valid-id-1", failingRepo, importer, mediaStorage)
        assertEquals(EditorUiState.Error, failingVm.uiState.value)
    }

    @Test
    fun videoPicked_nullIsIgnored_andValidImportAppendsClipAndSaves() = runTest(testDispatcher) {
        val initial = Project(
            id = "proj-1",
            title = "Demo",
            tracks = listOf(Track(id = "track-1", type = TrackType.VIDEO)),
        )
        val repo = FakeEditorProjectRepository(listOf(initial))
        val importer = FakeVideoImporter(mediaStorage, nextDurationMs = 4_500L)
        var nextId = 1
        val vm = EditorViewModel("proj-1", repo, importer, mediaStorage) { "gen-${nextId++}" }

        vm.onEvent(EditorEvent.VideoPicked(null))
        assertEquals(0, importer.callCount)

        vm.onEvent(EditorEvent.VideoPicked("content://media/picker/1"))
        val state = vm.uiState.value as EditorUiState.Loaded
        assertFalse(state.isImporting)
        assertNull(state.error)
        assertEquals(4_500L, state.project.durationMs)
        val clip = state.project.tracks[0].clips.single()
        assertEquals("gen-1", clip.id)
        assertEquals(0L, clip.startTimeMs)
        assertEquals(4_500L, clip.durationMs)
        assertEquals(0L, clip.trimStartMs)
        assertEquals(4_500L, clip.trimEndMs)
        assertEquals(1, state.previewPaths.size)
        assertEquals(state.project, repo.getProject("proj-1"))
    }

    @Test
    fun videoPicked_mapsEachImportFailureReasonToMatchingEditorError() = runTest(testDispatcher) {
        val initial = Project(id = "proj-1", title = "Demo")
        val repo = FakeEditorProjectRepository(listOf(initial))
        val importer = FakeVideoImporter(mediaStorage)
        val vm = EditorViewModel("proj-1", repo, importer, mediaStorage)

        val cases = listOf(
            Reason.UNREADABLE to EditorError.IMPORT_FAILED,
            Reason.FAILED to EditorError.IMPORT_FAILED,
            Reason.NOT_A_VIDEO to EditorError.IMPORT_NOT_A_VIDEO,
            Reason.NO_SPACE to EditorError.IMPORT_NO_SPACE,
        )
        for ((reason, expectedError) in cases) {
            importer.failureReason = reason
            vm.onEvent(EditorEvent.VideoPicked("content://media/picker/video"))
            val loaded = vm.uiState.value as EditorUiState.Loaded
            assertFalse(loaded.isImporting)
            assertEquals(expectedError, loaded.error)
        }
    }

    @Test
    fun videoPicked_whenSaveFails_deletesCopiedFileAndRestoresProject() = runTest(testDispatcher) {
        val initial = Project(
            id = "proj-1",
            title = "Demo",
            tracks = listOf(Track(id = "t-1", type = TrackType.VIDEO)),
        )
        val repo = FakeEditorProjectRepository(listOf(initial), shouldFailSave = true)
        val importer = FakeVideoImporter(mediaStorage, nextDurationMs = 3_000L)
        val vm = EditorViewModel("proj-1", repo, importer, mediaStorage)

        vm.onEvent(EditorEvent.VideoPicked("content://media/picker/1"))

        val state = vm.uiState.value as EditorUiState.Loaded
        assertEquals(initial, state.project)
        assertEquals(EditorError.SAVE_FAILED, state.error)
        assertFalse(state.isImporting)
        assertFalse(mediaStorage.resolve(importer.lastRelativePath)?.exists() == true)
    }

    @Test
    fun videoPicked_whileImportInFlight_ignoresSecondPick() = runTest(testDispatcher) {
        val initial = Project(id = "proj-1", title = "Demo")
        val repo = FakeEditorProjectRepository(listOf(initial))
        val gate = CompletableDeferred<ImportedVideo>()
        val importer = FakeVideoImporter(mediaStorage, deferredResult = gate)
        val vm = EditorViewModel("proj-1", repo, importer, mediaStorage)

        vm.onEvent(EditorEvent.VideoPicked("content://media/picker/first"))
        assertTrue((vm.uiState.value as EditorUiState.Loaded).isImporting)

        vm.onEvent(EditorEvent.VideoPicked("content://media/picker/second"))
        assertEquals(1, importer.callCount)

        val relPath = mediaStorage.clipRelativePath("proj-1", "clip-1", "mp4")
        mediaStorage.resolve(relPath)?.apply {
            parentFile?.mkdirs()
            writeText("data")
        }
        gate.complete(ImportedVideo(relPath, 2_000L))

        val state = vm.uiState.value as EditorUiState.Loaded
        assertFalse(state.isImporting)
        assertEquals(1, state.project.tracks[0].clips.size)
    }

    @Test
    fun removeFlow_requiresConfirmation_deletesFileOnlyWhenUnreferenced_andRestoresOnSaveFailure() =
        runTest(testDispatcher) {
            val sharedRel = mediaStorage.clipRelativePath("proj-1", "shared", "mp4")
            val uniqueRel = mediaStorage.clipRelativePath("proj-1", "unique", "mp4")
            val sharedFile = mediaStorage.resolve(sharedRel)?.apply { parentFile?.mkdirs(); writeText("s") }
            val uniqueFile = mediaStorage.resolve(uniqueRel)?.apply { parentFile?.mkdirs(); writeText("u") }

            val c1 = Clip(id = "c1", mediaUri = sharedRel, startTimeMs = 0L, durationMs = 2_000L)
            val c2 = Clip(id = "c2", mediaUri = sharedRel, startTimeMs = 2_000L, durationMs = 2_000L)
            val c3 = Clip(id = "c3", mediaUri = uniqueRel, startTimeMs = 4_000L, durationMs = 3_000L)
            val initial = Project(
                id = "proj-1",
                title = "Demo",
                durationMs = 7_000L,
                tracks = listOf(Track(id = "t-1", type = TrackType.VIDEO, clips = listOf(c1, c2, c3))),
            )
            val repo = FakeEditorProjectRepository(listOf(initial))
            val vm = EditorViewModel("proj-1", repo, FakeVideoImporter(mediaStorage), mediaStorage)

            vm.onEvent(EditorEvent.RequestRemoveClip("c1"))
            assertEquals("c1", (vm.uiState.value as EditorUiState.Loaded).pendingRemoveClipId)
            vm.onEvent(EditorEvent.CancelRemoveClip)
            assertNull((vm.uiState.value as EditorUiState.Loaded).pendingRemoveClipId)

            vm.onEvent(EditorEvent.RequestRemoveClip("c1"))
            vm.onEvent(EditorEvent.ConfirmRemoveClip)
            assertTrue(sharedFile?.exists() == true)
            assertEquals(listOf("c2", "c3"), (vm.uiState.value as EditorUiState.Loaded).project.tracks[0].clips.map { it.id })

            repo.shouldFailSave = true
            vm.onEvent(EditorEvent.RequestRemoveClip("c3"))
            vm.onEvent(EditorEvent.ConfirmRemoveClip)
            val failedState = vm.uiState.value as EditorUiState.Loaded
            assertEquals(EditorError.SAVE_FAILED, failedState.error)
            assertEquals(listOf("c2", "c3"), failedState.project.tracks[0].clips.map { it.id })
            assertTrue(uniqueFile?.exists() == true)

            repo.shouldFailSave = false
            vm.onEvent(EditorEvent.RequestRemoveClip("c3"))
            vm.onEvent(EditorEvent.ConfirmRemoveClip)
            assertFalse(uniqueFile?.exists() == true)
            assertEquals(listOf("c2"), (vm.uiState.value as EditorUiState.Loaded).project.tracks[0].clips.map { it.id })
        }
}

private class FakeVideoImporter(
    private val storage: ProjectMediaStorage,
    private val nextDurationMs: Long = 5_000L,
    var failureReason: Reason? = null,
    private val deferredResult: CompletableDeferred<ImportedVideo>? = null,
) : VideoImporter {
    var callCount = 0
        private set
    var lastRelativePath: String = ""
        private set

    override suspend fun importVideo(projectId: String, sourceUri: String): ImportedVideo {
        callCount++
        failureReason?.let { throw VideoImportException(it) }
        deferredResult?.let { return it.await() }
        val rel = storage.clipRelativePath(projectId, "imported-$callCount", "mp4")
        lastRelativePath = rel
        storage.resolve(rel)?.apply { parentFile?.mkdirs(); writeText("fake-video") }
        return ImportedVideo(relativePath = rel, durationMs = nextDurationMs)
    }
}

private class FakeEditorProjectRepository(
    initialProjects: List<Project>,
    private val shouldFailLoad: Boolean = false,
    var shouldFailSave: Boolean = false,
) : ProjectRepository {
    private val map = initialProjects.associateBy { it.id }.toMutableMap()
    override suspend fun listProjects(): List<Project> = map.values.toList()
    override suspend fun getProject(id: String): Project? =
        if (shouldFailLoad) throw IOException("Read failed") else map[id]
    override suspend fun createProject(title: String): Project =
        Project(id = "new-id", title = title).also { map[it.id] = it }
    override suspend fun saveProject(project: Project) {
        if (shouldFailSave) throw IOException("Write failed")
        map[project.id] = project
    }
    override suspend fun deleteProject(id: String) {
        map.remove(id)
    }
}
