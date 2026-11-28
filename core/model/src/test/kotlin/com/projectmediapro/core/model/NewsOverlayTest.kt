package com.projectmediapro.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsOverlayTest {

    @Test
    fun evaluateAnimation_entersHoldsAndExitsCleanly() {
        val lowerThird = NewsOverlayItem(
            id = "n1",
            templateId = NewsTemplateId.SPEAKER_LOWER_THIRD,
            headline = "Alex Rivera",
            subline = "Senior Correspondent",
            startTimeMs = 1_000L,
            durationMs = 5_000L,
        )
        assertFalse(NewsOverlayLayout.evaluateAnimation(lowerThird, 500L).visible)
        val entering = NewsOverlayLayout.evaluateAnimation(lowerThird, 1_150L)
        assertTrue(entering.visible)
        assertTrue(entering.alpha in 0.1f..0.99f)
        assertFalse(entering.isStaticHold)
        val holding = NewsOverlayLayout.evaluateAnimation(lowerThird, 3_500L)
        assertTrue(holding.visible)
        assertEquals(1f, holding.alpha, 0.01f)
        assertTrue(holding.isStaticHold)
        assertFalse(NewsOverlayLayout.evaluateAnimation(lowerThird, 6_500L).visible)
    }

    @Test
    fun evaluateAnimation_continuousMotionTickerIsNotStaticHold() {
        val breaking = NewsOverlayItem(
            id = "n2",
            templateId = NewsTemplateId.BREAKING_DESK_16_9,
            headline = "Markets Rally",
            tickerText = "NIFTY +1.2% | S&P 500 +0.8%",
            startTimeMs = 0L,
            durationMs = 6_000L,
        )
        val mid = NewsOverlayLayout.evaluateAnimation(breaking, 3_000L)
        assertTrue(mid.visible)
        assertFalse(mid.isStaticHold)
    }

    @Test
    fun captionSafeYFraction_shiftsAboveLowerThirdBand() {
        val bands = listOf(NewsOverlayLayout.bandForTemplate(NewsTemplateId.BREAKING_DESK_16_9, isPortrait = false))
        val safeY = NewsOverlayLayout.resolveCaptionSafeYFraction(
            requestedYFraction = 0.84f,
            occupiedBands = bands,
        )
        assertTrue("Expected caption Y ($safeY) shifted above lower third (0.67)", safeY < 0.62f)
    }

    @Test
    fun captionSafeYFraction_avoidsAllBandsInDenseMultiBandLayout() {
        val bands = listOf(OccupiedBand(0.30f, 0.40f), OccupiedBand(0.45f, 0.55f))
        val safeY = NewsOverlayLayout.resolveCaptionSafeYFraction(
            requestedYFraction = 0.50f,
            occupiedBands = bands,
        )
        assertEquals(0.205f, safeY, 0.002f)
        assertTrue(safeY + 0.07f <= 0.30f - 0.025f + 0.001f)
    }

    @Test
    fun tickerAutoSuppressesInPortraitWhenCaptionsActive() {
        val ticker = NewsOverlayItem(
            id = "t1",
            templateId = NewsTemplateId.DATA_TICKER_DESK,
            headline = "NIFTY +1.4%",
            startTimeMs = 0L,
            durationMs = 6_000L,
        )
        assertTrue(NewsOverlayLayout.shouldSuppressOverlay(ticker, isPortrait = true, captionsActive = true))
        assertFalse(NewsOverlayLayout.shouldSuppressOverlay(ticker, isPortrait = false, captionsActive = true))
        assertFalse(NewsOverlayLayout.shouldSuppressOverlay(ticker, isPortrait = true, captionsActive = false))
    }

    @Test
    fun liveBugQuantizedFrameBucketUpdatesOnDiscreteTicks() {
        val bug = NewsOverlayItem(
            id = "bug1",
            templateId = NewsTemplateId.LIVE_BUG_CLOCK,
            headline = "LIVE",
            startTimeMs = 0L,
            durationMs = 10_000L,
            content = NewsContentConfig(showLiveDot = false, clockMode = NewsClockMode.CUSTOM_START),
        )
        val animA = NewsOverlayLayout.evaluateAnimation(bug, 2_100L)
        val animB = NewsOverlayLayout.evaluateAnimation(bug, 2_800L)
        val animC = NewsOverlayLayout.evaluateAnimation(bug, 3_100L)
        val bucketA = NewsOverlayLayout.quantizedFrameBucket(bug, 2_100L, animA)
        val bucketB = NewsOverlayLayout.quantizedFrameBucket(bug, 2_800L, animB)
        val bucketC = NewsOverlayLayout.quantizedFrameBucket(bug, 3_100L, animC)
        assertEquals(bucketA, bucketB)
        assertTrue(bucketC > bucketB)
    }

    @Test
    fun editorialHighlighterHoldWaitsForLastStaggeredLine() {
        val card = NewsOverlayItem(
            id = "e1",
            templateId = NewsTemplateId.EDITORIAL_QUOTE_CARD,
            headline = "Every second counts when a story breaks live on air.",
            startTimeMs = 0L,
            durationMs = 6_000L,
            motion = NewsMotionConfig(lineStaggerMs = 600L),
        )
        val mid = NewsOverlayLayout.evaluateAnimation(card, 2_000L)
        assertTrue(mid.visible)
        assertTrue(mid.highlightProgress < 1f)
        assertFalse(mid.isStaticHold)
        val done = NewsOverlayLayout.evaluateAnimation(card, 2_600L)
        assertEquals(1f, done.highlightProgress, 0.001f)
        assertTrue(done.isStaticHold)
    }

    @Test
    fun bandForItemHonorsPortraitOverrideOnLandscapeStage() {
        val item = NewsOverlayItem(
            id = "b1",
            templateId = NewsTemplateId.BREAKING_DESK_16_9,
            headline = "Portrait layout on a 16:9 stage",
            content = NewsContentConfig(aspectModePortrait = true),
        )
        val band = NewsOverlayLayout.bandForItem(item, isPortrait = false)
        val expected = NewsOverlayLayout.bandForTemplate(NewsTemplateId.BREAKING_DESK_16_9, isPortrait = true)
        assertEquals(expected.topFraction, band.topFraction, 0f)
        assertEquals(expected.bottomFraction, band.bottomFraction, 0f)
    }

    @Test
    fun otsBandGrowsWithScaleSoCaptionsNeverCollide() {
        val item = NewsOverlayItem(
            id = "ots1",
            templateId = NewsTemplateId.OTS_TOPIC_CARD,
            headline = "SPECIAL REPORT",
            motion = NewsMotionConfig(scale = 1.4f),
        )
        val band = NewsOverlayLayout.bandForItem(item, isPortrait = false)
        assertTrue("OTS band bottom (${band.bottomFraction}) must cover the scaled card", band.bottomFraction > 0.60f)
    }

    @Test
    fun stingerActiveHelperTracksVisibilityWindow() {
        val stinger = NewsOverlayItem(
            id = "s1",
            templateId = NewsTemplateId.STINGER_WIPE,
            headline = "BREAKING NEWS",
            startTimeMs = 1_000L,
            durationMs = 1_000L,
        )
        val overlays = listOf(stinger)
        assertFalse(NewsOverlayLayout.isStingerActiveAt(overlays, 500L))
        assertTrue(NewsOverlayLayout.isStingerActiveAt(overlays, 1_500L))
        assertFalse(NewsOverlayLayout.isStingerActiveAt(overlays, 2_500L))
    }

    @Test
    fun brandPresetThemesOverlayAndLegacyV1JsonDeserializesCleanly() {
        val preset = NewsBrandPreset.BUILT_IN_PRESETS.first { it.id == "builtin_global_cobalt" }
        val baseItem = NewsOverlayItem(
            id = "ots1",
            templateId = NewsTemplateId.OTS_TOPIC_CARD,
            headline = "SPECIAL REPORT",
        ).withBrandPreset(preset)
        assertEquals(preset.primaryColorArgb, baseItem.style.primaryColorArgb)
        assertTrue(baseItem.style.useCustomColors)
        val legacyJson = """{"id":"old1","templateId":"BREAKING_DESK_16_9","headline":"Legacy Headline"}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<NewsOverlayItem>(legacyJson)
        assertEquals("Legacy Headline", decoded.headline)
        assertNotNull(decoded.content)
        assertNotNull(decoded.style)
        assertNotNull(decoded.motion)
        assertEquals(-1, decoded.content.systemClockStartMinutes)
    }

    @Test
    fun timelineCrud_addsUpdatesAndRemovesNewsOverlays() {
        val base = Project(id = "p1", title = "News Demo")
        val item = NewsOverlayItem(
            id = "n1",
            templateId = NewsTemplateId.STAT_PROOF_CARD,
            headline = "+42%",
            subline = "Growth YoY",
            startTimeMs = 500L,
            durationMs = 3_000L,
        )
        val added = ProjectTimeline.addNewsOverlay(base, item)
        assertEquals(1, added.newsOverlays.size)
        val updated = ProjectTimeline.updateNewsOverlay(added, item.copy(headline = "+58%"))
        assertEquals("+58%", updated.newsOverlays.first().headline)
        val removed = ProjectTimeline.removeNewsOverlay(updated, "n1")
        assertTrue(removed.newsOverlays.isEmpty())
    }

    @Test
    fun applyNewsBrandPresetToAll_updatesAllOverlaysAtomically() {
        val base = Project(
            id = "p2",
            title = "Brand Test",
            newsOverlays = listOf(
                NewsOverlayItem(id = "a", templateId = NewsTemplateId.BREAKING_DESK_16_9, headline = "One"),
                NewsOverlayItem(id = "b", templateId = NewsTemplateId.LIVE_BUG_CLOCK, headline = "LIVE"),
            ),
        )
        val preset = NewsBrandPreset.BUILT_IN_PRESETS.first { it.id == "builtin_dark_noir" }
        val themed = ProjectTimeline.applyNewsBrandPresetToAll(base, preset)
        assertEquals(2, themed.newsOverlays.size)
        assertTrue(themed.newsOverlays.all { it.style.primaryColorArgb == preset.primaryColorArgb && it.style.useCustomColors })
    }
}
