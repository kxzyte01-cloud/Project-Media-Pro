package com.projectmediapro.app.data.template

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.AudioEnhanceConfig
import com.projectmediapro.core.model.AudioEqPreset
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.ColorLutPreset
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.audioSlots
import com.projectmediapro.core.model.mainVideoSlots
import com.projectmediapro.feature.captions.CaptionDocument
import com.projectmediapro.feature.captions.CaptionStyle
import com.projectmediapro.feature.captions.CaptionTemplateId
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step36ProjectTemplateSaverTest {

    @Test
    fun saveProjectAsTemplateCopiesLockedMediaOnlyAndPreservesCaptionStyle() = runTest {
        val tempRoot = Files.createTempDirectory("pmp-step36-saver").toFile()
        try {
            val projectMedia = ProjectMediaStorage(File(tempRoot, "media"))
            val templateMedia = ProjectMediaStorage(File(tempRoot, "template_media"))
            var seq = 1
            val templateRepo = FileTemplateRepository(
                directory = File(tempRoot, "templates"),
                idProvider = { "tpl-${seq++}" },
                clock = { 10_000L },
                mediaStorage = templateMedia,
            )

            val videoRel = projectMedia.clipRelativePath("proj-1", "v1", "mp4")
            val audioRel = projectMedia.clipRelativePath("proj-1", "a1", "mp3")
            requireNotNull(projectMedia.resolve(videoRel)).apply {
                parentFile?.mkdirs()
                writeText("private-user-video")
            }
            requireNotNull(projectMedia.resolve(audioRel)).apply {
                parentFile?.mkdirs()
                writeText("locked-song-bytes")
            }

            val project = Project(
                id = "proj-1",
                title = "Sunset Edit",
                tracks = listOf(
                    Track(
                        id = "tv",
                        type = TrackType.VIDEO,
                        clips = listOf(
                            Clip(
                                id = "v1",
                                mediaUri = videoRel,
                                startTimeMs = 0L,
                                durationMs = 4_000L,
                                visualEffects = ClipVisualEffects(lut = ColorLutPreset.GOLDEN_SUNSET),
                            ),
                        ),
                    ),
                    Track(
                        id = "ta",
                        type = TrackType.AUDIO,
                        clips = listOf(
                            Clip(
                                id = "a1",
                                mediaUri = audioRel,
                                startTimeMs = 0L,
                                durationMs = 8_000L,
                                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.WARM_BROADCAST),
                            ),
                        ),
                    ),
                ),
            )

            val captionDoc = CaptionDocument(
                projectId = "proj-1",
                style = CaptionStyle(templateId = CaptionTemplateId.KINETIC_COLLAGE),
            )

            val saver = ProjectTemplateSaver(
                templateRepository = templateRepo,
                projectMediaStorage = projectMedia,
                templateMediaStorage = templateMedia,
                idProvider = { "slot-${seq++}" },
            )

            val saved = saver.saveProjectAsTemplate(
                project = project,
                templateName = "My Sunset Style",
                description = "Golden LUT + Kinetic Lyrics",
                replaceableClipIds = setOf("v1"),
                captionDocument = captionDoc,
            )

            val reloaded = requireNotNull(templateRepo.getTemplate(saved.id))
            assertEquals("My Sunset Style", reloaded.name)
            assertNotNull(reloaded.captionStyleJson)
            assertTrue(reloaded.captionStyleJson.orEmpty().contains("KINETIC_COLLAGE"))

            val videoSlot = reloaded.mainVideoSlots().single()
            assertTrue(videoSlot.isReplaceable)
            assertNull("Replaceable video must not copy private media", videoSlot.filledMediaUri)
            assertEquals(ColorLutPreset.GOLDEN_SUNSET, videoSlot.visualEffects?.lut)

            val audioSlot = reloaded.audioSlots().single()
            assertNotNull("Locked audio must copy media to template storage", audioSlot.filledMediaUri)
            val copiedAudioFile = requireNotNull(templateMedia.resolve(requireNotNull(audioSlot.filledMediaUri)))
            assertTrue(copiedAudioFile.isFile)
            assertEquals("locked-song-bytes", copiedAudioFile.readText())
            assertEquals(AudioEqPreset.WARM_BROADCAST, audioSlot.audioEnhance?.eqPreset)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
