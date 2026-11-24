package com.projectmediapro.app.home

import com.projectmediapro.app.data.media.ProjectMediaHealthChecker
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.project.FileProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.projectmediapro.app.editor.EditorUiState
import com.projectmediapro.app.editor.resolvePreviewClips
import com.projectmediapro.app.editor.withSyncedProject
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.UndoRedoManager
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Step41HomeMetadataAndHealthIntegrationTest {

    @Test
    fun formatDurationAndAspectProduceExpectedTabularStrings() {
        assertEquals("0:00", HomeMetadataFormatter.formatDuration(0L))
        assertEquals("0:00", HomeMetadataFormatter.formatDuration(-500L))
        assertEquals("0:14", HomeMetadataFormatter.formatDuration(14_200L))
        assertEquals("1:05", HomeMetadataFormatter.formatDuration(65_000L))
        assertEquals("1:02:03", HomeMetadataFormatter.formatDuration(3_723_000L))

        assertEquals("9:16", HomeMetadataFormatter.formatAspect(CanvasAspect.RATIO_9_16))
        assertEquals("16:9", HomeMetadataFormatter.formatAspect(CanvasAspect.RATIO_16_9))
        assertEquals("1:1", HomeMetadataFormatter.formatAspect(CanvasAspect.RATIO_1_1))
        assertEquals("4:5", HomeMetadataFormatter.formatAspect(CanvasAspect.RATIO_4_5))
        assertEquals("Auto", HomeMetadataFormatter.formatAspect(CanvasAspect.ORIGINAL))
    }

    @Test
    fun editorStateHelpersExcludesZeroByteCorruptFilesAndTracksMissingMediaCount() {
        val tempRoot = Files.createTempDirectory("pmp-step41-sync").toFile()
        try {
            val storage = ProjectMediaStorage(File(tempRoot, "media"))
            val okRel = storage.clipRelativePath("p1", "ok", "mp4")
            val zeroRel = storage.clipRelativePath("p1", "zero", "mp4")
            val okFile = requireNotNull(storage.resolve(okRel)).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(256))
            }
            val zeroFile = requireNotNull(storage.resolve(zeroRel)).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(0))
            }

            assertTrue(ProjectMediaHealthChecker.isHealthyMediaFile(okFile))
            assertFalse(ProjectMediaHealthChecker.isHealthyMediaFile(zeroFile))

            val project = Project(
                id = "p1",
                title = "Health Sync",
                tracks = listOf(
                    Track(
                        id = "tv",
                        type = TrackType.VIDEO,
                        clips = listOf(
                            Clip(id = "c1", mediaUri = okRel, startTimeMs = 0L, durationMs = 2_000L),
                            Clip(id = "c2", mediaUri = zeroRel, startTimeMs = 2_000L, durationMs = 2_000L),
                        ),
                    ),
                ),
            )

            val previewClips = resolvePreviewClips(project, storage)
            assertEquals("Zero-byte corrupt video must be excluded from preview", 1, previewClips.size)

            val history = UndoRedoManager(project)
            val synced = EditorUiState.Loaded(project = project).withSyncedProject(
                previousProject = project,
                targetProject = project,
                preferredSelectedId = null,
                mediaStorage = storage,
                history = history,
            )
            assertEquals(1, synced.missingMediaCount)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun homeViewModelResolvesHealthyThumbnailsAndSkipsCorruptClips() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                val tempRoot = Files.createTempDirectory("pmp-step41-thumbs").toFile()
                try {
                    val storage = ProjectMediaStorage(File(tempRoot, "media"))
                    val repo = FileProjectRepository(
                        directory = File(tempRoot, "projects"),
                        ioDispatcher = dispatcher,
                        mediaStorage = storage,
                    )
                    val pOk = repo.createProject("Healthy Project")
                    val okRel = storage.clipRelativePath(pOk.id, "c1", "mp4")
                    requireNotNull(storage.resolve(okRel)).apply {
                        parentFile?.mkdirs()
                        writeBytes(ByteArray(128))
                    }
                    repo.saveProject(
                        pOk.copy(
                            tracks = listOf(
                                Track(
                                    id = "tv1",
                                    type = TrackType.VIDEO,
                                    clips = listOf(Clip(id = "c1", mediaUri = okRel, startTimeMs = 0L, durationMs = 2_000L)),
                                ),
                            ),
                        ),
                    )

                    val pBad = repo.createProject("Corrupt Project")
                    val badRel = storage.clipRelativePath(pBad.id, "c2", "mp4")
                    requireNotNull(storage.resolve(badRel)).apply {
                        parentFile?.mkdirs()
                        writeBytes(ByteArray(0))
                    }
                    repo.saveProject(
                        pBad.copy(
                            tracks = listOf(
                                Track(
                                    id = "tv2",
                                    type = TrackType.VIDEO,
                                    clips = listOf(Clip(id = "c2", mediaUri = badRel, startTimeMs = 0L, durationMs = 2_000L)),
                                ),
                            ),
                        ),
                    )

                    val vm = HomeViewModel(projectRepository = repo, mediaStorage = storage)
                    assertTrue(vm.uiState.value.thumbnailTargets.containsKey(pOk.id))
                    assertFalse(vm.uiState.value.thumbnailTargets.containsKey(pBad.id))
                } finally {
                    tempRoot.deleteRecursively()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
