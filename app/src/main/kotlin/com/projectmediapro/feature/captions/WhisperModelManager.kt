package com.projectmediapro.feature.captions

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Supported on-device Open Whisper quantized GGML models (multilingual).
 */
enum class WhisperModelVariant(
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val minValidBytes: Long,
    val approxMb: Int,
    val sha256: String,
) {
    TINY(
        fileName = "ggml-tiny-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-tiny-q5_1.bin",
        expectedBytes = 32_152_673L,
        minValidBytes = 10_000_000L,
        approxMb = 32,
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
    ),
    BASE(
        fileName = "ggml-base-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin",
        expectedBytes = 59_707_625L,
        minValidBytes = 25_000_000L,
        approxMb = 60,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
    ),
    SMALL(
        fileName = "ggml-small-q5_1.bin",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-small-q5_1.bin",
        expectedBytes = 190_085_487L,
        minValidBytes = 80_000_000L,
        approxMb = 190,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
    ),
}

sealed interface WhisperModelState {
    data object NotInstalled : WhisperModelState
    data class Downloading(val percent: Int) : WhisperModelState
    data class Installed(val sizeBytes: Long) : WhisperModelState
    data object Error : WhisperModelState
}

/**
 * Manages optional on-demand Open Whisper models (`tiny`, `base`, `small` Q5_1 multilingual)
 * inside app-private storage with HTTPS-only atomic download, per-model switching, and deletion.
 */
