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
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList

/**
 * Media3 1.4.1 [BitmapOverlay] that renders animated captions onto a single reusable frame bitmap
 * allocated in [configure] and recycled deterministically in [release].
 */
@OptIn(UnstableApi::class)
class CaptionBitmapOverlay(
    private val document: CaptionDocument,
) : BitmapOverlay() {

    private var frameBitmap: Bitmap? = null
    private var frameCanvas: Canvas? = null

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        recycleFrame()
        val width = videoSize.width.coerceAtLeast(1)
        val height = videoSize.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        frameBitmap = bitmap
        frameCanvas = Canvas(bitmap)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bitmap = frameBitmap?.takeIf { !it.isRecycled } ?: Bitmap.createBitmap(
            1,
            1,
            Bitmap.Config.ARGB_8888,
        ).also {
            frameBitmap = it
            frameCanvas = Canvas(it)
        }
        val canvas = frameCanvas ?: Canvas(bitmap).also { frameCanvas = it }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val frameState = CaptionFrameEvaluator.evaluate(document, presentationTimeUs)
        if (frameState.hasVisibleContent) {
            CaptionCanvasRenderer.render(
                canvas = canvas,
                widthPx = bitmap.width.toFloat(),
                heightPx = bitmap.height.toFloat(),
                frameState = frameState,
            )
        }
        return bitmap
    }

    override fun release() {
        super.release()
        recycleFrame()
    }

    private fun recycleFrame() {
        frameBitmap?.let { if (!it.isRecycled) it.recycle() }
        frameBitmap = null
        frameCanvas = null
    }
}

object CaptionOverlayEffectFactory {

    @OptIn(UnstableApi::class)
    fun create(document: CaptionDocument?): Effect? {
        if (document == null || document.lines.isEmpty()) return null
        val overlays: ImmutableList<TextureOverlay> = ImmutableList.of(CaptionBitmapOverlay(document))
        return OverlayEffect(overlays)
    }
}
