package com.projectmediapro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsOverlayTest {

    @Test
    fun evaluateAnimation_respectsVisibilityAndStaticHold() {
        val lowerThird = NewsOverlayItem(
            id = "n1",
            templateId = NewsTemplateId.SPEAKER_LOWER_THIRD,
            headline = "Aarav Sen",
            subline = "Senior Tech Editor",
            startTimeMs = 1_000L,
            durationMs = 5_000L,
        )
        assertFalse(NewsOverlayLayout.evaluateAnimation(lowerThird, 500L).visible)
        val entering = NewsOverlayLayout.evaluateAnimation(lowerThird, 1_150L)
        assertTrue(entering.visible)
        assertFalse(entering.isStaticHold)

        val holding = NewsOverlayLayout.evaluateAnimation(lowerThird, 3_500L)
        assertTrue(holding.visible)
        assertTrue(holding.isStaticHold)
        assertEquals(1f, holding.alpha, 0.001f)

        assertFalse(NewsOverlayLayout.evaluateAnimation(lowerThird, 6_500L).visible)
    }

    @Test
    fun breakingDesk_isNeverStaticHoldBecauseTickerScrolls() {
        val breaking = NewsOverlayItem(
            id = "n2",
            templateId = NewsTemplateId.BREAKING_DESK_16_9,
            headline = "Markets Rally on AI Chip Surge",
            tickerText = "TECH +2.4%  •  GLOBAL GDP UP 3.1%",
            startTimeMs = 0L,
            durationMs = 6_000L,
        )
        val mid = NewsOverlayLayout.evaluateAnimation(breaking, 3_000L)
        assertTrue(mid.visible)
        assertFalse(mid.isStaticHold)
    }

    @Test
    fun resolveCaptionSafeYFraction_nudgesCaptionsAboveLowerThird() {
        val bands = listOf(NewsOverlayLayout.bandForTemplate(NewsTemplateId.BREAKING_DESK_16_9, isPortrait = false))
        val safeY = NewsOverlayLayout.resolveCaptionSafeYFraction(
            requestedYFraction = 0.80f,
            occupiedBands = bands,
        )
        assertTrue("Expected captions nudged above 0.67 lower-third band, got $safeY", safeY < 0.60f)
    }

    @Test
    fun projectTimeline_newsOverlayCrudWorksCleanly() {
        val base = Project(id = "p1", title = "News")
        val item = NewsOverlayItem(
            id = "n1",
            templateId = NewsTemplateId.STAT_PROOF_CARD,
            headline = "+42%",
            subline = "YoY Export Speedup",
            startTimeMs = 2_000L,
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
