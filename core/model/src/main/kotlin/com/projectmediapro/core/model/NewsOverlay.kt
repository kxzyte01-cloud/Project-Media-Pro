package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Ten broadcast & editorial creator overlay templates:
 * Top 8 customizable Phase 2 News effects plus unified Speaker Lower-Third, Stat Proof Card,
 * and backward-compatible Shorts Alert.
 */
@Serializable
enum class NewsTemplateId {
    /** #1 Animated Breaking News Lower-Third (sheen sweep, >>> chevrons, dual-tab header, intro pill). */
    BREAKING_DESK_16_9,

    /** Shorts Alert 9:16 (preserved for backward compatibility with existing v1 projects). */
    BREAKING_SHORTS_9_16,

    /** #6 Animated Marker Highlighter Card (Cream Paper MULTIPLY / Dark Noir under-text SRC_OVER). */
    EDITORIAL_QUOTE_CARD,

    /** Unified Template #4: 2-Tier Speaker Lower-Third (Name + Role/Title with vertical accent bar). */
    SPEAKER_LOWER_THIRD,

    /** Unified Template #5: 3-Second Fact / Stat Proof Card (Odometer hero stat + label + source). */
    STAT_PROOF_CARD,

    /** #2 Scrolling News Crawl / Ticker Bar (plain text or structured Up/Down/Neutral market chips). */
    DATA_TICKER_DESK,

    /** #3 LIVE Bug + Location Tag & Timestamp Clock (pulsing dot, logo, city label, HH:MM clock). */
    LIVE_BUG_CLOCK,

    /** #4 Over-the-Shoulder (OTS) Topic Card (upper-corner photo/screenshot + title bar + caption). */
    OTS_TOPIC_CARD,

    /** #5 Breaking News Stinger Wipe Transition (0.6s-1.5s diagonal branded wipe + leading flash bar). */
    STINGER_WIPE,

    /** #7 Hand-Drawn Circle, Underline & Redaction Bar Callout (PathMeasure stroke or ink censor wipe). */
    CALLOUT_ANNOTATOR,

    /** #8 Source Citation & Archive Footage HUD (minimal top-corner pill + optional archive scanlines). */
    SOURCE_ARCHIVE_HUD,
}

@Serializable
enum class NewsThemeVariant {
    BROADCAST_RED,
    CREAM_PAPER,
    DARK_NOIR,
    SLATE_BLUE,
}

@Serializable
enum class NewsCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

@Serializable
enum class NewsMotionStyle {
    SLIDE,
    WIPE,
    POP,
    FADE,
    NONE,
}

@Serializable
enum class NewsCalloutMode {
    CIRCLE,
    UNDERLINE,
    REDACTION_BAR,
}

@Serializable
enum class NewsClockMode {
    OFF,
    SYSTEM_TIME,
    CUSTOM_START,
}

@Serializable
enum class NewsSfxCue {
    NONE,
    NEWSROOM_HIT,
    TRANSITION_WHOOSH,
    MARKER_SCRATCH,
    TELETYPE_TICK,
}

@Serializable
enum class TrendDirection {
    UP,
    DOWN,
    NEUTRAL,
}

@Serializable
data class TickerItem(
    val text: String,
    val delta: String = "",
    val trend: TrendDirection = TrendDirection.NEUTRAL,
)

@Serializable
data class NewsContentConfig(
    val tab1Label: String = "BREAKING",
    val tab2Label: String = "LIVE",
    val locationLabel: String = "NEW YORK DESK",
    val clockMode: NewsClockMode = NewsClockMode.SYSTEM_TIME,
    val customClockStartMinutes: Int = 9 * 60 + 30,
    val imageUri: String = "",
    val logoUri: String = "",
    val tickerItems: List<TickerItem> = emptyList(),
    val tickerSeparator: String = "•",
    val tickerChipMode: Boolean = false,
    val yearTag: String = "2024",
    val calloutMode: NewsCalloutMode = NewsCalloutMode.CIRCLE,
    val showChevrons: Boolean = true,
    val introPillReveal: Boolean = true,
    val showLiveDot: Boolean = true,
    val archiveScanlines: Boolean = false,
    val wipeRightToLeft: Boolean = false,
    val aspectModePortrait: Boolean = false,
)

