package com.projectmediapro.app.data.template

import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.OverlayImporter
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.media.copyStreamAtomically
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.ResolvedTemplateMedia
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.TemplateApplier
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.feature.captions.CaptionDocument
import com.projectmediapro.feature.captions.CaptionRepository
import com.projectmediapro.feature.captions.CaptionStyle
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Carries the exact [trackType] and [reason] of a failed template slot import so callers map the right error.
 */
class TemplateSlotImportException(
    val trackType: TrackType,
    val reason: Reason,
    cause: Throwable? = null,
) : Exception("${trackType.name}:${reason.name}", cause)

/**
 * Materializes a [Template] plus user-picked replacement URIs into a standalone saved [Project].
 * Locked template files are copied atomically into project storage so the project stays self-contained.
 */
class TemplateProjectCreator(
    private val projectRepository: ProjectRepository,
    private val projectMediaStorage: ProjectMediaStorage,
    private val templateMediaStorage: ProjectMediaStorage,
    private val videoImporter: VideoImporter,
    private val audioImporter: AudioImporter,
    private val overlayImporter: OverlayImporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val captionRepository: CaptionRepository? = null,
) {

    private val styleJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createProjectFromTemplate(
        template: Template,
        projectTitle: String,
        pickedUrisBySlotId: Map<String, String>,
    ): String {
        val created = projectRepository.createProject(projectTitle)
        try {
            val resolvedMedia = mutableMapOf<String, ResolvedTemplateMedia>()
            for (slot in template.slots) {
                val pickedUri = pickedUrisBySlotId[slot.id]
                val filledUri = slot.filledMediaUri
                if (pickedUri != null) {
                    resolvedMedia[slot.id] = importPickedSlot(created.id, slot.requiredType, pickedUri)
                } else if (filledUri != null) {
                    val copiedPath = copyLockedTemplateFile(created.id, slot.requiredType, filledUri)
                    if (copiedPath != null) {
                        resolvedMedia[slot.id] = ResolvedTemplateMedia(
                            relativePath = copiedPath,
                            durationMs = slot.durationMs,
                        )
                    }
                }
            }

            val assembled = TemplateApplier.buildProjectFromTemplate(
                baseProject = created,
                template = template,
                resolvedMediaBySlotId = resolvedMedia,
                idProvider = idProvider,
            )
            projectRepository.saveProject(assembled)
            applyTemplateCaptionStyle(assembled.id, template.captionStyleJson)
            return assembled.id
        } catch (cancelled: CancellationException) {
            runCatching { projectRepository.deleteProject(created.id) }
            throw cancelled
        } catch (failure: Exception) {
            runCatching { projectRepository.deleteProject(created.id) }
            throw failure
        }
    }

    private suspend fun importPickedSlot(
        projectId: String,
        trackType: TrackType,
        sourceUri: String,
    ): ResolvedTemplateMedia {
        return try {
            when (trackType) {
                TrackType.AUDIO -> {
                    val imported = audioImporter.importAudio(projectId, sourceUri)
                    ResolvedTemplateMedia(imported.relativePath, imported.durationMs)
                }
                TrackType.OVERLAY -> {
                    val imported = overlayImporter.importOverlay(projectId, sourceUri)
                    ResolvedTemplateMedia(imported.relativePath, 0L)
                }
                else -> {
                    val imported = videoImporter.importVideo(projectId, sourceUri)
                    ResolvedTemplateMedia(imported.relativePath, imported.durationMs)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: VideoImportException) {
            throw TemplateSlotImportException(trackType, failure.reason, failure)
        }
    }

    private suspend fun copyLockedTemplateFile(
        projectId: String,
        trackType: TrackType,
        templateRelativePath: String,
    ): String? = withContext(ioDispatcher) {
        val sourceFile: File = templateMediaStorage.resolve(templateRelativePath)
            ?.takeIf { it.isFile }
            ?: return@withContext null
        val extension = sourceFile.extension.ifBlank { "mp4" }
        val targetRel = projectMediaStorage.clipRelativePath(projectId, idProvider(), extension)
        val targetFile = projectMediaStorage.resolve(targetRel)
            ?: throw TemplateSlotImportException(trackType, Reason.FAILED)
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        try {
            sourceFile.inputStream().use { input ->
                copyStreamAtomically(
                    input = input,
                    partFile = partFile,
                    finalFile = targetFile,
                    onChunk = { ensureActive() },
                )
            }
            targetRel
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ioError: IOException) {
            throw TemplateSlotImportException(trackType, Reason.FAILED, ioError)
        }
    }

    private suspend fun applyTemplateCaptionStyle(projectId: String, captionStyleJson: String?) {
        val repo = captionRepository ?: return
        val raw = captionStyleJson?.takeIf { it.isNotBlank() } ?: return
        val decodedStyle = try {
            styleJson.decodeFromString(CaptionStyle.serializer(), raw)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        try {
            repo.saveCaptions(
                CaptionDocument(
                    projectId = projectId,
                    style = decodedStyle,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Best-effort caption style prefill must not fail project creation.
        }
    }
}
