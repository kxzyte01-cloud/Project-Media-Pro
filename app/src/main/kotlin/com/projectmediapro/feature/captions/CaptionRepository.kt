package com.projectmediapro.feature.captions

import com.projectmediapro.app.data.AtomicFileStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                AtomicFileStore.ensureDirectory(directory, "captions")
                AtomicFileStore.writeUtf8Atomically(
                    directory = directory,
                    targetFile = captionFile(document.projectId),
                    tempPrefix = "$FILE_SUFFIX.${document.projectId}",
                    content = json.encodeToString(CaptionDocument.serializer(), document),
                    failureMessage = "Failed to write captions atomically",
                )
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

    private suspend fun <T> runOnIo(block: suspend () -> T): T = AtomicFileStore.runOnIo(
        ioDispatcher = ioDispatcher,
        accessDeniedMessage = "Caption storage access denied",
        block = block,
    )

    private fun captionFile(projectId: String): File = File(directory, "$projectId$FILE_SUFFIX")

    private companion object {
        const val FILE_SUFFIX = ".captions.json"
        val ID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun isValidId(id: String): Boolean = ID_REGEX.matches(id)
    }
}