@Serializable
data class NewsStyleConfig(
    val primaryColorArgb: Long = 0xFFD91E2AL,
    val secondaryColorArgb: Long = 0xFF121620L,
    val textColorArgb: Long = 0xFFFFFFFFL,
    val highlightColorArgb: Long = 0xFFFACC15L,
    val fontScale: Float = 1f,
    val backgroundOpacity: Float = 0.94f,
    val cornerRadiusDp: Float = 8f,
    val roughEdgeIntensity: Float = 0.45f,
    val strokeWidthDp: Float = 6f,
    val useCustomColors: Boolean = false,
)

@Serializable
data class NewsMotionConfig(
    val inOutStyle: NewsMotionStyle = NewsMotionStyle.SLIDE,
    val animationSpeed: Float = 1f,
    val tickerSpeedPxPerSec: Float = 120f,
    val lineStaggerMs: Long = 220L,
    val corner: NewsCorner = NewsCorner.TOP_LEFT,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val scale: Float = 1f,
    val sfxCue: NewsSfxCue = NewsSfxCue.NONE,
    val sfxVolume: Float = 0.75f,
    val safeZoneLock: Boolean = true,
)

@Serializable
data class NewsBrandPreset(
    val id: String,
    val name: String,
    val logoUri: String = "",
    val primaryColorArgb: Long,
    val secondaryColorArgb: Long,
    val textColorArgb: Long,
    val highlightColorArgb: Long = 0xFFFACC15L,
    val fontScale: Float = 1f,
    val cornerRadiusDp: Float = 8f,
    val themeVariant: NewsThemeVariant = NewsThemeVariant.BROADCAST_RED,
    val builtIn: Boolean = false,
) {
    companion object {
        val BUILT_IN_PRESETS: List<NewsBrandPreset> = listOf(
            NewsBrandPreset(
                id = "builtin_broadcast_crimson",
                name = "Broadcast Crimson",
                primaryColorArgb = 0xFFD91E2AL,
                secondaryColorArgb = 0xFF121620L,
                textColorArgb = 0xFFFFFFFFL,
                highlightColorArgb = 0xFFFACC15L,
                fontScale = 1.0f,
                cornerRadiusDp = 6f,
                themeVariant = NewsThemeVariant.BROADCAST_RED,
                builtIn = true,
            ),
            NewsBrandPreset(
                id = "builtin_global_cobalt",
                name = "Global Cobalt",
                primaryColorArgb = 0xFF1D4ED8L,
                secondaryColorArgb = 0xFF0F172AL,
                textColorArgb = 0xFFF8FAFC,
                highlightColorArgb = 0xFF38BDF8L,
                fontScale = 1.0f,
                cornerRadiusDp = 8f,
                themeVariant = NewsThemeVariant.SLATE_BLUE,
                builtIn = true,
            ),
            NewsBrandPreset(
                id = "builtin_editorial_cream",
                name = "Editorial Cream",
                primaryColorArgb = 0xFFC81E2BL,
                secondaryColorArgb = 0xFFF5EFE0L,
                textColorArgb = 0xFF151821L,
                highlightColorArgb = 0xFFFFE548L,
                fontScale = 1.0f,
                cornerRadiusDp = 10f,
                themeVariant = NewsThemeVariant.CREAM_PAPER,
                builtIn = true,
            ),
            NewsBrandPreset(
                id = "builtin_dark_noir",
                name = "Dark Noir",
                primaryColorArgb = 0xFFF59E0BL,
                secondaryColorArgb = 0xFF0D1119L,
                textColorArgb = 0xFFF9FAFB,
                highlightColorArgb = 0xFF22D3EEL,
                fontScale = 1.0f,
                cornerRadiusDp = 8f,
                themeVariant = NewsThemeVariant.DARK_NOIR,
                builtIn = true,
            ),
        )
    }
}

/**
 * A timed, animated news/creator broadcast overlay stored on [Project.newsOverlays].
 * Backward-compatible with schemaVersion = 1 (all new fields carry safe defaults).
 */
