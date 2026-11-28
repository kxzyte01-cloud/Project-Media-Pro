package com.projectmediapro.feature.news

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.projectmediapro.core.model.NewsAnimState
import com.projectmediapro.core.model.NewsCalloutMode
import com.projectmediapro.core.model.NewsClockMode
import com.projectmediapro.core.model.NewsCorner
import com.projectmediapro.core.model.NewsMotionStyle
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.NewsOverlayLayout
import com.projectmediapro.core.model.NewsTemplateId
import com.projectmediapro.core.model.NewsThemeVariant
import com.projectmediapro.core.model.TickerItem
import com.projectmediapro.core.model.TrendDirection
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal class NewsDrawScratch {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val sansBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val sansNormal: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val monoBold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    val rect = RectF()
    val rect2 = RectF()
    val rect3 = RectF()
    val srcRect = Rect()
    val path = Path()
    val segmentPath = Path()
    val pathMeasure = PathMeasure()
    val shaderMatrix = Matrix()
    val multiplyXfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)

    // Cached sheen shader (rebuilt only when primary color or dimensions change)
    var sheenShader: LinearGradient? = null
    var sheenPrimary: Int = 0
    var sheenWidth: Float = -1f
    var sheenTop: Float = -1f
    var sheenBottom: Float = -1f

    // Cached scanline REPEAT shader (replaces ~110 drawRect calls/frame with 1 drawRect)
    var scanlineShader: LinearGradient? = null
    var scanlineStep: Float = -1f
    var scanlineColor: Int = 0

    // Memoized clock string per minute key
    var cachedClockMinuteKey: Long = Long.MIN_VALUE
    var cachedClockString: String = ""
}

internal data class ResolvedNewsPalette(
    val primary: Int,
    val secondary: Int,
    val text: Int,
    val subText: Int,
    val highlight: Int,
    val isLightBg: Boolean,
)

/**
 * Pure Android Canvas procedural drawers for all 10 customizable Phase 2 News effects
 * (Top 8 handpicked effects + Speaker Lower-Third + Stat Proof Card + backward-compatible Shorts Alert).
 *
 * Hardened with Qwen4-Max audit fixes: full-travel stinger wipe, deterministic clock anchor,
 * unified zero-drift chip crawl advance, elapsed-time LIVE dot breath, surrogate-safe ellipsize,
 * bounded tab/location overflow guards, hoisted typefaces/shaders, and strict minSdk 26 safety.
 */
object NewsEffectDrawers {

    private val STAT_NUM_REGEX = Regex("""([+-]?\d+(?:\.\d+)?)""")
    private val WHITESPACE_REGEX = Regex("\\s+")

    internal fun resolvePalette(item: NewsOverlayItem): ResolvedNewsPalette {
        if (item.style.useCustomColors) {
            val sec = item.style.secondaryColorArgb.toInt()
            val lum = (Color.red(sec) * 0.299f + Color.green(sec) * 0.587f + Color.blue(sec) * 0.114f) / 255f
            val txt = item.style.textColorArgb.toInt()
            return ResolvedNewsPalette(
                primary = item.style.primaryColorArgb.toInt(),
                secondary = sec,
                text = txt,
                subText = Color.argb(210, Color.red(txt), Color.green(txt), Color.blue(txt)),
                highlight = item.style.highlightColorArgb.toInt(),
                isLightBg = lum > 0.58f,
            )
        }
        return when (item.theme) {
            NewsThemeVariant.BROADCAST_RED -> ResolvedNewsPalette(
                primary = 0xFFD91E2A.toInt(),
                secondary = 0xFF121620.toInt(),
                text = Color.WHITE,
                subText = 0xFFD6DCE8.toInt(),
                highlight = 0xFFFACC15.toInt(),
                isLightBg = false,
            )
            NewsThemeVariant.CREAM_PAPER -> ResolvedNewsPalette(
                primary = 0xFFC81E2B.toInt(),
                secondary = 0xFFF5EFE0.toInt(),
                text = 0xFF151821.toInt(),
                subText = 0xFF4B5563.toInt(),
                highlight = 0xFFFFE548.toInt(),
                isLightBg = true,
            )
            NewsThemeVariant.DARK_NOIR -> ResolvedNewsPalette(
                primary = 0xFFF59E0B.toInt(),
                secondary = 0xFF0D1119.toInt(),
                text = 0xFFF9FAFB.toInt(),
                subText = 0xFF9CA3AF.toInt(),
                highlight = 0xFF22D3EE.toInt(),
                isLightBg = false,
            )
            NewsThemeVariant.SLATE_BLUE -> ResolvedNewsPalette(
                primary = 0xFF1D4ED8.toInt(),
                secondary = 0xFF0F172A.toInt(),
                text = 0xFFF8FAFC.toInt(),
                subText = 0xFFCBD5E1.toInt(),
                highlight = 0xFF38BDF8.toInt(),
                isLightBg = false,
            )
        }
    }

