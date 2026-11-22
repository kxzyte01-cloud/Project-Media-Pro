package com.projectmediapro.app.data.template

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.TemplateSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pure-JVM codec that exports and imports portable offline `.zip` Template Packs (`template.json`
 * plus any bundled fixed media files) with strict zip-slip path traversal protection.
 */
object TemplatePackCodec {

    private const val TEMPLATE_JSON_ENTRY = "template.json"
    private const val MEDIA_PREFIX = "media/"
    private const val MAX_PACK_BYTES = 250L * 1024L * 1024L
    private const val MAX_ENTRIES = 128

    @Throws(IOException::class)
    fun exportTemplatePack(
        template: Template,
        mediaStorage: ProjectMediaStorage,
        outputStream: OutputStream,
    ) {
        ZipOutputStream(outputStream).use { zipOut ->
            val jsonBytes = TemplateSerializer.serialize(template).toByteArray(Charsets.UTF_8)
            zipOut.putNextEntry(ZipEntry(TEMPLATE_JSON_ENTRY))
            zipOut.write(jsonBytes)
            zipOut.closeEntry()

            val seenUris = LinkedHashSet<String>()
            template.slots.forEach { slot ->
                val rel = slot.filledMediaUri?.takeIf { it.isNotBlank() } ?: return@forEach
                if (!seenUris.add(rel)) return@forEach
                val file = mediaStorage.resolve(rel) ?: return@forEach
                if (!file.isFile) return@forEach

                val safeEntryName = "$MEDIA_PREFIX${file.name}"
                zipOut.putNextEntry(ZipEntry(safeEntryName))
                FileInputStream(file).use { input -> input.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }
    }

    @Throws(IOException::class)
    fun importTemplatePack(
        inputStream: InputStream,
        mediaStorage: ProjectMediaStorage,
        idProvider: () -> String = { UUID.randomUUID().toString() },
    ): Template {
        val newTemplateId = idProvider()
        var parsedTemplate: Template? = null
        val extractedByFileName = mutableMapOf<String, String>()
        var totalBytes = 0L
        var entryCount = 0

        try {
            ZipInputStream(inputStream).use { zipIn ->
                while (true) {
                    val entry = zipIn.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) {
                        throw IOException("Template pack contains too many entries")
                    }
                    val entryName = entry.name
                    if (entry.isDirectory || entryName.contains("..") || entryName.startsWith("/") || entryName.contains('\\')) {
                        zipIn.closeEntry()
                        continue
                    }

                    if (entryName == TEMPLATE_JSON_ENTRY) {
                        val bytes = readBounded(zipIn, MAX_PACK_BYTES - totalBytes)
                        totalBytes += bytes.size
                        parsedTemplate = TemplateSerializer.deserialize(bytes.toString(Charsets.UTF_8))
                    } else if (entryName.startsWith(MEDIA_PREFIX)) {
                        val rawName = entryName.removePrefix(MEDIA_PREFIX)
                        if (rawName.isNotEmpty() && !rawName.contains('/')) {
                            val ext = rawName.substringAfterLast('.', "mp4").lowercase()
                            val clipId = idProvider()
                            val relPath = mediaStorage.clipRelativePath(newTemplateId, clipId, ext)
                            val destFile = mediaStorage.resolve(relPath)
                                ?: throw IOException("Could not resolve media destination")
                            destFile.parentFile?.mkdirs()
                            val written = copyBounded(zipIn, destFile, MAX_PACK_BYTES - totalBytes)
                            totalBytes += written
                            extractedByFileName[rawName] = relPath
                        }
                    }
                    zipIn.closeEntry()
                }
            }

            val source = parsedTemplate ?: throw IOException("Template pack is missing template.json")
            val now = System.currentTimeMillis()
            val remappedSlots = source.slots.map { slot ->
                val oldUri = slot.filledMediaUri
                val newUri = if (!oldUri.isNullOrBlank()) {
                    val oldFileName = oldUri.substringAfterLast('/')
                    extractedByFileName[oldFileName]
                } else {
                    null
                }
                slot.copy(
                    id = idProvider(),
                    filledMediaUri = newUri,
                )
            }
            return source.copy(
                id = newTemplateId,
                slots = remappedSlots,
                createdAt = now,
                updatedAt = now,
            )
        } catch (error: Exception) {
            mediaStorage.deleteProjectMedia(newTemplateId)
            if (error is IOException) throw error
            throw IOException("Invalid or corrupted template pack", error)
        }
    }

    private fun readBounded(input: InputStream, maxRemaining: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buf)
            if (read < 0) break
            total += read
            if (total > maxRemaining) {
                throw IOException("Template pack exceeds maximum size")
            }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    private fun copyBounded(input: InputStream, dest: File, maxRemaining: Long): Long {
        var total = 0L
        FileOutputStream(dest).use { out ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                total += read
                if (total > maxRemaining) {
                    throw IOException("Template pack exceeds maximum size")
                }
                out.write(buf, 0, read)
            }
            out.fd.sync()
        }
        return total
    }
}
