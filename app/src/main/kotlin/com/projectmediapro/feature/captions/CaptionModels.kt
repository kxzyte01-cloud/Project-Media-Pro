package com.projectmediapro.feature.captions

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object CaptionTime {
    const val MICROS_PER_MS = 1_000L
    const val MICROS_PER_SEC = 1_000_000L
    const val MIN_DURATION_US = 1_000L
    const val NUDGE_STEP_US = 50_000L
}

object CaptionStyleBounds {
    val FONT_SCALE_RANGE = 0.65f..1.75f
    val POSITION_X_RANGE = 0.10f..0.90f
    val POSITION_Y_RANGE = 0.15f..0.90f
    val STROKE_WIDTH_RANGE = 0.0f..0.22f
    val OFFSET_MS_RANGE = -2_000f..2_000f
}

@Serializable
data class WordTiming(
    val id: String,
    val text: String,
    val startUs: Long,
    val endUs: Long,
    val syllableCount: Int = 1,
    val confidence: Float = 1.0f,
    val isPlaceholder: Boolean = false,
) {
    init {
        require(startUs >= 0L) { "startUs must be non-negative: $startUs" }
        require(endUs >= startUs) { "endUs ($endUs) must be >= startUs ($startUs)" }
        require(syllableCount >= 1) { "syllableCount must be >= 1: $syllableCount" }
    }

    val durationUs: Long
        get() = endUs - startUs
}

@Serializable
data class CaptionLine(
    val id: String,
    val words: List<WordTiming>,
    val startUs: Long,
    val endUs: Long,
) {
    init {
        require(words.isNotEmpty()) { "CaptionLine must contain at least one word" }
        require(startUs >= 0L) { "startUs must be non-negative: $startUs" }
        require(endUs >= startUs) { "endUs ($endUs) must be >= startUs ($startUs)" }
    }
}

@Serializable
data class LyricsStylePreset(
    val id: String,
    val displayName: String,
    val maxWordsPerLine: Int,
    val maxCharsPerLine: Int,
    val maxLineGapUs: Long,
    val minLineDurationUs: Long,
    val minWordDurationUs: Long,
    val syllableDensityFloorUsPerSyllable: Long,
    val joinShortOrphans: Boolean = true,
) {
    init {
        require(maxWordsPerLine >= 1) { "maxWordsPerLine must be >= 1" }
        require(maxCharsPerLine >= 4) { "maxCharsPerLine must be >= 4" }
        require(maxLineGapUs >= 0L) { "maxLineGapUs must be >= 0" }
        require(minLineDurationUs >= 0L) { "minLineDurationUs must be >= 0" }
        require(minWordDurationUs >= CaptionTime.MIN_DURATION_US) { "minWordDurationUs must be >= 1000 Us" }
        require(syllableDensityFloorUsPerSyllable >= 0L) { "syllableDensityFloorUsPerSyllable must be >= 0" }
    }
}

object LyricsPresetRegistry {
    const val FAST_FLOW_ID = "fast_flow"
    const val MELODIC_ID = "melodic"
    const val SPEECH_ID = "speech"

    val FAST_FLOW = LyricsStylePreset(
        id = FAST_FLOW_ID,
        displayName = "Fast Flow",
        maxWordsPerLine = 3,
        maxCharsPerLine = 18,
        maxLineGapUs = 180_000L,
        minLineDurationUs = 260_000L,
        minWordDurationUs = 90_000L,
        syllableDensityFloorUsPerSyllable = 70_000L,
        joinShortOrphans = true,
    )

    val MELODIC = LyricsStylePreset(
        id = MELODIC_ID,
        displayName = "Melodic",
        maxWordsPerLine = 5,
        maxCharsPerLine = 28,
        maxLineGapUs = 380_000L,
        minLineDurationUs = 550_000L,
        minWordDurationUs = 140_000L,
        syllableDensityFloorUsPerSyllable = 110_000L,
        joinShortOrphans = true,
    )

