package com.projectmediapro.app.data.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a single frame from a local video file and writes it as a PNG for freeze-frame clips.
 */
interface FreezeFrameExtractor {
    @Throws(IOException::class)
    suspend fun extractFrame(
        videoFile: File,
        timeUs: Long,
        outputFile: File,
    )
}

class AndroidFreezeFrameExtractor : FreezeFrameExtractor {

    override suspend fun extractFrame(
        videoFile: File,
        timeUs: Long,
        outputFile: File,
    ) = withContext(Dispatchers.IO) {
        if (!videoFile.isFile) {
            throw IOException("Source video file not found")
        }
        val parent = outputFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Could not create freeze-frame directory")
        }

        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val clampedTimeUs = timeUs.coerceAtLeast(0L)
            frame = retriever.getFrameAtTime(clampedTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(clampedTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.frameAtTime
                ?: throw IOException("Could not decode video frame")

            FileOutputStream(outputFile).use { stream ->
                if (!frame.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("Could not encode freeze-frame PNG")
                }
            }
        } catch (cancelled: CancellationException) {
            outputFile.delete()
            throw cancelled
        } catch (error: IOException) {
            outputFile.delete()
            throw error
        } catch (error: RuntimeException) {
            outputFile.delete()
            throw IOException("Failed to extract freeze frame", error)
        } finally {
            frame?.let { if (!it.isRecycled) it.recycle() }
            runCatching { retriever.release() }
        }
    }
}