@Serializable
data class NewsOverlayItem(
    val id: String,
    val templateId: NewsTemplateId,
    val headline: String,
    val subline: String = "",
    val badgeLabel: String = "",
    val tickerText: String = "",
    val theme: NewsThemeVariant = defaultThemeFor(templateId),
    val startTimeMs: Long = 0L,
    val durationMs: Long = defaultDurationMsFor(templateId),
    val content: NewsContentConfig = NewsContentConfig(),
    val style: NewsStyleConfig = NewsStyleConfig(),
    val motion: NewsMotionConfig = NewsMotionConfig(),
) {
    val endTimeMs: Long
        get() = startTimeMs.coerceAtLeast(0L) + durationMs.coerceAtLeast(MIN_DURATION_MS)

    fun withBrandPreset(preset: NewsBrandPreset): NewsOverlayItem = copy(
        theme = preset.themeVariant,
        content = if (preset.logoUri.isNotBlank()) content.copy(logoUri = preset.logoUri) else content,
        style = style.copy(
            primaryColorArgb = preset.primaryColorArgb,
            secondaryColorArgb = preset.secondaryColorArgb,
            textColorArgb = preset.textColorArgb,
            highlightColorArgb = preset.highlightColorArgb,
            fontScale = preset.fontScale.coerceIn(0.75f, 1.5f),
            cornerRadiusDp = preset.cornerRadiusDp.coerceIn(0f, 24f),
            useCustomColors = true,
        ),
    )

    companion object {
        const val MIN_DURATION_MS: Long = 600L
        const val MAX_DURATION_MS: Long = 60_000L
        const val STINGER_MIN_DURATION_MS: Long = 600L
        const val STINGER_MAX_DURATION_MS: Long = 1_500L

        fun defaultThemeFor(templateId: NewsTemplateId): NewsThemeVariant = when (templateId) {
            NewsTemplateId.BREAKING_DESK_16_9 -> NewsThemeVariant.BROADCAST_RED
            NewsTemplateId.BREAKING_SHORTS_9_16 -> NewsThemeVariant.BROADCAST_RED
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> NewsThemeVariant.CREAM_PAPER
            NewsTemplateId.SPEAKER_LOWER_THIRD -> NewsThemeVariant.SLATE_BLUE
            NewsTemplateId.STAT_PROOF_CARD -> NewsThemeVariant.DARK_NOIR
            NewsTemplateId.DATA_TICKER_DESK -> NewsThemeVariant.DARK_NOIR
            NewsTemplateId.LIVE_BUG_CLOCK -> NewsThemeVariant.BROADCAST_RED
            NewsTemplateId.OTS_TOPIC_CARD -> NewsThemeVariant.SLATE_BLUE
            NewsTemplateId.STINGER_WIPE -> NewsThemeVariant.BROADCAST_RED
            NewsTemplateId.CALLOUT_ANNOTATOR -> NewsThemeVariant.BROADCAST_RED
            NewsTemplateId.SOURCE_ARCHIVE_HUD -> NewsThemeVariant.DARK_NOIR
        }

        fun defaultDurationMsFor(templateId: NewsTemplateId): Long = when (templateId) {
            NewsTemplateId.STINGER_WIPE -> 1_000L
            NewsTemplateId.STAT_PROOF_CARD -> 3_000L
            NewsTemplateId.CALLOUT_ANNOTATOR -> 3_500L
            NewsTemplateId.BREAKING_DESK_16_9 -> 6_000L
            NewsTemplateId.DATA_TICKER_DESK -> 7_000L
            NewsTemplateId.LIVE_BUG_CLOCK -> 10_000L
            else -> 5_000L
        }

        fun defaultBadgeFor(templateId: NewsTemplateId): String = when (templateId) {
            NewsTemplateId.BREAKING_DESK_16_9 -> "BREAKING NEWS"
            NewsTemplateId.BREAKING_SHORTS_9_16 -> "BREAKING"
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> "EDITORIAL"
            NewsTemplateId.SPEAKER_LOWER_THIRD -> "LIVE"
            NewsTemplateId.STAT_PROOF_CARD -> "FACT CHECK"
            NewsTemplateId.DATA_TICKER_DESK -> "MARKETS"
            NewsTemplateId.LIVE_BUG_CLOCK -> "LIVE"
            NewsTemplateId.OTS_TOPIC_CARD -> "SPECIAL REPORT"
            NewsTemplateId.STINGER_WIPE -> "BREAKING NEWS"
            NewsTemplateId.CALLOUT_ANNOTATOR -> "CALLOUT"
            NewsTemplateId.SOURCE_ARCHIVE_HUD -> "FILE FOOTAGE"
        }

        fun defaultSfxFor(templateId: NewsTemplateId): NewsSfxCue = when (templateId) {
            NewsTemplateId.BREAKING_DESK_16_9 -> NewsSfxCue.NEWSROOM_HIT
            NewsTemplateId.STINGER_WIPE -> NewsSfxCue.TRANSITION_WHOOSH
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> NewsSfxCue.MARKER_SCRATCH
            NewsTemplateId.DATA_TICKER_DESK -> NewsSfxCue.TELETYPE_TICK
            else -> NewsSfxCue.NONE
        }
    }
}

