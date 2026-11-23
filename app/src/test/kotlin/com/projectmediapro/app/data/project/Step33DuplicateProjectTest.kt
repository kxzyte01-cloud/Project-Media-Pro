package com.projectmediapro.app.data.project

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.AudioEnhanceConfig
import com.projectmediapro.core.model.AudioEqPreset
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.OverlayBlendMode
import com.projectmediapro.core.model.OverlayVisualStyle
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step33DuplicateProjectTest {

    @Test
    fun duplicateProjectClonesClipsMediaAndSurvivesOriginalDeletion() = runTest {
        val tempRoot = Files.createTempDirectory("pmp-step33-dup").toFile()
        try {
            val projectsDir = File(tempRoot, "projects")
            val mediaDir = File(tempRoot, "media")
            val mediaStorage = ProjectMediaStorage(mediaDir)
            var idSeq = 1
            val repo = FileProjectRepository(
                directory = projectsDir,
                idProvider = { "id-${idSeq++}" },
                clock = { 1_000L + idSeq },
                mediaStorage = mediaStorage,
            )

            val created = repo.createProject("My Viral Reel")
            val relMedia = mediaStorage.clipRelativePath(created.id, "clip-1", "mp4")
            val mediaFile = requireNotNull(mediaStorage.resolve(relMedia))
            mediaFile.parentFile?.mkdirs()
            mediaFile.writeText("fake-video-bytes")

            val populated = created.copy(
                tracks = listOf(
                    Track(
                        id = "v1",
                        type = TrackType.VIDEO,
                        clips = listOf(
                            Clip(
                                id = "clip-1",
                                mediaUri = relMedia,
                                startTimeMs = 0L,
                                durationMs = 4_000L,
                                overlayStyle = OverlayVisualStyle(blendMode = OverlayBlendMode.SCREEN_LUMA),
                                audioEnhance = AudioEnhanceConfig(
                                    eqPreset = AudioEqPreset.VOCAL_CLARITY,
                                    autoDuckMusicEnabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
            )
            repo.saveProject(populated)

            val cloned = repo.duplicateProject(created.id, copySuffix = "Copy")
            assertNotNull(cloned)
            val copy = requireNotNull(cloned)
            assertNotEquals(created.id, copy.id)
            assertEquals("My Viral Reel (Copy)", copy.title)

            val clonedClip = copy.tracks.single().clips.single()
            assertTrue(
                "Cloned clip URI should point to cloned project folder",
                clonedClip.mediaUri.startsWith("${copy.id}/"),
            )
            assertEquals(OverlayBlendMode.SCREEN_LUMA, clonedClip.overlayStyle?.blendMode)
            assertEquals(AudioEqPreset.VOCAL_CLARITY, clonedClip.audioEnhance?.eqPreset)
            assertEquals(true, clonedClip.audioEnhance?.autoDuckMusicEnabled)

            val clonedMediaFile = requireNotNull(mediaStorage.resolve(clonedClip.mediaUri))
            assertTrue(clonedMediaFile.isFile)
            assertEquals("fake-video-bytes", clonedMediaFile.readText())

            // Deleting original project must not delete cloned project's media
            repo.deleteProject(created.id)
            assertTrue("Cloned media must survive original deletion", clonedMediaFile.isFile)
            assertNotNull(repo.getProject(copy.id))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun duplicateProjectInvokesOnProjectDuplicatedAndToleratesCallbackFailure() = runTest {
        val tempRoot = Files.createTempDirectory("pmp-step33-dup-cb").toFile()
        try {
            val projectsDir = File(tempRoot, "projects")
            val duplicatedPairs = mutableListOf<Pair<String, String>>()
            var idSeq = 1
            val repo = FileProjectRepository(
                directory = projectsDir,
                idProvider = { "id-${idSeq++}" },
                clock = { 2_000L + idSeq },
                onProjectDuplicated = { sourceId, targetId ->
                    duplicatedPairs.add(sourceId to targetId)
                    throw IllegalStateException("Simulated caption store failure")
                },
            )

            val created = repo.createProject("Lyric Edit")
            val copy = requireNotNull(repo.duplicateProject(created.id, copySuffix = "Copy"))

            assertEquals(listOf(created.id to copy.id), duplicatedPairs)
            assertNotNull(repo.getProject(copy.id))
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
