package com.projectmediapro.app.data.export

import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
import kotlin.math.roundToInt

/**
 * Pure geometry and encoding-target helpers mapping [CanvasAspect], [ExportQuality],
 * [ExportFrameRate], and [ExportBitrate] to even-dimension sizes and target bitrates.
 */
object ExportGeometry {
    const val HD_LONG_SIDE_PX: Int = 1280
    const val FULL_HD_LONG_SIDE_PX: Int = 1920
    const val QHD_2K_LONG_SIDE_PX: Int = 2560
    const val UHD_4K_LONG_SIDE_PX: Int = 3840

    fun longSideCapFor(quality: ExportQuality): Int = when (quality) {
        ExportQuality.HD_720P -> HD_LONG_SIDE_PX
        ExportQuality.FULL_HD_1080P -> FULL_HD_LONG_SIDE_PX
        ExportQuality.QHD_1440P -> QHD_2K_LONG_SIDE_PX
        ExportQuality.UHD_4K -> UHD_4K_LONG_SIDE_PX
    }

    fun targetBitrateBps(
        quality: ExportQuality,
        frameRate: ExportFrameRate = ExportFrameRate.AUTO,
        bitrate: ExportBitrate = ExportBitrate.STANDARD,
    ): Int {
        val baseBps = when (quality) {
            ExportQuality.HD_720P -> 5_000_000
            ExportQuality.FULL_HD_1080P -> 10_000_000
            ExportQuality.QHD_1440P -> 18_000_000
            ExportQuality.UHD_4K -> 30_000_000
        }
        val bitrateMultiplier = when (bitrate) {
            ExportBitrate.FAST_SMALL -> 0.65f
            ExportBitrate.STANDARD -> 1.0f
            ExportBitrate.HIGH -> 1.5f
        }
        val fpsMultiplier = if (frameRate == ExportFrameRate.FPS_60) 1.35f else 1.0f
        return (baseBps * bitrateMultiplier * fpsMultiplier).roundToInt().coerceAtLeast(1_500_000)
    }

    fun sizeFor(
        aspect: CanvasAspect,
        quality: ExportQuality,
        firstClipWidth: Int,
        firstClipHeight: Int,
    ): Pair<Int, Int>? {
        val shortSide = when (quality) {
            ExportQuality.HD_720P -> 720
            ExportQuality.FULL_HD_1080P -> 1080
            ExportQuality.QHD_1440P -> 1440
            ExportQuality.UHD_4K -> 2160
        }
        val longSideCap = longSideCapFor(quality)
        return when (aspect) {
            CanvasAspect.RATIO_16_9 -> Pair(toEven(longSideCap), toEven(shortSide))
            CanvasAspect.RATIO_9_16 -> Pair(toEven(shortSide), toEven(longSideCap))
            CanvasAspect.RATIO_1_1 -> Pair(toEven(shortSide), toEven(shortSide))
            CanvasAspect.RATIO_4_5 -> Pair(toEven(shortSide), toEven((shortSide * 5) / 4))
            CanvasAspect.ORIGINAL -> fitToLongSide(firstClipWidth, firstClipHeight, longSideCap)
        }
    }

    fun previewAspectRatio(aspect: CanvasAspect): Float = when (aspect) {
        CanvasAspect.ORIGINAL, CanvasAspect.RATIO_16_9 -> 16f / 9f
        CanvasAspect.RATIO_9_16 -> 9f / 16f
        CanvasAspect.RATIO_1_1 -> 1f
        CanvasAspect.RATIO_4_5 -> 4f / 5f
    }

    internal fun fitToLongSide(
        width: Int,
        height: Int,
        maxLongSidePx: Int,
    ): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || maxLongSidePx < 2) return null
        val longSide = maxOf(width, height)
        val scale = if (longSide > maxLongSidePx) {
            maxLongSidePx.toDouble() / longSide
        } else {
            1.0
        }
        return Pair(toEven((width * scale).roundToInt()), toEven((height * scale).roundToInt()))
    }

    private fun toEven(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)
}
