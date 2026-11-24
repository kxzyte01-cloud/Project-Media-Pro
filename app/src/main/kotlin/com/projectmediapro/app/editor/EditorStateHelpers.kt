package com.projectmediapro.app.editor

import com.projectmediapro.app.data.export.VideoExportException
import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.ImportedAudio
import com.projectmediapro.app.data.media.ImportedOverlay
import com.projectmediapro.app.data.media.OverlayImporter
import com.projectmediapro.app.data.media.ProjectMediaHealthChecker
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.UndoRedoManager
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.overlayClips
import com.projectmediapro.core.model.textClips
import com.projectmediapro.core.model.videoClips

internal object FailingAudioImporter : AudioImporter {
    override suspend fun importAudio(projectId: String, sourceUri: String): ImportedAudio {
        throw VideoImportException(Reason.FAILED)
    }
}

internal object FailingOverlayImporter : OverlayImporter {
    override suspend fun importOverlay(projectId: String, sourceUri: String): ImportedOverlay {
        throw VideoImportException(Reason.FAILED)
    }
}

internal data class ClipLocation(
    val trackType: TrackType,
    val clip: Clip,
    val indexInTrack: Int,
    val trackClips: List<Clip>,
) {
    val isVideo: Boolean get() = trackType == TrackType.VIDEO
    val isAudio: Boolean get() = trackType == TrackType.AUDIO
    val isOverlay: Boolean get() = trackType == TrackType.OVERLAY
    val isText: Boolean get() = trackType == TrackType.TEXT
    val isFreeTimed: Boolean get() = isOverlay || isText
    val clipNumber: Int get() = indexInTrack + 1
    val canMoveEarlier: Boolean get() = !isFreeTimed && indexInTrack > 0
    val canMoveLater: Boolean get() = !isFreeTimed && indexInTrack in 0 until trackClips.lastIndex
}

internal fun Project.findClipLocation(clipId: String?): ClipLocation? {
    if (clipId == null) return null
    val tracksToCheck = listOf(
        TrackType.VIDEO to videoClips(),
        TrackType.AUDIO to audioClips(),
        TrackType.OVERLAY to overlayClips(),
        TrackType.TEXT to textClips(),
    )
    for ((trackType, clips) in tracksToCheck) {
        val index = clips.indexOfFirst { it.id == clipId }
        if (index >= 0) {
            return ClipLocation(trackType, clips[index], index, clips)
        }
    }
    return null
}

internal fun resolvePreviewClips(
    project: Project,
    mediaStorage: ProjectMediaStorage,
): List<PreviewClip> =
    project.videoClips().mapNotNull { clip ->
        val file = mediaStorage.resolve(clip.mediaUri)
        if (file == null || !ProjectMediaHealthChecker.isHealthyMediaFile(file)) return@mapNotNull null
        val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
        PreviewClip(
            path = file.absolutePath,
            durationMs = ProjectTimeline.effectiveDurationMs(clip),
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            volume = clip.volume,
            audioBoost = ProjectTimeline.sanitizeAudioBoost(clip.audioBoost),
            audioEnhance = clip.audioEnhance?.takeUnless { it.isIdentity },
            playbackSpeed = ProjectTimeline.effectiveSpeed(clip),
            rotationDegrees = ProjectTimeline.normalizeRotationDegrees(clip.rotationDegrees),
            flipHorizontal = clip.flipHorizontal,
            flipVertical = clip.flipVertical,
            crop = clip.crop?.sanitized()?.takeUnless { it.isIdentity },
            isStillImage = clip.isStillImage,
        )
    }

internal fun resolvePreviewAudioClips(
    project: Project,
    mediaStorage: ProjectMediaStorage,
): List<PreviewClip> =
    project.audioClips().mapNotNull { clip ->
        val file = mediaStorage.resolve(clip.mediaUri)
        if (file == null || !ProjectMediaHealthChecker.isHealthyMediaFile(file)) return@mapNotNull null
        val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
        PreviewClip(
            path = file.absolutePath,
            durationMs = ProjectTimeline.effectiveDurationMs(clip),
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            volume = clip.volume,
            audioBoost = ProjectTimeline.sanitizeAudioBoost(clip.audioBoost),
            audioEnhance = clip.audioEnhance?.takeUnless { it.isIdentity },
            playbackSpeed = ProjectTimeline.effectiveSpeed(clip),
        )
    }

internal fun resolveOverlayFilePaths(
    project: Project,
    mediaStorage: ProjectMediaStorage,
): Map<String, String> =
    buildMap {
        for (clip in project.overlayClips()) {
            val file = mediaStorage.resolve(clip.mediaUri)
            if (file != null && ProjectMediaHealthChecker.isHealthyMediaFile(file)) {
                put(clip.id, file.absolutePath)
            }
        }
    }

internal fun resolveClipFilePaths(
    project: Project,
    mediaStorage: ProjectMediaStorage,
): Map<String, String> =
    buildMap {
        for (clip in project.videoClips() + project.audioClips()) {
            val file = mediaStorage.resolve(clip.mediaUri)
            if (file != null && ProjectMediaHealthChecker.isHealthyMediaFile(file)) {
                put(clip.id, file.absolutePath)
            }
        }
    }

