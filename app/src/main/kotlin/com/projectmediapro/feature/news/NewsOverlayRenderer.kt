package com.projectmediapro.feature.news

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import com.projectmediapro.core.model.NewsAnimState
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.NewsOverlayLayout
import com.projectmediapro.core.model.NewsTemplateId
import com.projectmediapro.core.model.NewsThemeVariant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure Android [Canvas] broadcast renderer powering all 6 Duel-vetted animated news & creator
 * templates for BOTH live Compose preview and Media3 Transformer export.
 *
 * Designed for zero per-frame object allocations on the export GL thread via [ThreadLocal] scratch.
 */
object NewsOverlayRenderer {

    private class RenderScratch {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val multiplyMode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        val rect = RectF()
        val rect2 = RectF()
        val boldSans: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val normalSans: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val boldSerif: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    private val scratchLocal = object : ThreadLocal<RenderScratch>() {
        override fun initialValue(): RenderScratch = RenderScratch()
    }

    fun renderAll(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        overlays: List<NewsOverlayItem>,
        timeMs: Long,
    ) {
        if (overlays.isEmpty() || widthPx <= 1f || heightPx <= 1f) return
        val scratch = scratchLocal.get() ?: RenderScratch()
        for (item in overlays) {
            val anim = NewsOverlayLayout.evaluateAnimation(item, timeMs)
            if (!anim.visible) continue
            renderItem(canvas, scratch, widthPx, heightPx, item, anim)
        }
    }

    private fun renderItem(
        canvas: Canvas,
        scratch: RenderScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
    ) {
        when (item.templateId) {
            NewsTemplateId.BREAKING_DESK_16_9 -> drawBreakingDesk(canvas, scratch, w, h, item, anim)
            NewsTemplateId.BREAKING_SHORTS_9_16 -> drawBreakingShortsAlert(canvas, scratch, w, h, item, anim)
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> drawEditorialQuoteCard(canvas, scratch, w, h, item, anim)
            NewsTemplateId.SPEAKER_LOWER_THIRD -> drawSpeakerLowerThird(canvas, scratch, w, h, item, anim)
            NewsTemplateId.STAT_PROOF_CARD -> drawStatProofCard(canvas, scratch, w, h, item, anim)
            NewsTemplateId.DATA_TICKER_DESK -> drawDataTickerDesk(canvas, scratch, w, h, item, anim)
        }
    }

    private fun drawBreakingDesk(
        canvas: Canvas,
        s: RenderScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
    ) {
        val isPortrait = h > w
        // EBU R 095 5% title-safe on 16:9; Shorts safe-zone on 9:16
        val left = if (isPortrait) w * 0.04f else w * 0.05f
        val right = if (isPortrait) w * 0.82f else w * 0.95f
        val band = NewsOverlayLayout.bandForTemplate(item.templateId, isPortrait)
        val bandTop = h * band.topFraction
        val bandBottom = h * band.bottomFraction
        val bandH = (bandBottom - bandTop).coerceAtLeast(48f)
        val slideOffsetY = (1f - anim.slideProgress) * bandH * 0.55f

        val top = bandTop + slideOffsetY
        val bottom = bandBottom + slideOffsetY
        val accent = accentColorFor(item.theme)
        val badgeH = bandH * 0.26f
        val headlineH = bandH * 0.44f
        val tickerTop = top + badgeH + headlineH

        // 1. Top BREAKING NEWS pill
        val badgeText = item.badgeLabel.ifBlank { NewsOverlayItem.defaultBadgeFor(item.templateId) }.uppercase()
        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(12f, badgeH * 0.58f)
            textAlign = Paint.Align.LEFT
            color = applyAlpha(Color.WHITE, anim.alpha)
        }
        val badgeW = (s.textPaint.measureText(badgeText) + badgeH * 1.1f).coerceAtMost(right - left)
        s.rect.set(left, top, left + badgeW, top + badgeH)
        s.fillPaint.color = applyAlpha(accent, anim.alpha)
        canvas.drawRoundRect(s.rect, 6f, 6f, s.fillPaint)
        drawVerticallyCenteredText(canvas, badgeText, left + badgeH * 0.55f, top + badgeH * 0.5f, s.textPaint)

