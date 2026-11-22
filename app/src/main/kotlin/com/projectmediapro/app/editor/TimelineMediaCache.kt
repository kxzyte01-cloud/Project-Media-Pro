package com.projectmediapro.app.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import com.projectmediapro.feature.captions.CaptionTime
import com.projectmediapro.feature.captions.MediaCodecPcmAudioExtractor
import com.projectmediapro.feature.captions.PcmAudioExtractor
import com.projectmediapro.feature.captions.PcmExtractionException
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory offline cache for timeline video filmstrip thumbnails and audio waveform peaks.
 * Uses API 26-safe [MediaMetadataRetriever.getFrameAtTime] + [Bitmap.createScaledBitmap].
 */
object TimelineMediaCache {

    private const val THUMB_WIDTH_PX = 96
    private const val THUMB_HEIGHT_PX = 54
    private const val MAX_WAVEFORM_DURATION_US = 5L * 60L * CaptionTime.MICROS_PER_SEC

    private val filmstripCache = object : LruCache<String, List<Bitmap>>(16) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: List<Bitmap>,
            newValue: List<Bitmap>?,
        ) {
            // Compose ImageBitmap wrappers may still reference bitmaps briefly during draw;
            // rely on GC rather than eager recycle() to avoid Canvas recycled-bitmap crashes.
        }
    }

    private val waveformCache = LruCache<String, List<Float>>(32)
    private val defaultPcmExtractor: PcmAudioExtractor = MediaCodecPcmAudioExtractor()

    suspend fun loadVideoFilmstrip(
        filePath: String,
        isStillImage: Boolean,
        trimStartMs: Long,
        trimEndMs: Long,
        frameCount: Int,
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val safeCount = frameCount.coerceIn(1, 6)
        val cacheKey = "$filePath|$isStillImage|$trimStartMs|$trimEndMs|$safeCount"
        synchronized(filmstripCache) {
            filmstripCache.get(cacheKey)?.let { return@withContext it }
        }

        val file = File(filePath)
        if (!file.isFile) return@withContext emptyList()

        val result = if (isStillImage) {
            decodeScaledImageThumbnail(file)?.let { listOf(it) }.orEmpty()
        } else {
            extractVideoFrames(file, trimStartMs, trimEndMs, safeCount)
        }

        if (result.isNotEmpty()) {
            synchronized(filmstripCache) {
                filmstripCache.put(cacheKey, result)
            }
        }
        result
    }

    suspend fun loadAudioWaveform(
        filePath: String,
        trimStartMs: Long,
        trimEndMs: Long,
        bucketCount: Int = 48,
        extractor: PcmAudioExtractor = defaultPcmExtractor,
    ): List<Float> = withContext(Dispatchers.IO) {
        val safeBuckets = bucketCount.coerceIn(8, 96)
        val cacheKey = "$filePath|$trimStartMs|$trimEndMs|$safeBuckets"
        synchronized(waveformCache) {
            waveformCache.get(cacheKey)?.let { return@withContext it }
        }

        val file = File(filePath)
        if (!file.isFile) return@withContext emptyList()

        val trimStartUs = trimStartMs.coerceAtLeast(0L) * CaptionTime.MICROS_PER_MS
        val trimEndUs = if (trimEndMs > trimStartMs) {
            trimEndMs * CaptionTime.MICROS_PER_MS
        } else {
            Long.MAX_VALUE
        }
        val peaks = try {
            val pcm = extractor.extractMono16kPcm(
                file = file,
                trimStartUs = trimStartUs,
                trimEndUs = trimEndUs,
                maxDurationUs = MAX_WAVEFORM_DURATION_US,
            )
            TimelineScale.downsamplePcmToPeaks(pcm.samples, safeBuckets)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PcmExtractionException) {
            emptyList()
        }

        if (peaks.isNotEmpty()) {
            synchronized(waveformCache) {
                waveformCache.put(cacheKey, peaks)
            }
        }
        peaks
    }

    private suspend fun extractVideoFrames(
        file: File,
        trimStartMs: Long,
        trimEndMs: Long,
        frameCount: Int,
    ): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(file.absolutePath)
            val metaDurationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val startMs = trimStartMs.coerceAtLeast(0L)
            val endMs = when {
                trimEndMs > startMs -> trimEndMs
                metaDurationMs > startMs -> metaDurationMs
                else -> startMs + 1_000L
            }
            val spanMs = (endMs - startMs).coerceAtLeast(1L)

            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                val fraction = (i + 0.5f) / frameCount.toFloat()
                val timeUs = (startMs + (spanMs * fraction).toLong()) * 1_000L
                val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                    ?: continue
                val scaled = scaleThumbnail(raw)
                if (scaled !== raw && !raw.isRecycled) {
                    raw.recycle()
                }
                frames.add(scaled)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
        } finally {
            runCatching { retriever.release() }
        }
        return frames
    }

    private fun decodeScaledImageThumbnail(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= THUMB_WIDTH_PX) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val scaled = scaleThumbnail(decoded)
        if (scaled !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }
        return scaled
    }

    private fun scaleThumbnail(source: Bitmap): Bitmap {
        if (source.width <= THUMB_WIDTH_PX && source.height <= THUMB_HEIGHT_PX) {
            return source
        }
        return Bitmap.createScaledBitmap(source, THUMB_WIDTH_PX, THUMB_HEIGHT_PX, true)
    }
}
