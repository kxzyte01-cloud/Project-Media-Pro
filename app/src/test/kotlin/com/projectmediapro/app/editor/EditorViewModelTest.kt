package com.projectmediapro.app.editor

import com.projectmediapro.app.data.export.ExportPlan
import com.projectmediapro.app.data.export.ExportSaver
import com.projectmediapro.app.data.export.SavedExport
import com.projectmediapro.app.data.export.VideoExportException
import com.projectmediapro.app.data.export.VideoExporter
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

        val failingVm = EditorViewModel(
            projectId = "valid-id-1",
            repository = FakeEditorProjectRepository(emptyList(), shouldFailLoad = true),
            videoImporter = importer,
            mediaStorage = mediaStorage,
        )
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
        val vm = EditorViewModel(
            projectId = "proj-1",
            repository = repo,
            videoImporter = importer,
            mediaStorage = mediaStorage,
            idProvider = { "gen-${nextId++}" },
        )

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
        val lastCopiedFile = mediaStorage.resolve(importer.lastRelativePath)
        assertFalse(lastCopiedFile?.exists() == true)
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
            val sharedFile = mediaStorage.resolve(sharedRel)
            val uniqueFile = mediaStorage.resolve(uniqueRel)
            sharedFile?.parentFile?.mkdirs()
            sharedFile?.writeText("shared-bytes")
            uniqueFile?.writeText("unique-bytes")

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

    // Proves that an export runs to completion, saves to the gallery or a chosen file, and removes the temp file.
    @Test
    fun export_success_savesToGalleryAndUri_andDeletesTempFile() = runTest(testDispatcher) {
        val project = projectWithOneClip(writeExportClip("proj-1", "c1"))
        val exportDir = File(tempFolder.root, "exports")
        val exporter = FakeVideoExporter(progressSteps = listOf(45, 100))
        val saver = FakeExportSaver()
        val vm = EditorViewModel(
            "proj-1",
            FakeEditorProjectRepository(listOf(project)),
            FakeVideoImporter(mediaStorage),
            mediaStorage,
            videoExporter = exporter,
            exportSaver = saver,
            tempDirectory = exportDir,
        )

        vm.onEvent(EditorEvent.StartExport())
        assertEquals(ExportUiState.Done(savedToGallery = true), (vm.uiState.value as EditorUiState.Loaded).export)
        assertEquals(1, saver.gallerySaves)
        assertFalse(File(exportDir, "proj-1.mp4").exists())

        vm.onEvent(EditorEvent.DismissExportDone)
        assertEquals(ExportUiState.Idle, (vm.uiState.value as EditorUiState.Loaded).export)

        vm.onEvent(EditorEvent.StartExport(destinationUri = "content://saf/video.mp4"))
        assertEquals(ExportUiState.Done(savedToGallery = false), (vm.uiState.value as EditorUiState.Loaded).export)
        assertEquals("content://saf/video.mp4", saver.lastSavedUri)
    }

    // Proves that while an export runs, imports, removals and a second export are ignored, and Cancel cleans up.
    @Test
    fun export_whileRunning_blocksOtherActions_andCancelReturnsToIdleWithoutTempFile() = runTest(testDispatcher) {
        val project = projectWithOneClip(writeExportClip("proj-1", "c1"))
        val exportDir = File(tempFolder.root, "exports")
        val exporter = FakeVideoExporter(gate = CompletableDeferred())
        val importer = FakeVideoImporter(mediaStorage)
        val vm = EditorViewModel(
            "proj-1",
            FakeEditorProjectRepository(listOf(project)),
            importer,
            mediaStorage,
            videoExporter = exporter,
            exportSaver = FakeExportSaver(),
            tempDirectory = exportDir,
        )

        vm.onEvent(EditorEvent.StartExport())
        assertEquals(ExportUiState.Running(25), (vm.uiState.value as EditorUiState.Loaded).export)

        vm.onEvent(EditorEvent.VideoPicked("content://media/picker/2"))
        vm.onEvent(EditorEvent.RequestRemoveClip("c1"))
        vm.onEvent(EditorEvent.StartExport())
        assertEquals(0, importer.callCount)
        assertNull((vm.uiState.value as EditorUiState.Loaded).pendingRemoveClipId)
        assertEquals(1, exporter.callCount)

        vm.onEvent(EditorEvent.CancelExport)
        val cancelled = vm.uiState.value as EditorUiState.Loaded
        assertEquals(ExportUiState.Idle, cancelled.export)
        assertNull(cancelled.error)
        assertFalse(File(exportDir, "proj-1.mp4").exists())
    }

    // Proves that every export failure (space, format, other, save, nothing to export) shows the right error.
    @Test
    fun export_mapsEveryFailureToItsError() = runTest(testDispatcher) {
        val rel = writeExportClip("proj-1", "c1")
        val project = projectWithOneClip(rel)
        var freeBytes = 1024L
        val exporter = FakeVideoExporter()
        val saver = FakeExportSaver()
        val vm = EditorViewModel(
            "proj-1",
            FakeEditorProjectRepository(listOf(project)),
            FakeVideoImporter(mediaStorage),
            mediaStorage,
            videoExporter = exporter,
            exportSaver = saver,
            cacheSpaceProvider = { freeBytes },
            tempDirectory = File(tempFolder.root, "exports"),
        )

        vm.onEvent(EditorEvent.StartExport())
        assertEquals(EditorError.EXPORT_NO_SPACE, (vm.uiState.value as EditorUiState.Loaded).error)
        assertEquals(0, exporter.callCount)

        freeBytes = Long.MAX_VALUE
        for ((reason, expected) in listOf(
            VideoExportException.Reason.UNSUPPORTED to EditorError.EXPORT_UNSUPPORTED,
            VideoExportException.Reason.NO_SPACE to EditorError.EXPORT_NO_SPACE,
            VideoExportException.Reason.FAILED to EditorError.EXPORT_FAILED,
        )) {
            exporter.failureReason = reason
            vm.onEvent(EditorEvent.StartExport())
            assertEquals(expected, (vm.uiState.value as EditorUiState.Loaded).error)
        }

        exporter.failureReason = null
        saver.shouldFail = true
        vm.onEvent(EditorEvent.StartExport())
        assertEquals(EditorError.EXPORT_SAVE_FAILED, (vm.uiState.value as EditorUiState.Loaded).error)

        mediaStorage.deleteClipFile(rel)
        vm.onEvent(EditorEvent.StartExport())
        assertEquals(EditorError.EXPORT_NOTHING, (vm.uiState.value as EditorUiState.Loaded).error)
    }

    // Proves that exporting a project without clips does nothing.
    @Test
    fun export_withoutClips_isIgnored() = runTest(testDispatcher) {
        val empty = Project(id = "proj-1", title = "Empty")
        val exporter = FakeVideoExporter()
        val vm = EditorViewModel(
            "proj-1",
            FakeEditorProjectRepository(listOf(empty)),
            FakeVideoImporter(mediaStorage),
            mediaStorage,
            videoExporter = exporter,
            exportSaver = FakeExportSaver(),
            tempDirectory = File(tempFolder.root, "exports"),
        )

        vm.onEvent(EditorEvent.StartExport())

        assertEquals(0, exporter.callCount)
        assertEquals(ExportUiState.Idle, (vm.uiState.value as EditorUiState.Loaded).export)
    }

    private fun writeExportClip(projectId: String, clipId: String): String {
        val relativePath = mediaStorage.clipRelativePath(projectId, clipId, "mp4")
        val file = mediaStorage.resolve(relativePath)
        file?.parentFile?.mkdirs()
        file?.writeText("video")
        return relativePath
    }

    private fun projectWithOneClip(relativePath: String): Project {
        val clip = Clip(
            id = "c1",
            mediaUri = relativePath,
            startTimeMs = 0L,
            durationMs = 4_000L,
            trimEndMs = 4_000L,
        )
        return Project(
            id = "proj-1",
            title = "Demo",
            durationMs = 4_000L,
            tracks = listOf(Track(id = "t-1", type = TrackType.VIDEO, clips = listOf(clip))),
        )
    }
}

