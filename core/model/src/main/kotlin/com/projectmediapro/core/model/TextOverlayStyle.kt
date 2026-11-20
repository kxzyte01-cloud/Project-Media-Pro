package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TextFontFamily {
    SANS,
    SERIF,
    MONOSPACE,
    CONDENSED,
}

@Serializable
enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

@Serializable
enum class TextStylePreset {
    CLEAN_LYRIC,
    CINEMA_TITLE,
    KARAOKE_PILL,
    NEON_GLOW,
    BOLD_OUTLINE,
    MINIMAL_CAPTION,
}

/**
 * Resolution-independent styling for [TrackType.TEXT] clips.
 *
 * [textSizeFraction] is a fraction of the canvas height so live preview and 720p/1080p/4K exports
 * stay visually identical. Colors are stored as 32-bit ARGB values in [Long] form (`0xAARRGGBBL`).
 */
@Serializable
data class TextOverlayStyle(
    val fontFamily: TextFontFamily = TextFontFamily.SANS,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val textSizeFraction: Float = DEFAULT_TEXT_SIZE_FRACTION,
    val textColorArgb: Long = COLOR_WHITE,
    val strokeColorArgb: Long = COLOR_BLACK,
    val strokeWidthFraction: Float = DEFAULT_STROKE_WIDTH_FRACTION,
    val backgroundColorArgb: Long = COLOR_BG_DARK_GLASS,
    val hasBackground: Boolean = false,
    val hasShadow: Boolean = true,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val letterSpacingEm: Float = DEFAULT_LETTER_SPACING_EM,
) {
    fun sanitized(): TextOverlayStyle = copy(
        textSizeFraction = if (textSizeFraction.isNaN()) {
            DEFAULT_TEXT_SIZE_FRACTION
        } else {
            textSizeFraction.coerceIn(MIN_TEXT_SIZE_FRACTION, MAX_TEXT_SIZE_FRACTION)
        },
        strokeWidthFraction = if (strokeWidthFraction.isNaN()) {
            DEFAULT_STROKE_WIDTH_FRACTION
        } else {
            strokeWidthFraction.coerceIn(MIN_STROKE_WIDTH_FRACTION, MAX_STROKE_WIDTH_FRACTION)
        },
        letterSpacingEm = if (letterSpacingEm.isNaN()) {
            DEFAULT_LETTER_SPACING_EM
        } else {
            letterSpacingEm.coerceIn(MIN_LETTER_SPACING_EM, MAX_LETTER_SPACING_EM)
        },
    )

    companion object {
        const val DEFAULT_TEXT_SIZE_FRACTION: Float = 0.055f
        const val MIN_TEXT_SIZE_FRACTION: Float = 0.025f
        const val MAX_TEXT_SIZE_FRACTION: Float = 0.14f

        const val DEFAULT_STROKE_WIDTH_FRACTION: Float = 0.06f
        const val MIN_STROKE_WIDTH_FRACTION: Float = 0.0f
        const val MAX_STROKE_WIDTH_FRACTION: Float = 0.22f

        const val DEFAULT_LETTER_SPACING_EM: Float = 0.0f
        const val MIN_LETTER_SPACING_EM: Float = 0.0f
        const val MAX_LETTER_SPACING_EM: Float = 0.25f

        const val COLOR_WHITE: Long = 0xFFFFFFFFL
        const val COLOR_BLACK: Long = 0xFF000000L
        const val COLOR_CREAM: Long = 0xFFFFF5E1L
        const val COLOR_AMBER: Long = 0xFFFBBF24L
        const val COLOR_CORAL: Long = 0xFFF87171L
        const val COLOR_CYAN: Long = 0xFF38BDF8L
        const val COLOR_MINT: Long = 0xFF34D399L
        const val COLOR_VIOLET: Long = 0xFFC084FCL

        const val COLOR_BG_DARK_GLASS: Long = 0xB30B0F19L
        const val COLOR_BG_SOLID_BLACK: Long = 0xFF000000L
        const val COLOR_BG_WARM_TINT: Long = 0xCC78350FL
        const val COLOR_BG_LIGHT_FROST: Long = 0xD9F8FAFCL

        fun preset(preset: TextStylePreset): TextOverlayStyle = when (preset) {
            TextStylePreset.CLEAN_LYRIC -> TextOverlayStyle(
                fontFamily = TextFontFamily.SANS,
                isBold = true,
                isItalic = false,
                textSizeFraction = 0.055f,
                textColorArgb = COLOR_WHITE,
                strokeColorArgb = COLOR_BLACK,
                strokeWidthFraction = 0.05f,
                hasBackground = false,
                hasShadow = true,
                alignment = TextAlignment.CENTER,
                letterSpacingEm = 0.02f,
            )
            TextStylePreset.CINEMA_TITLE -> TextOverlayStyle(
                fontFamily = TextFontFamily.SERIF,
                isBold = false,
                isItalic = true,
                textSizeFraction = 0.068f,
                textColorArgb = COLOR_CREAM,
                strokeColorArgb = COLOR_BLACK,
                strokeWidthFraction = 0.0f,
                hasBackground = false,
                hasShadow = true,
                alignment = TextAlignment.CENTER,
                letterSpacingEm = 0.14f,
            )
            TextStylePreset.KARAOKE_PILL -> TextOverlayStyle(
                fontFamily = TextFontFamily.SANS,
                isBold = true,
                isItalic = false,
                textSizeFraction = 0.052f,
                textColorArgb = COLOR_AMBER,
                strokeColorArgb = COLOR_BLACK,
                strokeWidthFraction = 0.0f,
                backgroundColorArgb = COLOR_BG_DARK_GLASS,
                hasBackground = true,
                hasShadow = false,
                alignment = TextAlignment.CENTER,
                letterSpacingEm = 0.02f,
            )
            TextStylePreset.NEON_GLOW -> TextOverlayStyle(
                fontFamily = TextFontFamily.CONDENSED,
                isBold = true,
                isItalic = false,
                textSizeFraction = 0.064f,
                textColorArgb = COLOR_CYAN,
                strokeColorArgb = 0xFF082F49L,
                strokeWidthFraction = 0.10f,
                hasBackground = false,
                hasShadow = true,
                alignment = TextAlignment.CENTER,
                letterSpacingEm = 0.06f,
            )
            TextStylePreset.BOLD_OUTLINE -> TextOverlayStyle(
                fontFamily = TextFontFamily.CONDENSED,
                isBold = true,
                isItalic = false,
                textSizeFraction = 0.072f,
                textColorArgb = COLOR_WHITE,
                strokeColorArgb = COLOR_BLACK,
                strokeWidthFraction = 0.16f,
                hasBackground = false,
                hasShadow = true,
                alignment = TextAlignment.CENTER,
                letterSpacingEm = 0.04f,
            )
            TextStylePreset.MINIMAL_CAPTION -> TextOverlayStyle(
                fontFamily = TextFontFamily.MONOSPACE,
                isBold = false,
                isItalic = false,
                textSizeFraction = 0.042f,
                textColorArgb = COLOR_BLACK,
                strokeColorArgb = COLOR_WHITE,
                strokeWidthFraction = 0.0f,
                backgroundColorArgb = COLOR_BG_LIGHT_FROST,
                hasBackground = true,
                hasShadow = false,
                alignment = TextAlignment.CENTER,
                letterSpacingEm = 0.04f,
            )
        }
    }
}
