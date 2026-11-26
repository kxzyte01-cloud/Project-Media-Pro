package com.projectmediapro.core.model

import kotlinx.serialization.Serializable

/**
 * Six Duel-vetted animated broadcast & universal creator overlay templates.
 */
@Serializable
enum class NewsTemplateId {
    /** #1 Breaking News Desk (16:9 EBU R 095 title-safe, red banner + 75 px/s ticker). */
    BREAKING_DESK_16_9,

    /** #2 Breaking News Shorts Alert (9:16 YouTube Shorts safe zone + live pulse pill). */
    BREAKING_SHORTS_9_16,

    /** #3 Editorial Quote & Highlight Card (MULTIPLY sweep on Cream Paper, under-text SRC_OVER on Dark Noir). */
    EDITORIAL_QUOTE_CARD,

    /** #4 2-Tier Speaker Lower-Third (Name + Role/Title with vertical accent bar). */
    SPEAKER_LOWER_THIRD,

    /** #5 3-Second Fact / Stat Proof Card (High-impact hero stat + label + source badge). */
    STAT_PROOF_CARD,

    /** #6 Manual Data / Market Ticker Desk (Bottom ticker desk scrolling 95 px/s + category pill). */
    DATA_TICKER_DESK,
}

@Serializable
enum class NewsThemeVariant {
    BROADCAST_RED,
    CREAM_PAPER,
    DARK_NOIR,
    SLATE_BLUE,
}

/**
 * A timed, animated news/creator broadcast overlay stored on [Project.newsOverlays].
 * Backward-compatible with schemaVersion = 1 (defaults to emptyList() on older projects).
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
) {
    val endTimeMs: Long
        get() = startTimeMs.coerceAtLeast(0L) + durationMs.coerceAtLeast(MIN_DURATION_MS)

    companion object {
        const val MIN_DURATION_MS: Long = 1_000L
        const val MAX_DURATION_MS: Long = 60_000L

        fun defaultThemeFor(templateId: NewsTemplateId): NewsThemeVariant = when (templateId) {
            NewsTemplateId.BREAKING_DESK_16_9 -> NewsThemeVariant.BROADCAST_RED
            NewsTemplateId.BREAKING_SHORTS_9_16 -> NewsThemeVariant.BROADCAST_RED
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> NewsThemeVariant.CREAM_PAPER
            NewsTemplateId.SPEAKER_LOWER_THIRD -> NewsThemeVariant.SLATE_BLUE
            NewsTemplateId.STAT_PROOF_CARD -> NewsThemeVariant.DARK_NOIR
            NewsTemplateId.DATA_TICKER_DESK -> NewsThemeVariant.DARK_NOIR
        }

        fun defaultDurationMsFor(templateId: NewsTemplateId): Long = when (templateId) {
            NewsTemplateId.STAT_PROOF_CARD -> 3_000L
            NewsTemplateId.BREAKING_DESK_16_9 -> 6_000L
            NewsTemplateId.DATA_TICKER_DESK -> 7_000L
            else -> 5_000L
        }

        fun defaultBadgeFor(templateId: NewsTemplateId): String = when (templateId) {
            NewsTemplateId.BREAKING_DESK_16_9 -> "BREAKING NEWS"
            NewsTemplateId.BREAKING_SHORTS_9_16 -> "BREAKING"
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> "EDITORIAL"
            NewsTemplateId.SPEAKER_LOWER_THIRD -> "LIVE"
            NewsTemplateId.STAT_PROOF_CARD -> "FACT CHECK"
            NewsTemplateId.DATA_TICKER_DESK -> "MARKETS"
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
    val isStaticHold: Boolean,
)

/**
 * Pure-JVM layout, animation easing, and caption-safe band solver shared between live preview,
 * `NewsOverlayRenderer`, and `CaptionCanvasRenderer`.
 */
object NewsOverlayLayout {

    private const val ENTER_EXIT_MS: Long = 400L
    private const val HIGHLIGHT_SWEEP_MS: Long = 900L

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
                isStaticHold = true,
            )
        }
        val elapsedMs = (timeMs - start).coerceAtLeast(0L)
        val remainingMs = (end - timeMs).coerceAtLeast(0L)
        val enterExit = minOf(ENTER_EXIT_MS, duration / 3L).coerceAtLeast(1L)

        val enterT = (elapsedMs.toFloat() / enterExit.toFloat()).coerceIn(0f, 1f)
        val exitT = (remainingMs.toFloat() / enterExit.toFloat()).coerceIn(0f, 1f)
        val easedEnter = easeOutCubic(enterT)
        val easedExit = easeOutCubic(exitT)
        val alpha = minOf(easedEnter, easedExit)
        val slideProgress = minOf(easedEnter, easedExit)

        val highlightProgress = when (item.templateId) {
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> {
                val sweepStart = enterExit / 2L
                val sweepT = ((elapsedMs - sweepStart).toFloat() / HIGHLIGHT_SWEEP_MS.toFloat()).coerceIn(0f, 1f)
                easeOutCubic(sweepT)
            }
            else -> 1f
        }

        val hasContinuousMotion = item.templateId == NewsTemplateId.BREAKING_DESK_16_9 ||
            item.templateId == NewsTemplateId.DATA_TICKER_DESK ||
            item.templateId == NewsTemplateId.BREAKING_SHORTS_9_16
        val inHoldWindow = elapsedMs >= enterExit && remainingMs >= enterExit && highlightProgress >= 1f
        val isStaticHold = !hasContinuousMotion && inHoldWindow

        return NewsAnimState(
            visible = alpha > 0.005f,
            alpha = alpha,
            slideProgress = slideProgress,
            highlightProgress = highlightProgress,
            elapsedSec = elapsedMs / 1_000f,
            isStaticHold = isStaticHold,
        )
    }

    /**
     * Vertical screen band occupied by [templateId] respecting EBU R 095 (16:9) and YouTube Shorts (9:16) safe zones.
     */
    fun bandForTemplate(templateId: NewsTemplateId, isPortrait: Boolean): OccupiedBand = when (templateId) {
        NewsTemplateId.BREAKING_DESK_16_9 -> if (isPortrait) OccupiedBand(0.54f, 0.65f) else OccupiedBand(0.67f, 0.95f)
        NewsTemplateId.BREAKING_SHORTS_9_16 -> if (isPortrait) OccupiedBand(0.15f, 0.34f) else OccupiedBand(0.06f, 0.28f)
        NewsTemplateId.EDITORIAL_QUOTE_CARD -> if (isPortrait) OccupiedBand(0.28f, 0.56f) else OccupiedBand(0.22f, 0.62f)
        NewsTemplateId.SPEAKER_LOWER_THIRD -> if (isPortrait) OccupiedBand(0.50f, 0.64f) else OccupiedBand(0.72f, 0.93f)
        NewsTemplateId.STAT_PROOF_CARD -> if (isPortrait) OccupiedBand(0.24f, 0.48f) else OccupiedBand(0.20f, 0.54f)
        NewsTemplateId.DATA_TICKER_DESK -> if (isPortrait) OccupiedBand(0.57f, 0.65f) else OccupiedBand(0.84f, 0.95f)
    }

    fun occupiedBandsAt(
        overlays: List<NewsOverlayItem>,
        timeMs: Long,
        isPortrait: Boolean,
    ): List<OccupiedBand> {
        if (overlays.isEmpty()) return emptyList()
        return buildList {
            for (item in overlays) {
                if (evaluateAnimation(item, timeMs).visible) {
                    add(bandForTemplate(item.templateId, isPortrait))
                }
            }
        }
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