    internal fun drawOverlay(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        timeMs: Long,
        bitmapLoader: ((String) -> Bitmap?)? = null,
    ) {
        val palette = resolvePalette(item)
        scratch.textPaint.letterSpacing = 0f
        when (item.templateId) {
            NewsTemplateId.BREAKING_DESK_16_9 -> drawBreakingNewsLowerThird(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.BREAKING_SHORTS_9_16 -> drawBreakingShortsAlert(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.DATA_TICKER_DESK -> drawScrollingTickerBar(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.LIVE_BUG_CLOCK -> drawLiveBugClock(canvas, scratch, w, h, item, anim, palette, timeMs, bitmapLoader)
            NewsTemplateId.OTS_TOPIC_CARD -> drawOtsTopicCard(canvas, scratch, w, h, item, anim, palette, bitmapLoader)
            NewsTemplateId.STINGER_WIPE -> drawStingerWipe(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.EDITORIAL_QUOTE_CARD -> drawMarkerHighlighterCard(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.CALLOUT_ANNOTATOR -> drawCalloutAnnotator(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.SOURCE_ARCHIVE_HUD -> drawSourceArchiveHud(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.SPEAKER_LOWER_THIRD -> drawSpeakerLowerThird(canvas, scratch, w, h, item, anim, palette)
            NewsTemplateId.STAT_PROOF_CARD -> drawStatProofCard(canvas, scratch, w, h, item, anim, palette)
        }
        scratch.textPaint.letterSpacing = 0f
    }

    private fun applyMotionTransform(
        canvas: Canvas,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        anchorX: Float,
        anchorY: Float,
        slideDx: Float,
        slideDy: Float,
    ): Float {
        val p = anim.slideProgress.coerceIn(0f, 1f)
        when (item.motion.inOutStyle) {
            NewsMotionStyle.SLIDE -> canvas.translate(slideDx * (1f - p), slideDy * (1f - p))
            NewsMotionStyle.POP -> {
                val s = 0.82f + 0.18f * p
                canvas.scale(s, s, anchorX, anchorY)
            }
            NewsMotionStyle.WIPE, NewsMotionStyle.FADE, NewsMotionStyle.NONE -> Unit
        }
        return anim.alpha.coerceIn(0f, 1f)
    }

    private fun withAlpha(color: Int, alphaFraction: Float): Int {
        val a = (Color.alpha(color) * alphaFraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    // -------------------------------------------------------------------------
    // #1 ANIMATED BREAKING NEWS LOWER-THIRD
    // -------------------------------------------------------------------------
    private fun drawBreakingNewsLowerThird(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val isPortrait = item.content.aspectModePortrait || h > w
        val band = NewsOverlayLayout.bandForTemplate(NewsTemplateId.BREAKING_DESK_16_9, isPortrait)
        val marginX = w * (if (isPortrait) 0.045f else 0.055f)
        val cardLeft = marginX
        val cardRight = w - marginX
        val fullWidth = (cardRight - cardLeft).coerceAtLeast(80f)
        val cardTop = h * band.topFraction
        val cardBottom = h * band.bottomFraction
        val cardH = (cardBottom - cardTop).coerceAtLeast(48f)
        val radius = (item.style.cornerRadiusDp * (min(w, h) / 400f)).coerceIn(0f, cardH * 0.35f)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.25f, 1f)

        // Optional 1.5s intro pill-to-bar reveal (easeOutQuint expansion)
        val revealFactor = if (item.content.introPillReveal) {
            val t = (anim.elapsedSec / 1.5f).coerceIn(0f, 1f)
            if (t < 0.30f) 0.36f else 0.36f + 0.64f * easeOutQuint((t - 0.30f) / 0.70f)
        } else {
            1f
        }
        val activeRight = cardLeft + fullWidth * revealFactor

        canvas.save()
        val alpha = applyMotionTransform(
            canvas = canvas,
            item = item,
            anim = anim,
            anchorX = cardLeft,
            anchorY = (cardTop + cardBottom) * 0.5f,
            slideDx = -w * 0.12f,
            slideDy = 0f,
        )
        if (item.motion.inOutStyle == NewsMotionStyle.WIPE) {
            canvas.clipRect(cardLeft, cardTop - cardH * 0.5f, cardLeft + fullWidth * anim.slideProgress, cardBottom + 8f)
        }

        // Dual-tab category header above main bar (bounded widths, ellipsized)
        val tab1 = item.content.tab1Label.ifBlank { item.badgeLabel.ifBlank { "BREAKING" } }.uppercase(Locale.US)
        val tab2 = item.content.tab2Label.trim().uppercase(Locale.US)
        val tabH = (cardH * 0.28f).coerceIn(22f, 44f)
        val tabTop = (cardTop - tabH + 2f).coerceAtLeast(h * 0.04f)
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.letterSpacing = 0.08f
        scratch.textPaint.textSize = (tabH * 0.56f * fontScale).coerceIn(11f, tabH * 0.76f)
        val tab1W = (scratch.textPaint.measureText(tab1) + tabH * 0.9f).coerceAtMost(fullWidth * 0.42f)
        val tab2W = if (tab2.isNotEmpty()) {
            (scratch.textPaint.measureText(tab2) + tabH * 0.85f).coerceAtMost(fullWidth * 0.30f)
        } else {
            0f
        }

        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        scratch.rect.set(cardLeft, tabTop, cardLeft + tab1W, cardTop + radius * 0.4f)
        canvas.drawRoundRect(scratch.rect, radius * 0.6f, radius * 0.6f, scratch.fillPaint)

        scratch.textPaint.color = withAlpha(Color.WHITE, alpha)
        scratch.textPaint.textAlign = Paint.Align.LEFT
        val tabBaseline = tabTop + tabH * 0.70f
        canvas.drawText(
            ellipsize(tab1, scratch.textPaint, (tab1W - tabH * 0.75f).coerceAtLeast(16f)),
            cardLeft + tabH * 0.38f,
            tabBaseline,
            scratch.textPaint,
        )

        if (tab2.isNotEmpty()) {
            scratch.fillPaint.color = withAlpha(0xFF0B0E14.toInt(), alpha * bgOpacity)
            scratch.rect.set(cardLeft + tab1W + 4f, tabTop, cardLeft + tab1W + 4f + tab2W, cardTop + radius * 0.4f)
            canvas.drawRoundRect(scratch.rect, radius * 0.6f, radius * 0.6f, scratch.fillPaint)
            scratch.textPaint.color = withAlpha(palette.highlight, alpha)
            canvas.drawText(
                ellipsize(tab2, scratch.textPaint, (tab2W - tabH * 0.70f).coerceAtLeast(16f)),
                cardLeft + tab1W + 4f + tabH * 0.35f,
                tabBaseline,
                scratch.textPaint,
            )
        }
        scratch.textPaint.letterSpacing = 0f

        // Main bar background
        val hasTicker = item.tickerText.isNotBlank()
        val tickerH = if (hasTicker) cardH * 0.27f else 0f
        val bodyBottom = cardBottom - tickerH
        scratch.rect.set(cardLeft, cardTop, activeRight, bodyBottom)
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        // Cached animated red-wave / metallic sheen sweep
        canvas.save()
        scratch.path.reset()
        scratch.path.addRoundRect(scratch.rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(scratch.path)

        val sheenWidth = fullWidth * 0.34f
        if (scratch.sheenShader == null ||
            scratch.sheenPrimary != palette.primary ||
            scratch.sheenWidth != sheenWidth ||
            scratch.sheenTop != cardTop ||
            scratch.sheenBottom != bodyBottom
        ) {
            scratch.sheenPrimary = palette.primary
            scratch.sheenWidth = sheenWidth
            scratch.sheenTop = cardTop
            scratch.sheenBottom = bodyBottom
            scratch.sheenShader = LinearGradient(
                0f,
                cardTop,
                sheenWidth,
                bodyBottom,
                intArrayOf(
                    Color.TRANSPARENT,
                    withAlpha(palette.primary, 0.34f),
                    withAlpha(Color.WHITE, 0.22f),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.42f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val sheenCycle = (anim.elapsedSec * item.motion.animationSpeed.coerceIn(0.4f, 2.5f) * 0.55f) % 1.6f
        val sheenX = cardLeft - sheenWidth + sheenCycle * (fullWidth + sheenWidth * 1.4f)
        scratch.shaderMatrix.reset()
        scratch.shaderMatrix.setTranslate(sheenX, 0f)
        scratch.sheenShader?.setLocalMatrix(scratch.shaderMatrix)
        scratch.fillPaint.shader = scratch.sheenShader
        scratch.fillPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRect(scratch.rect, scratch.fillPaint)
        scratch.fillPaint.shader = null
        scratch.fillPaint.alpha = 255

        // Left accent stripe + slanted pulsing directional chevrons (>>>)
        val accentW = (fullWidth * 0.012f).coerceIn(5f, 14f)
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        canvas.drawRect(cardLeft, cardTop, cardLeft + accentW, bodyBottom, scratch.fillPaint)

        var textStartX = cardLeft + accentW + cardH * 0.22f
        if (item.content.showChevrons && revealFactor > 0.42f) {
            val chevronAreaW = (cardH * 0.55f).coerceIn(22f, 54f)
            val chevronH = (bodyBottom - cardTop) * 0.46f
            val cy = (cardTop + bodyBottom) * 0.5f
            val step = chevronAreaW / 3.4f
            scratch.strokePaint.strokeWidth = (chevronH * 0.14f).coerceIn(2f, 5f)
            for (i in 0..2) {
                val pulse = 0.30f + 0.70f * ((sin((anim.elapsedSec * 5.2f - i * 1.05f).toDouble()) + 1.0) * 0.5).toFloat()
                scratch.strokePaint.color = withAlpha(palette.primary, alpha * pulse)
                val cx = textStartX + i * step
                scratch.path.reset()
                scratch.path.moveTo(cx, cy - chevronH * 0.5f)
                scratch.path.lineTo(cx + step * 0.65f, cy)
                scratch.path.lineTo(cx, cy + chevronH * 0.5f)
                canvas.drawPath(scratch.path, scratch.strokePaint)
            }
            textStartX += chevronAreaW + cardH * 0.12f
        }

        // Wrap headline against FINAL width (cardRight) so it reveals cleanly without re-wrapping
        val bodyH = (bodyBottom - cardTop).coerceAtLeast(32f)
        val finalTextW = (cardRight - textStartX - cardH * 0.22f).coerceAtLeast(40f)
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.letterSpacing = -0.01f
        scratch.textPaint.textSize = (bodyH * (if (item.subline.isNotBlank()) 0.34f else 0.42f) * fontScale)
            .coerceIn(14f, bodyH * 0.56f)
        scratch.textPaint.color = withAlpha(palette.text, alpha)
        val headline = item.headline.ifBlank { "BREAKING NEWS UPDATE" }
        val lines = wrapLines(headline, scratch.textPaint, finalTextW, maxLines = if (item.subline.isBlank()) 2 else 1)
        val fm = scratch.textPaint.fontMetrics
        val lineH = fm.descent - fm.ascent
        var baseline = if (lines.size == 1 && item.subline.isNotBlank()) {
            cardTop + bodyH * 0.46f
        } else {
            val blockH = lineH * lines.size
            cardTop + (bodyH - blockH) * 0.5f - fm.ascent
        }
        for (line in lines) {
            canvas.drawText(line, textStartX, baseline, scratch.textPaint)
            baseline += lineH * 1.02f
        }
        scratch.textPaint.letterSpacing = 0f

        if (item.subline.isNotBlank()) {
            scratch.textPaint.typeface = scratch.sansNormal
            scratch.textPaint.textSize = (bodyH * 0.23f * fontScale).coerceIn(11f, bodyH * 0.34f)
            scratch.textPaint.color = withAlpha(palette.subText, alpha)
            val sub = ellipsize(item.subline, scratch.textPaint, finalTextW)
            canvas.drawText(sub, textStartX, cardTop + bodyH * 0.80f, scratch.textPaint)
        }
        canvas.restore()

        // Smooth fade-in for integrated bottom ticker strip (revealFactor 0.60 -> 0.75)
        val tickerFade = ((revealFactor - 0.60f) / 0.15f).coerceIn(0f, 1f)
        if (hasTicker && tickerFade > 0.01f) {
            val tAlpha = alpha * tickerFade
            scratch.rect2.set(cardLeft, bodyBottom + 2f, activeRight, cardBottom)
            scratch.fillPaint.color = withAlpha(palette.primary, tAlpha * 0.92f)
            canvas.drawRoundRect(scratch.rect2, radius * 0.5f, radius * 0.5f, scratch.fillPaint)
            drawMarqueeText(
                canvas = canvas,
                scratch = scratch,
                bounds = scratch.rect2,
                text = item.tickerText,
                speedPxPerSec = item.motion.tickerSpeedPxPerSec * (w / 1080f).coerceAtLeast(0.35f),
                elapsedSec = anim.elapsedSec,
                textColor = withAlpha(Color.WHITE, tAlpha),
                fontScale = fontScale,
            )
        }
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // #2 SCROLLING NEWS CRAWL / TICKER BAR (TEXT + MARKET CHIP MODE)
    // -------------------------------------------------------------------------
    private fun drawScrollingTickerBar(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val isPortrait = h > w
        val band = NewsOverlayLayout.bandForTemplate(NewsTemplateId.DATA_TICKER_DESK, isPortrait)
        val marginX = w * 0.03f
        val barTop = h * band.topFraction
        val barBottom = h * band.bottomFraction
        val barH = (barBottom - barTop).coerceAtLeast(36f)
        val radius = (item.style.cornerRadiusDp * (min(w, h) / 420f)).coerceIn(0f, barH * 0.45f)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.3f, 1f)

        canvas.save()
        val alpha = applyMotionTransform(
            canvas = canvas,
            item = item,
            anim = anim,
            anchorX = w * 0.5f,
            anchorY = barBottom,
            slideDx = 0f,
            slideDy = barH * 1.4f,
        )

        scratch.rect.set(marginX, barTop, w - marginX, barBottom)
        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        // Category pill on left
        val pillLabel = item.badgeLabel.ifBlank { NewsOverlayItem.defaultBadgeFor(item.templateId) }.uppercase(Locale.US)
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.letterSpacing = 0.08f
        scratch.textPaint.textSize = (barH * 0.42f * fontScale).coerceIn(11f, barH * 0.56f)
        val pillW = (scratch.textPaint.measureText(pillLabel) + barH * 0.7f).coerceAtMost(w * 0.34f)
        scratch.rect2.set(marginX, barTop, marginX + pillW, barBottom)
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        canvas.drawRoundRect(scratch.rect2, radius, radius, scratch.fillPaint)

        scratch.textPaint.color = withAlpha(Color.WHITE, alpha)
        scratch.textPaint.textAlign = Paint.Align.CENTER
        val fm = scratch.textPaint.fontMetrics
        val baseline = barTop + (barH - (fm.descent - fm.ascent)) * 0.5f - fm.ascent
        canvas.drawText(
            ellipsize(pillLabel, scratch.textPaint, (pillW - 12f).coerceAtLeast(16f)),
            marginX + pillW * 0.5f,
            baseline,
            scratch.textPaint,
        )
        scratch.textPaint.textAlign = Paint.Align.LEFT
        scratch.textPaint.letterSpacing = 0f

        scratch.rect3.set(marginX + pillW + 8f, barTop, w - marginX - 8f, barBottom)
        val chips = resolveTickerItems(item)
        val sep = item.content.tickerSeparator.ifBlank { "•" }
        val speed = item.motion.tickerSpeedPxPerSec.coerceIn(30f, 320f) * (w / 1080f).coerceAtLeast(0.35f)

        canvas.save()
        canvas.clipRect(scratch.rect3)
        if (item.content.tickerChipMode && chips.isNotEmpty()) {
            drawMarketChipCrawl(canvas, scratch, scratch.rect3, chips, sep, speed, anim.elapsedSec, alpha, palette, fontScale)
        } else {
            val joined = if (chips.isNotEmpty()) {
                chips.joinToString("  $sep  ") { if (it.delta.isNotBlank()) "${it.text} ${it.delta}" else it.text }
            } else {
                item.tickerText.ifBlank { item.headline.ifBlank { "BREAKING UPDATES • STAY TUNED" } }
                    .replace("|", "  $sep  ")
            }
            drawMarqueeText(
                canvas = canvas,
                scratch = scratch,
                bounds = scratch.rect3,
                text = joined,
                speedPxPerSec = speed,
                elapsedSec = anim.elapsedSec,
                textColor = withAlpha(palette.text, alpha),
                fontScale = fontScale,
            )
        }
        canvas.restore()
        canvas.restore()
    }

    private fun resolveTickerItems(item: NewsOverlayItem): List<TickerItem> {
        if (item.content.tickerItems.isNotEmpty()) return item.content.tickerItems
        val raw = item.tickerText.ifBlank { item.headline }
        if (raw.isBlank()) return emptyList()
        return raw.split("|", "•")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { token ->
                val parts = token.split(" ")
                val last = parts.lastOrNull().orEmpty()
                val trend = when {
                    last.startsWith("+") || last.contains("▲") -> TrendDirection.UP
                    last.startsWith("-") || last.contains("▼") -> TrendDirection.DOWN
                    else -> TrendDirection.NEUTRAL
                }
                if (trend != TrendDirection.NEUTRAL && parts.size > 1) {
                    TickerItem(parts.dropLast(1).joinToString(" "), last, trend)
                } else {
                    TickerItem(token, "", TrendDirection.NEUTRAL)
                }
            }
    }

    // Unified single advance table so measured loop width == exact drawn width (fixes P1-1)
    private fun drawMarketChipCrawl(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        bounds: RectF,
        items: List<TickerItem>,
        separator: String,
        speedPxPerSec: Float,
        elapsedSec: Float,
        alpha: Float,
        palette: ResolvedNewsPalette,
        fontScale: Float,
    ) {
        val barH = bounds.height()
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.textSize = (barH * 0.40f * fontScale).coerceIn(11f, barH * 0.52f)
        val fm = scratch.textPaint.fontMetrics
        val baseline = bounds.top + (barH - (fm.descent - fm.ascent)) * 0.5f - fm.ascent
        val chipPadX = barH * 0.34f
        val labelGap = barH * 0.16f
        val chipTailGap = barH * 0.14f
        val sepStr = " $separator "
        val sepW = scratch.textPaint.measureText(sepStr)

        val labelWidths = FloatArray(items.size)
        val deltaTexts = arrayOfNulls<String>(items.size)
        val chipWidths = FloatArray(items.size)
        var sumW = 0f
        for (i in items.indices) {
            val ti = items[i]
            val lw = scratch.textPaint.measureText(ti.text)
            labelWidths[i] = lw
            val dt = formatDeltaText(ti)
            deltaTexts[i] = dt
            val cw = if (dt.isNotEmpty()) scratch.textPaint.measureText(dt) + chipPadX else 0f
            chipWidths[i] = cw
            sumW += lw + labelGap + (if (cw > 0f) cw + chipTailGap else 0f) + sepW
        }
        val loopW = sumW.coerceAtLeast(bounds.width())
        val offset = (elapsedSec * speedPxPerSec) % loopW
        var startX = bounds.left - offset

        while (startX < bounds.right + loopW * 0.25f) {
            var x = startX
            for (i in items.indices) {
                val ti = items[i]
                scratch.textPaint.color = withAlpha(palette.text, alpha)
                canvas.drawText(ti.text, x, baseline, scratch.textPaint)
                x += labelWidths[i] + labelGap

                val dt = deltaTexts[i].orEmpty()
                val cw = chipWidths[i]
                if (cw > 0f) {
                    val chipColor = when (ti.trend) {
                        TrendDirection.UP -> 0xFF16A34A.toInt()
                        TrendDirection.DOWN -> 0xFFDC2626.toInt()
                        TrendDirection.NEUTRAL -> 0xFF475569.toInt()
                    }
                    scratch.fillPaint.color = withAlpha(chipColor, alpha)
                    scratch.rect2.set(x, bounds.top + barH * 0.18f, x + cw, bounds.bottom - barH * 0.18f)
                    canvas.drawRoundRect(scratch.rect2, barH * 0.18f, barH * 0.18f, scratch.fillPaint)
                    scratch.textPaint.color = withAlpha(Color.WHITE, alpha)
                    canvas.drawText(dt, x + chipPadX * 0.5f, baseline, scratch.textPaint)
                    x += cw + chipTailGap
                }

                scratch.textPaint.color = withAlpha(palette.subText, alpha * 0.50f)
                canvas.drawText(sepStr, x, baseline, scratch.textPaint)
                x += sepW
            }
            startX += loopW
        }
    }

    private fun formatDeltaText(ti: TickerItem): String {
        val d = ti.delta.trim()
        if (d.isEmpty()) return ""
        return when (ti.trend) {
            TrendDirection.UP -> if (d.startsWith("▲")) d else "▲ $d"
            TrendDirection.DOWN -> if (d.startsWith("▼")) d else "▼ $d"
            TrendDirection.NEUTRAL -> d
        }
    }

    // -------------------------------------------------------------------------
    // #3 LIVE BUG + LOCATION TAG & TIMESTAMP CLOCK (P0-4, P1-2, P1-3 FIXED)
    // -------------------------------------------------------------------------
    private fun drawLiveBugClock(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
        timeMs: Long,
        bitmapLoader: ((String) -> Bitmap?)?,
    ) {
        val baseUnit = min(w, h)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val pillH = (baseUnit * 0.062f * fontScale).coerceIn(28f, 68f)
        val marginX = w * 0.045f
        val marginY = h * (if (h > w) 0.075f else 0.055f)
        val radius = (item.style.cornerRadiusDp * (baseUnit / 420f)).coerceIn(0f, pillH * 0.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.3f, 1f)

        val badge = item.badgeLabel.ifBlank { "LIVE" }.uppercase(Locale.US)
        val location = item.content.locationLabel.trim().uppercase(Locale.US)
        val clockStr = formatClockString(item, timeMs, scratch)
        val logoBitmap = item.content.logoUri.takeIf { it.isNotBlank() }?.let { bitmapLoader?.invoke(it) }

        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.letterSpacing = 0.08f
        scratch.textPaint.textSize = (pillH * 0.44f).coerceIn(11f, 30f)
        val dotSpace = if (item.content.showLiveDot) pillH * 0.52f else pillH * 0.25f
        val liveSegW = dotSpace + scratch.textPaint.measureText(badge) + pillH * 0.38f
        val logoSegW = if (logoBitmap != null && !logoBitmap.isRecycled) pillH * 1.05f else 0f
        val clockSegW = if (clockStr.isNotEmpty()) scratch.textPaint.measureText(clockStr) + pillH * 0.52f else 0f

        // Cap location into remaining available width so clock NEVER overflows off-screen (P1-3)
        val maxTotalW = w * 0.88f
        val fixedW = logoSegW + liveSegW + clockSegW
        val locNaturalW = if (location.isNotEmpty()) scratch.textPaint.measureText(location) + pillH * 0.52f else 0f
        val locSegW = min(locNaturalW, max(0f, maxTotalW - fixedW))
        val totalW = fixedW + locSegW

        val isRight = item.motion.corner == NewsCorner.TOP_RIGHT || item.motion.corner == NewsCorner.BOTTOM_RIGHT
        val isBottom = item.motion.corner == NewsCorner.BOTTOM_LEFT || item.motion.corner == NewsCorner.BOTTOM_RIGHT
        val left = if (isRight) w - marginX - totalW else marginX
        val top = if (isBottom) h - marginY - pillH else marginY

        canvas.save()
        val alpha = applyMotionTransform(
            canvas = canvas,
            item = item,
            anim = anim,
            anchorX = left + totalW * 0.5f,
            anchorY = top + pillH * 0.5f,
            slideDx = if (isRight) 36f else -36f,
            slideDy = 0f,
        )

        scratch.rect.set(left, top, left + totalW, top + pillH)
        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        var cursorX = left
        if (logoBitmap != null && !logoBitmap.isRecycled) {
            // Fit-center inside logo box instead of square stretch
            val pad = pillH * 0.14f
            val boxW = pillH - pad * 2f
            val boxH = pillH - pad * 2f
            val scale = min(boxW / logoBitmap.width.coerceAtLeast(1), boxH / logoBitmap.height.coerceAtLeast(1))
            val dw = logoBitmap.width * scale
            val dh = logoBitmap.height * scale
            val lx = cursorX + pad + (boxW - dw) * 0.5f
            val ly = top + pad + (boxH - dh) * 0.5f
            scratch.rect2.set(lx, ly, lx + dw, ly + dh)
            scratch.srcRect.set(0, 0, logoBitmap.width, logoBitmap.height)
            scratch.bitmapPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(logoBitmap, scratch.srcRect, scratch.rect2, scratch.bitmapPaint)
            cursorX += logoSegW
        }

        // Primary LIVE segment
        scratch.rect2.set(cursorX, top, cursorX + liveSegW, top + pillH)
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        canvas.drawRoundRect(scratch.rect2, radius, radius, scratch.fillPaint)

        val fm = scratch.textPaint.fontMetrics
        val baseline = top + (pillH - (fm.descent - fm.ascent)) * 0.5f - fm.ascent

        // Breathing pulse derived from ELAPSED time (matches quantizedFrameBucket 500ms step, fixes P1-2)
        if (item.content.showLiveDot) {
            val pulseOn = (((timeMs - item.startTimeMs).coerceAtLeast(0L) / 500L) % 2L) == 0L
            val dotAlpha = if (pulseOn) 1f else 0.55f
            val dotRadius = if (pulseOn) pillH * 0.14f else pillH * 0.115f
            scratch.fillPaint.color = withAlpha(Color.WHITE, alpha * dotAlpha)
            canvas.drawCircle(cursorX + pillH * 0.30f, top + pillH * 0.5f, dotRadius, scratch.fillPaint)
        }
        scratch.textPaint.color = withAlpha(Color.WHITE, alpha)
        canvas.drawText(badge, cursorX + dotSpace, baseline, scratch.textPaint)
        cursorX += liveSegW

        if (locSegW > pillH * 0.52f && location.isNotEmpty()) {
            scratch.textPaint.color = withAlpha(palette.text, alpha)
            val drawnLoc = ellipsize(location, scratch.textPaint, locSegW - pillH * 0.48f)
            canvas.drawText(drawnLoc, cursorX + pillH * 0.24f, baseline, scratch.textPaint)
            cursorX += locSegW
        }

        if (clockStr.isNotEmpty()) {
            scratch.fillPaint.color = withAlpha(palette.text, alpha * 0.22f)
            canvas.drawRect(cursorX, top + pillH * 0.22f, cursorX + 1f, top + pillH * 0.78f, scratch.fillPaint)
            scratch.textPaint.typeface = scratch.monoBold
            scratch.textPaint.letterSpacing = 0f
            scratch.textPaint.color = withAlpha(palette.highlight, alpha)
            canvas.drawText(clockStr, cursorX + pillH * 0.22f, baseline, scratch.textPaint)
            scratch.textPaint.typeface = scratch.sansBold
        }
        scratch.textPaint.letterSpacing = 0f
        canvas.restore()
    }

    private fun formatClockString(item: NewsOverlayItem, timeMs: Long, scratch: NewsDrawScratch): String {
        val elapsedMin = ((timeMs - item.startTimeMs).coerceAtLeast(0L) / 60_000L).toInt()
        val totalMin = when (item.content.clockMode) {
            NewsClockMode.OFF -> return ""
            NewsClockMode.CUSTOM_START -> (item.content.customClockStartMinutes + elapsedMin) % (24 * 60)
            NewsClockMode.SYSTEM_TIME -> {
                if (item.content.systemClockStartMinutes >= 0) {
                    (item.content.systemClockStartMinutes + elapsedMin) % (24 * 60)
                } else {
                    val cal = Calendar.getInstance()
                    cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                }
            }
        }
        val key = totalMin.toLong()
        if (scratch.cachedClockMinuteKey != key) {
            scratch.cachedClockMinuteKey = key
            scratch.cachedClockString = String.format(Locale.US, "%02d:%02d", (totalMin / 60) % 24, totalMin % 60)
        }
        return scratch.cachedClockString
    }

    // -------------------------------------------------------------------------
    // #4 OVER-THE-SHOULDER (OTS) TOPIC CARD
    // -------------------------------------------------------------------------
    private fun drawOtsTopicCard(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
        bitmapLoader: ((String) -> Bitmap?)?,
    ) {
        val isPortrait = h > w
        val cardW = (w * (if (isPortrait) 0.42f else 0.27f) * item.motion.scale.coerceIn(0.6f, 1.4f))
            .coerceIn(130f, w * 0.65f)
        val cardH = min(cardW * 0.88f, h * (if (isPortrait) 0.42f else 0.56f))
        val isRight = item.motion.corner == NewsCorner.TOP_RIGHT || item.motion.corner == NewsCorner.BOTTOM_RIGHT
        val marginX = w * 0.045f
        val marginY = h * (if (isPortrait) 0.12f else 0.09f)
        val left = if (isRight) w - marginX - cardW else marginX
        val top = marginY
        val radius = (item.style.cornerRadiusDp * (min(w, h) / 380f)).coerceIn(0f, 28f)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.35f, 1f)

        canvas.save()
        val alpha = applyMotionTransform(
            canvas = canvas,
            item = item,
            anim = anim,
            anchorX = left + cardW * 0.5f,
            anchorY = top + cardH * 0.5f,
            slideDx = if (isRight) cardW * 0.35f else -cardW * 0.35f,
            slideDy = 0f,
        )

        scratch.rect.set(left, top, left + cardW, top + cardH)
        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        canvas.save()
        scratch.path.reset()
        scratch.path.addRoundRect(scratch.rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(scratch.path)

        val headerH = cardH * 0.19f
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        canvas.drawRect(left, top, left + cardW, top + headerH, scratch.fillPaint)
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.letterSpacing = 0.06f
        scratch.textPaint.textSize = (headerH * 0.54f * fontScale).coerceIn(10f, headerH * 0.75f)
        scratch.textPaint.color = withAlpha(Color.WHITE, alpha)
        val headerLabel = item.headline.ifBlank { item.badgeLabel.ifBlank { "SPECIAL REPORT" } }.uppercase(Locale.US)
        canvas.drawText(
            ellipsize(headerLabel, scratch.textPaint, cardW - 18f),
            left + 10f,
            top + headerH * 0.72f,
            scratch.textPaint,
        )
        scratch.textPaint.letterSpacing = 0f

        val footerH = if (item.subline.isNotBlank()) cardH * 0.22f else 0f
        val imgTop = top + headerH
        val imgBottom = top + cardH - footerH
        val imgBitmap = item.content.imageUri.takeIf { it.isNotBlank() }?.let { bitmapLoader?.invoke(it) }
        if (imgBitmap != null && !imgBitmap.isRecycled) {
            drawCenterCropBitmap(canvas, scratch, imgBitmap, left, imgTop, left + cardW, imgBottom, alpha)
        } else {
            scratch.fillPaint.color = withAlpha(palette.primary, alpha * 0.16f)
            canvas.drawRect(left, imgTop, left + cardW, imgBottom, scratch.fillPaint)
            scratch.textPaint.typeface = scratch.sansBold
            scratch.textPaint.textSize = (cardH * 0.10f).coerceIn(11f, 22f)
            scratch.textPaint.color = withAlpha(palette.text, alpha * 0.65f)
            scratch.textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("OTS VISUAL", left + cardW * 0.5f, (imgTop + imgBottom) * 0.53f, scratch.textPaint)
            scratch.textPaint.textAlign = Paint.Align.LEFT
        }

        if (footerH > 0f) {
            scratch.fillPaint.color = withAlpha(palette.secondary, alpha * 0.96f)
            canvas.drawRect(left, imgBottom, left + cardW, top + cardH, scratch.fillPaint)
            scratch.textPaint.typeface = scratch.sansNormal
            scratch.textPaint.textSize = (footerH * 0.44f * fontScale).coerceIn(10f, footerH * 0.65f)
            scratch.textPaint.color = withAlpha(palette.text, alpha)
            canvas.drawText(
                ellipsize(item.subline, scratch.textPaint, cardW - 18f),
                left + 10f,
                imgBottom + footerH * 0.66f,
                scratch.textPaint,
            )
        }
        canvas.restore()

        scratch.strokePaint.strokeWidth = (min(w, h) / 360f).coerceIn(1.5f, 4f)
        scratch.strokePaint.color = withAlpha(palette.primary, alpha)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.strokePaint)
        canvas.restore()
    }

    private fun drawCenterCropBitmap(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        bmp: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Float,
    ) {
        val dstW = (right - left).coerceAtLeast(1f)
        val dstH = (bottom - top).coerceAtLeast(1f)
        val dstAspect = dstW / dstH
        val srcAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
        if (srcAspect > dstAspect) {
            val cropW = (bmp.height * dstAspect).roundToInt().coerceIn(1, bmp.width)
            val x0 = (bmp.width - cropW) / 2
            scratch.srcRect.set(x0, 0, x0 + cropW, bmp.height)
        } else {
            val cropH = (bmp.width / dstAspect).roundToInt().coerceIn(1, bmp.height)
            val y0 = (bmp.height - cropH) / 2
            scratch.srcRect.set(0, y0, bmp.width, y0 + cropH)
        }
        scratch.rect2.set(left, top, right, bottom)
        scratch.bitmapPaint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(bmp, scratch.srcRect, scratch.rect2, scratch.bitmapPaint)
    }

    // -------------------------------------------------------------------------
    // #5 BREAKING NEWS STINGER WIPE TRANSITION (P0-1 FULL TRAVEL FIXED)
    // -------------------------------------------------------------------------
    private fun drawStingerWipe(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val p = anim.normalizedProgress.coerceIn(0f, 1f)
        val alpha = anim.alpha.coerceIn(0f, 1f)
        val bandW = w * 0.92f
        val slant = w * 0.16f
        val flashW = w * 0.022f
        val dir = if (item.content.wipeRightToLeft) -1f else 1f
        val travel = w + bandW + slant * 2f + flashW * 2f
        val leadX = if (dir > 0f) {
            -slant - flashW + p * travel
        } else {
            w + slant + flashW - p * travel
        }
        val trailX = leadX - dir * bandW
        val xMin = min(leadX, trailX)
        val xMax = max(leadX, trailX)

        canvas.save()
        // Main secondary diagonal band
        scratch.path.reset()
        scratch.path.moveTo(xMin, 0f)
        scratch.path.lineTo(xMax, 0f)
        scratch.path.lineTo(xMax - slant, h)
        scratch.path.lineTo(xMin - slant, h)
        scratch.path.close()
        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha)
        canvas.drawPath(scratch.path, scratch.fillPaint)

        // Primary diagonal accent stripe
        val stripeW = w * 0.14f
        val stripeX1 = leadX - dir * stripeW * 1.4f
        val stripeX2 = leadX - dir * stripeW * 0.25f
        scratch.path.reset()
        scratch.path.moveTo(stripeX1, 0f)
        scratch.path.lineTo(stripeX2, 0f)
        scratch.path.lineTo(stripeX2 - slant, h)
        scratch.path.lineTo(stripeX1 - slant, h)
        scratch.path.close()
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        canvas.drawPath(scratch.path, scratch.fillPaint)

        // Trailing soft flash tail
        val tailW = w * 0.06f
        val tail1 = leadX - dir * (flashW + tailW)
        val tail2 = leadX - dir * flashW
        scratch.path.reset()
        scratch.path.moveTo(tail1, 0f)
        scratch.path.lineTo(tail2, 0f)
        scratch.path.lineTo(tail2 - slant, h)
        scratch.path.lineTo(tail1 - slant, h)
        scratch.path.close()
        scratch.fillPaint.color = withAlpha(Color.WHITE, alpha * 0.35f)
        canvas.drawPath(scratch.path, scratch.fillPaint)

        // Leading crisp flash bar
        scratch.path.reset()
        scratch.path.moveTo(leadX - flashW, 0f)
        scratch.path.lineTo(leadX + flashW * 0.4f, 0f)
        scratch.path.lineTo(leadX + flashW * 0.4f - slant, h)
        scratch.path.lineTo(leadX - flashW - slant, h)
        scratch.path.close()
        scratch.fillPaint.color = withAlpha(Color.WHITE, alpha * 0.92f)
        canvas.drawPath(scratch.path, scratch.fillPaint)

        // Centered banner text during middle 60% of wipe
        val textAlpha = (1f - abs(p - 0.5f) * 3.1f).coerceIn(0f, 1f) * alpha
        if (textAlpha > 0.02f) {
            val banner = item.headline.ifBlank { item.badgeLabel.ifBlank { "BREAKING NEWS" } }.uppercase(Locale.US)
            val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
            scratch.textPaint.typeface = scratch.sansBold
            scratch.textPaint.letterSpacing = 0.12f
            scratch.textPaint.textSize = (min(w, h) * 0.075f * fontScale).coerceIn(20f, 82f)
            scratch.textPaint.textAlign = Paint.Align.CENTER
            scratch.textPaint.color = withAlpha(palette.text, textAlpha)
            val fm = scratch.textPaint.fontMetrics
            val baseline = h * 0.5f - (fm.ascent + fm.descent) * 0.5f
            canvas.drawText(ellipsize(banner, scratch.textPaint, w * 0.84f), w * 0.5f, baseline, scratch.textPaint)
            scratch.textPaint.textAlign = Paint.Align.LEFT
            scratch.textPaint.letterSpacing = 0f
        }
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // #6 ANIMATED MARKER HIGHLIGHTER CARD (CREAM PAPER MULTIPLY / DARK NOIR)
    // -------------------------------------------------------------------------
    private fun drawMarkerHighlighterCard(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val isPortrait = h > w
        val band = NewsOverlayLayout.bandForTemplate(NewsTemplateId.EDITORIAL_QUOTE_CARD, isPortrait)
        val marginX = w * (if (isPortrait) 0.07f else 0.14f)
        val cardLeft = marginX
        val cardRight = w - marginX
        val cardTop = h * band.topFraction
        val cardBottom = h * band.bottomFraction
        val cardW = (cardRight - cardLeft).coerceAtLeast(120f)
        val cardH = (cardBottom - cardTop).coerceAtLeast(90f)
        val radius = (item.style.cornerRadiusDp * (min(w, h) / 380f)).coerceIn(0f, 28f)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.35f, 1f)
        val pad = cardW * 0.06f

        canvas.save()
        val alpha = applyMotionTransform(
            canvas = canvas,
            item = item,
            anim = anim,
            anchorX = (cardLeft + cardRight) * 0.5f,
            anchorY = (cardTop + cardBottom) * 0.5f,
            slideDx = 0f,
            slideDy = cardH * 0.16f,
        )

        scratch.rect.set(cardLeft, cardTop, cardRight, cardBottom)
        val layerId = canvas.saveLayer(scratch.rect, null)
        scratch.fillPaint.shader = null
        scratch.fillPaint.xfermode = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        canvas.drawRect(cardLeft, cardTop + radius, cardLeft + 6f, cardBottom - radius, scratch.fillPaint)

        val quote = item.headline.ifBlank { "\"Key documentary quote highlighted line by line.\"" }
        scratch.textPaint.typeface = scratch.serifBold
        var quoteSize = (cardH * 0.155f * fontScale).coerceIn(15f, 44f)
        scratch.textPaint.textSize = quoteSize
        val maxTextW = (cardW - pad * 2f).coerceAtLeast(60f)
        var lines = wrapLines(quote, scratch.textPaint, maxTextW, maxLines = 3)
        var fm = scratch.textPaint.fontMetrics
        var lineH = (fm.descent - fm.ascent) * 1.24f
        var startY = cardTop + pad * 1.1f - fm.ascent

        // Reserve bottom 18% for attribution; shrink once if 3 lines would collide
        val attrCeiling = cardBottom - cardH * 0.18f
        if (lines.isNotEmpty() && startY + (lines.size - 1) * lineH + fm.descent > attrCeiling) {
            quoteSize = (quoteSize * 0.82f).coerceAtLeast(13f)
            scratch.textPaint.textSize = quoteSize
            lines = wrapLines(quote, scratch.textPaint, maxTextW, maxLines = 3)
            fm = scratch.textPaint.fontMetrics
            lineH = (fm.descent - fm.ascent) * 1.20f
            startY = cardTop + pad * 0.95f - fm.ascent
        }

        val staggerSec = (item.motion.lineStaggerMs.coerceIn(0L, 1_000L) / 1_000f)
        val sweepDurSec = (0.75f / item.motion.animationSpeed.coerceIn(0.4f, 2.5f)).coerceAtLeast(0.25f)
        val rough = item.style.roughEdgeIntensity.coerceIn(0f, 1f)

        if (!palette.isLightBg) {
            drawHighlighterPass(canvas, scratch, lines, cardLeft + pad, startY, lineH, fm, anim.elapsedSec, staggerSec, sweepDurSec, rough, palette.highlight, alpha, useMultiply = false)
        }
        scratch.textPaint.color = withAlpha(palette.text, alpha)
        for (idx in lines.indices) {
            canvas.drawText(lines[idx], cardLeft + pad, startY + idx * lineH, scratch.textPaint)
        }
        if (palette.isLightBg) {
            drawHighlighterPass(canvas, scratch, lines, cardLeft + pad, startY, lineH, fm, anim.elapsedSec, staggerSec, sweepDurSec, rough, palette.highlight, alpha, useMultiply = true)
        }

        if (item.subline.isNotBlank() || item.badgeLabel.isNotBlank()) {
            val attr = item.subline.ifBlank { item.badgeLabel }
            scratch.textPaint.typeface = scratch.sansBold
            scratch.textPaint.letterSpacing = 0.06f
            scratch.textPaint.textSize = (cardH * 0.095f * fontScale).coerceIn(11f, 22f)
            scratch.textPaint.color = withAlpha(palette.subText, alpha)
            canvas.drawText(
                ellipsize("— $attr", scratch.textPaint, maxTextW),
                cardLeft + pad,
                cardBottom - pad * 0.55f,
                scratch.textPaint,
            )
            scratch.textPaint.letterSpacing = 0f
        }
        canvas.restoreToCount(layerId)
        canvas.restore()
    }

    private fun drawHighlighterPass(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        lines: List<String>,
        textX: Float,
        startY: Float,
        lineH: Float,
        fm: Paint.FontMetrics,
        elapsedSec: Float,
        staggerSec: Float,
        sweepDurSec: Float,
        rough: Float,
        highlightColor: Int,
        alpha: Float,
        useMultiply: Boolean,
    ) {
        scratch.fillPaint.color = withAlpha(highlightColor, alpha * (if (useMultiply) 0.85f else 0.42f))
        scratch.fillPaint.xfermode = if (useMultiply) scratch.multiplyXfermode else null
        for (idx in lines.indices) {
            val lineT = ((elapsedSec - 0.18f - idx * staggerSec) / sweepDurSec).coerceIn(0f, 1f)
            val eased = easeOutCubic(lineT)
            if (eased <= 0.01f) continue
            val lw = scratch.textPaint.measureText(lines[idx]) * eased
            val baseY = startY + idx * lineH
            val topY = baseY + fm.ascent * 0.86f
            val botY = baseY + fm.descent * 0.92f
            drawRoughHighlightStroke(canvas, scratch, textX - 3f, topY, lw + 6f, botY - topY, rough, idx)
        }
        scratch.fillPaint.xfermode = null
    }

    private fun drawRoughHighlightStroke(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        roughness: Float,
        seed: Int,
    ) {
        if (roughness <= 0.05f) {
            scratch.rect2.set(x, y, x + width, y + height)
            canvas.drawRoundRect(scratch.rect2, 4f, 4f, scratch.fillPaint)
            return
        }
        val amp = height * 0.11f * roughness
        val steps = 10
        scratch.path.reset()
        scratch.path.moveTo(x, y + amp * sin(seed * 1.7).toFloat())
        for (i in 1..steps) {
            val fx = x + width * (i.toFloat() / steps)
            val jitter = sin(i * 2.1 + seed * 3.3).toFloat() * amp
            scratch.path.lineTo(fx, y + jitter)
        }
        for (i in steps downTo 0) {
            val fx = x + width * (i.toFloat() / steps)
            val jitter = cos(i * 1.9 + seed * 2.7).toFloat() * amp
            scratch.path.lineTo(fx, y + height + jitter)
        }
        scratch.path.close()
        canvas.drawPath(scratch.path, scratch.fillPaint)
    }

    // -------------------------------------------------------------------------
    // #7 HAND-DRAWN CIRCLE, UNDERLINE & REDACTION BAR CALLOUT
    // -------------------------------------------------------------------------
    private fun drawCalloutAnnotator(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val cx = w * item.motion.anchorX.coerceIn(0.10f, 0.90f)
        val cy = h * item.motion.anchorY.coerceIn(0.10f, 0.90f)
        val scale = item.motion.scale.coerceIn(0.4f, 2.2f)
        val drawProgress = anim.highlightProgress.coerceIn(0f, 1f)
        val alpha = anim.alpha.coerceIn(0f, 1f)
        val strokeColor = if (item.style.useCustomColors) palette.highlight else palette.primary
        val strokePx = (item.style.strokeWidthDp * (min(w, h) / 360f)).coerceIn(3f, 28f)

        canvas.save()
        when (item.content.calloutMode) {
            NewsCalloutMode.CIRCLE -> {
                val rx = min(w, h) * 0.16f * scale
                val ry = rx * 0.62f
                scratch.path.reset()
                val segs = 40
                for (i in 0..segs) {
                    val theta = -PI * 0.42 + (i.toDouble() / segs) * (2.0 * PI * 1.12)
                    val wobble = 1.0 + 0.045 * sin(theta * 3.0)
                    val px = cx + (rx * cos(theta) * wobble).toFloat()
                    val py = cy + (ry * sin(theta) * wobble).toFloat()
                    if (i == 0) scratch.path.moveTo(px, py) else scratch.path.lineTo(px, py)
                }
                scratch.pathMeasure.setPath(scratch.path, false)
                scratch.segmentPath.reset()
                scratch.pathMeasure.getSegment(0f, scratch.pathMeasure.length * drawProgress, scratch.segmentPath, true)
                scratch.strokePaint.strokeWidth = strokePx
                scratch.strokePaint.color = withAlpha(strokeColor, alpha)
                canvas.drawPath(scratch.segmentPath, scratch.strokePaint)

                // Subtle second pen pass for authentic hand-drawn feel
                canvas.translate(1.5f, 1.5f)
                scratch.strokePaint.strokeWidth = strokePx * 0.6f
                scratch.strokePaint.color = withAlpha(strokeColor, alpha * 0.35f)
                canvas.drawPath(scratch.segmentPath, scratch.strokePaint)
            }
            NewsCalloutMode.UNDERLINE -> {
                val halfW = min(w, h) * 0.24f * scale
                scratch.path.reset()
                scratch.path.moveTo(cx - halfW, cy + 4f)
                scratch.path.quadTo(cx, cy - 8f, cx + halfW, cy + 2f)
                scratch.pathMeasure.setPath(scratch.path, false)
                scratch.segmentPath.reset()
                scratch.pathMeasure.getSegment(0f, scratch.pathMeasure.length * drawProgress, scratch.segmentPath, true)
                scratch.strokePaint.strokeWidth = strokePx
                scratch.strokePaint.color = withAlpha(strokeColor, alpha)
                canvas.drawPath(scratch.segmentPath, scratch.strokePaint)
            }
            NewsCalloutMode.REDACTION_BAR -> {
                val barW = min(w, h) * 0.44f * scale
                val barH = (min(w, h) * 0.075f * scale).coerceIn(22f, 96f)
                val x0 = cx - barW * 0.5f
                val y0 = cy - barH * 0.5f
                scratch.fillPaint.shader = null
                scratch.fillPaint.color = withAlpha(0xFF0A0A0C.toInt(), alpha * 0.96f)
                drawRoughHighlightStroke(canvas, scratch, x0, y0, barW * drawProgress, barH, 0.32f, 7)
            }
        }
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // #8 SOURCE CITATION & ARCHIVE FOOTAGE HUD (1-DRAW-CALL REPEAT SCANLINES)
    // -------------------------------------------------------------------------
    private fun drawSourceArchiveHud(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val alpha = anim.alpha.coerceIn(0f, 1f)
        val baseUnit = min(w, h)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.25f, 1f)

        if (item.content.archiveScanlines) {
            scratch.fillPaint.shader = null
            scratch.fillPaint.color = withAlpha(0xFF14181F.toInt(), alpha * 0.14f)
            canvas.drawRect(0f, 0f, w, h, scratch.fillPaint)

            val step = (h / 110f).coerceIn(3f, 8f)
            val lineColor = withAlpha(Color.BLACK, 0.10f)
            if (scratch.scanlineShader == null || scratch.scanlineStep != step || scratch.scanlineColor != lineColor) {
                scratch.scanlineStep = step
                scratch.scanlineColor = lineColor
                val frac = (1.5f / step).coerceIn(0.1f, 0.5f)
                scratch.scanlineShader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    step,
                    intArrayOf(lineColor, lineColor, Color.TRANSPARENT, Color.TRANSPARENT),
                    floatArrayOf(0f, frac, frac, 1f),
                    Shader.TileMode.REPEAT,
                )
            }
            scratch.fillPaint.shader = scratch.scanlineShader
            scratch.fillPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, scratch.fillPaint)
            scratch.fillPaint.shader = null
            scratch.fillPaint.alpha = 255
        }

        val prefix = item.badgeLabel.ifBlank { "FILE FOOTAGE" }.uppercase(Locale.US)
        val source = item.headline.trim()
        val year = item.content.yearTag.trim()
        val pillText = buildString {
            append(prefix)
            if (source.isNotEmpty()) append(": ").append(source.uppercase(Locale.US))
            if (year.isNotEmpty()) append(" · ").append(year)
        }

        val pillH = (baseUnit * 0.052f * fontScale).coerceIn(24f, 54f)
        scratch.textPaint.typeface = scratch.monoBold
        scratch.textPaint.letterSpacing = 0.06f
        scratch.textPaint.textSize = (pillH * 0.44f).coerceIn(10f, 24f)
        val pillW = (scratch.textPaint.measureText(pillText) + pillH * 0.85f).coerceAtMost(w * 0.86f)
        val marginX = w * 0.045f
        val marginY = h * (if (h > w) 0.075f else 0.05f)
        val isRight = item.motion.corner == NewsCorner.TOP_RIGHT || item.motion.corner == NewsCorner.BOTTOM_RIGHT
        val isBottom = item.motion.corner == NewsCorner.BOTTOM_LEFT || item.motion.corner == NewsCorner.BOTTOM_RIGHT
        val left = if (isRight) w - marginX - pillW else marginX
        val top = if (isBottom) h - marginY - pillH else marginY
        val radius = (item.style.cornerRadiusDp * (baseUnit / 440f)).coerceIn(0f, pillH * 0.5f)

        canvas.save()
        val a = applyMotionTransform(
            canvas,
            item,
            anim,
            left + pillW * 0.5f,
            top + pillH * 0.5f,
            if (isRight) 28f else -28f,
            0f,
        )
        scratch.rect.set(left, top, left + pillW, top + pillH)
        scratch.fillPaint.color = withAlpha(palette.secondary, a * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)
        scratch.strokePaint.strokeWidth = 1.5f
        scratch.strokePaint.color = withAlpha(palette.text, a * 0.25f)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.strokePaint)

        val fm = scratch.textPaint.fontMetrics
        val baseline = top + (pillH - (fm.descent - fm.ascent)) * 0.5f - fm.ascent
        scratch.textPaint.color = withAlpha(palette.text, a)
        canvas.drawText(
            ellipsize(pillText, scratch.textPaint, pillW - pillH * 0.5f),
            left + pillH * 0.42f,
            baseline,
            scratch.textPaint,
        )
        scratch.textPaint.letterSpacing = 0f
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // UNIFIED TEMPLATE #4: 2-TIER SPEAKER LOWER-THIRD
    // -------------------------------------------------------------------------
    private fun drawSpeakerLowerThird(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val isPortrait = h > w
        val band = NewsOverlayLayout.bandForTemplate(NewsTemplateId.SPEAKER_LOWER_THIRD, isPortrait)
        val marginX = w * 0.055f
        val top = h * band.topFraction
        val bottom = h * band.bottomFraction
        val cardH = (bottom - top).coerceAtLeast(52f)
        val cardW = (w * (if (isPortrait) 0.78f else 0.52f)).coerceAtLeast(180f)
        val radius = (item.style.cornerRadiusDp * (min(w, h) / 400f)).coerceIn(0f, cardH * 0.35f)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.3f, 1f)

        canvas.save()
        val alpha = applyMotionTransform(canvas, item, anim, marginX, (top + bottom) * 0.5f, -cardW * 0.22f, 0f)
        scratch.rect.set(marginX, top, marginX + cardW, bottom)
        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        val barW = (cardH * 0.11f).coerceIn(5f, 12f)
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        scratch.rect2.set(marginX, top, marginX + barW, bottom)
        canvas.drawRoundRect(scratch.rect2, radius * 0.5f, radius * 0.5f, scratch.fillPaint)

        val textX = marginX + barW + cardH * 0.22f
        val availW = (cardW - barW - cardH * 0.38f).coerceAtLeast(60f)
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.letterSpacing = 0.01f
        scratch.textPaint.textSize = (cardH * 0.36f * fontScale).coerceIn(14f, cardH * 0.48f)
        scratch.textPaint.color = withAlpha(palette.text, alpha)
        canvas.drawText(ellipsize(item.headline.ifBlank { "SPEAKER NAME" }, scratch.textPaint, availW), textX, top + cardH * 0.46f, scratch.textPaint)

        scratch.textPaint.typeface = scratch.sansNormal
        scratch.textPaint.letterSpacing = 0f
        scratch.textPaint.textSize = (cardH * 0.24f * fontScale).coerceIn(11f, cardH * 0.34f)
        scratch.textPaint.color = withAlpha(palette.subText, alpha)
        canvas.drawText(ellipsize(item.subline.ifBlank { "Correspondent · Studio Desk" }, scratch.textPaint, availW), textX, top + cardH * 0.78f, scratch.textPaint)
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // UNIFIED TEMPLATE #5: 3-SECOND FACT / STAT PROOF CARD (P1-6 FIXED)
    // -------------------------------------------------------------------------
    private fun drawStatProofCard(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val isPortrait = h > w
        val band = NewsOverlayLayout.bandForTemplate(NewsTemplateId.STAT_PROOF_CARD, isPortrait)
        val marginX = w * (if (isPortrait) 0.10f else 0.22f)
        val top = h * band.topFraction
        val bottom = h * band.bottomFraction
        val cardW = (w - marginX * 2f).coerceAtLeast(160f)
        val cardH = (bottom - top).coerceAtLeast(88f)
        val radius = (item.style.cornerRadiusDp * (min(w, h) / 380f)).coerceIn(0f, 28f)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.35f, 1f)

        canvas.save()
        val alpha = applyMotionTransform(canvas, item, anim, w * 0.5f, (top + bottom) * 0.5f, 0f, 24f)
        scratch.rect.set(marginX, top, marginX + cardW, bottom)
        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        scratch.rect2.set(marginX, top, marginX + cardW, top + 6f)
        canvas.drawRoundRect(scratch.rect2, radius, radius, scratch.fillPaint)

        // 650ms easeOutExpo odometer count-up preserving source decimal precision
        val countT = (anim.elapsedSec / 0.65f).coerceIn(0f, 1f)
        val easedCount = if (countT >= 1f) 1f else 1f - 2f.pow(-10f * countT)
        val rawStat = item.headline.ifBlank { "+42%" }
        val animatedStat = animateNumericStat(rawStat, easedCount)

        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.textSize = (cardH * 0.36f * fontScale).coerceIn(22f, cardH * 0.42f)
        scratch.textPaint.textAlign = Paint.Align.CENTER
        val statColor = when {
            rawStat.trim().startsWith("+") -> 0xFF4ADE80.toInt()
            rawStat.trim().startsWith("-") -> 0xFFFB7185.toInt()
            else -> palette.highlight
        }
        scratch.textPaint.color = withAlpha(statColor, alpha)
        canvas.drawText(ellipsize(animatedStat, scratch.textPaint, cardW - 28f), w * 0.5f, top + cardH * 0.46f, scratch.textPaint)

        if (item.subline.isNotBlank()) {
            scratch.textPaint.textSize = (cardH * 0.15f * fontScale).coerceIn(12f, cardH * 0.18f)
            scratch.textPaint.color = withAlpha(palette.text, alpha)
            canvas.drawText(ellipsize(item.subline, scratch.textPaint, cardW - 28f), w * 0.5f, top + cardH * 0.74f, scratch.textPaint)
        }
        val source = item.badgeLabel.ifBlank { NewsOverlayItem.defaultBadgeFor(item.templateId) }
        scratch.textPaint.letterSpacing = 0.08f
        scratch.textPaint.textSize = (cardH * 0.105f * fontScale).coerceIn(10f, cardH * 0.12f)
        scratch.textPaint.color = withAlpha(palette.subText, alpha)
        canvas.drawText(ellipsize(source.uppercase(Locale.US), scratch.textPaint, cardW - 28f), w * 0.5f, top + cardH * 0.92f, scratch.textPaint)
        scratch.textPaint.textAlign = Paint.Align.LEFT
        scratch.textPaint.letterSpacing = 0f
        canvas.restore()
    }

    private fun animateNumericStat(raw: String, progress: Float): String {
        val match = STAT_NUM_REGEX.find(raw) ?: return raw
        val target = match.value.toDoubleOrNull() ?: return raw
        val dotIdx = match.value.indexOf('.')
        val decimals = if (dotIdx >= 0) (match.value.length - dotIdx - 1).coerceIn(1, 4) else 0
        val signPrefix = if (match.value.startsWith("+") && target >= 0.0) "+" else ""
        val current = target * progress
        val formatted = if (decimals > 0) {
            String.format(Locale.US, "%s%.${decimals}f", signPrefix, current)
        } else {
            String.format(Locale.US, "%s%d", signPrefix, current.roundToInt())
        }
        return raw.replaceRange(match.range, formatted)
    }

    // -------------------------------------------------------------------------
    // BACKWARD-COMPATIBLE SHORTS ALERT 9:16
    // -------------------------------------------------------------------------
    private fun drawBreakingShortsAlert(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        w: Float,
        h: Float,
        item: NewsOverlayItem,
        anim: NewsAnimState,
        palette: ResolvedNewsPalette,
    ) {
        val isPortrait = h > w
        val band = NewsOverlayLayout.bandForTemplate(NewsTemplateId.BREAKING_SHORTS_9_16, isPortrait)
        val marginX = w * 0.06f
        val top = h * band.topFraction
        val bottom = h * band.bottomFraction
        val cardW = (w - marginX * 2f).coerceAtLeast(140f)
        val cardH = (bottom - top).coerceAtLeast(64f)
        val radius = (item.style.cornerRadiusDp * (min(w, h) / 400f)).coerceIn(0f, 24f)
        val fontScale = item.style.fontScale.coerceIn(0.75f, 1.5f)
        val bgOpacity = item.style.backgroundOpacity.coerceIn(0.3f, 1f)

        canvas.save()
        val alpha = applyMotionTransform(canvas, item, anim, w * 0.5f, top, 0f, -cardH * 0.4f)
        scratch.rect.set(marginX, top, marginX + cardW, bottom)
        scratch.fillPaint.shader = null
        scratch.fillPaint.color = withAlpha(palette.secondary, alpha * bgOpacity)
        canvas.drawRoundRect(scratch.rect, radius, radius, scratch.fillPaint)

        val pillH = cardH * 0.30f
        scratch.fillPaint.color = withAlpha(palette.primary, alpha)
        scratch.rect2.set(marginX, top, marginX + cardW, top + pillH)
        canvas.drawRoundRect(scratch.rect2, radius, radius, scratch.fillPaint)

        val badge = item.badgeLabel.ifBlank { "BREAKING" }.uppercase(Locale.US)
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.letterSpacing = 0.08f
        scratch.textPaint.textSize = (pillH * 0.56f * fontScale).coerceIn(11f, pillH * 0.74f)
        scratch.textPaint.color = withAlpha(Color.WHITE, alpha)
        canvas.drawText(ellipsize(badge, scratch.textPaint, cardW - 28f), marginX + 14f, top + pillH * 0.72f, scratch.textPaint)
        scratch.textPaint.letterSpacing = 0f

        scratch.textPaint.textSize = (cardH * 0.24f * fontScale).coerceIn(14f, 34f)
        scratch.textPaint.color = withAlpha(palette.text, alpha)
        val lines = wrapLines(item.headline.ifBlank { "BREAKING UPDATE" }, scratch.textPaint, cardW - 28f, maxLines = 2)
        var y = top + pillH + cardH * 0.28f
        for (line in lines) {
            canvas.drawText(line, marginX + 14f, y, scratch.textPaint)
            y += cardH * 0.27f
        }
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // SHARED TEXT & MARQUEE HELPERS (P1-7 EMOJI-SAFE & ELLIPSIZED FLUSH)
    // -------------------------------------------------------------------------
    private fun drawMarqueeText(
        canvas: Canvas,
        scratch: NewsDrawScratch,
        bounds: RectF,
        text: String,
        speedPxPerSec: Float,
        elapsedSec: Float,
        textColor: Int,
        fontScale: Float,
    ) {
        val barH = bounds.height()
        scratch.textPaint.typeface = scratch.sansBold
        scratch.textPaint.textSize = (barH * 0.44f * fontScale).coerceIn(10f, barH * 0.62f)
        scratch.textPaint.color = textColor
        val clean = text.trim().ifEmpty { "BREAKING NEWS" }
        val unit = "$clean   •   "
        val unitW = scratch.textPaint.measureText(unit).coerceAtLeast(bounds.width() * 0.5f)
        val offset = (elapsedSec * speedPxPerSec.coerceIn(30f, 320f)) % unitW
        val fm = scratch.textPaint.fontMetrics
        val baseline = bounds.top + (barH - (fm.descent - fm.ascent)) * 0.5f - fm.ascent

        canvas.save()
        canvas.clipRect(bounds)
        var x = bounds.left - offset
        while (x < bounds.right + unitW) {
            canvas.drawText(unit, x, baseline, scratch.textPaint)
            x += unitW
        }
        canvas.restore()
    }

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val words = text.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = StringBuilder(candidate)
            } else {
                result.add(ellipsize(current.toString(), paint, maxWidth))
                if (result.size >= maxLines) {
                    result[maxLines - 1] = ellipsize("${result[maxLines - 1]} $word", paint, maxWidth)
                    return result
                }
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty() && result.size < maxLines) {
            result.add(ellipsize(current.toString(), paint, maxWidth))
        }
        return result
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (paint.measureText(text.substring(0, mid) + ellipsis) <= maxWidth) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        if (low > 0 && low < text.length && Character.isLowSurrogate(text[low])) {
            low -= 1
        }
        return if (low <= 0) ellipsis else text.substring(0, low) + ellipsis
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }

    private fun easeOutQuint(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - inv * inv * inv * inv * inv
    }
}