    val SPEECH = LyricsStylePreset(
        id = SPEECH_ID,
        displayName = "Speech",
        maxWordsPerLine = 7,
        maxCharsPerLine = 38,
        maxLineGapUs = 600_000L,
        minLineDurationUs = 700_000L,
        minWordDurationUs = 150_000L,
        syllableDensityFloorUsPerSyllable = 120_000L,
        joinShortOrphans = true,
    )

    val all: List<LyricsStylePreset> = listOf(FAST_FLOW, MELODIC, SPEECH)

    fun byId(id: String): LyricsStylePreset = all.firstOrNull { it.id == id } ?: SPEECH
}

@Serializable
enum class CaptionTemplateId {
    KARAOKE_HIGHLIGHT,
    POP_IN,
    TYPEWRITER,
    CLEAN_SUBTITLE,
    KINETIC_GRUNGE,
    EDITORIAL_SCRATCH,
    NEON_GLOW_STACK,
    MUSIC_PLAYER,
    KINETIC_COLLAGE,
    KNOCKOUT_MASK,
    CRIMSON_ZINE,
    COLD_INVERT,
    CODE_POEM,
    BLACKOUT_KINETIC,
}

data class CaptionTemplate(
    val id: CaptionTemplateId,
    val displayName: String,
    val popScaleFactor: Float,
    val entranceDurationUs: Long,
    val activeWordScaleBoost: Float,
    val defaultBackgroundBox: Boolean,
)

object CaptionTemplateRegistry {
    val all: List<CaptionTemplate> = listOf(
        CaptionTemplate(
            id = CaptionTemplateId.KARAOKE_HIGHLIGHT,
            displayName = "Karaoke Highlight",
            popScaleFactor = 1.0f,
            entranceDurationUs = 110_000L,
            activeWordScaleBoost = 0.08f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.POP_IN,
            displayName = "Pop-In",
            popScaleFactor = 1.16f,
            entranceDurationUs = 140_000L,
            activeWordScaleBoost = 0.10f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.KINETIC_GRUNGE,
            displayName = "Kinetic Pop",
            popScaleFactor = 1.24f,
            entranceDurationUs = 120_000L,
            activeWordScaleBoost = 0.14f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.NEON_GLOW_STACK,
            displayName = "Neon Glow",
            popScaleFactor = 1.18f,
            entranceDurationUs = 130_000L,
            activeWordScaleBoost = 0.12f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.EDITORIAL_SCRATCH,
            displayName = "Editorial Film",
            popScaleFactor = 1.06f,
            entranceDurationUs = 110_000L,
            activeWordScaleBoost = 0.07f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.MUSIC_PLAYER,
            displayName = "Music Player",
            popScaleFactor = 1.0f,
            entranceDurationUs = 100_000L,
            activeWordScaleBoost = 0.06f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.KINETIC_COLLAGE,
            displayName = "Misty Kinetic",
            popScaleFactor = 1.22f,
            entranceDurationUs = 125_000L,
            activeWordScaleBoost = 0.16f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.KNOCKOUT_MASK,
            displayName = "Video Mask",
            popScaleFactor = 1.18f,
            entranceDurationUs = 140_000L,
            activeWordScaleBoost = 0.12f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.CRIMSON_ZINE,
            displayName = "Crimson Zine",
            popScaleFactor = 1.20f,
            entranceDurationUs = 105_000L,
            activeWordScaleBoost = 0.15f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.COLD_INVERT,
            displayName = "Cold Invert",
            popScaleFactor = 1.24f,
            entranceDurationUs = 85_000L,
            activeWordScaleBoost = 0.12f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.CODE_POEM,
            displayName = "Code Poem",
            popScaleFactor = 1.0f,
            entranceDurationUs = 90_000L,
            activeWordScaleBoost = 0.0f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.BLACKOUT_KINETIC,
            displayName = "Blackout",
            popScaleFactor = 1.26f,
            entranceDurationUs = 120_000L,
            activeWordScaleBoost = 0.08f,
            defaultBackgroundBox = false,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.TYPEWRITER,
            displayName = "Typewriter",
            popScaleFactor = 1.0f,
            entranceDurationUs = 90_000L,
            activeWordScaleBoost = 0.0f,
            defaultBackgroundBox = true,
        ),
        CaptionTemplate(
            id = CaptionTemplateId.CLEAN_SUBTITLE,
            displayName = "Clean Subtitle",
            popScaleFactor = 1.0f,
            entranceDurationUs = 120_000L,
            activeWordScaleBoost = 0.0f,
            defaultBackgroundBox = true,
        ),
    )