/**
 * Normalized vertical screen band (`0f` = top edge, `1f` = bottom edge) occupied by an active
 * broadcast overlay. Shared with `CaptionCanvasRenderer` so captions never collide with lower-thirds
 * or tickers.
 */
data class OccupiedBand(
    val topFraction: Float,
    val bottomFraction: Float,
)

/**
 * Evaluated animation state for one [NewsOverlayItem] at a given timeline timestamp.
 */
data class NewsAnimState(
    val visible: Boolean,
    val alpha: Float,
    val slideProgress: Float,
    val highlightProgress: Float,
    val elapsedSec: Float,
    val normalizedProgress: Float,
    val isStaticHold: Boolean,
)

/**
 * Pure-JVM layout, animation easing, static-frame bucketing, and caption-safe band solver shared
 * between live preview, `NewsOverlayRenderer`, and `CaptionCanvasRenderer`.
 */
object NewsOverlayLayout {

    private const val ENTER_EXIT_MS: Long = 400L
    private const val HIGHLIGHT_SWEEP_MS: Long = 900L
    const val STATIC_HOLD_BUCKET: Int = -1
    const val DYNAMIC_FRAME_BUCKET: Int = -2

    fun evaluateAnimation(item: NewsOverlayItem, timeMs: Long): NewsAnimState {
        val start = item.startTimeMs.coerceAtLeast(0L)
        val duration = item.durationMs.coerceAtLeast(NewsOverlayItem.MIN_DURATION_MS)
        val end = start + duration
        if (timeMs < start || timeMs > end) {
            return NewsAnimState(
                visible = false,
                alpha = 0f,
                slideProgress = 0f,
                highlightProgress = 0f,
                elapsedSec = 0f,
                normalizedProgress = 0f,
                isStaticHold = true,
            )
        }
        val elapsedMs = (timeMs - start).coerceAtLeast(0L)
        val remainingMs = (end - timeMs).coerceAtLeast(0L)
        val speed = item.motion.animationSpeed.coerceIn(0.4f, 2.5f)
        val baseEnterExit = (ENTER_EXIT_MS / speed).toLong().coerceAtLeast(120L)
        val enterExit = minOf(baseEnterExit, duration / 3L).coerceAtLeast(1L)

        val enterT = if (item.motion.inOutStyle == NewsMotionStyle.NONE) {
            1f
        } else {
            (elapsedMs.toFloat() / enterExit.toFloat()).coerceIn(0f, 1f)
        }
        val exitT = if (item.motion.inOutStyle == NewsMotionStyle.NONE) {
            1f
        } else {
            (remainingMs.toFloat() / enterExit.toFloat()).coerceIn(0f, 1f)
        }
        val easedEnter = easeOutCubic(enterT)
        val easedExit = easeOutCubic(exitT)
        val alpha = minOf(easedEnter, easedExit)
        val slideProgress = minOf(easedEnter, easedExit)
        val normalizedProgress = (elapsedMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

        val highlightProgress = when (item.templateId) {
            NewsTemplateId.EDITORIAL_QUOTE_CARD,
            NewsTemplateId.CALLOUT_ANNOTATOR -> {
                val sweepStart = if (item.motion.inOutStyle == NewsMotionStyle.NONE) 0L else enterExit / 3L
                val sweepDur = (HIGHLIGHT_SWEEP_MS / speed).toFloat().coerceAtLeast(250f)
                val sweepT = ((elapsedMs - sweepStart).toFloat() / sweepDur).coerceIn(0f, 1f)
                easeOutCubic(sweepT)
            }
            else -> 1f
        }

        val hasContinuousMotion = when (item.templateId) {
            NewsTemplateId.BREAKING_DESK_16_9,
            NewsTemplateId.DATA_TICKER_DESK,
            NewsTemplateId.BREAKING_SHORTS_9_16,
            NewsTemplateId.STINGER_WIPE -> true
            NewsTemplateId.LIVE_BUG_CLOCK -> item.content.showLiveDot || item.content.clockMode != NewsClockMode.OFF
            NewsTemplateId.STAT_PROOF_CARD -> elapsedMs < 700L
            else -> false
        }
        val inHoldWindow = elapsedMs >= enterExit && remainingMs >= enterExit && highlightProgress >= 1f
        val isStaticHold = !hasContinuousMotion && inHoldWindow

        return NewsAnimState(
            visible = alpha > 0.005f,
            alpha = alpha,
            slideProgress = slideProgress,
            highlightProgress = highlightProgress,
            elapsedSec = elapsedMs / 1_000f,
            normalizedProgress = normalizedProgress,
            isStaticHold = isStaticHold,
        )
    }

    /**
     * Quantized cache bucket for static-region Media3 BitmapOverlay caching:
     * - [STATIC_HOLD_BUCKET] when the overlay is completely static and can reuse its cached bitmap.
     * - Non-negative integer bucket for 1-second clock/pulse step overlays ([NewsTemplateId.LIVE_BUG_CLOCK]).
     * - [DYNAMIC_FRAME_BUCKET] when smooth per-frame animation or scrolling requires re-rendering.
     */
    fun quantizedFrameBucket(item: NewsOverlayItem, timeMs: Long, anim: NewsAnimState): Int {
        if (anim.isStaticHold) return STATIC_HOLD_BUCKET
        if (item.templateId == NewsTemplateId.LIVE_BUG_CLOCK && anim.alpha >= 0.99f) {
            // Re-rasterize only on 500ms pulse step (if dot enabled) or 1000ms clock tick.
            val stepMs = if (item.content.showLiveDot) 500L else 1_000L
            return ((timeMs - item.startTimeMs).coerceAtLeast(0L) / stepMs).toInt()
        }
        return DYNAMIC_FRAME_BUCKET
    }

    /**
     * Safe-Zone Rule: Bottom ticker crawl automatically suppresses in 9:16 portrait whenever
     * Whisper captions are active and safe-zone lock is enabled.
     */
    fun shouldSuppressOverlay(
        item: NewsOverlayItem,
        isPortrait: Boolean,
        captionsActive: Boolean,
    ): Boolean {
        if (!item.motion.safeZoneLock) return false
        return isPortrait && captionsActive && item.templateId == NewsTemplateId.DATA_TICKER_DESK
    }

    /**
     * Vertical screen band occupied by [templateId] respecting EBU R 095 (16:9) and YouTube Shorts (9:16) safe zones.
     */
    fun bandForTemplate(
        templateId: NewsTemplateId,
        isPortrait: Boolean,
        corner: NewsCorner = NewsCorner.TOP_LEFT,
    ): OccupiedBand = when (templateId) {
        NewsTemplateId.BREAKING_DESK_16_9 -> if (isPortrait) OccupiedBand(0.54f, 0.68f) else OccupiedBand(0.67f, 0.95f)
        NewsTemplateId.BREAKING_SHORTS_9_16 -> if (isPortrait) OccupiedBand(0.15f, 0.34f) else OccupiedBand(0.06f, 0.28f)
        NewsTemplateId.EDITORIAL_QUOTE_CARD -> if (isPortrait) OccupiedBand(0.28f, 0.56f) else OccupiedBand(0.22f, 0.62f)
        NewsTemplateId.SPEAKER_LOWER_THIRD -> if (isPortrait) OccupiedBand(0.50f, 0.64f) else OccupiedBand(0.72f, 0.93f)
        NewsTemplateId.STAT_PROOF_CARD -> if (isPortrait) OccupiedBand(0.24f, 0.48f) else OccupiedBand(0.20f, 0.54f)
        NewsTemplateId.DATA_TICKER_DESK -> if (isPortrait) OccupiedBand(0.57f, 0.66f) else OccupiedBand(0.84f, 0.95f)
        NewsTemplateId.LIVE_BUG_CLOCK -> when (corner) {
            NewsCorner.BOTTOM_LEFT, NewsCorner.BOTTOM_RIGHT -> OccupiedBand(0.84f, 0.96f)
            else -> OccupiedBand(0.04f, 0.14f)
        }
        NewsTemplateId.OTS_TOPIC_CARD -> OccupiedBand(0.08f, 0.42f)
        NewsTemplateId.STINGER_WIPE -> OccupiedBand(0.0f, 1.0f)
        NewsTemplateId.CALLOUT_ANNOTATOR -> OccupiedBand(0.0f, 0.0f)
        NewsTemplateId.SOURCE_ARCHIVE_HUD -> when (corner) {
            NewsCorner.BOTTOM_LEFT, NewsCorner.BOTTOM_RIGHT -> OccupiedBand(0.86f, 0.95f)
            else -> OccupiedBand(0.04f, 0.12f)
        }
    }

    fun occupiedBandsAt(
        overlays: List<NewsOverlayItem>,
        timeMs: Long,
        isPortrait: Boolean,
        captionsActive: Boolean = false,
    ): List<OccupiedBand> {
        if (overlays.isEmpty()) return emptyList()
        return buildList {
            for (item in overlays) {
                if (shouldSuppressOverlay(item, isPortrait, captionsActive)) continue
                if (evaluateAnimation(item, timeMs).visible) {
                    val band = bandForTemplate(item.templateId, isPortrait, item.motion.corner)
                    if (band.bottomFraction > band.topFraction) {
                        add(band)
                    }
                }
            }
        }
    }

    /**
     * Convenience single-band union for callers expecting a single [OccupiedBand].
     */
    fun computeOccupiedBand(
        timeMs: Long,
        aspectRatio: Float,
        activeItems: List<NewsOverlayItem>,
        captionsActive: Boolean = false,
    ): OccupiedBand? {
        val bands = occupiedBandsAt(
            overlays = activeItems,
            timeMs = timeMs,
            isPortrait = aspectRatio < 1f,
            captionsActive = captionsActive,
        )
        if (bands.isEmpty()) return null
        val minTop = bands.minOf { it.topFraction }
        val maxBottom = bands.maxOf { it.bottomFraction }
        return OccupiedBand(minTop, maxBottom)
    }

    /**
     * Adjusts a caption's normalized vertical anchor [requestedYFraction] so it does not collide
     * with any active [occupiedBands]. Prefers nudging cleanly above lower-third/ticker bands.
     */
    fun resolveCaptionSafeYFraction(
        requestedYFraction: Float,
        occupiedBands: List<OccupiedBand>,
        captionHalfHeight: Float = 0.07f,
        margin: Float = 0.025f,
    ): Float {
        if (occupiedBands.isEmpty()) return requestedYFraction
        var y = requestedYFraction.coerceIn(0.10f, 0.90f)
        for (band in occupiedBands) {
            // Skip full-frame stinger wipes so captions don't jump erratically during a 1s wipe.
            if (band.topFraction <= 0.01f && band.bottomFraction >= 0.99f) continue
            val captionTop = y - captionHalfHeight
            val captionBottom = y + captionHalfHeight
            val overlaps = captionBottom > (band.topFraction - margin) &&
                captionTop < (band.bottomFraction + margin)
            if (overlaps) {
                val aboveY = band.topFraction - captionHalfHeight - margin
                val belowY = band.bottomFraction + captionHalfHeight + margin
                y = when {
                    aboveY >= 0.12f -> aboveY
                    belowY <= 0.88f -> belowY
                    else -> y
                }
            }
        }
        return y.coerceIn(0.10f, 0.90f)
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }
}