private class FakeVideoExporter(
    private val progressSteps: List<Int> = listOf(50, 100),
    var failureReason: VideoExportException.Reason? = null,
    private val gate: CompletableDeferred<Unit>? = null,
) : VideoExporter {
    var callCount = 0

    override suspend fun export(plan: ExportPlan, outputFile: File, onProgress: (Int) -> Unit) {
        callCount++
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("mp4-bytes")
        if (gate != null) {
            onProgress(25)
            gate.await()
        }
        failureReason?.let { throw VideoExportException(it) }
        progressSteps.forEach { onProgress(it) }
    }
}

private class FakeExportSaver(var shouldFail: Boolean = false) : ExportSaver {
    var gallerySaves = 0
    var lastSavedUri: String? = null

    override suspend fun saveToGallery(source: File, displayName: String): SavedExport {
        if (shouldFail) throw IOException("Save failed")
        gallerySaves++
        return SavedExport("Movies/ProjectMediaPro/$displayName.mp4")
    }

    override suspend fun saveToUri(source: File, destinationUri: String) {
        if (shouldFail) throw IOException("Save failed")
        lastSavedUri = destinationUri
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
        if (deferredResult != null) {
            return deferredResult.await()
        }
        val rel = storage.clipRelativePath(projectId, "imported-$callCount", "mp4")
        lastRelativePath = rel
        val file = storage.resolve(rel)
        file?.parentFile?.mkdirs()
        file?.writeText("fake-video-content")
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

    override suspend fun getProject(id: String): Project? {
        if (shouldFailLoad) throw IOException("Storage read failed")
        return map[id]
    }

    override suspend fun createProject(title: String): Project {
        val project = Project(id = "new-id", title = title)
        map[project.id] = project
        return project
    }

    override suspend fun saveProject(project: Project) {
        if (shouldFailSave) throw IOException("Storage write failed")
        map[project.id] = project
    }

    override suspend fun deleteProject(id: String) {
        map.remove(id)
    }
}
