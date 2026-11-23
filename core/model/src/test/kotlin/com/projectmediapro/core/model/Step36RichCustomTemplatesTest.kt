package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step36RichCustomTemplatesTest {

    @Test
    fun projectToTemplateAndBackPreservesEffectsAudioOverlayTextAndStyle() {
        val sourceProject = Project(
            id = "proj-src",
            title = "Neon Edit",
            canvasAspect = CanvasAspect.RATIO_9_16,
            canvasBackground = CanvasBackground.BLACK,
            soundReactiveEnabled = true,
            opticalFlowEnabled = true,
            tracks = listOf(
                Track(
                    id = "tv1",
                    type = TrackType.VIDEO,
                    clips = listOf(
                        Clip(
                            id = "v1",
                            mediaUri = "proj-src/v1.mp4",
                            startTimeMs = 0L,
                            durationMs = 3_000L,
                            playbackSpeed = 1.5f,
                            speedRamp = SpeedRampPreset.JUMP_CUT,
                            visualEffects = ClipVisualEffects(
                                lut = ColorLutPreset.CRIMSON_MOOD,
                                transitionIn = ClipTransition.WHIP_PAN_LEFT,
                            ),
                            rotationDegrees = 90,
                        ),
                    ),
                ),
                Track(
                    id = "ta1",
                    type = TrackType.AUDIO,
                    clips = listOf(
                        Clip(
                            id = "a1",
                            mediaUri = "proj-src/a1.mp3",
                            startTimeMs = 0L,
                            durationMs = 6_000L,
                            volume = 0.8f,
                            audioEnhance = AudioEnhanceConfig(
                                eqPreset = AudioEqPreset.VOCAL_CLARITY,
                                autoDuckMusicEnabled = true,
                            ),
                        ),
                    ),
                ),
                Track(
                    id = "to1",
                    type = TrackType.OVERLAY,
                    clips = listOf(
                        Clip(
                            id = "o1",
                            mediaUri = "proj-src/logo.png",
                            startTimeMs = 500L,
                            durationMs = 2_000L,
                            overlayStyle = OverlayVisualStyle(blendMode = OverlayBlendMode.SCREEN_LUMA),
                        ),
                    ),
                ),
                Track(
                    id = "tt1",
                    type = TrackType.TEXT,
                    clips = listOf(
                        Clip(
                            id = "t1",
                            mediaUri = "",
                            startTimeMs = 200L,
                            durationMs = 2_500L,
                            textContent = "MIDNIGHT DRIVE",
                            textStyle = TextOverlayStyle(fontScale = 1.25f),
                        ),
                    ),
                ),
            ),
        )

        var seq = 1
        val captionJson = "{\"templateId\":\"CRIMSON_ZINE\"}"
        val baseTemplate = Template(id = "tpl-1", name = "Neon Recipe")
        val template = ProjectToTemplateConverter.fromProject(
            baseTemplate = baseTemplate,
            project = sourceProject,
            description = "Saved from Neon Edit",
            replaceableClipIds = setOf("v1"),
            lockedMediaPathsByClipId = mapOf(
                "a1" to "tpl-1/a1.mp3",
                "o1" to "tpl-1/logo.png",
            ),
            captionStyleJson = captionJson,
            idProvider = { "gen-${seq++}" },
        )

        assertEquals(CanvasAspect.RATIO_9_16, template.canvasAspect)
        assertTrue(template.soundReactiveEnabled)
        assertTrue(template.opticalFlowEnabled)
        assertEquals(1, template.textClips.size)
        assertEquals("MIDNIGHT DRIVE", template.textClips.single().textContent)
        assertEquals(captionJson, template.captionStyleJson)

        val videoSlot = template.mainVideoSlots().single()
        assertTrue(videoSlot.isReplaceable)
        assertNull(videoSlot.filledMediaUri)
        assertEquals(ColorLutPreset.CRIMSON_MOOD, videoSlot.visualEffects?.lut)
        assertEquals(SpeedRampPreset.JUMP_CUT, videoSlot.speedRamp)

        val audioSlot = template.audioSlots().single()
        assertEquals("tpl-1/a1.mp3", audioSlot.filledMediaUri)
        assertEquals(true, audioSlot.audioEnhance?.autoDuckMusicEnabled)

        val overlaySlot = template.overlaySlots().single()
        assertEquals(OverlayBlendMode.SCREEN_LUMA, overlaySlot.overlayStyle?.blendMode)

        // Apply template with a shorter replacement video (2000ms < 3000ms slot): must NOT stretch.
        val applied = TemplateApplier.buildProjectFromTemplate(
            baseProject = Project(id = "proj-new", title = "New Edit"),
            template = template,
            resolvedMediaBySlotId = mapOf(
                videoSlot.id to ResolvedTemplateMedia("proj-new/new_v1.mp4", 2_000L),
                audioSlot.id to ResolvedTemplateMedia("proj-new/a1.mp3", 6_000L),
                overlaySlot.id to ResolvedTemplateMedia("proj-new/logo.png", 0L),
            ),
            idProvider = { "app-${seq++}" },
        )

        assertTrue(applied.soundReactiveEnabled)
        assertTrue(applied.opticalFlowEnabled)

        val newVideo = applied.videoClips().single()
        assertEquals(2_000L, newVideo.sourceDurationMs)
        assertEquals(2_000L, newVideo.trimEndMs)
        assertEquals(1_333L, newVideo.durationMs)
        assertEquals(ColorLutPreset.CRIMSON_MOOD, newVideo.visualEffects?.lut)
        assertEquals(ClipTransition.WHIP_PAN_LEFT, newVideo.visualEffects?.transitionIn)
        assertEquals(SpeedRampPreset.JUMP_CUT, newVideo.speedRamp)
        assertEquals(90, newVideo.rotationDegrees)

        val newAudio = applied.audioClips().single()
        assertEquals(AudioEqPreset.VOCAL_CLARITY, newAudio.audioEnhance?.eqPreset)
        assertEquals(true, newAudio.audioEnhance?.autoDuckMusicEnabled)

        val newOverlay = applied.overlayClips().single()
        assertEquals(OverlayBlendMode.SCREEN_LUMA, newOverlay.overlayStyle?.blendMode)

        val newText = applied.textClips().single()
        assertEquals("MIDNIGHT DRIVE", newText.textContent)
        assertEquals(1.25f, newText.textStyle?.fontScale ?: 0f, 0.001f)
    }
}