internal fun resolveAudioPreview(
    clip: Clip,
    mediaStorage: ProjectMediaStorage,
): AudioPreviewState? {
    val file = mediaStorage.resolve(clip.mediaUri)
    if (file == null || !ProjectMediaHealthChecker.isHealthyMediaFile(file)) return null
    val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
    return AudioPreviewState(
        clipId = clip.id,
        path = file.absolutePath,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        volume = ProjectTimeline.sanitizeVolume(clip.volume),
        audioBoost = ProjectTimeline.sanitizeAudioBoost(clip.audioBoost),
        audioEnhance = clip.audioEnhance?.takeUnless { it.isIdentity },
        playbackSpeed = ProjectTimeline.effectiveSpeed(clip),
    )
}

internal fun resolveClipSelection(
    previousProject: Project,
    previousSelectedId: String?,
    updatedProject: Project,
): String? {
    val newVideos = updatedProject.videoClips()
    val newAudios = updatedProject.audioClips()
    val newOverlays = updatedProject.overlayClips()
    val newTexts = updatedProject.textClips()
    if (newVideos.isEmpty() && newAudios.isEmpty() && newOverlays.isEmpty() && newTexts.isEmpty()) {
        return null
    }

    if (previousSelectedId != null) {
        if (updatedProject.findClipLocation(previousSelectedId) != null) {
            return previousSelectedId
        }
        val oldLocation = previousProject.findClipLocation(previousSelectedId)
        if (oldLocation != null) {
            val sameTrackNewClips = when (oldLocation.trackType) {
                TrackType.VIDEO -> newVideos
                TrackType.AUDIO -> newAudios
                TrackType.OVERLAY -> newOverlays
                TrackType.TEXT -> newTexts
            }
            if (sameTrackNewClips.isNotEmpty()) {
                return sameTrackNewClips[oldLocation.indexInTrack.coerceAtMost(sameTrackNewClips.lastIndex)].id
            }
        }
    }

    return newVideos.firstOrNull()?.id
        ?: newAudios.firstOrNull()?.id
        ?: newOverlays.firstOrNull()?.id
        ?: newTexts.firstOrNull()?.id
}

internal fun EditorUiState.Loaded.withSyncedProject(
    previousProject: Project,
    targetProject: Project,
    preferredSelectedId: String?,
    mediaStorage: ProjectMediaStorage,
    history: UndoRedoManager<Project>,
    keepActiveDialog: Boolean = false,
): EditorUiState.Loaded = copy(
    project = targetProject,
    previewClips = resolvePreviewClips(targetProject, mediaStorage),
    previewAudioClips = resolvePreviewAudioClips(targetProject, mediaStorage),
    overlayFilePaths = resolveOverlayFilePaths(targetProject, mediaStorage),
    clipFilePaths = resolveClipFilePaths(targetProject, mediaStorage),
    selectedClipId = resolveClipSelection(
        previousProject = previousProject,
        previousSelectedId = preferredSelectedId,
        updatedProject = targetProject,
    ),
    canUndo = history.canUndo,
    canRedo = history.canRedo,
    missingMediaCount = ProjectMediaHealthChecker.findMissingMedia(targetProject, mediaStorage).size,
    activeDialog = if (keepActiveDialog) activeDialog else null,
    importingTrack = null,
    audioPreview = null,
    error = null,
)

internal fun EditorUiState.Loaded.withSaveFailedRollback(
    previousProject: Project,
    mediaStorage: ProjectMediaStorage,
    history: UndoRedoManager<Project>,
): EditorUiState.Loaded = copy(
    project = previousProject,
    previewClips = resolvePreviewClips(previousProject, mediaStorage),
    previewAudioClips = resolvePreviewAudioClips(previousProject, mediaStorage),
    overlayFilePaths = resolveOverlayFilePaths(previousProject, mediaStorage),
    clipFilePaths = resolveClipFilePaths(previousProject, mediaStorage),
    canUndo = history.canUndo,
    canRedo = history.canRedo,
    missingMediaCount = ProjectMediaHealthChecker.findMissingMedia(previousProject, mediaStorage).size,
    activeDialog = null,
    importingTrack = null,
    audioPreview = null,
    error = EditorError.SAVE_FAILED,
)

internal fun mapImportError(
    reason: Reason,
    trackType: TrackType,
): EditorError = when (reason) {
    Reason.NOT_A_VIDEO -> when (trackType) {
        TrackType.AUDIO -> EditorError.IMPORT_NOT_AUDIO
        TrackType.OVERLAY -> EditorError.IMPORT_NOT_IMAGE
        else -> EditorError.IMPORT_NOT_A_VIDEO
    }
    Reason.NO_SPACE -> EditorError.IMPORT_NO_SPACE
    Reason.UNREADABLE, Reason.FAILED -> EditorError.IMPORT_FAILED
}

internal fun mapExportError(reason: VideoExportException.Reason): EditorError = when (reason) {
    VideoExportException.Reason.UNSUPPORTED -> EditorError.EXPORT_UNSUPPORTED
    VideoExportException.Reason.NO_SPACE -> EditorError.EXPORT_NO_SPACE
    VideoExportException.Reason.FAILED -> EditorError.EXPORT_FAILED
}
