package com.projectmediapro.feature.captions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.NewsOverlayLayout

/**
 * Pure-JVM visual equality comparator for [CaptionFrameState].
 *
 * Ignores raw timestamps (`presentationTimeUs` / `adjustedTimeUs`) so export frames whose
 * rendered caption pixels are identical can reuse the previous [Bitmap] instance, while any
 * visible animation change triggers a fresh buffer swap for OpenGL texture upload.
 */
internal object CaptionFrameVisualEquality {

    fun isVisuallyIdentical(
        previous: CaptionFrameState?,
        current: CaptionFrameState,
    ): Boolean {
        if (previous == null) return false
        if (!previous.hasVisibleContent && !current.hasVisibleContent) return true
        if (previous.hasVisibleContent != current.hasVisibleContent) return false
        if (current.style.rainFontEnabled) return false
        when (current.style.templateId) {
            CaptionTemplateId.KINETIC_GRUNGE,
            CaptionTemplateId.EDITORIAL_SCRATCH,
            CaptionTemplateId.BLACKOUT_KINETIC,
            CaptionTemplateId.COLD_INVERT,
            CaptionTemplateId.KINETIC_COLLAGE,
            CaptionTemplateId.CRIMSON_ZINE,
            -> return false
            else -> Unit
        }
        val usesSongProgress = current.style.templateId == CaptionTemplateId.MUSIC_PLAYER ||
            current.style.templateId == CaptionTemplateId.CODE_POEM
        val songProgressMatches = !usesSongProgress ||
            previous.songProgressFraction == current.songProgressFraction
        return previous.activeLineId == current.activeLineId &&
            previous.lineAlpha == current.lineAlpha &&
            previous.showCursor == current.showCursor &&
            previous.activeLineIndex == current.activeLineIndex &&
            previous.totalLines == current.totalLines &&
            previous.previousLineText == current.previousLineText &&
            previous.nextLineText == current.nextLineText &&
            songProgressMatches &&
            previous.style == current.style &&
            previous.words == current.words
    }
}

/**
 * Media3 1.4.1 [BitmapOverlay] that renders animated captions using ping-pong double-buffering.
 *
 * Media3's [BitmapOverlay] caches the last uploaded [Bitmap] by object reference and skips
 * OpenGL texture re-upload when [getBitmap] returns the same instance. Alternating between two
 * pre-allocated frame buffers whenever [CaptionFrameVisualEquality.isVisuallyIdentical] returns
 * `false` guarantees every animated caption frame uploads cleanly to the GPU with zero per-frame
 * heap allocations, while unchanged or empty frames reuse the cached texture.
 */
@OptIn(UnstableApi::class)
class CaptionBitmapOverlay(
    private val document: CaptionDocument,
    private val newsOverlays: List<NewsOverlayItem> = emptyList(),
) : BitmapOverlay() {

    private var bufferA: Bitmap? = null
    private var canvasA: Canvas? = null
    private var bufferB: Bitmap? = null
    private var canvasB: Canvas? = null
    private var nextIsA: Boolean = true
    private var lastRenderedState: CaptionFrameState? = null
    private var lastReturnedBitmap: Bitmap? = null
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
        val scaleX = videoWidth.toFloat() / bitmapWidth.toFloat()
        val scaleY = videoHeight.toFloat() / bitmapHeight.toFloat()
        overlaySettings = OverlaySettings.Builder()
            .setScale(scaleX, scaleY)
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
        val frameState = CaptionFrameEvaluator.evaluate(document, presentationTimeUs)
        val cached = lastReturnedBitmap
        if (
            newsOverlays.isEmpty() &&
            cached != null &&
            !cached.isRecycled &&
            CaptionFrameVisualEquality.isVisuallyIdentical(lastRenderedState, frameState)
        ) {
            return cached
        }

        val (targetBitmap, targetCanvas) = acquireNextBuffer()
        targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (frameState.hasVisibleContent) {
            val occupiedBands = NewsOverlayLayout.occupiedBandsAt(
                overlays = newsOverlays,
                timeMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L),
                isPortrait = targetBitmap.height > targetBitmap.width,
            )
            CaptionCanvasRenderer.render(
                canvas = targetCanvas,
                widthPx = targetBitmap.width.toFloat(),
                heightPx = targetBitmap.height.toFloat(),
                frameState = frameState,
                occupiedBands = occupiedBands,
            )
        }
        lastRenderedState = frameState
        lastReturnedBitmap = targetBitmap
        return targetBitmap
    }

    override fun release() {
        super.release()
        recycleFrames()
    }

    private fun acquireNextBuffer(): Pair<Bitmap, Canvas> {
        val useA = nextIsA
        nextIsA = !nextIsA
        return if (useA) {
            val bitmap = bufferA?.takeIf { !it.isRecycled } ?: Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888,
            ).also {
                bufferA = it
                canvasA = Canvas(it)
            }
            val canvas = canvasA ?: Canvas(bitmap).also { canvasA = it }
            bitmap to canvas
        } else {
            val bitmap = bufferB?.takeIf { !it.isRecycled } ?: Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888,
            ).also {
                bufferB = it
                canvasB = Canvas(it)
            }
            val canvas = canvasB ?: Canvas(bitmap).also { canvasB = it }
            bitmap to canvas
        }
    }

    private fun recycleFrames() {
        bufferA?.let { if (!it.isRecycled) it.recycle() }
        bufferB?.let { if (!it.isRecycled) it.recycle() }
        bufferA = null
        canvasA = null
        bufferB = null
        canvasB = null
        nextIsA = true
        lastRenderedState = null
        lastReturnedBitmap = null
        overlaySettings = OverlaySettings.Builder().build()
    }

    companion object {
        private const val MAX_OVERLAY_LONG_SIDE_PX = 1920
    }
}

object CaptionOverlayEffectFactory {

    @OptIn(UnstableApi::class)
    fun createOverlay(
        document: CaptionDocument?,
        newsOverlays: List<NewsOverlayItem> = emptyList(),
    ): TextureOverlay? {
        if (document == null || document.lines.isEmpty()) return null
        return CaptionBitmapOverlay(document, newsOverlays)
    }

    @OptIn(UnstableApi::class)
    fun create(document: CaptionDocument?): Effect? {
        val overlay = createOverlay(document) ?: return null
        val overlays: ImmutableList<TextureOverlay> = ImmutableList.of(overlay)
        return OverlayEffect(overlays)
    }
}
