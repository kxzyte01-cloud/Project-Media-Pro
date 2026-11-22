package com.projectmediapro.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Shared atomic UTF-8 file persistence helpers used by file-backed repositories.
 * Uses fsync + ATOMIC_MOVE with a REPLACE_EXISTING fallback for Android FUSE/scoped filesystems.
 */
internal object AtomicFileStore {

    suspend fun <T> runOnIo(
        ioDispatcher: CoroutineDispatcher,
        accessDeniedMessage: String,
        block: suspend () -> T,
    ): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            throw IOException(accessDeniedMessage, denied)
        }
    }

    fun ensureDirectory(directory: File, label: String) {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not create $label directory: ${directory.absolutePath}")
        }
        if (!directory.isDirectory) {
            throw IOException("$label path is not a directory: ${directory.absolutePath}")
        }
    }

    fun writeUtf8Atomically(
        directory: File,
        targetFile: File,
        tempPrefix: String,
        content: String,
        failureMessage: String,
    ) {
        val tempFile = File(directory, "$tempPrefix.${UUID.randomUUID()}.tmp")
        try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException(failureMessage, failure)
        } finally {
            runCatching {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }
}