    fun byId(id: CaptionTemplateId): CaptionTemplate =
        all.firstOrNull { it.id == id } ?: all.first()
}

@Serializable
enum class CaptionFontFamily(val displayName: String) {
    SANS_BOLD("Sans Bold"),
    CONDENSED_DISPLAY("Display"),
    MONOSPACE("Monospace"),
    SERIF_EDITORIAL("Editorial"),
    RAIN_GLASS("Rain Glass"),
}

@Serializable
data class CaptionPosition(
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.80f,
)

@Serializable
data class CaptionStyle(
    val templateId: CaptionTemplateId = CaptionTemplateId.KARAOKE_HIGHLIGHT,
    val fontFamily: CaptionFontFamily = CaptionFontFamily.SANS_BOLD,
    val fontSizeScale: Float = 1.0f,
    val primaryColorArgb: Long = 0xFFFFFFFFL,
    val highlightColorArgb: Long = 0xFFFACC15L,
    val strokeColorArgb: Long = 0xFF0F172AL,
    val strokeWidthFraction: Float = 0.10f,
    val shadowEnabled: Boolean = true,
    val shadowColorArgb: Long = 0xCC000000L,
    val backgroundBoxEnabled: Boolean = false,
    val backgroundBoxColorArgb: Long = 0xB30F172AL,
    val uppercase: Boolean = false,
    val position: CaptionPosition = CaptionPosition(),
    val customFontPath: String? = null,
    val rainFontEnabled: Boolean = false,
    val transparentBackground: Boolean = false,
) {
    fun withTemplate(newTemplateId: CaptionTemplateId): CaptionStyle {
        val template = CaptionTemplateRegistry.byId(newTemplateId)
        val adjustedPosition = if (
            newTemplateId == CaptionTemplateId.KNOCKOUT_MASK ||
            newTemplateId == CaptionTemplateId.COLD_INVERT ||
            newTemplateId == CaptionTemplateId.CODE_POEM ||
            newTemplateId == CaptionTemplateId.BLACKOUT_KINETIC
        ) {
            position.copy(yFraction = 0.5f)
        } else {
            position
        }
        return copy(
            templateId = newTemplateId,
            backgroundBoxEnabled = template.defaultBackgroundBox,
            position = adjustedPosition,
            rainFontEnabled = if (newTemplateId == CaptionTemplateId.MUSIC_PLAYER) true else rainFontEnabled,
        )
    }

    fun encodeToJson(): String = styleJson.encodeToString(serializer(), this)

    companion object {
        private val styleJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun default(): CaptionStyle = CaptionStyle()

        fun decodeFromJsonOrNull(raw: String?): CaptionStyle? {
            val trimmed = raw?.takeIf { it.isNotBlank() } ?: return null
            return try {
                styleJson.decodeFromString(serializer(), trimmed)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

@Serializable
data class CaptionDocument(
    val projectId: String,
    val rawWords: List<WordTiming>,
    val lines: List<CaptionLine>,
    val presetId: String = LyricsPresetRegistry.SPEECH_ID,
    val globalOffsetUs: Long = 0L,
    val style: CaptionStyle = CaptionStyle.default(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
