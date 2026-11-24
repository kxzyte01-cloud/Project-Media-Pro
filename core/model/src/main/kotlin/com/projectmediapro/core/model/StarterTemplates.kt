package com.projectmediapro.core.model

fun Template.isStarterTemplate(): Boolean = id.startsWith(StarterTemplates.ID_PREFIX)

/**
 * Built-in zero-media starter template blueprints available on fresh install,
 * pre-wired with matching LUTs, transitions, Audio Studio presets, and viral lyric styles.
 */
object StarterTemplates {

    const val ID_PREFIX = "starter-"
    const val MUSIC_LYRICS_REEL_ID = "starter-music-lyrics-reel"
    const val AESTHETIC_LYRIC_SQUARE_ID = "starter-aesthetic-lyric-square"
    const val YOUTUBE_INTRO_OUTRO_ID = "starter-youtube-intro-outro"
    const val QUICK_BEAT_MONTAGE_ID = "starter-quick-beat-montage"
    const val MISTY_KINETIC_LYRICS_ID = "starter-misty-kinetic-lyrics"
    const val SUNSET_MASK_MONTAGE_ID = "starter-sunset-mask-montage"
    const val CRIMSON_ZINE_EDIT_ID = "starter-crimson-zine-edit"
    const val COLD_INVERT_EDIT_ID = "starter-cold-invert-edit"
    const val CODE_POEM_EDIT_ID = "starter-code-poem-edit"
    const val BLACKOUT_LYRICS_16_9_ID = "starter-blackout-lyrics-16-9"

    fun all(): List<Template> = listOf(
        musicLyricsReel(),
        aestheticLyricSquare(),
        youtubeIntroOutro(),
        quickBeatMontage(),
        mistyKineticLyrics(),
        sunsetMaskMontage(),
        crimsonZineEdit(),
        coldInvertEdit(),
        codePoemEdit(),
        blackoutLyrics16x9(),
    )

    fun findById(id: String): Template? = all().firstOrNull { it.id == id }