        // 2. Main headline bar
        s.rect.set(left, top + badgeH, right, tickerTop)
        s.fillPaint.color = applyAlpha(0xF0111622.toInt(), anim.alpha)
        canvas.drawRect(s.rect, s.fillPaint)
        // Left accent stripe
        s.fillPaint.color = applyAlpha(accent, anim.alpha)
        canvas.drawRect(left, top + badgeH, left + max(6f, w * 0.006f), tickerTop, s.fillPaint)

        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(16f, headlineH * 0.50f)
            color = applyAlpha(Color.WHITE, anim.alpha)
        }
        val headlineText = ellipsize(item.headline.ifBlank { "BREAKING HEADLINE" }, s.textPaint, (right - left) - 36f)
        drawVerticallyCenteredText(canvas, headlineText, left + 20f, top + badgeH + headlineH * 0.5f, s.textPaint)

        // 3. Scrolling headline ticker (75 px/s scaled to 1080p)
        val tickerH = (bottom - tickerTop).coerceAtLeast(18f)
        s.rect.set(left, tickerTop, right, bottom)
        s.fillPaint.color = applyAlpha(0xF5F4F6F8.toInt(), anim.alpha)
        canvas.drawRect(s.rect, s.fillPaint)

        val tickerContent = item.tickerText.ifBlank { item.subline.ifBlank { item.headline } }
        if (tickerContent.isNotBlank()) {
            s.textPaint.apply {
                typeface = s.boldSans
                textSize = max(12f, tickerH * 0.56f)
                color = applyAlpha(0xFF111827.toInt(), anim.alpha)
            }
            val unit = "$tickerContent   •   "
            val unitW = s.textPaint.measureText(unit).coerceAtLeast(1f)
            val speedPxPerSec = 75f * (w / 1920f).coerceIn(0.45f, 2f)
            val scrollX = (anim.elapsedSec * speedPxPerSec) % unitW

            canvas.save()
            canvas.clipRect(left + 8f, tickerTop, right - 8f, bottom)
            var drawX = left + 8f - scrollX
            val centerY = tickerTop + tickerH * 0.5f
            while (drawX < right) {
                drawVerticallyCenteredText(canvas, unit, drawX, centerY, s.textPaint)
                drawX += unitW
            }
            canvas.restore()
        }
    }

    private fun drawBreakingShortsAlert(
        canvas: Canvas,
        s: RenderScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
    ) {
        val isPortrait = h > w
        // YouTube Shorts safe zone: 4% left, 18% right, 15% top
        val left = if (isPortrait) w * 0.04f else w * 0.08f
        val right = if (isPortrait) w * 0.82f else w * 0.62f
        val band = NewsOverlayLayout.bandForTemplate(item.templateId, isPortrait)
        val top = h * band.topFraction - (1f - anim.slideProgress) * 32f
        val bottom = h * band.bottomFraction - (1f - anim.slideProgress) * 32f
        val cardH = (bottom - top).coerceAtLeast(64f)
        val accent = accentColorFor(item.theme)

        // Card container
        s.rect.set(left, top, right, bottom)
        s.fillPaint.color = applyAlpha(0xF00F131D.toInt(), anim.alpha)
        canvas.drawRoundRect(s.rect, 18f, 18f, s.fillPaint)
        s.strokePaint.strokeWidth = max(2f, w * 0.003f)
        s.strokePaint.color = applyAlpha(accent, anim.alpha)
        canvas.drawRoundRect(s.rect, 18f, 18f, s.strokePaint)

        // Top badge + pulsing LIVE dot
        val pad = max(14f, cardH * 0.12f)
        val pillH = cardH * 0.24f
        val badgeText = item.badgeLabel.ifBlank { NewsOverlayItem.defaultBadgeFor(item.templateId) }.uppercase()
        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(11f, pillH * 0.58f)
            textAlign = Paint.Align.LEFT
            color = applyAlpha(Color.WHITE, anim.alpha)
        }
        val pulse = 0.55f + 0.45f * abs(sin(anim.elapsedSec * 4.5f))
        val dotR = max(4f, pillH * 0.22f)
        val pillW = (s.textPaint.measureText(badgeText) + dotR * 4f + pad * 1.4f).coerceAtMost(right - left - pad * 2f)
        s.rect2.set(left + pad, top + pad, left + pad + pillW, top + pad + pillH)
        s.fillPaint.color = applyAlpha(accent, anim.alpha)
        canvas.drawRoundRect(s.rect2, pillH * 0.45f, pillH * 0.45f, s.fillPaint)

        s.fillPaint.color = applyAlpha(Color.WHITE, anim.alpha * pulse)
        canvas.drawCircle(left + pad + dotR * 2.2f, top + pad + pillH * 0.5f, dotR, s.fillPaint)
        drawVerticallyCenteredText(canvas, badgeText, left + pad + dotR * 4.2f, top + pad + pillH * 0.5f, s.textPaint)

        // Bold headline
        val headlineY = top + pad + pillH + cardH * 0.24f
        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(16f, cardH * 0.18f)
            color = applyAlpha(Color.WHITE, anim.alpha)
        }
        val headline = ellipsize(item.headline.ifBlank { "URGENT UPDATE" }, s.textPaint, (right - left) - pad * 2f)
        drawVerticallyCenteredText(canvas, headline, left + pad, headlineY, s.textPaint)

        if (item.subline.isNotBlank()) {
            s.textPaint.apply {
                typeface = s.normalSans
                textSize = max(12f, cardH * 0.13f)
                color = applyAlpha(0xFFCBD5E1.toInt(), anim.alpha)
            }
            val sub = ellipsize(item.subline, s.textPaint, (right - left) - pad * 2f)
            drawVerticallyCenteredText(canvas, sub, left + pad, headlineY + cardH * 0.22f, s.textPaint)
        }
    }

    private fun drawEditorialQuoteCard(
        canvas: Canvas,
        s: RenderScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
    ) {
        val isPortrait = h > w
        val isCream = item.theme == NewsThemeVariant.CREAM_PAPER
        val left = if (isPortrait) w * 0.06f else w * 0.14f
        val right = if (isPortrait) w * 0.80f else w * 0.86f
        val band = NewsOverlayLayout.bandForTemplate(item.templateId, isPortrait)
        val top = h * band.topFraction + (1f - anim.slideProgress) * 24f
        val bottom = h * band.bottomFraction + (1f - anim.slideProgress) * 24f
        val cardH = (bottom - top).coerceAtLeast(80f)
        val pad = max(18f, cardH * 0.14f)

        val bgColor = if (isCream) 0xF5F6F1E7.toInt() else 0xF012141C.toInt()
        val inkColor = if (isCream) 0xFF1A1A1A.toInt() else 0xFFF8FAFC.toInt()
        val subColor = if (isCream) 0xFF52525B.toInt() else 0xFF94A3B8.toInt()
        val highlightColor = if (isCream) 0xFFFFE55C.toInt() else 0xFF1D4ED8.toInt()

        // Card background
        s.rect.set(left, top, right, bottom)
        s.fillPaint.color = applyAlpha(bgColor, anim.alpha)
        canvas.drawRoundRect(s.rect, 14f, 14f, s.fillPaint)

        // Prepare quote headline text
        s.textPaint.apply {
            typeface = s.boldSerif
            textSize = max(18f, cardH * 0.19f)
            textAlign = Paint.Align.LEFT
            color = applyAlpha(inkColor, anim.alpha)
        }
        val quoteText = ellipsize("“${item.headline.ifBlank { "Key editorial quote" }}”", s.textPaint, (right - left) - pad * 2f)
        val quoteW = s.textPaint.measureText(quoteText)
        val quoteY = top + cardH * 0.44f
        val fm = s.textPaint.fontMetrics
        val hlTop = quoteY + fm.ascent - 4f
        val hlBottom = quoteY + fm.descent + 4f
        val sweepRight = left + pad + quoteW * anim.highlightProgress

        if (!isCream) {
            // Dark Noir: draw under-text SRC_OVER highlight bar BEFORE text for crisp contrast
            s.highlightPaint.xfermode = null
            s.highlightPaint.color = applyAlpha(highlightColor, anim.alpha * 0.85f)
            s.rect2.set(left + pad - 6f, hlTop, sweepRight + 6f, hlBottom)
            canvas.drawRoundRect(s.rect2, 6f, 6f, s.highlightPaint)
            canvas.drawText(quoteText, left + pad, quoteY, s.textPaint)
        } else {
            // Cream Paper: draw text first, then MULTIPLY highlighter sweep over text
            canvas.drawText(quoteText, left + pad, quoteY, s.textPaint)
            s.highlightPaint.xfermode = s.multiplyMode
            s.highlightPaint.color = applyAlpha(highlightColor, anim.alpha)
            s.rect2.set(left + pad - 4f, hlTop, sweepRight + 4f, hlBottom)
            canvas.drawRoundRect(s.rect2, 4f, 4f, s.highlightPaint)
            s.highlightPaint.xfermode = null
        }

        // Attribution line
        val attribution = item.subline.ifBlank { item.badgeLabel }
        if (attribution.isNotBlank()) {
            s.textPaint.apply {
                typeface = s.boldSans
                textSize = max(12f, cardH * 0.12f)
                color = applyAlpha(subColor, anim.alpha)
            }
            val attrText = ellipsize("— $attribution", s.textPaint, (right - left) - pad * 2f)
            canvas.drawText(attrText, left + pad, bottom - pad * 0.9f, s.textPaint)
        }
    }

    private fun drawSpeakerLowerThird(
        canvas: Canvas,
        s: RenderScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
    ) {
        val isPortrait = h > w
        val left = if (isPortrait) w * 0.04f else w * 0.05f
        val maxW = if (isPortrait) w * 0.76f else w * 0.46f
        val band = NewsOverlayLayout.bandForTemplate(item.templateId, isPortrait)
        val top = h * band.topFraction
        val bottom = h * band.bottomFraction
        val bandH = (bottom - top).coerceAtLeast(52f)
        val slideX = -(1f - anim.slideProgress) * maxW * 0.35f
        val accent = accentColorFor(item.theme)

        val x0 = left + slideX
        val barW = max(8f, w * 0.007f)
        val nameTierH = bandH * 0.56f

        // Tier 1: Name plate
        s.rect.set(x0, top, x0 + maxW, top + nameTierH)
        s.fillPaint.color = applyAlpha(0xF2101828.toInt(), anim.alpha)
        canvas.drawRect(s.rect, s.fillPaint)

        // Tier 2: Role/Title plate
        s.rect.set(x0, top + nameTierH, x0 + maxW * 0.88f, bottom)
        s.fillPaint.color = applyAlpha(0xEB1E293B.toInt(), anim.alpha)
        canvas.drawRect(s.rect, s.fillPaint)

        // Vertical accent bar across both tiers
        s.fillPaint.color = applyAlpha(accent, anim.alpha)
        canvas.drawRect(x0, top, x0 + barW, bottom, s.fillPaint)

        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(16f, nameTierH * 0.52f)
            textAlign = Paint.Align.LEFT
            color = applyAlpha(Color.WHITE, anim.alpha)
        }
        val nameText = ellipsize(item.headline.ifBlank { "SPEAKER NAME" }, s.textPaint, maxW - barW - 24f)
        drawVerticallyCenteredText(canvas, nameText, x0 + barW + 14f, top + nameTierH * 0.5f, s.textPaint)

        s.textPaint.apply {
            typeface = s.normalSans
            textSize = max(12f, (bandH - nameTierH) * 0.52f)
            color = applyAlpha(0xFFE2E8F0.toInt(), anim.alpha)
        }
        val roleText = ellipsize(item.subline.ifBlank { item.badgeLabel }, s.textPaint, maxW * 0.88f - barW - 24f)
        drawVerticallyCenteredText(canvas, roleText, x0 + barW + 14f, top + nameTierH + (bandH - nameTierH) * 0.5f, s.textPaint)
    }

    private fun drawStatProofCard(
        canvas: Canvas,
        s: RenderScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
    ) {
        val isPortrait = h > w
        val left = if (isPortrait) w * 0.06f else w * 0.24f
        val right = if (isPortrait) w * 0.80f else w * 0.76f
        val band = NewsOverlayLayout.bandForTemplate(item.templateId, isPortrait)
        val top = h * band.topFraction
        val bottom = h * band.bottomFraction
        val cardH = (bottom - top).coerceAtLeast(80f)
        val scale = 0.88f + 0.12f * anim.slideProgress
        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f
        val accent = accentColorFor(item.theme)

        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        s.rect.set(left, top, right, bottom)
        s.fillPaint.color = applyAlpha(0xF20D1117.toInt(), anim.alpha)
        canvas.drawRoundRect(s.rect, 20f, 20f, s.fillPaint)
        s.strokePaint.strokeWidth = max(2.5f, w * 0.003f)
        s.strokePaint.color = applyAlpha(accent, anim.alpha)
        canvas.drawRoundRect(s.rect, 20f, 20f, s.strokePaint)

        // Hero Stat number
        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(28f, cardH * 0.38f)
            textAlign = Paint.Align.CENTER
            color = applyAlpha(accent, anim.alpha)
        }
        val statText = ellipsize(item.headline.ifBlank { "+42%" }, s.textPaint, (right - left) - 32f)
        drawVerticallyCenteredText(canvas, statText, cx, top + cardH * 0.36f, s.textPaint)

        // Label
        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(14f, cardH * 0.15f)
            color = applyAlpha(Color.WHITE, anim.alpha)
        }
        val labelText = ellipsize(item.subline.ifBlank { "KEY METRIC" }, s.textPaint, (right - left) - 32f)
        drawVerticallyCenteredText(canvas, labelText, cx, top + cardH * 0.66f, s.textPaint)

        // Source citation badge
        val source = item.badgeLabel.ifBlank { NewsOverlayItem.defaultBadgeFor(item.templateId) }
        s.textPaint.apply {
            typeface = s.normalSans
            textSize = max(11f, cardH * 0.10f)
            color = applyAlpha(0xFF94A3B8.toInt(), anim.alpha)
        }
        val sourceText = ellipsize(source.uppercase(), s.textPaint, (right - left) - 32f)
        drawVerticallyCenteredText(canvas, sourceText, cx, bottom - cardH * 0.13f, s.textPaint)
        canvas.restore()
    }

    private fun drawDataTickerDesk(
        canvas: Canvas,
        s: RenderScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
    ) {
        val isPortrait = h > w
        val left = if (isPortrait) w * 0.04f else w * 0.05f
        val right = if (isPortrait) w * 0.82f else w * 0.95f
        val band = NewsOverlayLayout.bandForTemplate(item.templateId, isPortrait)
        val bandTop = h * band.topFraction
        val bandBottom = h * band.bottomFraction
        val bandH = (bandBottom - bandTop).coerceAtLeast(36f)
        val slideY = (1f - anim.slideProgress) * bandH
        val top = bandTop + slideY
        val bottom = bandBottom + slideY
        val accent = accentColorFor(item.theme)

        // Full dark ticker strip
        s.rect.set(left, top, right, bottom)
        s.fillPaint.color = applyAlpha(0xF20B1220.toInt(), anim.alpha)
        canvas.drawRoundRect(s.rect, 8f, 8f, s.fillPaint)

        // Left category pill
        val pillLabel = item.badgeLabel.ifBlank { NewsOverlayItem.defaultBadgeFor(item.templateId) }.uppercase()
        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(12f, bandH * 0.44f)
            textAlign = Paint.Align.LEFT
            color = applyAlpha(Color.WHITE, anim.alpha)
        }
        val pillW = (s.textPaint.measureText(pillLabel) + 28f).coerceAtMost((right - left) * 0.35f)
        s.rect2.set(left, top, left + pillW, bottom)
        s.fillPaint.color = applyAlpha(accent, anim.alpha)
        canvas.drawRoundRect(s.rect2, 8f, 8f, s.fillPaint)
        drawVerticallyCenteredText(canvas, pillLabel, left + 14f, top + bandH * 0.5f, s.textPaint)

        // Scrolling data/market chips (95 px/s scaled to 1080p)
        val rawItems = item.tickerText.ifBlank { item.headline.ifBlank { "NIFTY +1.4% | BTC $98K | GOLD +0.8%" } }
        val unit = rawItems.replace("|", "   •   ") + "   •   "
        s.textPaint.apply {
            typeface = s.boldSans
            textSize = max(13f, bandH * 0.46f)
            color = applyAlpha(0xFFF8FAFC.toInt(), anim.alpha)
        }
        val unitW = s.textPaint.measureText(unit).coerceAtLeast(1f)
        val speedPxPerSec = 95f * (w / 1920f).coerceIn(0.45f, 2f)
        val scrollX = (anim.elapsedSec * speedPxPerSec) % unitW

        val clipLeft = left + pillW + 10f
        if (clipLeft < right - 8f) {
            canvas.save()
            canvas.clipRect(clipLeft, top, right - 8f, bottom)
            var drawX = clipLeft - scrollX
            val centerY = top + bandH * 0.5f
            while (drawX < right) {
                drawVerticallyCenteredText(canvas, unit, drawX, centerY, s.textPaint)
                drawX += unitW
            }
            canvas.restore()
        }
    }

    private fun accentColorFor(theme: NewsThemeVariant): Int = when (theme) {
        NewsThemeVariant.BROADCAST_RED -> 0xFFDC2626.toInt()
        NewsThemeVariant.CREAM_PAPER -> 0xFFD97706.toInt()
        NewsThemeVariant.DARK_NOIR -> 0xFF38BDF8.toInt()
        NewsThemeVariant.SLATE_BLUE -> 0xFF2563EB.toInt()
    }

    private fun applyAlpha(argb: Int, alpha: Float): Int {
        val baseA = Color.alpha(argb)
        val outA = (baseA * alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return Color.argb(outA, Color.red(argb), Color.green(argb), Color.blue(argb))
    }

    private fun drawVerticallyCenteredText(
        canvas: Canvas,
        text: String,
        x: Float,
        centerY: Float,
        paint: Paint,
    ) {
        val fm = paint.fontMetrics
        canvas.drawText(text, x, centerY - (fm.ascent + fm.descent) * 0.5f, paint)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val avail = (maxWidth - paint.measureText(ellipsis)).coerceAtLeast(0f)
        var lo = 1
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (paint.measureText(text, 0, mid) <= avail) lo = mid else hi = mid - 1
        }
        return text.substring(0, lo) + ellipsis
    }
}
