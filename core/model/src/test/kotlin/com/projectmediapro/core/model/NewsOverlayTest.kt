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
    fun brandPresetThemesOverlayAndLegacyV1JsonDeserializesCleanly() {
        val preset = NewsBrandPreset.BUILT_IN_PRESETS.first { it.id == "builtin_global_cobalt" }
        val baseItem = NewsOverlayItem(
            id = "ots1",
            templateId = NewsTemplateId.OTS_TOPIC_CARD,
            headline = "SPECIAL REPORT",
        ).withBrandPreset(preset)
        assertEquals(preset.primaryColorArgb, baseItem.style.primaryColorArgb)
        assertTrue(baseItem.style.useCustomColors)

        // Legacy v1 JSON without content/style/motion fields must deserialize with defaults
        val legacyJson = """{"id":"old1","templateId":"BREAKING_DESK_16_9","headline":"Legacy Headline"}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<NewsOverlayItem>(legacyJson)
        assertEquals("Legacy Headline", decoded.headline)
        assertNotNull(decoded.content)
        assertNotNull(decoded.style)
        assertNotNull(decoded.motion)
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
}
