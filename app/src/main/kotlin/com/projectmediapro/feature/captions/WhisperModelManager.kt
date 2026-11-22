package com.projectmediapro.feature.captions

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface WhisperModelState {
    data object NotInstalled : WhisperModelState
    data class Downloading(val percent: Int) : WhisperModelState
    data class Installed(val sizeBytes: Long) : WhisperModelState
    data object Error : WhisperModelState
}

/**
 * Manages the optional on-demand Open Whisper (`ggml-tiny-q5_1.bin`, ~32 MB multilingual) model
 * inside app-private storage with HTTPS-only atomic download and 1-tap deletion.
 */
class WhisperModelManager(
    private val modelsDirectory: File,
    private val downloadUrl: String = DEFAULT_MODEL_URL,
) {
    private val downloadMutex = Mutex()
    private val _state = MutableStateFlow<WhisperModelState>(resolveDiskState())
    val state: StateFlow<WhisperModelState> = _state.asStateFlow()

    fun modelFile(): File = File(modelsDirectory, MODEL_FILE_NAME)

    fun installedModelFileOrNull(): File? {
        val file = modelFile()
        return if (file.isFile && file.length() >= MIN_VALID_MODEL_BYTES) file else null
    }

    fun refreshState() {
        if (_state.value !is WhisperModelState.Downloading) {
            _state.value = resolveDiskState()
        }
    }

    suspend fun downloadModel(): Boolean = downloadMutex.withLock {
        downloadModelLocked()
    }

    private suspend fun downloadModelLocked(): Boolean = withContext(Dispatchers.IO) {
        if (!modelsDirectory.isDirectory && !modelsDirectory.mkdirs() && !modelsDirectory.isDirectory) {
            _state.value = WhisperModelState.Error
            return@withContext false
        }
        val targetFile = modelFile()
        val tempFile = File(modelsDirectory, "$MODEL_FILE_NAME.tmp")
        tempFile.delete()
        _state.value = WhisperModelState.Downloading(0)

        var connection: HttpURLConnection? = null
        try {
            val url = URL(downloadUrl)
            if (!url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("Only HTTPS model downloads are permitted")
            }
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code downloading Whisper model")
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: EXPECTED_MODEL_BYTES
            var downloadedBytes = 0L
            var lastPercent = -1

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                        if (pct != lastPercent) {
                            lastPercent = pct
                            _state.value = WhisperModelState.Downloading(pct)
                        }
                    }
                    output.fd.sync()
                }
            }

            if (downloadedBytes < MIN_VALID_MODEL_BYTES) {
                throw IOException("Downloaded Whisper model is unexpectedly small ($downloadedBytes bytes)")
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            _state.value = WhisperModelState.Installed(targetFile.length())
            true
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            _state.value = resolveDiskState()
            throw cancelled
        } catch (_: Exception) {
            tempFile.delete()
            _state.value = WhisperModelState.Error
            false
        } finally {
            connection?.disconnect()
        }
    }

    fun deleteModel() {
        val file = modelFile()
        runCatching { file.delete() }
        runCatching { File(modelsDirectory, "$MODEL_FILE_NAME.tmp").delete() }
        _state.value = WhisperModelState.NotInstalled
    }

    private fun resolveDiskState(): WhisperModelState {
        val file = installedModelFileOrNull()
        return if (file != null) {
            WhisperModelState.Installed(file.length())
        } else {
            WhisperModelState.NotInstalled
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "ggml-tiny-q5_1.bin"
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
        const val EXPECTED_MODEL_BYTES = 32_152_673L
        const val MIN_VALID_MODEL_BYTES = 10_000_000L
    }
}
