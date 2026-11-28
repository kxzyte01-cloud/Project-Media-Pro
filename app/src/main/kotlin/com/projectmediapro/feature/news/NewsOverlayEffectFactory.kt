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
 * Ping-pong double-buffering + quantized dirty-state caching (zero per-frame allocations, zero non-null assertions).
 */
@OptIn(UnstableApi::class)
class NewsBitmapOverlay(
    private val overlays: List<NewsOverlayItem>,
    private val captionsActive: Boolean = false,
    context: Context? = null,
) : BitmapOverlay() {

    private val bitmapCache: NewsOverlayBitmapCache? = context?.let { NewsOverlayBitmapCache(it) }
    private val loader: ((String) -> Bitmap?)? = bitmapCache?.let { cache -> { uri -> cache.load(uri) } }

    private var bufferA: Bitmap? = null
    private var canvasA: Canvas? = null
    private var bufferB: Bitmap? = null
    private var canvasB: Canvas? = null
    private var nextIsA: Boolean = true
    private var acquiredCanvas: Canvas? = null
    private var lastReturnedBitmap: Bitmap? = null
    private var isPortraitVideo: Boolean = false
    private var overlaySettings: OverlaySettings = OverlaySettings.Builder().build()

    // Dirty-state tracking (fixed-size, zero per-frame allocation).
    private val slotIdxCur = IntArray(overlays.size.coerceAtLeast(1))
    private val slotBucketCur = IntArray(overlays.size.coerceAtLeast(1))
    private val slotIdxLast = IntArray(overlays.size.coerceAtLeast(1))
    private val slotBucketLast = IntArray(overlays.size.coerceAtLeast(1))
    private var lastSlotCount: Int = -1
    private var curAnyVisible: Boolean = false

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
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
        val mustRender = computeDirty(timeMs)
        val cached = lastReturnedBitmap
        if (!mustRender && cached != null && !cached.isRecycled) {
            return cached
        }
        val targetBitmap = acquireNextBuffer()
        val targetCanvas = acquiredCanvas ?: Canvas(targetBitmap)
        targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (curAnyVisible) {
            NewsOverlayRenderer.renderAll(
                canvas = targetCanvas,
                widthPx = targetBitmap.width.toFloat(),
                heightPx = targetBitmap.height.toFloat(),
                overlays = overlays,
                timeMs = timeMs,
                captionsActive = captionsActive,
                bitmapLoader = loader,
            )
        }
        lastReturnedBitmap = targetBitmap
        return targetBitmap
    }

    override fun release() {
        super.release()
        recycleFrames()
        bitmapCache?.clear()
    }

    private fun computeDirty(timeMs: Long): Boolean {
        var count = 0
        var i = 0
        while (i < overlays.size) {
            val item = overlays[i]
            if (!NewsOverlayLayout.shouldSuppressOverlay(item, isPortraitVideo, captionsActive)) {
                val anim = NewsOverlayLayout.evaluateAnimation(item, timeMs)
                if (anim.visible) {
                    val bucket = NewsOverlayLayout.quantizedFrameBucket(item, timeMs, anim)
                    if (bucket == NewsOverlayLayout.DYNAMIC_FRAME_BUCKET) {
                        curAnyVisible = true
                        lastSlotCount = -1
                        return true
                    }
                    slotIdxCur[count] = i
                    slotBucketCur[count] = bucket
                    count++
                }
            }
            i++
        }
        curAnyVisible = count > 0
        if (count == lastSlotCount) {
            var same = true
            var j = 0
            while (j < count) {
                if (slotIdxCur[j] != slotIdxLast[j] || slotBucketCur[j] != slotBucketLast[j]) {
                    same = false
                    break
                }
                j++
            }
            if (same) return false
        }
        System.arraycopy(slotIdxCur, 0, slotIdxLast, 0, count)
        System.arraycopy(slotBucketCur, 0, slotBucketLast, 0, count)
        lastSlotCount = count
        return true
    }

    private fun acquireNextBuffer(): Bitmap {
        var a = bufferA
        var ca = canvasA
        var b = bufferB
        var cb = canvasB
        if (a == null || ca == null || b == null || cb == null) {
            a = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            ca = Canvas(a)
            b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            cb = Canvas(b)
            bufferA = a
            canvasA = ca
            bufferB = b
            canvasB = cb
        }
        val useA = nextIsA
        nextIsA = !nextIsA
        acquiredCanvas = if (useA) ca else cb
        return if (useA) a else b
    }

    private fun recycleFrames() {
        bufferA?.takeIf { !it.isRecycled }?.recycle()
        bufferB?.takeIf { !it.isRecycled }?.recycle()
        bufferA = null
        canvasA = null
        bufferB = null
        canvasB = null
        acquiredCanvas = null
        lastReturnedBitmap = null
        lastSlotCount = -1
        curAnyVisible = false
        nextIsA = true
    }

    companion object {
        private const val MAX_OVERLAY_LONG_SIDE_PX = 1080
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
