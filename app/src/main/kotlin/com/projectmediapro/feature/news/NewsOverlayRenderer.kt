package com.projectmediapro.feature.news

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.LruCache
import com.projectmediapro.core.model.NewsOverlayItem
import com.projectmediapro.core.model.NewsOverlayLayout
import com.projectmediapro.core.model.NewsSfxCue
import java.io.File

/**
 * Bounded LRU bitmap loader for optional Channel Logo & OTS Topic Card images.
 * Decodes scaled bitmaps safely without OOM and recycles cleanly on eviction AND [clear].
 */
class NewsOverlayBitmapCache(private val appContext: Context) {

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHED_BITMAPS) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            if (oldValue != null && newValue == null && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    fun load(uriOrPath: String): Bitmap? {
        if (uriOrPath.isBlank()) return null
        val hit = cache.get(uriOrPath)
        if (hit != null) {
            if (!hit.isRecycled) return hit
            cache.remove(uriOrPath)
        }
        val decoded = decodeScaled(uriOrPath) ?: return null
        cache.put(uriOrPath, decoded)
        return decoded
    }

    fun clear() {
        cache.evictAll()
    }

    private fun decodeScaled(uriOrPath: String): Bitmap? = runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uriOrPath)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        if (maxDim <= 0) return null
        var sample = 1
        while (maxDim / sample > MAX_DECODE_SIDE_PX) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        openStream(uriOrPath)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
    }.getOrNull()

    private fun openStream(uriOrPath: String) = when {
        uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://") ->
            appContext.contentResolver.openInputStream(Uri.parse(uriOrPath))
        else -> {
            val file = File(uriOrPath)
            if (file.exists()) file.inputStream() else null
        }
    }

    companion object {
        private const val MAX_CACHED_BITMAPS = 8
        private const val MAX_DECODE_SIDE_PX = 512
    }
}

/**
 * Optional lightweight offline SFX asset resolver for News overlays.
 * Checks `assets/sfx/news_*.wav` (or `.ogg`) and gracefully returns null (silent fallback)
 * when bundled audio assets are absent.
 */
object NewsSfxHelper {
    fun assetPathFor(context: Context, cue: NewsSfxCue): String? {
        val base = when (cue) {
            NewsSfxCue.NONE -> return null
            NewsSfxCue.NEWSROOM_HIT -> "sfx/newsroom_hit"
            NewsSfxCue.TRANSITION_WHOOSH -> "sfx/transition_whoosh"
            NewsSfxCue.MARKER_SCRATCH -> "sfx/marker_scratch"
            NewsSfxCue.TELETYPE_TICK -> "sfx/teletype_tick"
        }
        val candidates = listOf("$base.wav", "$base.ogg", "$base.m4a")
        val existing = runCatching { context.assets.list("sfx")?.toSet() ?: emptySet() }.getOrDefault(emptySet())
        for (path in candidates) {
            val fileName = path.substringAfter("sfx/")
            if (fileName in existing) return path
        }
        return null
    }
}

/**
 * Unified news & broadcast overlay renderer shared 1:1 between Compose live preview
 * (`NewsPreviewOverlay` / `NewsQuickFillSheet`) and Media3 Transformer export (`NewsBitmapOverlay`).
 */
object NewsOverlayRenderer {

    private val scratchHolder = ThreadLocal<NewsDrawScratch>()

    fun renderAll(
        canvas: Canvas,
        widthPx: Float,
        heightPx: Float,
        overlays: List<NewsOverlayItem>,
        timeMs: Long,
        captionsActive: Boolean = false,
        bitmapLoader: ((String) -> Bitmap?)? = null,
    ) {
        if (overlays.isEmpty() || widthPx <= 0f || heightPx <= 0f) return
        val scratch = scratchHolder.get() ?: NewsDrawScratch().also { scratchHolder.set(it) }
        val isPortrait = heightPx > widthPx
        for (item in overlays) {
            if (NewsOverlayLayout.shouldSuppressOverlay(item, isPortrait, captionsActive)) continue
            val anim = NewsOverlayLayout.evaluateAnimation(item, timeMs)
            if (!anim.visible) continue
            NewsEffectDrawers.drawOverlay(
                canvas = canvas,
                scratch = scratch,
                w = widthPx,
                h = heightPx,
                item = item,
                anim = anim,
                timeMs = timeMs,
                bitmapLoader = bitmapLoader,
            )
        }
    }
}
