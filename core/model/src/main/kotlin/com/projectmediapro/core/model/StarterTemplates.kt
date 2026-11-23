package com.projectmediapro.core.model

fun Template.isStarterTemplate(): Boolean = id.startsWith(StarterTemplates.ID_PREFIX)

/**
 * Built-in zero-media starter template blueprints available on fresh install.
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
        description = "Vertical lyrics edit with 3 clip slots, song track, and cover overlay.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "slr-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(id = "slr-v1", label = "Hook Clip", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "slr-v2", label = "Verse Clip", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "slr-v3", label = "Drop Clip", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "slr-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(id = "slr-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
            Slot(
                id = "slr-ov1",
                label = "Cover Art",
                requiredType = TrackType.OVERLAY,
                role = SlotRole.OVERLAY,
                isReplaceable = true,
                durationMs = TemplateBuilder.DEFAULT_OVERLAY_DURATION_MS,
            ),
        ),
    )

    private fun aestheticLyricSquare(): Template = Template(
        id = AESTHETIC_LYRIC_SQUARE_ID,
        name = "Aesthetic Lyric Edit (1:1)",
        description = "Square music edit with 2 background clips and 1 song track.",
        canvasAspect = CanvasAspect.RATIO_1_1,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "sls-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(id = "sls-v1", label = "Background 1", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sls-v2", label = "Background 2", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sls-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(id = "sls-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )

    private fun youtubeIntroOutro(): Template = Template(
        id = YOUTUBE_INTRO_OUTRO_ID,
        name = "YouTube Intro + Outro (16:9)",
        description = "Widescreen format with locked intro, outro, 2 main videos, and 2 audio tracks.",
        canvasAspect = CanvasAspect.RATIO_16_9,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "syt-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(id = "syt-v1", label = "Video 1", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "syt-v2", label = "Video 2", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "syt-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(id = "syt-a1", label = "Audio 1", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
            Slot(id = "syt-a2", label = "Audio 2", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )

    private fun quickBeatMontage(): Template = Template(
        id = QUICK_BEAT_MONTAGE_ID,
        name = "Quick Beat Montage (9:16)",
        description = "Fast 4-clip vertical montage synced to a single beat track.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "sqm-in", label = "Intro", requiredType = TrackType.VIDEO, role = SlotRole.INTRO, isReplaceable = false),
            Slot(id = "sqm-v1", label = "Clip 1", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sqm-v2", label = "Clip 2", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sqm-v3", label = "Clip 3", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sqm-v4", label = "Clip 4", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sqm-out", label = "Outro", requiredType = TrackType.VIDEO, role = SlotRole.OUTRO, isReplaceable = false),
            Slot(id = "sqm-a1", label = "Beat Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )

    private fun mistyKineticLyrics(): Template = Template(
        id = MISTY_KINETIC_LYRICS_ID,
        name = "Misty Nature Lyrics (9:16)",
        description = "Single continuous aerial or nature clip paired with your song for Misty Kinetic typography.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "smk-v1", label = "Aerial / Nature Clip", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "smk-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )

    private fun sunsetMaskMontage(): Template = Template(
        id = SUNSET_MASK_MONTAGE_ID,
        name = "Sunset Mask Montage (9:16)",
        description = "Video-through-text intro clip followed by 4 aesthetic sky montage cuts and a song track.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "ssm-v1", label = "Text Mask Clip (Clouds)", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "ssm-v2", label = "Montage Shot 1", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "ssm-v3", label = "Montage Shot 2", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "ssm-v4", label = "Montage Shot 3", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "ssm-v5", label = "Montage Shot 4", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "ssm-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )

    private fun crimsonZineEdit(): Template = Template(
        id = CRIMSON_ZINE_EDIT_ID,
        name = "Crimson Zine Edit (9:16)",
        description = "Dark-romantic paper sketch or clip backdrop paired with your song for Crimson Zine typography.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "scz-v1", label = "Sketch / Art Backdrop", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "scz-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
            Slot(
                id = "scz-ov1",
                label = "Paper / Sketch Overlay",
                requiredType = TrackType.OVERLAY,
                role = SlotRole.OVERLAY,
                isReplaceable = true,
                durationMs = TemplateBuilder.DEFAULT_OVERLAY_DURATION_MS,
            ),
        ),
    )

    private fun coldInvertEdit(): Template = Template(
        id = COLD_INVERT_EDIT_ID,
        name = "Cold Invert Edit (9:16)",
        description = "High-contrast black/white strobe lyric edit paired with your beat track for Cold Invert typography.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "sci-v1", label = "Backdrop Clip", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sci-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )

    private fun codePoemEdit(): Template = Template(
        id = CODE_POEM_EDIT_ID,
        name = "Code Poem Edit (9:16)",
        description = "Dark-room IDE lyric edit with syntax-highlighted JS code, CRT scanlines, and glowing bridge markers.",
        canvasAspect = CanvasAspect.RATIO_9_16,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "scp-v1", label = "Dark Ambient Backdrop", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "scp-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )

    private fun blackoutLyrics16x9(): Template = Template(
        id = BLACKOUT_LYRICS_16_9_ID,
        name = "Blackout Lyrics (16:9)",
        description = "Pure black 16:9 canvas with crisp white kinetic lyrics and song track.",
        canvasAspect = CanvasAspect.RATIO_16_9,
        canvasBackground = CanvasBackground.BLACK,
        createdAt = 0L,
        updatedAt = 0L,
        slots = listOf(
            Slot(id = "sbl-v1", label = "Black / Ambient Clip", requiredType = TrackType.VIDEO, role = SlotRole.MAIN_VIDEO, isReplaceable = true),
            Slot(id = "sbl-a1", label = "Song Track", requiredType = TrackType.AUDIO, role = SlotRole.AUDIO, isReplaceable = true),
        ),
    )
}
