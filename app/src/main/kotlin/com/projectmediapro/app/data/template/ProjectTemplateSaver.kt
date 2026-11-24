package com.projectmediapro.app.data.template

import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.copyStreamAtomically
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectToTemplateConverter
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.overlayClips
import com.projectmediapro.core.model.videoClips
import com.projectmediapro.feature.captions.CaptionDocument
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Converts a live edited [Project] into a reusable saved [Template].
 * Copies media atomically into [templateMediaStorage] ONLY for locked clips so replaceable slots never retain private footage.
 */
class ProjectTemplateSaver(
    private val templateRepository: TemplateRepository,
    private val projectMediaStorage: ProjectMediaStorage,
    private val templateMediaStorage: ProjectMediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun saveProjectAsTemplate(
        project: Project,
        templateName: String,
        description: String,
        replaceableClipIds: Set<String>,
        captionDocument: CaptionDocument? = null,
    ): Template {
        val cleanName = templateName.trim().take(TemplateRepository.MAX_NAME_LENGTH)
        if (cleanName.isEmpty()) {
            throw IOException("Template name cannot be empty.")
        }

        val created = templateRepository.createTemplate(cleanName)
        try {
            val lockedPaths = mutableMapOf<String, String>()
            val allMediaClips = project.videoClips() + project.audioClips() + project.overlayClips()
            for (clip in allMediaClips) {
                if (clip.id in replaceableClipIds) continue
                if (clip.mediaUri.isBlank()) continue
                val copied = copyLockedProjectFile(
                    templateId = created.id,
                    projectRelativePath = clip.mediaUri,
                )
                if (copied != null) {
                    lockedPaths[clip.id] = copied
                }
            }

            val serializedCaptionStyle = captionDocument?.style?.encodeToJson()

            val fullTemplate = ProjectToTemplateConverter.fromProject(
                baseTemplate = created,
                project = project,
                description = description,
                replaceableClipIds = replaceableClipIds,
                lockedMediaPathsByClipId = lockedPaths,
                captionStyleJson = serializedCaptionStyle,
                idProvider = idProvider,
            )
            templateRepository.saveTemplate(fullTemplate)
            return fullTemplate
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                try {
                    templateRepository.deleteTemplate(created.id)
                } catch (_: Exception) {
                    // Best-effort rollback of incomplete record.
                }
            }
            throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                try {
                    templateRepository.deleteTemplate(created.id)
                } catch (_: Exception) {
                    // Best-effort rollback of incomplete record.
                }
            }
            throw failure
        }
    }

    private suspend fun copyLockedProjectFile(
        templateId: String,
        projectRelativePath: String,
    ): String? = withContext(ioDispatcher) {
        val sourceFile: File = projectMediaStorage.resolve(projectRelativePath)
            ?.takeIf { it.isFile }
            ?: return@withContext null
        val extension = sourceFile.extension.ifBlank { "mp4" }
        val targetRel = templateMediaStorage.clipRelativePath(templateId, idProvider(), extension)
        val targetFile = templateMediaStorage.resolve(targetRel)
            ?: throw IOException("Could not resolve template media destination.")
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        sourceFile.inputStream().use { input ->
            copyStreamAtomically(
                input = input,
                partFile = partFile,
                finalFile = targetFile,
                onChunk = { ensureActive() },
            )
        }
        targetRel
    }
}
