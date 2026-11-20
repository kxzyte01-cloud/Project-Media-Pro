package com.projectmediapro.feature.captions

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface CaptionRepository {
    suspend fun loadCaptions(projectId: String): CaptionDocument?
    suspend fun saveCaptions(document: CaptionDocument)
    suspend fun deleteCaptions(projectId: String)
}

class FileCaptionRepository(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CaptionRepository {

    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun loadCaptions(projectId: String): CaptionDocument? {
        if (!isValidId(projectId)) return null
        return runOnIo {
            mutex.withLock {
                val target = captionFile(projectId)
                if (!target.isFile) return@withLock null
                try {
                    val decoded = json.decodeFromString(
                        CaptionDocument.serializer(),
                        target.readText(Charsets.UTF_8),
                    )
                    decoded.takeIf { it.schemaVersion == CaptionDocument.CURRENT_SCHEMA_VERSION }
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    override suspend fun saveCaptions(document: CaptionDocument) {
        if (!isValidId(document.projectId)) {
            throw IOException("Invalid projectId for captions: ${document.projectId}")
        }
        runOnIo {
            mutex.withLock {
                ensureDirectory()
                writeAtomically(document)
            }
        }
    }

    override suspend fun deleteCaptions(projectId: String) {
        if (!isValidId(projectId)) return
        runOnIo {
            mutex.withLock {
                val target = captionFile(projectId)
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not delete caption file: ${target.name}")
                }
            }
        }
    }

    private suspend fun <T> runOnIo(block: suspend () -> T): T = withContext(ioDispatcher) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (denied: SecurityException) {
            throw IOException("Caption storage access denied", denied)
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create captions directory: ${directory.absolutePath}")
        }
        if (!directory.isDirectory) {
            throw IOException("Captions path is not a directory: ${directory.absolutePath}")
        }
    }

    private fun writeAtomically(document: CaptionDocument) {
        val targetFile = captionFile(document.projectId)
        val tempFile = File(directory, "$FILE_SUFFIX.${document.projectId}.${UUID.randomUUID()}.tmp")
        try {
            val bytes = json.encodeToString(CaptionDocument.serializer(), document).toByteArray(Charsets.UTF_8)
            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            // Some Android FUSE/scoped filesystems reject ATOMIC_MOVE; fall back after fsync.
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
            throw IOException("Failed to write captions atomically", failure)
        } finally {
            runCatching {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    private fun captionFile(projectId: String): File = File(directory, "$projectId$FILE_SUFFIX")

    private companion object {
        const val FILE_SUFFIX = ".captions.json"
        val ID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun isValidId(id: String): Boolean = ID_REGEX.matches(id)
    }
}