    private fun musicLyricsReel(): Template = Template(
        id = MUSIC_LYRICS_REEL_ID,
        name = "Music Lyrics Reel (9:16)",
        description = "Vertical lyrics edit with 3 clip slots, Dreamy Glow grade, song track, and cover overlay.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "slr-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(
                id = "slr-v1",
                label = "Hook Clip",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.DREAMY_GLOW, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 350L),
            ),
            Slot(
                id = "slr-v2",
                label = "Verse Clip",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.DREAMY_GLOW, transitionIn = ClipTransition.ZOOM_IN, fadeInMs = 300L),
            ),
            Slot(
                id = "slr-v3",
                label = "Drop Clip",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.DREAMY_GLOW, transitionIn = ClipTransition.WHIP_LEFT, fadeInMs = 300L),
            ),
            Slot(id = "slr-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(
                id = "slr-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.STUDIO_WARMTH),
            ),
            Slot(
                id = "slr-ov1",
                label = "Cover Art",
                requiredType = TrackType.OVERLAY,
                role = SlotRole.OVERLAY,
                isReplaceable = true,
                durationMs = TemplateBuilder.DEFAULT_OVERLAY_DURATION_MS,
                overlayStyle = OverlayVisualStyle(cornerRadiusFraction = 0.14f, opacity = 0.94f),
            ),
        ),
    )

    private fun aestheticLyricSquare(): Template = Template(
        id = AESTHETIC_LYRIC_SQUARE_ID,
        name = "Aesthetic Lyric Edit (1:1)",
        description = "Square retro-film music edit with 2 background clips and warm Lo-Fi radio audio.",
        canvasAspect = CanvasAspect.RATIO_1_1,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "sls-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(
                id = "sls-v1",
                label = "Background 1",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.RETRO_FILM, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 400L),
            ),
            Slot(
                id = "sls-v2",
                label = "Background 2",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.RETRO_FILM, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 400L),
            ),
            Slot(id = "sls-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(
                id = "sls-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.LOFI_RADIO),
            ),
        ),
    )

    private fun youtubeIntroOutro(): Template = Template(
        id = YOUTUBE_INTRO_OUTRO_ID,
        name = "YouTube Intro + Outro (16:9)",
        description = "Widescreen cinema format with locked intro/outro, vocal clarity EQ, and auto-ducking music.",
        canvasAspect = CanvasAspect.RATIO_16_9,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "syt-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(
                id = "syt-v1",
                label = "Video 1",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.CINEMA, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 350L),
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.VOCAL_CLARITY, noiseGateEnabled = true),
            ),
            Slot(
                id = "syt-v2",
                label = "Video 2",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.CINEMA, transitionIn = ClipTransition.SLIDE_UP, fadeInMs = 300L),
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.VOCAL_CLARITY, noiseGateEnabled = true),
            ),
            Slot(id = "syt-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(
                id = "syt-a1",
                label = "Audio 1",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.STUDIO_WARMTH, autoDuckMusicEnabled = true),
            ),
            Slot(
                id = "syt-a2",
                label = "Audio 2",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(autoDuckMusicEnabled = true),
            ),
        ),
    )

    private fun quickBeatMontage(): Template = Template(
        id = QUICK_BEAT_MONTAGE_ID,
        name = "Quick Beat Montage (9:16)",
        description = "Fast 4-clip vertical montage with Whip/Zoom transitions, speed ramps, and Bass Boost beat track.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        soundReactiveEnabled = true,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "sqm-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(
                id = "sqm-v1",
                label = "Clip 1",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.VIVID, transitionIn = ClipTransition.ZOOM_IN, fadeInMs = 250L),
                speedRamp = SpeedRampPreset.MONTAGE,
            ),
            Slot(
                id = "sqm-v2",
                label = "Clip 2",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.VIVID, transitionIn = ClipTransition.WHIP_LEFT, fadeInMs = 250L),
                speedRamp = SpeedRampPreset.JUMP_CUT,
            ),
            Slot(
                id = "sqm-v3",
                label = "Clip 3",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.VIVID, transitionIn = ClipTransition.WHIP_RIGHT, fadeInMs = 250L),
                speedRamp = SpeedRampPreset.MONTAGE,
            ),
            Slot(
                id = "sqm-v4",
                label = "Clip 4",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.VIVID, transitionIn = ClipTransition.ZOOM_OUT, fadeInMs = 250L),
                speedRamp = SpeedRampPreset.HERO,
            ),
            Slot(id = "sqm-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(
                id = "sqm-a1",
                label = "Beat Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.BASS_BOOST),
            ),
        ),
    )

    private fun mistyKineticLyrics(): Template = Template(
        id = MISTY_KINETIC_LYRICS_ID,
        name = "Misty Nature Lyrics (9:16)",
        description = "Aerial or nature clip with Misty Moss LUT, warm studio audio, and Misty Kinetic typography.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        captionStyleJson = "{\"templateId\":\"KINETIC_COLLAGE\"}",
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(
                id = "smk-v1",
                label = "Aerial / Nature Clip",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.MISTY_MOSS, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 450L),
            ),
            Slot(
                id = "smk-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.STUDIO_WARMTH),
            ),
        ),
    )

    private fun sunsetMaskMontage(): Template = Template(
        id = SUNSET_MASK_MONTAGE_ID,
        name = "Sunset Mask Montage (9:16)",
        description = "Golden Sunset LUT, video-through-text intro, 4 sky montage cuts with Whip/Zoom transitions, and Bass Boost.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        captionStyleJson = "{\"templateId\":\"KNOCKOUT_MASK\"}",
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(
                id = "ssm-v1",
                label = "Text Mask Clip (Clouds)",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.GOLDEN_SUNSET, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 350L),
            ),
            Slot(
                id = "ssm-v2",
                label = "Montage Shot 1",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.GOLDEN_SUNSET, transitionIn = ClipTransition.ZOOM_IN, fadeInMs = 250L),
            ),
            Slot(
                id = "ssm-v3",
                label = "Montage Shot 2",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.GOLDEN_SUNSET, transitionIn = ClipTransition.WHIP_LEFT, fadeInMs = 250L),
            ),
            Slot(
                id = "ssm-v4",
                label = "Montage Shot 3",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.GOLDEN_SUNSET, transitionIn = ClipTransition.WHIP_RIGHT, fadeInMs = 250L),
            ),
            Slot(
                id = "ssm-v5",
                label = "Montage Shot 4",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.GOLDEN_SUNSET, transitionIn = ClipTransition.ZOOM_OUT, fadeInMs = 250L),
            ),
            Slot(
                id = "ssm-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.BASS_BOOST),
            ),
        ),
    )

    private fun crimsonZineEdit(): Template = Template(
        id = CRIMSON_ZINE_EDIT_ID,
        name = "Crimson Zine Edit (9:16)",
        description = "Crimson Mood LUT, multiply paper overlay, and Crimson Zine editorial typography.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        captionStyleJson = "{\"templateId\":\"CRIMSON_ZINE\"}",
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(
                id = "scz-v1",
                label = "Sketch / Art Backdrop",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.CRIMSON_MOOD, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 400L),
            ),
            Slot(
                id = "scz-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.STUDIO_WARMTH),
            ),
            Slot(
                id = "scz-ov1",
                label = "Paper / Sketch Overlay",
                requiredType = TrackType.OVERLAY,
                role = SlotRole.OVERLAY,
                isReplaceable = true,
                durationMs = TemplateBuilder.DEFAULT_OVERLAY_DURATION_MS,
                overlayStyle = OverlayVisualStyle(blendMode = OverlayBlendMode.MULTIPLY, opacity = 0.85f),
            ),
        ),
    )

    private fun coldInvertEdit(): Template = Template(
        id = COLD_INVERT_EDIT_ID,
        name = "Cold Invert Edit (9:16)",
        description = "High-contrast Noir Punch strobe lyric edit with sound-reactive pulse and Cold Invert typography.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        soundReactiveEnabled = true,
        captionStyleJson = "{\"templateId\":\"COLD_INVERT\"}",
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(
                id = "sci-v1",
                label = "Backdrop Clip",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.NOIR_PUNCH, transitionIn = ClipTransition.FADE_WHITE, fadeInMs = 200L),
            ),
            Slot(
                id = "sci-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.BASS_BOOST),
            ),
        ),
    )

    private fun codePoemEdit(): Template = Template(
        id = CODE_POEM_EDIT_ID,
        name = "Code Poem Edit (9:16)",
        description = "Dark-room Cyber Neon IDE lyric edit with syntax-highlighted JS code and CRT scanlines.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        captionStyleJson = "{\"templateId\":\"CODE_POEM\"}",
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(
                id = "scp-v1",
                label = "Dark Ambient Backdrop",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.CYBER_NEON, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 350L),
            ),
            Slot(
                id = "scp-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.LOFI_RADIO),
            ),
        ),
    )

    private fun blackoutLyrics16x9(): Template = Template(
        id = BLACKOUT_LYRICS_16_9_ID,
        name = "Blackout Lyrics (16:9)",
        description = "Pure black 16:9 canvas with Noir Punch contrast and crisp white kinetic lyrics.",
        canvasAspect = CanvasAspect.RATIO_16_9,
        canvasBackground = CanvasBackground.BLACK,
        captionStyleJson = "{\"templateId\":\"BLACKOUT_KINETIC\"}",
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(
                id = "sbl-v1",
                label = "Black / Ambient Clip",
                requiredType = TrackType.VIDEO,
                role = SlotRole.MAIN_VIDEO,
                isReplaceable = true,
                visualEffects = ClipVisualEffects(filter = VideoFilterPreset.NOIR_PUNCH, transitionIn = ClipTransition.FADE_BLACK, fadeInMs = 350L),
            ),
            Slot(
                id = "sbl-a1",
                label = "Song Track",
                requiredType = TrackType.AUDIO,
                role = SlotRole.AUDIO,
                isReplaceable = true,
                audioEnhance = AudioEnhanceConfig(eqPreset = AudioEqPreset.STUDIO_WARMTH),
            ),
        ),
    )
}
