package com.projectmediapro.feature.news

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
 * Media3 1.4.1 [BitmapOverlay] for animated news/creator broadcast overlays.
 *
 * Uses ping-pong double-buffering plus static-frame dirty-key caching:
 * - When all visible overlays are in their static hold phase (no scrolling ticker or live pulse),
 *   returns the cached [Bitmap] instance so Media3 skips redundant OpenGL texture uploads.
 * - When animating or scrolling, swaps cleanly between two pre-allocated buffers with zero heap churn.
 */
@OptIn(UnstableApi::class)
class NewsBitmapOverlay(
    private val overlays: List<NewsOverlayItem>,
) : BitmapOverlay() {

    private var bufferA: Bitmap? = null
    private var canvasA: Canvas? = null
    private var bufferB: Bitmap? = null
    private var canvasB: Canvas? = null
    private var nextIsA: Boolean = true
    private var lastReturnedBitmap: Bitmap? = null
    private var lastStaticKey: String? = null
    private var overlaySettings: OverlaySettings = OverlaySettings.Builder().build()

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        recycleFrames()
        val videoWidth = videoSize.width.coerceAtLeast(1)
        val videoHeight = videoSize.height.coerceAtLeast(1)
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
        lastStaticKey = null
        lastReturnedBitmap = null
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
        val staticKey = computeStaticKeyOrNull(timeMs)
        val cached = lastReturnedBitmap
        if (staticKey != null && staticKey == lastStaticKey && cached != null && !cached.isRecycled) {
            return cached
        }

        val (targetBitmap, targetCanvas) = acquireNextBuffer()
        targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (staticKey != EMPTY_KEY) {
            NewsOverlayRenderer.renderAll(
                canvas = targetCanvas,
                widthPx = targetBitmap.width.toFloat(),
                heightPx = targetBitmap.height.toFloat(),
                overlays = overlays,
                timeMs = timeMs,
            )
        }
        lastStaticKey = staticKey
        lastReturnedBitmap = targetBitmap
        return targetBitmap
    }

    override fun release() {
        super.release()
        recycleFrames()
    }

    private fun computeStaticKeyOrNull(timeMs: Long): String? {
        var anyVisible = false
        var keyBuilder: StringBuilder? = null
        for (item in overlays) {
            val anim = NewsOverlayLayout.evaluateAnimation(item, timeMs)
            if (!anim.visible) continue
            anyVisible = true
            if (!anim.isStaticHold) return null
            if (keyBuilder == null) keyBuilder = StringBuilder()
            keyBuilder.append(item.id).append('|')
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
        lastStaticKey = null
    }

    companion object {
        private const val MAX_OVERLAY_LONG_SIDE_PX = 1080
        private const val EMPTY_KEY = "__EMPTY__"
    }
}

object NewsOverlayEffectFactory {
    @OptIn(UnstableApi::class)
    fun createOverlay(overlays: List<NewsOverlayItem>): NewsBitmapOverlay? =
        if (overlays.isEmpty()) null else NewsBitmapOverlay(overlays)
}
