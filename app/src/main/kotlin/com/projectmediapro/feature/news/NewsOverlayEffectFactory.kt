package com.projectmediapro.feature.news

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.NewsOverlayLayout

/**
 * Media3 1.4.1 [BitmapOverlay] for all 10 customizable animated news/creator broadcast overlays.
 *
 * Uses ping-pong double-buffering plus quantized dirty-key caching:
 * - Static hold regions and 1-second clock/pulse ticks (`LIVE_BUG_CLOCK`) return the cached
 *   [Bitmap] instance so Media3 skips redundant OpenGL texture uploads.
 * - Automatically suppresses 9:16 bottom tickers when Whisper captions are active (`captionsActive`).
 * - Deterministically recycles both ping-pong buffers and loaded logo/OTS bitmaps in [release].
 */
@OptIn(UnstableApi::class)
class NewsBitmapOverlay(
    private val overlays: List<NewsOverlayItem>,
    private val captionsActive: Boolean = false,
    context: Context? = null,
) : BitmapOverlay() {

    private val bitmapCache: NewsOverlayBitmapCache? = context?.let { NewsOverlayBitmapCache(it) }
    private var bufferA: Bitmap? = null
    private var canvasA: Canvas? = null
    private var bufferB: Bitmap? = null
    private var canvasB: Canvas? = null
    private var nextIsA: Boolean = true
    private var lastReturnedBitmap: Bitmap? = null
    private var lastCacheKey: String? = null
    private var isPortraitVideo: Boolean = false
    private var overlaySettings: OverlaySettings = OverlaySettings.Builder().build()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        recycleFrames()
        val videoWidth = videoSize.width.coerceAtLeast(1)
        val videoHeight = videoSize.height.coerceAtLeast(1)
        isPortraitVideo = videoHeight > videoWidth
        val longSide = maxOf(videoWidth, videoHeight)
        val downscale = if (longSide > MAX_OVERLAY_LONG_SIDE_PX) {
            MAX_OVERLAY_LONG_SIDE_PX.toFloat() / longSide.toFloat()
        } else {
            1f
        }
        val bitmapWidth = (videoWidth * downscale).toInt().coerceAtLeast(1)
        val bitmapHeight = (videoHeight * downscale).toInt().coerceAtLeast(1)
        overlaySettings = OverlaySettings.Builder()
            .setScale(
                videoWidth.toFloat() / bitmapWidth.toFloat(),
                videoHeight.toFloat() / bitmapHeight.toFloat(),
            )
            .build()
        val first = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        bufferA = first
        canvasA = Canvas(first)
        bufferB = second
        canvasB = Canvas(second)
        lastCacheKey = null
        lastReturnedBitmap = null
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
        val cacheKey = computeCacheKeyOrNull(timeMs)
        val cached = lastReturnedBitmap
        if (cacheKey != null && cacheKey == lastCacheKey && cached != null && !cached.isRecycled) {
            return cached
        }

        val (targetBitmap, targetCanvas) = acquireNextBuffer()
        targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (cacheKey != EMPTY_KEY) {
            NewsOverlayRenderer.renderAll(
                canvas = targetCanvas,
                widthPx = targetBitmap.width.toFloat(),
                heightPx = targetBitmap.height.toFloat(),
                overlays = overlays,
                timeMs = timeMs,
                captionsActive = captionsActive,
                bitmapLoader = bitmapCache?.let { cache -> { uri -> cache.load(uri) } },
            )
        }
        lastCacheKey = cacheKey
        lastReturnedBitmap = targetBitmap
        return targetBitmap
    }

    override fun release() {
        super.release()
        recycleFrames()
        bitmapCache?.clear()
    }

    private fun computeCacheKeyOrNull(timeMs: Long): String? {
        var anyVisible = false
        var keyBuilder: StringBuilder? = null
        for (item in overlays) {
            if (NewsOverlayLayout.shouldSuppressOverlay(item, isPortraitVideo, captionsActive)) continue
            val anim = NewsOverlayLayout.evaluateAnimation(item, timeMs)
            if (!anim.visible) continue
            anyVisible = true
            val bucket = NewsOverlayLayout.quantizedFrameBucket(item, timeMs, anim)
            if (bucket == NewsOverlayLayout.DYNAMIC_FRAME_BUCKET) return null
            if (keyBuilder == null) keyBuilder = StringBuilder()
            keyBuilder.append(item.id).append(':').append(bucket).append('|')
        }
        return if (!anyVisible) EMPTY_KEY else keyBuilder.toString()
    }

    private fun acquireNextBuffer(): Pair<Bitmap, Canvas> {
        val a = bufferA
        val ca = canvasA
        val b = bufferB
        val cb = canvasB
        if (a == null || ca == null || b == null || cb == null) {
            val fallback = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bufferA = fallback
            canvasA = Canvas(fallback)
            return fallback to (canvasA ?: Canvas(fallback))
        }
        val result = if (nextIsA) a to ca else b to cb
        nextIsA = !nextIsA
        return result
    }

    private fun recycleFrames() {
        bufferA?.takeIf { !it.isRecycled }?.recycle()
        bufferB?.takeIf { !it.isRecycled }?.recycle()
        bufferA = null
        canvasA = null
        bufferB = null
        canvasB = null
        lastReturnedBitmap = null
        lastCacheKey = null
    }

    companion object {
        private const val MAX_OVERLAY_LONG_SIDE_PX = 1080
        private const val EMPTY_KEY = "__EMPTY__"
    }
}

object NewsOverlayEffectFactory {
    @OptIn(UnstableApi::class)
    fun createOverlay(
        overlays: List<NewsOverlayItem>,
        captionsActive: Boolean = false,
        context: Context? = null,
    ): NewsBitmapOverlay? =
        if (overlays.isEmpty()) null else NewsBitmapOverlay(overlays, captionsActive, context)
}
