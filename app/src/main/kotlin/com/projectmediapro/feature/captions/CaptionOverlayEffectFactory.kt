package com.projectmediapro.feature.captions

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList

@OptIn(UnstableApi::class)
class CaptionBitmapOverlay(
    private val document: CaptionDocument,
) : CanvasOverlay(/* useInputFrameSize = */ true) {

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val frameState = CaptionFrameEvaluator.evaluate(document, presentationTimeUs)
        CaptionCanvasRenderer.render(
            canvas = canvas,
            widthPx = canvas.width.toFloat(),
            heightPx = canvas.height.toFloat(),
            frameState = frameState,
        )
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
