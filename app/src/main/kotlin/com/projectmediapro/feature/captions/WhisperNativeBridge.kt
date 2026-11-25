package com.projectmediapro.feature.captions

data class WhisperSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Safe JNI wrapper around `libmediapro_whisper.so` (ggml-org/whisper.cpp).
 * Falls back gracefully (returns [isNativeAvailable] = false) on JVM unit tests or builds where
 * native whisper.cpp was not compiled.
 */
object WhisperNativeBridge {

    private val nativeLock = Any()

    val isNativeAvailable: Boolean by lazy {
        try {
            System.loadLibrary("mediapro_whisper")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun transcribePcm16k(
        modelPath: String,
        samplesFloat: FloatArray,
        numThreads: Int = defaultThreadCount(),
    ): List<WhisperSegment>? {
        if (!isNativeAvailable || samplesFloat.isEmpty()) return null
        return synchronized(nativeLock) {
            val ctx = nativeInitContext(modelPath)
            if (ctx == 0L) return@synchronized null
            try {
                val status = nativeFullTranscribe(ctx, numThreads.coerceIn(1, 4), samplesFloat)
                if (status != 0) return@synchronized null
                val count = nativeGetSegmentCount(ctx).coerceAtLeast(0)
                buildList(count) {
                    for (i in 0 until count) {
                        val rawBytes = nativeGetSegmentTextBytes(ctx, i)
                        val rawText = String(rawBytes, Charsets.UTF_8).trim()
                        if (rawText.isEmpty()) continue
                        val startMs = nativeGetSegmentT0Ms(ctx, i).coerceAtLeast(0L)
                        val endMs = nativeGetSegmentT1Ms(ctx, i).coerceAtLeast(startMs + 80L)
                        add(WhisperSegment(text = rawText, startMs = startMs, endMs = endMs))
                    }
                }
            } finally {
                nativeFreeContext(ctx)
            }
        }
    }

    private fun defaultThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores / 2).coerceIn(2, 4)
    }

    private external fun nativeInitContext(modelPath: String): Long
    private external fun nativeFreeContext(contextPtr: Long)
    private external fun nativeFullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray): Int
    private external fun nativeGetSegmentCount(contextPtr: Long): Int
    private external fun nativeGetSegmentTextBytes(contextPtr: Long, index: Int): ByteArray
    private external fun nativeGetSegmentT0Ms(contextPtr: Long, index: Int): Long
    private external fun nativeGetSegmentT1Ms(contextPtr: Long, index: Int): Long
}
