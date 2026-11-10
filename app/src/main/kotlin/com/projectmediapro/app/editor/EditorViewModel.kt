package com.projectmediapro.app.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.media.VideoImportDataSource
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.Track
import com.projectmediapro.core.model.TrackKind
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorUiState(
    val project: Project? = null,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val error: EditorError? = null,
)

enum class EditorError { PROJECT_NOT_FOUND, IMPORT_FAILED }

/** Loads and persists one editing project. */
class EditorViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val videoImportDataSource: VideoImportDataSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val project = repository.getProject(projectId)
                _uiState.value = EditorUiState(
                    project = project,
                    isLoading = false,
                    error = if (project == null) EditorError.PROJECT_NOT_FOUND else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = EditorUiState(
                    isLoading = false,
                    error = EditorError.PROJECT_NOT_FOUND,
                )
            }
        }
    }

    fun importVideo(uri: Uri) {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            val current = _uiState.value.project
            if (current == null) {
                _uiState.value = _uiState.value.copy(error = EditorError.PROJECT_NOT_FOUND)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isImporting = true, error = null)
            try {
                val imported = videoImportDataSource.importVideo(uri)
                val videoTrack = current.tracks.firstOrNull { it.kind == TrackKind.VIDEO }
                val existingClips = videoTrack?.clips.orEmpty()
                val startTime = existingClips.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 0L
                val clip = Clip(
                    id = UUID.randomUUID().toString(),
                    sourceUri = imported.sourceUri,
                    startTimeMs = startTime,
                    durationMs = imported.durationMs.coerceAtLeast(1L),
                )
                val tracks = if (videoTrack == null) {
                    current.tracks + Track(
                        id = UUID.randomUUID().toString(),
                        kind = TrackKind.VIDEO,
                        clips = listOf(clip),
                    )
                } else {
                    current.tracks.map { track ->
                        if (track.id == videoTrack.id) track.copy(clips = track.clips + clip) else track
                    }
                }
                val updated = current.copy(
                    tracks = tracks,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                repository.saveProject(updated)
                _uiState.value = _uiState.value.copy(
                    project = updated,
                    isImporting = false,
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = EditorError.IMPORT_FAILED,
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            projectId: String,
            repository: ProjectRepository,
            videoImportDataSource: VideoImportDataSource,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(EditorViewModel::class.java))
                return EditorViewModel(projectId, repository, videoImportDataSource) as T
            }
        }
    }
}
