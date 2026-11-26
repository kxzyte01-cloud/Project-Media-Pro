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
    private const val MAX_JSON_BYTES = 2L * 1024L * 1024L
    private const val MAX_ENTRIES = 128

    @Throws(IOException::class)
    fun exportTemplatePack(
        template: Template,
        mediaStorage: ProjectMediaStorage,
        outputStream: OutputStream,
    ) {
        ZipOutputStream(outputStream).use { zipOut ->
            val jsonBytes = TemplateSerializer.serialize(template).toByteArray(Charsets.UTF_8)
            if (jsonBytes.size.toLong() > MAX_JSON_BYTES) {
                throw IOException("Template metadata exceeds maximum allowed pack JSON size")
            }
            zipOut.putNextEntry(ZipEntry(TEMPLATE_JSON_ENTRY))
            zipOut.write(jsonBytes)
            zipOut.closeEntry()

            var exportedBytes = jsonBytes.size.toLong()
            var exportedEntries = 1
            val seenUris = LinkedHashSet<String>()
            val seenEntryNames = HashSet<String>()
            template.slots.forEach { slot ->
                val rel = slot.filledMediaUri?.takeIf { it.isNotBlank() } ?: return@forEach
                if (!seenUris.add(rel)) return@forEach
                val file = mediaStorage.resolve(rel)
                    ?: throw IOException("Missing locked template media for slot ${slot.id}: $rel")
                if (!file.isFile) {
                    throw IOException("Locked template media file does not exist for slot ${slot.id}: $rel")
                }
                exportedEntries += 1
                if (exportedEntries > MAX_ENTRIES) {
                    throw IOException("Template pack exceeds maximum entry limit ($MAX_ENTRIES)")
                }
                exportedBytes += file.length()
                if (exportedBytes > MAX_PACK_BYTES) {
                    throw IOException("Template pack exceeds maximum size limit ($MAX_PACK_BYTES bytes)")
                }

                val safeEntryName = "$MEDIA_PREFIX${file.name}"
                if (!seenEntryNames.add(safeEntryName)) {
                    throw IOException("Duplicate media filename in template pack: ${file.name}")
                }
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
                    if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains('\\')) {
                        throw IOException("Template pack contains unsafe entry path: $entryName")
                    }
                    if (entry.isDirectory) {
                        zipIn.closeEntry()
                        continue
                    }

                    if (entryName == TEMPLATE_JSON_ENTRY) {
                        val jsonCap = minOf(MAX_JSON_BYTES, MAX_PACK_BYTES - totalBytes)
                        val bytes = readBounded(zipIn, jsonCap)
                        totalBytes += bytes.size
                        parsedTemplate = TemplateSerializer.deserialize(bytes.toString(Charsets.UTF_8))
                    } else if (entryName.startsWith(MEDIA_PREFIX)) {
                        val rawName = entryName.removePrefix(MEDIA_PREFIX)
                        if (rawName.isEmpty() || rawName.contains('/')) {
                            throw IOException("Invalid media entry in template pack: $entryName")
                        }
                        if (extractedByFileName.containsKey(rawName)) {
                            throw IOException("Duplicate media entry in template pack: $rawName")
                        }
                        val ext = rawName.substringAfterLast('.', "mp4").lowercase()
                        val clipId = idProvider()
                        val relPath = mediaStorage.clipRelativePath(newTemplateId, clipId, ext)
                        val destFile = mediaStorage.resolve(relPath)
                            ?: throw IOException("Could not resolve media destination")
                        destFile.parentFile?.mkdirs()
                        val written = copyBounded(zipIn, destFile, MAX_PACK_BYTES - totalBytes)
                        totalBytes += written
                        extractedByFileName[rawName] = relPath
                    } else {
                        throw IOException("Unexpected entry in template pack: $entryName")
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
        } catch (error: Throwable) {
            mediaStorage.deleteProjectMedia(newTemplateId)
            if (error is Error) throw error
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
