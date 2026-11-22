package com.projectmediapro.app.data.template

import com.projectmediapro.app.data.AtomicFileStore
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.project.FileProjectRepository
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.TemplateBuilder
import com.projectmediapro.core.model.TemplateSerializer
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/**
 * File-backed [TemplateRepository] storing one `<id>.pmt.json` file per template in [directory].
 */
class FileTemplateRepository(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val mediaStorage: ProjectMediaStorage? = null,
) : TemplateRepository {

    private val mutex = Mutex()

    override suspend fun listTemplates(): List<Template> = runOnIo {
        mutex.withLock {
            AtomicFileStore.ensureDirectory(directory, "templates")
            val files = directory.listFiles()
                ?: throw IOException("Could not list template directory: ${directory.absolutePath}")
            val templates = mutableListOf<Template>()

            for (file in files) {
                if (!file.isFile || !file.name.endsWith(FILE_SUFFIX)) continue
                val fileId = file.name.removeSuffix(FILE_SUFFIX)
                if (!FileProjectRepository.isValidId(fileId)) continue

                val template = readTemplateSafely(file) ?: continue
                if (template.id != fileId) continue
                if (template.schemaVersion != Template.CURRENT_SCHEMA_VERSION) continue
                templates.add(template)
            }

            templates.sortedWith(
                compareByDescending<Template> { it.updatedAt }.thenBy { it.id },
            )
        }
    }

    override suspend fun getTemplate(id: String): Template? {
        if (!FileProjectRepository.isValidId(id)) return null

        return runOnIo {
            mutex.withLock {
                if (!directory.exists()) return@withLock null
                AtomicFileStore.ensureDirectory(directory, "templates")

                val file = templateFile(id)
                if (!file.isFile) return@withLock null

                val template = readTemplateSafely(file) ?: return@withLock null
                if (template.id != id || template.schemaVersion != Template.CURRENT_SCHEMA_VERSION) {
                    return@withLock null
                }
                template
            }
        }
    }

    override suspend fun createTemplate(name: String): Template = runOnIo {
        mutex.withLock {
            AtomicFileStore.ensureDirectory(directory, "templates")

            val templateId = idProvider()
            val introId = idProvider()
            val videoId = idProvider()
            val outroId = idProvider()
            if (!FileProjectRepository.isValidId(templateId)) {
                throw IOException("Generated template ID is invalid.")
            }
            if (templateFile(templateId).exists()) {
                throw IOException("Generated template ID already exists: $templateId")
            }

            val now = clock()
            val template = Template(
                id = templateId,
                name = normalizeName(name),
                slots = TemplateBuilder.createDefaultSlots(
                    introId = introId,
                    mainVideoId = videoId,
                    outroId = outroId,
                ),
                createdAt = now,
                updatedAt = now,
                schemaVersion = Template.CURRENT_SCHEMA_VERSION,
            )
            writeTemplateAtomically(template)
            template
        }
    }

    override suspend fun saveTemplate(template: Template) {
        if (!FileProjectRepository.isValidId(template.id)) {
            throw IOException("Invalid template id: ${template.id}")
        }
        if (template.schemaVersion != Template.CURRENT_SCHEMA_VERSION) {
            throw IOException("Unsupported template schema version: ${template.schemaVersion}")
        }

        runOnIo {
            mutex.withLock {
                AtomicFileStore.ensureDirectory(directory, "templates")
                val updated = template.copy(
                    name = normalizeName(template.name),
                    updatedAt = clock(),
                )
                writeTemplateAtomically(updated)
            }
        }
    }

    override suspend fun deleteTemplate(id: String) {
        if (!FileProjectRepository.isValidId(id)) {
            throw IOException("Invalid template id: $id")
        }

        runOnIo {
            mutex.withLock {
                AtomicFileStore.ensureDirectory(directory, "templates")
                val target = templateFile(id)
                if (target.exists() && !target.delete()) {
                    throw IOException("Could not delete template file: ${target.name}")
                }
            }
            try {
                mediaStorage?.deleteProjectMedia(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Best-effort cleanup so media deletion failure does not fail deleteTemplate.
            }
        }
    }

    private suspend fun <T> runOnIo(block: suspend () -> T): T = AtomicFileStore.runOnIo(
        ioDispatcher = ioDispatcher,
        accessDeniedMessage = "Access to template storage was denied.",
        block = block,
    )

    private fun templateFile(id: String): File = File(directory, "$id$FILE_SUFFIX")

    private fun readTemplateSafely(file: File): Template? {
        return try {
            TemplateSerializer.deserialize(file.readText(Charsets.UTF_8))
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun writeTemplateAtomically(template: Template) {
        AtomicFileStore.writeUtf8Atomically(
            directory = directory,
            targetFile = templateFile(template.id),
            tempPrefix = "$FILE_SUFFIX.${template.id}",
            content = TemplateSerializer.serialize(template),
            failureMessage = "Failed to write template atomically",
        )
    }

    companion object {
        const val FILE_SUFFIX = ".pmt.json"

        fun normalizeName(rawName: String): String =
            rawName.trim().take(TemplateRepository.MAX_NAME_LENGTH)
    }
}
