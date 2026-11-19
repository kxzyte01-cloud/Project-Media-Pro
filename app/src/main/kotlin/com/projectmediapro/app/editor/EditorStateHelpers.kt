package com.projectmediapro.app.editor

import com.projectmediapro.app.data.export.VideoExportException
import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.ImportedAudio
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImportException.Reason
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.UndoRedoManager
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.videoClips

internal object FailingAudioImporter : AudioImporter {
    override suspend fun importAudio(projectId: String, sourceUri: String): ImportedAudio {
        throw VideoImportException(Reason.FAILED)
    }
}

internal data class ClipLocation(
    val trackType: TrackType,
    val clip: Clip,
    val indexInTrack: Int,
    val trackClips: List<Clip>,
) {
    val isAudio: Boolean get() = trackType == TrackType.AUDIO
    val clipNumber: Int get() = indexInTrack + 1
    val canMoveEarlier: Boolean get() = indexInTrack > 0
    val canMoveLater: Boolean get() = indexInTrack in 0 until trackClips.lastIndex
}

internal fun Project.findClipLocation(clipId: String?): ClipLocation? {
    if (clipId == null) return null
    val videos = videoClips()
    val videoIndex = videos.indexOfFirst { it.id == clipId }
    if (videoIndex >= 0) {
        return ClipLocation(TrackType.VIDEO, videos[videoIndex], videoIndex, videos)
    }
    val audios = audioClips()
    val audioIndex = audios.indexOfFirst { it.id == clipId }
    if (audioIndex >= 0) {
        return ClipLocation(TrackType.AUDIO, audios[audioIndex], audioIndex, audios)
    }
    return null
}

internal fun resolvePreviewClips(
    project: Project,
    mediaStorage: ProjectMediaStorage,
): List<PreviewClip> =
    project.videoClips().mapNotNull { clip ->
        val file = mediaStorage.resolve(clip.mediaUri)
        if (file == null || !file.isFile) return@mapNotNull null
        val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
        PreviewClip(
            path = file.absolutePath,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
        )
    }

internal fun resolveAudioPreview(
    clip: Clip,
    mediaStorage: ProjectMediaStorage,
): AudioPreviewState? {
    val file = mediaStorage.resolve(clip.mediaUri)
    if (file == null || !file.isFile) return null
    val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(clip)
    return AudioPreviewState(
        clipId = clip.id,
        path = file.absolutePath,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        volume = clip.volume.coerceIn(0f, 1f),
    )
}

internal fun resolveClipSelection(
    previousProject: Project,
    previousSelectedId: String?,
    updatedProject: Project,
): String? {
    val newVideoClips = updatedProject.videoClips()
    val newAudioClips = updatedProject.audioClips()
    if (newVideoClips.isEmpty() && newAudioClips.isEmpty()) return null

    if (previousSelectedId != null) {
        if (updatedProject.findClipLocation(previousSelectedId) != null) {
            return previousSelectedId
        }
        val oldLocation = previousProject.findClipLocation(previousSelectedId)
        if (oldLocation != null) {
            val sameTrackNewClips = if (oldLocation.isAudio) newAudioClips else newVideoClips
            val fallbackNewClips = if (oldLocation.isAudio) newVideoClips else newAudioClips
            return if (sameTrackNewClips.isNotEmpty()) {
                sameTrackNewClips[oldLocation.indexInTrack.coerceAtMost(sameTrackNewClips.lastIndex)].id
            } else {
                fallbackNewClips.firstOrNull()?.id
            }
        }
    }

    return newVideoClips.firstOrNull()?.id ?: newAudioClips.firstOrNull()?.id
}

internal fun EditorUiState.Loaded.withSyncedProject(
    previousProject: Project,
    targetProject: Project,
    preferredSelectedId: String?,
    mediaStorage: ProjectMediaStorage,
    history: UndoRedoManager<Project>,
): EditorUiState.Loaded = copy(
    project = targetProject,
    previewClips = resolvePreviewClips(targetProject, mediaStorage),
    selectedClipId = resolveClipSelection(
        previousProject = previousProject,
        previousSelectedId = preferredSelectedId,
        updatedProject = targetProject,
    ),
    canUndo = history.canUndo,
    canRedo = history.canRedo,
    activeDialog = null,
    isImporting = false,
    isImportingAudio = false,
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
    canUndo = history.canUndo,
    canRedo = history.canRedo,
    activeDialog = null,
    isImporting = false,
    isImportingAudio = false,
    audioPreview = null,
    error = EditorError.SAVE_FAILED,
)

internal fun mapImportError(
    reason: Reason,
    trackType: TrackType,
): EditorError = when (reason) {
    Reason.NOT_A_VIDEO -> if (trackType == TrackType.AUDIO) {
        EditorError.IMPORT_NOT_AUDIO
    } else {
        EditorError.IMPORT_NOT_A_VIDEO
    }
    Reason.NO_SPACE -> EditorError.IMPORT_NO_SPACE
    Reason.UNREADABLE, Reason.FAILED -> EditorError.IMPORT_FAILED
}

internal fun mapExportError(reason: VideoExportException.Reason): EditorError = when (reason) {
    VideoExportException.Reason.UNSUPPORTED -> EditorError.EXPORT_UNSUPPORTED
    VideoExportException.Reason.NO_SPACE -> EditorError.EXPORT_NO_SPACE
    VideoExportException.Reason.FAILED -> EditorError.EXPORT_FAILED
}