class WhisperModelManager(
    private val modelsDirectory: File,
    private val customDownloadUrl: String? = null,
) {
    private val downloadMutex = Mutex()
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun cancelActiveDownload() {
        runCatching { activeConnection?.disconnect() }
    }
    private val _selectedVariant = MutableStateFlow(loadSavedVariant())
    val selectedVariant: StateFlow<WhisperModelVariant> = _selectedVariant.asStateFlow()

    private val _installedVariants = MutableStateFlow(scanInstalledVariants())
    val installedVariants: StateFlow<Set<WhisperModelVariant>> = _installedVariants.asStateFlow()

    private val _state = MutableStateFlow<WhisperModelState>(resolveDiskState(_selectedVariant.value))
    val state: StateFlow<WhisperModelState> = _state.asStateFlow()

    fun modelFile(variant: WhisperModelVariant = _selectedVariant.value): File =
        File(modelsDirectory, variant.fileName)

    fun isInstalled(variant: WhisperModelVariant): Boolean {
        val file = modelFile(variant)
        return file.isFile && file.length() >= variant.minValidBytes && hasValidGgmlHeader(file)
    }

    /**
     * Returns the active selected model file if installed, or falls back to the highest-accuracy
     * installed variant if another model is present on disk.
     */
    fun installedModelFileOrNull(): File? {
        val active = _selectedVariant.value
        if (isInstalled(active)) return modelFile(active)
        val fallbackOrder = listOf(WhisperModelVariant.SMALL, WhisperModelVariant.BASE, WhisperModelVariant.TINY)
        return fallbackOrder.firstOrNull { isInstalled(it) }?.let { modelFile(it) }
    }

    fun selectVariant(variant: WhisperModelVariant) {
        if (_state.value is WhisperModelState.Downloading) return
        _selectedVariant.value = variant
        saveSelectedVariant(variant)
        _installedVariants.value = scanInstalledVariants()
        _state.value = resolveDiskState(variant)
    }

    fun refreshState() {
        _installedVariants.value = scanInstalledVariants()
        if (_state.value !is WhisperModelState.Downloading) {
            _state.value = resolveDiskState(_selectedVariant.value)
        }
    }

    suspend fun downloadModel(variant: WhisperModelVariant = _selectedVariant.value): Boolean =
        downloadMutex.withLock {
            _selectedVariant.value = variant
            saveSelectedVariant(variant)
            downloadModelLocked(variant)
        }

    private suspend fun downloadModelLocked(variant: WhisperModelVariant): Boolean = withContext(Dispatchers.IO) {
        if (!modelsDirectory.isDirectory && !modelsDirectory.mkdirs() && !modelsDirectory.isDirectory) {
            _state.value = WhisperModelState.Error
            return@withContext false
        }
        val targetFile = modelFile(variant)
        val tempFile = File(modelsDirectory, "${variant.fileName}.tmp")
        tempFile.delete()
        _state.value = WhisperModelState.Downloading(0)

        var connection: HttpURLConnection? = null
        try {
            connection = openHttpsFollowingRedirects(customDownloadUrl ?: variant.downloadUrl)
            activeConnection = connection
            val contentLength = connection.contentLengthLong
            val totalBytes = contentLength.takeIf { it > 0L } ?: variant.expectedBytes
            var downloadedBytes = 0L
            var lastPercent = -1
            val sha256Digest = MessageDigest.getInstance("SHA-256")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sha256Digest.update(buffer, 0, read)
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

            if (contentLength > 0L && downloadedBytes != contentLength) {
                throw IOException("Downloaded Whisper model truncated ($downloadedBytes / $contentLength bytes)")
            }
            if (downloadedBytes < variant.minValidBytes) {
                throw IOException("Downloaded Whisper model is unexpectedly small ($downloadedBytes bytes)")
            }
            if (!hasValidGgmlHeader(tempFile)) {
                throw IOException("Downloaded file does not have a valid GGML model header")
            }
            if (customDownloadUrl == null) {
                val actualSha256 = toLowerHex(sha256Digest.digest())
                if (!actualSha256.equals(variant.sha256, ignoreCase = true)) {
                    throw IOException("Whisper model SHA-256 mismatch (expected ${variant.sha256}, got $actualSha256)")
                }
            }
            moveModelAtomically(tempFile, targetFile)
            _installedVariants.value = scanInstalledVariants()
            _state.value = WhisperModelState.Installed(targetFile.length())
            true
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            _installedVariants.value = scanInstalledVariants()
            _state.value = resolveDiskState(variant)
            throw cancelled
        } catch (_: Exception) {
            tempFile.delete()
            _installedVariants.value = scanInstalledVariants()
            _state.value = WhisperModelState.Error
            false
        } finally {
            if (activeConnection === connection) {
                activeConnection = null
            }
            runCatching { connection?.disconnect() }
        }
    }

    fun deleteModel(variant: WhisperModelVariant = _selectedVariant.value) {
        if (_state.value is WhisperModelState.Downloading && _selectedVariant.value == variant) {
            cancelActiveDownload()
            return
        }
        val file = modelFile(variant)
        runCatching { file.delete() }
        runCatching { File(modelsDirectory, "${variant.fileName}.tmp").delete() }
        _installedVariants.value = scanInstalledVariants()
        if (_selectedVariant.value == variant) {
            _state.value = WhisperModelState.NotInstalled
        }
    }

    private fun scanInstalledVariants(): Set<WhisperModelVariant> =
        WhisperModelVariant.entries.filterTo(LinkedHashSet()) { isInstalled(it) }

    private fun resolveDiskState(variant: WhisperModelVariant): WhisperModelState {
        return if (isInstalled(variant)) {
            WhisperModelState.Installed(modelFile(variant).length())
        } else {
            WhisperModelState.NotInstalled
        }
    }

    private fun loadSavedVariant(): WhisperModelVariant {
        val marker = File(modelsDirectory, ACTIVE_MODEL_MARKER)
        val raw = runCatching { if (marker.isFile) marker.readText().trim() else "" }.getOrDefault("")
        return WhisperModelVariant.entries.firstOrNull { it.name == raw } ?: WhisperModelVariant.TINY
    }

    private fun saveSelectedVariant(variant: WhisperModelVariant) {
        runCatching {
            if (!modelsDirectory.isDirectory) modelsDirectory.mkdirs()
            File(modelsDirectory, ACTIVE_MODEL_MARKER).writeText(variant.name)
        }
    }

    private fun openHttpsFollowingRedirects(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl
        repeat(MAX_REDIRECTS) {
            val url = URL(currentUrl)
            if (!url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("Only HTTPS model downloads are permitted")
            }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ProjectMediaPro-Android")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                return conn
            }
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect without Location header")
                }
                currentUrl = URL(url, location).toString()
            } else {
                conn.disconnect()
                throw IOException("HTTP $code downloading Whisper model")
            }
        }
        throw IOException("Too many redirects downloading Whisper model")
    }

    companion object {
        internal fun toLowerHex(bytes: ByteArray): String {
            val hexChars = "0123456789abcdef"
            val out = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                out[i * 2] = hexChars[v ushr 4]
                out[i * 2 + 1] = hexChars[v and 0x0F]
            }
            return String(out)
        }

        internal fun hasValidGgmlHeader(file: File): Boolean = runCatching {
            if (!file.isFile || file.length() < 4L) return false
            val magic = ByteArray(4)
            FileInputStream(file).use { input ->
                if (input.read(magic) < 4) return false
            }
            val ascii = String(magic, Charsets.US_ASCII)
            ascii == "lmgg" || ascii == "ggml" || ascii == "GGUF"
        }.getOrDefault(false)

        internal fun moveModelAtomically(tempFile: File, targetFile: File) {
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }

        private const val MAX_REDIRECTS = 5
        private const val ACTIVE_MODEL_MARKER = "active_model.txt"
        const val MODEL_FILE_NAME = "ggml-tiny-q5_1.bin"
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
        const val EXPECTED_MODEL_BYTES = 32_152_673L
        const val MIN_VALID_MODEL_BYTES = 10_000_000L
    }
}
