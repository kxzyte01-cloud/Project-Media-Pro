package com.projectmediapro.app.data.media

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure-JVM validator for TrueType (`.ttf`) and OpenType (`.otf`) font binary headers.
 */
object FontHeaderValidator {
    fun isValidFontHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF
        // TrueType: 0x00010000
        if (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) return true
        // OpenType CFF: "OTTO"
        if (b0 == 'O'.code && b1 == 'T'.code && b2 == 'T'.code && b3 == 'O'.code) return true
        // Apple TrueType: "true"
        if (b0 == 't'.code && b1 == 'r'.code && b2 == 'u'.code && b3 == 'e'.code) return true
        // TrueType Collection: "ttcf"
        if (b0 == 't'.code && b1 == 't'.code && b2 == 'c'.code && b3 == 'f'.code) return true
        return false
    }
}

/**
 * Imports and stores user-picked `.ttf`/`.otf` fonts inside app-private storage (zero extra permissions).
 */
class CustomFontStorage(
    private val fontsDirectory: File,
) {
    @Throws(IOException::class)
    suspend fun importFontFromUri(
        context: Context,
        sourceUriString: String,
    ): File = withContext(Dispatchers.IO) {
        if (!fontsDirectory.isDirectory && !fontsDirectory.mkdirs() && !fontsDirectory.isDirectory) {
            throw IOException("Could not create custom fonts directory")
        }
        val uri = Uri.parse(sourceUriString)
        val tempFile = File(fontsDirectory, "font_${UUID.randomUUID()}.tmp")
        try {
            val header = ByteArray(4)
            var headerFilled = 0
            var totalBytes = 0L

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open selected font file")
            input.use { stream ->
                FileOutputStream(tempFile).use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val read = stream.read(buf)
                        if (read < 0) break
                        if (headerFilled < 4) {
                            val toCopy = minOf(4 - headerFilled, read)
                            System.arraycopy(buf, 0, header, headerFilled, toCopy)
                            headerFilled += toCopy
                        }
                        totalBytes += read
                        if (totalBytes > MAX_FONT_BYTES) {
                            throw IOException("Font file is larger than 20 MB")
                        }
                        out.write(buf, 0, read)
                    }
                    out.fd.sync()
                }
            }

            if (headerFilled < 4 || !FontHeaderValidator.isValidFontHeader(header)) {
                throw IOException("Selected file is not a valid .ttf or .otf font")
            }

            val ext = if (header[0].toInt() == 'O'.code) "otf" else "ttf"
            val finalFile = File(fontsDirectory, "custom_${UUID.randomUUID()}.$ext")
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            finalFile
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            throw cancelled
        } catch (error: IOException) {
            tempFile.delete()
            throw error
        } catch (error: RuntimeException) {
            tempFile.delete()
            throw IOException("Failed to import font file", error)
        }
    }

    companion object {
        private const val MAX_FONT_BYTES = 20L * 1024L * 1024L
    }
}
