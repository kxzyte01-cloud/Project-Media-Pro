package com.projectmediapro.app.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ExtractedAudioResult(
    val durationMs: Long,
)

/**
 * Extracts the audio track from a local video file into a standalone `.m4a` file without
 * re-encoding so users can independently trim, speed-ramp, mix, or replace video audio.
 */
interface VideoAudioExtractor {
    @Throws(IOException::class)
    suspend fun extractAudio(
        videoFile: File,
        trimStartMs: Long,
        trimEndMs: Long,
        outputFile: File,
    ): ExtractedAudioResult
}

class AndroidVideoAudioExtractor : VideoAudioExtractor {

    override suspend fun extractAudio(
        videoFile: File,
        trimStartMs: Long,
        trimEndMs: Long,
        outputFile: File,
    ): ExtractedAudioResult = withContext(Dispatchers.IO) {
        if (!videoFile.isFile) {
            throw IOException("Source video file not found")
        }
        val parent = outputFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Could not create extracted audio directory")
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = index
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                throw IOException("Video clip does not contain an audio track")
            }

            extractor.selectTrack(audioTrackIndex)
            val startUs = trimStartMs.coerceAtLeast(0L) * MICROS_PER_MS
            val endUs = if (trimEndMs > trimStartMs) trimEndMs * MICROS_PER_MS else -1L
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val maxInputSize = runCatching {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            }.getOrDefault(DEFAULT_BUFFER_SIZE_BYTES)
                .coerceIn(MIN_BUFFER_SIZE_BYTES, MAX_BUFFER_SIZE_BYTES)
            val buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val activeMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            muxer = activeMuxer
            val muxerTrackIndex = activeMuxer.addTrack(audioFormat)
            activeMuxer.start()
            muxerStarted = true

            var firstSampleUs = -1L
            var lastSampleUs = -1L
            var sampleCount = 0

            while (true) {
                coroutineContext.ensureActive()
                bufferInfo.offset = 0
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (endUs > 0L && sampleTimeUs > endUs) {
                    break
                }
                if (sampleTimeUs >= startUs) {
                    if (firstSampleUs < 0L) {
                        firstSampleUs = sampleTimeUs
                    }
                    lastSampleUs = sampleTimeUs
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = (sampleTimeUs - firstSampleUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                    activeMuxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    sampleCount++
                }
                if (!extractor.advance()) break
            }

            if (sampleCount == 0) {
                throw IOException("No audio samples were extracted from this video clip")
            }

            val measuredDurationMs = if (lastSampleUs > firstSampleUs) {
                (lastSampleUs - firstSampleUs) / MICROS_PER_MS
            } else if (endUs > startUs) {
                (endUs - startUs) / MICROS_PER_MS
            } else {
                val trackDurationUs = runCatching {
                    audioFormat.getLong(MediaFormat.KEY_DURATION)
                }.getOrDefault(0L)
                (trackDurationUs / MICROS_PER_MS).coerceAtLeast(0L)
            }

            ExtractedAudioResult(
                durationMs = measuredDurationMs.coerceAtLeast(MIN_EXTRACTED_DURATION_MS),
            )
        } catch (cancelled: CancellationException) {
            outputFile.delete()
            throw cancelled
        } catch (error: IOException) {
            outputFile.delete()
            throw error
        } catch (error: RuntimeException) {
            outputFile.delete()
            throw IOException("Failed to extract audio from video", error)
        } finally {
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    companion object {
        private const val MICROS_PER_MS = 1_000L
        private const val MIN_EXTRACTED_DURATION_MS = 500L
        private const val MIN_BUFFER_SIZE_BYTES = 64 * 1024
        private const val DEFAULT_BUFFER_SIZE_BYTES = 256 * 1024
        private const val MAX_BUFFER_SIZE_BYTES = 2 * 1024 * 1024
    }
}
