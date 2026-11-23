package com.projectmediapro.app.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.app.data.export.ExportPlanner
import com.projectmediapro.app.data.export.ExportSaver
import com.projectmediapro.app.data.export.FrameSnapshotSaver
import com.projectmediapro.app.data.export.VideoExportException
import com.projectmediapro.app.data.export.VideoExporter
import com.projectmediapro.app.data.media.AudioImporter
import com.projectmediapro.app.data.media.FreezeFrameExtractor
import com.projectmediapro.app.data.media.VideoAudioExtractor
import com.projectmediapro.app.data.media.OverlayImporter
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.VideoImportException
import com.projectmediapro.app.data.media.VideoImporter
import com.projectmediapro.app.data.project.ProjectRepository
import com.projectmediapro.app.data.template.ProjectTemplateSaver
import com.projectmediapro.app.data.template.TemplateRepository
import com.projectmediapro.core.model.TemplateStyleApplier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.projectmediapro.core.model.BeatEffectKind
import com.projectmediapro.core.model.BeatMarker
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ExportBitrate
import com.projectmediapro.core.model.ExportFrameRate
import com.projectmediapro.core.model.ExportQuality
import com.projectmediapro.core.model.Project
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.TrackType
import com.projectmediapro.core.model.UndoRedoManager
import com.projectmediapro.core.model.audioClips
import com.projectmediapro.core.model.overlayClips
import com.projectmediapro.core.model.textClips
import com.projectmediapro.core.model.videoClips
import com.projectmediapro.feature.captions.CaptionDocument
import com.projectmediapro.feature.captions.CaptionRepository
import com.projectmediapro.feature.captions.CaptionStyle
import com.projectmediapro.feature.captions.LyricsPacingEngine
import com.projectmediapro.feature.captions.LyricsPresetRegistry
import com.projectmediapro.feature.captions.PcmAudioExtractor
import com.projectmediapro.feature.captions.SrtCaptionCodec
import com.projectmediapro.feature.captions.TimelineAudioAssembler
import com.projectmediapro.feature.captions.TranscriptionEngine
import com.projectmediapro.feature.captions.TranscriptionRequest
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EditorViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val videoImporter: VideoImporter,
    private val mediaStorage: ProjectMediaStorage,
    private val audioImporter: AudioImporter = FailingAudioImporter,
    private val overlayImporter: OverlayImporter = FailingOverlayImporter,
    private val videoExporter: VideoExporter? = null,
    private val exportSaver: ExportSaver? = null,
    private val cacheSpaceProvider: () -> Long = { Long.MAX_VALUE },
    private val tempDirectory: File = File(System.getProperty("java.io.tmpdir") ?: ".", "exports"),
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val captionRepository: CaptionRepository? = null,
    private val pcmAudioExtractor: PcmAudioExtractor? = null,
    private val transcriptionEngine: TranscriptionEngine? = null,
    private val freezeFrameExtractor: FreezeFrameExtractor? = null,
    private val videoAudioExtractor: VideoAudioExtractor? = null,
    private val frameSnapshotSaver: FrameSnapshotSaver? = null,
    private val projectTemplateSaver: ProjectTemplateSaver? = null,
    private val templateRepository: TemplateRepository? = null,
) : ViewModel() {

    private val templateStyleJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val changeMutex = Mutex()
    private val exportMutex = Mutex()
    private var exportJob: Job? = null
    private var captionJob: Job? = null
    private var freezeJob: Job? = null
    private var extractAudioJob: Job? = null
    private var beatJob: Job? = null
    private var snapshotJob: Job? = null
    private var saveTemplateJob: Job? = null
    private val history = UndoRedoManager(Project(id = projectId, title = ""))

    init {
        viewModelScope.launch {
            changeMutex.withLock {
                _uiState.value = try {
                    val project = repository.getProject(projectId)
                    if (project != null) {
                        history.reset(project)
                        val savedCaptions = runCatching { captionRepository?.loadCaptions(projectId) }.getOrNull()
                        EditorUiState.Loaded(
                            project = project,
                            previewClips = resolvePreviewClips(project, mediaStorage),
                            previewAudioClips = resolvePreviewAudioClips(project, mediaStorage),
                            overlayFilePaths = resolveOverlayFilePaths(project, mediaStorage),
                            clipFilePaths = resolveClipFilePaths(project, mediaStorage),
                            selectedClipId = project.videoClips().firstOrNull()?.id
                                ?: project.audioClips().firstOrNull()?.id
                                ?: project.overlayClips().firstOrNull()?.id
                                ?: project.textClips().firstOrNull()?.id,
                            captionDocument = savedCaptions,
                        )
                    } else {
                        EditorUiState.NotFound
                    }
                } catch (_: IOException) {
                    EditorUiState.Error
                }
            }
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.VideoPicked -> handleMediaPicked(event.sourceUri, TrackType.VIDEO)
            is EditorEvent.AudioPicked -> handleMediaPicked(event.sourceUri, TrackType.AUDIO)
            is EditorEvent.OverlayPicked -> handleMediaPicked(event.sourceUri, TrackType.OVERLAY)
            is EditorEvent.SelectClip -> handleSelectClip(event.clipId)
            EditorEvent.ClearSelection -> handleClearSelection()
            EditorEvent.OpenTrim -> openSelectedClipDialog(
                predicate = { !it.isFreeTimed && ProjectTimeline.canTrimClip(it.clip) },
                dialogFactory = EditorDialog::Trim,
            )
            EditorEvent.OpenSplit -> openSelectedClipDialog(
                predicate = { it.isVideo && ProjectTimeline.canSplitClip(it.clip) },
                dialogFactory = EditorDialog::Split,
            )
            EditorEvent.OpenSpeed -> openSelectedClipDialog(
                predicate = { !it.isFreeTimed && !it.clip.isStillImage },
                dialogFactory = EditorDialog::Speed,
            )
            EditorEvent.OpenVolume -> openSelectedClipDialog(
                predicate = { it.isAudio || (it.isVideo && !it.clip.isStillImage) },
                dialogFactory = EditorDialog::Volume,
            )
            EditorEvent.OpenTransform -> openSelectedClipDialog(
                predicate = { it.isVideo },
                dialogFactory = EditorDialog::Transform,
            )
            EditorEvent.OpenCrop -> openSelectedClipDialog(
                predicate = { it.isVideo },
                dialogFactory = EditorDialog::Crop,
            )
            EditorEvent.OpenFreezeFrame -> openSelectedClipDialog(
                predicate = { it.isVideo && !it.clip.isStillImage },
                dialogFactory = EditorDialog::FreezeFrame,
            )
            EditorEvent.OpenClipEffects -> openSelectedClipDialog(
                predicate = { it.isVideo },
                dialogFactory = EditorDialog::ClipEffects,
            )
            EditorEvent.OpenOverlayPosition -> openSelectedClipDialog(
                predicate = { it.isOverlay },
                dialogFactory = EditorDialog::OverlayPosition,
            )
            EditorEvent.OpenOverlayTiming -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (ProjectTimeline.videoDurationMs(current.project) < ProjectTimeline.MIN_CLIP_DURATION_MS) {
                    return
                }
                openSelectedClipDialog(
                    predicate = { it.isOverlay },
                    dialogFactory = EditorDialog::OverlayTiming,
                )
            }
            EditorEvent.OpenAddText -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy) return
                _uiState.value = current.copy(
                    activeDialog = EditorDialog.TextEdit(clipId = null),
                    audioPreview = null,
                )
            }
            EditorEvent.OpenEditText -> openSelectedClipDialog(
                predicate = { it.isText },
                dialogFactory = { clipId -> EditorDialog.TextEdit(clipId = clipId) },
            )
            EditorEvent.OpenTextPosition -> openSelectedClipDialog(
                predicate = { it.isText },
                dialogFactory = EditorDialog::TextPosition,
            )
            EditorEvent.OpenTextTiming -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (ProjectTimeline.videoDurationMs(current.project) < ProjectTimeline.MIN_CLIP_DURATION_MS) {
                    return
                }
                openSelectedClipDialog(
                    predicate = { it.isText },
                    dialogFactory = EditorDialog::TextTiming,
                )
            }
            EditorEvent.OpenKeyframes -> openSelectedClipDialog(
                predicate = { it.isOverlay || it.isText },
                dialogFactory = EditorDialog::Keyframes,
            )
            EditorEvent.OpenBeats -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (!current.isBusy) {
                    _uiState.value = current.copy(
                        activeDialog = EditorDialog.Beats,
                        audioPreview = null,
                        error = null,
                    )
                }
            }
            EditorEvent.AutoDetectBeats -> handleAutoDetectBeats()
            is EditorEvent.TapBeatMarker -> handleTapBeatMarker(event.timeMs, event.effect)
            is EditorEvent.RemoveBeatMarker -> handleRemoveBeatMarker(event.markerId)
            EditorEvent.ClearBeatMarkers -> handleClearBeatMarkers()
            EditorEvent.AutoCutOnBeats -> handleAutoCutOnBeats()
            is EditorEvent.ToggleSoundReactive -> commitUndoableEdit(
                preferredSelectedClipId = null,
                keepActiveDialog = true,
                transform = { project -> ProjectTimeline.setSoundReactiveEnabled(project, event.enabled) },
            )
            EditorEvent.OpenCaptions -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy) return
                _uiState.value = current.copy(activeDialog = EditorDialog.Captions, audioPreview = null)
            }
            EditorEvent.OpenCanvas -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy) return
                _uiState.value = current.copy(activeDialog = EditorDialog.Canvas, audioPreview = null)
            }
            EditorEvent.OpenExportQuality -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy || current.project.videoClips().isEmpty()) return
                _uiState.value = current.copy(activeDialog = EditorDialog.Quality, audioPreview = null)
            }
            EditorEvent.DismissDialog -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                _uiState.value = current.copy(activeDialog = null)
            }
            is EditorEvent.ApplyTrim -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Trim)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isFreeTimed != false) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.trimClip(project, targetId, event.startMs, event.endMs)
                }
            }
            is EditorEvent.ApplySplit -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Split)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isVideo != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.splitClip(project, targetId, event.offsetMs, idProvider())
                }
            }
            is EditorEvent.ApplySpeed -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Speed)?.clipId
                    ?: current.selectedClipId
                    ?: return
                val location = current.project.findClipLocation(targetId) ?: return
                if (location.isFreeTimed || location.clip.isStillImage) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.setClipSpeed(project, targetId, event.speed, event.speedRamp)
                }
            }
            is EditorEvent.ApplyVolume -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Volume)?.clipId
                    ?: current.selectedClipId
                    ?: return
                val location = current.project.findClipLocation(targetId) ?: return
                if (!location.isAudio && !(location.isVideo && !location.clip.isStillImage)) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.setClipAudioMix(
                        project = project,
                        clipId = targetId,
                        volume = event.volume,
                        audioBoost = event.audioBoost,
                        fadeInMs = event.fadeInMs,
                        fadeOutMs = event.fadeOutMs,
                        audioEnhance = event.audioEnhance,
                    )
                }
            }
            is EditorEvent.ApplyTransform -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Transform)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isVideo != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.setClipTransform(
                        project = project,
                        clipId = targetId,
                        rotationDegrees = event.rotationDegrees,
                        flipHorizontal = event.flipHorizontal,
                        flipVertical = event.flipVertical,
                    )
                }
            }
            EditorEvent.DuplicateSelected -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = current.selectedClipId ?: return
                if (current.project.findClipLocation(targetId) == null) return
                val newClipId = idProvider()
                commitUndoableEdit(newClipId) { project ->
                    ProjectTimeline.duplicateClip(project, targetId, newClipId)
                }
            }
            is EditorEvent.ReplaceClipPicked -> handleReplaceClipPicked(event.sourceUri)
            is EditorEvent.CopyFromHere -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = current.selectedClipId ?: return
                if (current.project.findClipLocation(targetId) == null) return
                val newClipId = idProvider()
                commitUndoableEdit(newClipId) { project ->
                    ProjectTimeline.copyClipFromPlayhead(project, targetId, event.playheadMs, newClipId)
                }
            }
            is EditorEvent.CopyTillHere -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = current.selectedClipId ?: return
                if (current.project.findClipLocation(targetId) == null) return
                val newClipId = idProvider()
                commitUndoableEdit(newClipId) { project ->
                    ProjectTimeline.copyClipTillPlayhead(project, targetId, event.playheadMs, newClipId)
                }
            }
            is EditorEvent.CopyToHere -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = current.selectedClipId ?: return
                if (current.project.findClipLocation(targetId) == null) return
                val newClipId = idProvider()
                commitUndoableEdit(newClipId) { project ->
                    ProjectTimeline.copyClipToPlayhead(project, targetId, event.playheadMs, newClipId)
                }
            }
            is EditorEvent.ResizeTimedClip -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val location = current.project.findClipLocation(event.clipId) ?: return
                if (!location.isOverlay && !location.isText) return
                commitUndoableEdit(event.clipId) { project ->
                    if (location.isOverlay) {
                        ProjectTimeline.updateOverlayTiming(project, event.clipId, event.startTimeMs, event.durationMs)
                    } else {
                        ProjectTimeline.updateTextTiming(project, event.clipId, event.startTimeMs, event.durationMs)
                    }
                }
            }
            is EditorEvent.ApplyClipEffects -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.ClipEffects)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isVideo != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.updateClipVisualEffects(project, targetId, event.effects)
                }
            }
            is EditorEvent.ApplyCrop -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Crop)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isVideo != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.setClipCrop(project, targetId, event.crop)
                }
            }
            EditorEvent.ExtractAudioFromSelected -> handleExtractAudioFromSelected()
            is EditorEvent.SaveFrameSnapshot -> handleSaveFrameSnapshot(event.timelinePositionMs, event.destinationUri)
            is EditorEvent.ApplyFreezeFrame -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.FreezeFrame)?.clipId
                    ?: current.selectedClipId
                    ?: return
                handleApplyFreezeFrame(
                    sourceClipId = targetId,
                    sourceTimeMs = event.sourceTimeMs,
                    freezeDurationMs = event.durationMs,
                )
            }
            is EditorEvent.ApplyOverlayPosition -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.OverlayPosition)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isOverlay != true) return
                commitUndoableEdit(targetId) { project ->
                    val positioned = ProjectTimeline.updateOverlayTransform(
                        project = project,
                        clipId = targetId,
                        centerX = event.centerX,
                        centerY = event.centerY,
                        widthFraction = event.widthFraction,
                    )
                    if (event.style != null) {
                        ProjectTimeline.updateOverlayStyle(
                            project = positioned,
                            clipId = targetId,
                            style = event.style,
                        )
                    } else {
                        positioned
                    }
                }
            }
            is EditorEvent.ApplyOverlayTiming -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.OverlayTiming)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isOverlay != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.updateOverlayTiming(
                        project = project,
                        clipId = targetId,
                        startTimeMs = event.startTimeMs,
                        durationMs = event.durationMs,
                    )
                }
            }
            is EditorEvent.ApplyText -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val editDialog = current.activeDialog as? EditorDialog.TextEdit
                val existingClipId = editDialog?.clipId
                if (existingClipId != null) {
                    if (current.project.findClipLocation(existingClipId)?.isText != true) return
                    commitUndoableEdit(existingClipId) { project ->
                        ProjectTimeline.updateTextClip(
                            project = project,
                            clipId = existingClipId,
                            text = event.text,
                            style = event.style,
                        )
                    }
                } else {
                    val newClipId = idProvider()
                    commitUndoableEdit(newClipId) { project ->
                        ProjectTimeline.addTextClip(
                            project = project,
                            clipId = newClipId,
                            text = event.text,
                            style = event.style,
                            newTrackId = idProvider(),
                        )
                    }
                }
            }
            is EditorEvent.ApplyTextPosition -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.TextPosition)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isText != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.updateTextTransform(
                        project = project,
                        clipId = targetId,
                        centerX = event.centerX,
                        centerY = event.centerY,
                        widthFraction = event.widthFraction,
                    )
                }
            }
            is EditorEvent.ApplyTextTiming -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.TextTiming)?.clipId
                    ?: current.selectedClipId
                    ?: return
                if (current.project.findClipLocation(targetId)?.isText != true) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.updateTextTiming(
                        project = project,
                        clipId = targetId,
                        startTimeMs = event.startTimeMs,
                        durationMs = event.durationMs,
                    )
                }
            }
            is EditorEvent.ApplyKeyframes -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = (current.activeDialog as? EditorDialog.Keyframes)?.clipId
                    ?: current.selectedClipId
                    ?: return
                val location = current.project.findClipLocation(targetId) ?: return
                if (!location.isOverlay && !location.isText) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.updateClipKeyframes(project, targetId, event.keyframes)
                }
            }
            is EditorEvent.GenerateCaptions -> handleGenerateCaptions(event.providedScript)
            is EditorEvent.SelectCaptionPreset -> updateCaptions { doc ->
                val preset = LyricsPresetRegistry.byId(event.presetId)
                doc.copy(
                    presetId = preset.id,
                    lines = LyricsPacingEngine.buildLines(doc.rawWords, preset),
                )
            }
            is EditorEvent.SelectCaptionTemplate -> updateCaptions { doc ->
                doc.copy(style = doc.style.withTemplate(event.templateId))
            }
            is EditorEvent.UpdateCaptionOffsetUs -> updateCaptions { doc ->
                doc.copy(globalOffsetUs = event.offsetUs)
            }
            is EditorEvent.UpdateCaptionStyle -> updateCaptions { doc ->
                doc.copy(style = event.style)
            }
            is EditorEvent.UpdateCaptionWordText -> updateCaptions { doc ->
                val cleaned = event.newText.trim()
                if (cleaned.isEmpty()) return@updateCaptions doc
                val updatedWords = doc.rawWords.map { w ->
                    if (w.id == event.wordId) w.copy(text = cleaned, isPlaceholder = false) else w
                }
                val preset = LyricsPresetRegistry.byId(doc.presetId)
                doc.copy(
                    rawWords = updatedWords,
                    lines = LyricsPacingEngine.buildLines(updatedWords, preset),
                )
            }
            is EditorEvent.ImportSrtCaptions -> handleImportSrt(event.srtText)
            EditorEvent.ClearCaptions -> handleClearCaptions()
            EditorEvent.ReportCaptionError -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                _uiState.value = current.copy(error = EditorError.CAPTIONS_FAILED)
            }
            is EditorEvent.ApplyCanvas -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                commitUndoableEdit(current.selectedClipId) { project ->
                    project.copy(
                        canvasAspect = event.aspect,
                        canvasBackground = event.background,
                    )
                }
            }
            is EditorEvent.SelectExportQuality -> handleSelectExportQuality(event.quality, event.frameRate, event.bitrate, event.opticalFlowEnabled)
            is EditorEvent.MoveSelected -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                val targetId = current.selectedClipId ?: return
                if (current.project.findClipLocation(targetId)?.isFreeTimed != false) return
                commitUndoableEdit(targetId) { project ->
                    ProjectTimeline.moveClip(project, targetId, event.delta)
                }
            }
            EditorEvent.ToggleAudioPreview -> handleToggleAudioPreview()
            EditorEvent.StopAudioPreview -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.audioPreview != null) {
                    _uiState.value = current.copy(audioPreview = null)
                }
            }
            EditorEvent.Undo -> stepHistory(
                step = UndoRedoManager<Project>::undo,
                rollback = UndoRedoManager<Project>::redo,
            )
            EditorEvent.Redo -> stepHistory(
                step = UndoRedoManager<Project>::redo,
                rollback = UndoRedoManager<Project>::undo,
            )
            is EditorEvent.RequestRemoveClip -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy || current.project.findClipLocation(event.clipId) == null) return
                _uiState.value = current.copy(
                    pendingRemoveClipId = event.clipId,
                    activeDialog = null,
                    audioPreview = null,
                )
            }
            EditorEvent.CancelRemoveClip -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                _uiState.value = current.copy(pendingRemoveClipId = null)
            }
            EditorEvent.ConfirmRemoveClip -> handleConfirmRemoveClip()
            is EditorEvent.StartExport -> handleStartExport(event.destinationUri, event.quality, event.frameRate, event.bitrate, event.opticalFlowEnabled)
            EditorEvent.CancelExport -> handleCancelExport()
            EditorEvent.DismissExportDone -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.export is ExportUiState.Done) {
                    _uiState.value = current.copy(export = ExportUiState.Idle)
                }
            }
            EditorEvent.OpenSaveAsTemplate -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.isBusy || current.project.videoClips().isEmpty()) return
                _uiState.value = current.copy(
                    activeDialog = EditorDialog.SaveAsTemplate,
                    audioPreview = null,
                    error = null,
                )
            }
            is EditorEvent.ConfirmSaveAsTemplate -> handleSaveAsTemplate(
                name = event.name,
                description = event.description,
                replaceableClipIds = event.replaceableClipIds,
            )
            EditorEvent.DismissTemplateSavedBanner -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.savedTemplateName != null) {
                    _uiState.value = current.copy(savedTemplateName = null)
                }
            }
            EditorEvent.OpenApplyStyle -> handleOpenApplyStyle()
            is EditorEvent.ApplyTemplateStyle -> handleApplyTemplateStyle(event.templateId)
            EditorEvent.DismissStyleAppliedBanner -> {
                val current = _uiState.value as? EditorUiState.Loaded ?: return
                if (current.appliedStyleTemplateName != null) {
                    _uiState.value = current.copy(appliedStyleTemplateName = null)
                }
            }
        }
    }

    private fun handleReplaceClipPicked(sourceUri: String?) {
        if (sourceUri.isNullOrBlank()) return
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val location = current.project.findClipLocation(current.selectedClipId) ?: return
        if (!location.isVideo && !location.isAudio) return
        val trackType = location.trackType
        val targetClipId = location.clip.id
        val oldMediaUri = location.clip.mediaUri

        _uiState.value = current.copy(
            importingTrack = trackType,
            activeDialog = null,
            audioPreview = null,
            error = null,
        )

        viewModelScope.launch {
            val imported: Pair<String, Long> = try {
                if (trackType == TrackType.VIDEO) {
                    val result = videoImporter.importVideo(projectId, sourceUri)
                    result.relativePath to result.durationMs
                } else {
                    val result = audioImporter.importAudio(projectId, sourceUri)
                    result.relativePath to result.durationMs
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VideoImportException) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    importingTrack = null,
                    error = mapImportError(failure.reason, trackType),
                )
                return@launch
            }

            val (newMediaUri, newDurationMs) = imported
            val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
            val previousProject = latest.project
            val updatedProject = ProjectTimeline.replaceClipMedia(
                project = previousProject,
                clipId = targetClipId,
                newMediaUri = newMediaUri,
                newDurationMs = newDurationMs,
            )
            if (updatedProject == previousProject) {
                mediaStorage.deleteClipFile(newMediaUri)
                _uiState.value = latest.copy(importingTrack = null)
                return@launch
            }

            changeMutex.withLock {
                try {
                    repository.saveProject(updatedProject)
                    history.pushState(updatedProject)
                    val currentAfterSave = _uiState.value as? EditorUiState.Loaded ?: latest
                    _uiState.value = currentAfterSave.withSyncedProject(
                        previousProject = previousProject,
                        targetProject = updatedProject,
                        preferredSelectedId = targetClipId,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                    if (oldMediaUri.isNotBlank() &&
                        oldMediaUri != newMediaUri &&
                        !ProjectTimeline.isMediaReferenced(updatedProject, oldMediaUri)
                    ) {
                        mediaStorage.deleteClipFile(oldMediaUri)
                    }
                } catch (cancelled: CancellationException) {
                    mediaStorage.deleteClipFile(newMediaUri)
                    throw cancelled
                } catch (_: IOException) {
                    mediaStorage.deleteClipFile(newMediaUri)
                    val afterFailure = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    _uiState.value = afterFailure.withSaveFailedRollback(
                        previousProject = previousProject,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                }
            }
        }
    }

    private fun handleClearSelection() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        _uiState.value = current.copy(
            selectedClipId = null,
            activeDialog = null,
            audioPreview = null,
        )
    }

    private fun handleSelectClip(clipId: String) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy || current.project.findClipLocation(clipId) == null) return
        val updatedAudioPreview = if (current.selectedClipId == clipId) current.audioPreview else null
        _uiState.value = current.copy(
            selectedClipId = clipId,
            activeDialog = null,
            audioPreview = updatedAudioPreview,
        )
    }

    private fun handleToggleAudioPreview() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val location = current.project.findClipLocation(current.selectedClipId) ?: return
        if (!location.isAudio) return
        val nextPreview = if (current.audioPreview?.clipId == location.clip.id) {
            null
        } else {
            resolveAudioPreview(location.clip, mediaStorage)
        }
        _uiState.value = current.copy(audioPreview = nextPreview)
    }

    private fun openSelectedClipDialog(
        predicate: (ClipLocation) -> Boolean,
        dialogFactory: (String) -> EditorDialog,
    ) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val location = current.project.findClipLocation(current.selectedClipId) ?: return
        if (!predicate(location)) return
        _uiState.value = current.copy(
            activeDialog = dialogFactory(location.clip.id),
            audioPreview = null,
        )
    }

    private fun handleSelectExportQuality(
        quality: ExportQuality,
        frameRate: ExportFrameRate? = null,
        bitrate: ExportBitrate? = null,
        opticalFlowEnabled: Boolean? = null,
    ) {
        persistProjectTransition(
            preferredSelectedClipId = null,
            prepareTarget = { previous ->
                val nextFrameRate = frameRate ?: previous.exportFrameRate
                val nextBitrate = bitrate ?: previous.exportBitrate
                val nextOpticalFlow = opticalFlowEnabled ?: previous.opticalFlowEnabled
                if (previous.exportQuality == quality &&
                    previous.exportFrameRate == nextFrameRate &&
                    previous.exportBitrate == nextBitrate &&
                    previous.opticalFlowEnabled == nextOpticalFlow
                ) {
                    null
                } else {
                    previous.copy(
                        exportQuality = quality,
                        exportFrameRate = nextFrameRate,
                        exportBitrate = nextBitrate,
                        opticalFlowEnabled = nextOpticalFlow,
                    )
                }
            },
        )
    }

    private fun commitUndoableEdit(
        preferredSelectedClipId: String?,
        keepActiveDialog: Boolean = false,
        transform: (Project) -> Project,
    ) {
        persistProjectTransition(
            preferredSelectedClipId = preferredSelectedClipId,
            keepActiveDialog = keepActiveDialog,
            prepareTarget = { previous ->
                val updated = transform(previous)
                if (updated == previous) null else updated
            },
            onSaveSuccess = { updated -> history.pushState(updated) },
        )
    }

    private fun stepHistory(
        step: (UndoRedoManager<Project>) -> Project?,
        rollback: (UndoRedoManager<Project>) -> Project?,
    ) {
        persistProjectTransition(
            preferredSelectedClipId = null,
            prepareTarget = { previous ->
                step(history)?.copy(
                exportQuality = previous.exportQuality,
                exportFrameRate = previous.exportFrameRate,
                exportBitrate = previous.exportBitrate,
                opticalFlowEnabled = previous.opticalFlowEnabled,
            )
            },
            onSaveFailure = { rollback(history) },
        )
    }

    private fun persistProjectTransition(
        preferredSelectedClipId: String?,
        keepActiveDialog: Boolean = false,
        prepareTarget: (Project) -> Project?,
        onSaveSuccess: (Project) -> Unit = {},
        onSaveFailure: () -> Unit = {},
    ) {
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isBusy) return

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                if (latest.isBusy) return@withLock
                val previousProject = latest.project
                val targetProject = prepareTarget(previousProject)
                if (targetProject == null) {
                    if (!keepActiveDialog && latest.activeDialog != null) {
                        _uiState.value = latest.copy(activeDialog = null)
                    }
                    return@withLock
                }

                try {
                    repository.saveProject(targetProject)
                    onSaveSuccess(targetProject)
                    _uiState.value = latest.withSyncedProject(
                        previousProject = previousProject,
                        targetProject = targetProject,
                        preferredSelectedId = preferredSelectedClipId ?: latest.selectedClipId,
                        mediaStorage = mediaStorage,
                        history = history,
                        keepActiveDialog = keepActiveDialog,
                    )
                } catch (_: IOException) {
                    onSaveFailure()
                    _uiState.value = latest.withSaveFailedRollback(
                        previousProject = previousProject,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                }
            }
        }
    }

    private fun handleMediaPicked(
        sourceUri: String?,
        trackType: TrackType,
    ) {
        if (sourceUri == null) return
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isBusy) return

        _uiState.value = initial.copy(
            importingTrack = trackType,
            activeDialog = null,
            audioPreview = null,
            pendingRemoveClipId = null,
            error = null,
        )

        viewModelScope.launch {
            val (relativePath, durationMs) = try {
                when (trackType) {
                    TrackType.AUDIO -> {
                        val imported = audioImporter.importAudio(projectId, sourceUri)
                        imported.relativePath to imported.durationMs
                    }
                    TrackType.OVERLAY -> {
                        val imported = overlayImporter.importOverlay(projectId, sourceUri)
                        imported.relativePath to 0L
                    }
                    else -> {
                        val imported = videoImporter.importVideo(projectId, sourceUri)
                        imported.relativePath to imported.durationMs
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: VideoImportException) {
                finishImportWithError(mapImportError(failure.reason, trackType))
                return@launch
            } catch (_: Exception) {
                finishImportWithError(EditorError.IMPORT_FAILED)
                return@launch
            }

            var saved = false
            try {
                changeMutex.withLock {
                    val beforeSave = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    val previousProject = beforeSave.project
                    val newClipId = idProvider()
                    val updatedProject = if (trackType == TrackType.OVERLAY) {
                        ProjectTimeline.addOverlayClip(
                            project = previousProject,
                            clipId = newClipId,
                            mediaUri = relativePath,
                            newTrackId = idProvider(),
                        )
                    } else {
                        val clip = Clip(
                            id = newClipId,
                            mediaUri = relativePath,
                            startTimeMs = 0L,
                            durationMs = durationMs,
                            trimStartMs = 0L,
                            trimEndMs = durationMs,
                            sourceDurationMs = durationMs,
                        )
                        ProjectTimeline.appendClip(
                            project = previousProject,
                            clip = clip,
                            newTrackId = idProvider(),
                            trackType = trackType,
                        )
                    }
                    try {
                        repository.saveProject(updatedProject)
                        saved = true
                        history.reset(updatedProject)
                        val preferredSelection = if (trackType == TrackType.OVERLAY) {
                            newClipId
                        } else {
                            beforeSave.selectedClipId ?: newClipId
                        }
                        _uiState.value = beforeSave.withSyncedProject(
                            previousProject = previousProject,
                            targetProject = updatedProject,
                            preferredSelectedId = preferredSelection,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
                    } catch (_: IOException) {
                        mediaStorage.deleteClipFile(relativePath)
                        _uiState.value = beforeSave.withSaveFailedRollback(
                            previousProject = previousProject,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                if (!saved) {
                    mediaStorage.deleteClipFile(relativePath)
                }
                throw cancelled
            }
        }
    }

    private fun handleConfirmRemoveClip() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val targetClipId = current.pendingRemoveClipId ?: return
        _uiState.value = current.copy(pendingRemoveClipId = null, audioPreview = null, error = null)

        viewModelScope.launch {
            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                if (latest.isBusy) return@withLock
                val previousProject = latest.project
                val removedClip = previousProject.findClipLocation(targetClipId)?.clip ?: return@withLock

                val updatedProject = ProjectTimeline.removeClip(previousProject, targetClipId)
                try {
                    repository.saveProject(updatedProject)
                } catch (_: IOException) {
                    _uiState.value = latest.withSaveFailedRollback(
                        previousProject = previousProject,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                    return@withLock
                }

                if (removedClip.mediaUri.isNotBlank() &&
                    !ProjectTimeline.isMediaReferenced(updatedProject, removedClip.mediaUri)
                ) {
                    mediaStorage.deleteClipFile(removedClip.mediaUri)
                }

                history.reset(updatedProject)
                _uiState.value = latest.withSyncedProject(
                    previousProject = previousProject,
                    targetProject = updatedProject,
                    preferredSelectedId = latest.selectedClipId ?: targetClipId,
                    mediaStorage = mediaStorage,
                    history = history,
                )
            }
        }
    }

    private fun handleStartExport(
        destinationUri: String?,
        qualityOverride: ExportQuality?,
        frameRateOverride: ExportFrameRate? = null,
        bitrateOverride: ExportBitrate? = null,
        opticalFlowOverride: Boolean? = null,
    ) {
        val exporter = videoExporter
        val saver = exportSaver
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.project.videoClips().isEmpty() || initial.isBusy) return
        if (exportJob?.isActive == true) return
        if (exporter == null || saver == null) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.EXPORT_FAILED)
            return
        }

        val nextQuality = qualityOverride ?: initial.project.exportQuality
        val nextFrameRate = frameRateOverride ?: initial.project.exportFrameRate
        val nextBitrate = bitrateOverride ?: initial.project.exportBitrate
        val nextOpticalFlow = opticalFlowOverride ?: initial.project.opticalFlowEnabled
        val effectiveProject = if (
            nextQuality != initial.project.exportQuality ||
            nextFrameRate != initial.project.exportFrameRate ||
            nextBitrate != initial.project.exportBitrate ||
            nextOpticalFlow != initial.project.opticalFlowEnabled
        ) {
            initial.project.copy(
                exportQuality = nextQuality,
                exportFrameRate = nextFrameRate,
                exportBitrate = nextBitrate,
                opticalFlowEnabled = nextOpticalFlow,
            )
        } else {
            initial.project
        }

        val plan = try {
            ExportPlanner.plan(effectiveProject, initial.captionDocument, mediaStorage::resolve)
        } catch (_: IllegalStateException) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.EXPORT_NOTHING)
            return
        }
        if (cacheSpaceProvider() < ExportPlanner.estimateExportBytes(plan.totalDurationMs)) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.EXPORT_NO_SPACE)
            return
        }

        _uiState.value = initial.copy(
            project = effectiveProject,
            export = ExportUiState.Running(0),
            activeDialog = null,
            audioPreview = null,
            pendingRemoveClipId = null,
            error = null,
        )
        val projectTitle = effectiveProject.title
        val tempFile = exportTempFile()
        exportJob = viewModelScope.launch {
            if (effectiveProject != initial.project) {
                changeMutex.withLock {
                    runCatching { repository.saveProject(effectiveProject) }
                }
            }
            exportMutex.withLock {
                try {
                    tempDirectory.mkdirs()
                    if (tempFile.exists()) tempFile.delete()
                    exporter.export(plan, tempFile) { percent -> updateExportProgress(percent) }
                    try {
                        if (destinationUri != null) {
                            saver.saveToUri(tempFile, destinationUri)
                        } else {
                            saver.saveToGallery(tempFile, projectTitle)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        finishExport(ExportUiState.Idle, EditorError.EXPORT_SAVE_FAILED)
                        return@withLock
                    }
                    finishExport(ExportUiState.Done(savedToGallery = destinationUri == null), null)
                } catch (cancelled: CancellationException) {
                    finishExport(ExportUiState.Idle, null)
                    throw cancelled
                } catch (failure: VideoExportException) {
                    finishExport(ExportUiState.Idle, mapExportError(failure.reason))
                } catch (_: Exception) {
                    finishExport(ExportUiState.Idle, EditorError.EXPORT_FAILED)
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    private fun handleCancelExport() {
        exportJob?.cancel()
        exportJob = null
        exportTempFile().delete()
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.export is ExportUiState.Running) {
            _uiState.value = current.copy(export = ExportUiState.Idle, error = null)
        }
    }

    private fun finishImportWithError(error: EditorError) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = current.copy(
            importingTrack = null,
            error = error,
        )
    }

    private fun updateExportProgress(percent: Int) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.export is ExportUiState.Running) {
            _uiState.value = current.copy(export = ExportUiState.Running(percent.coerceIn(0, 100)))
        }
    }

    private fun finishExport(export: ExportUiState, error: EditorError?) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = current.copy(export = export, error = error)
    }

    private fun handleGenerateCaptions(providedScript: String?) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy || current.isTranscribingCaptions) return
        val trimmedScript = providedScript?.trim().orEmpty()
        if (trimmedScript.isNotEmpty() &&
            (SrtCaptionCodec.isLrcFormat(trimmedScript) || SrtCaptionCodec.isSrtFormat(trimmedScript))
        ) {
            handleImportSrt(trimmedScript)
            return
        }
        val extractor = pcmAudioExtractor ?: return
        val engine = transcriptionEngine ?: return
        val videoClips = current.project.videoClips()
        val audioClips = current.project.audioClips()
        if (videoClips.isEmpty() && audioClips.isEmpty()) return

        captionJob?.cancel()
        _uiState.value = current.copy(isTranscribingCaptions = true, error = null)
        captionJob = viewModelScope.launch {
            try {
                val assembled = TimelineAudioAssembler.assemble(
                    videoClips = videoClips,
                    audioClips = audioClips,
                    extractor = extractor,
                    resolveFile = mediaStorage::resolve,
                )
                val result = engine.transcribe(
                    TranscriptionRequest(
                        pcmAudio = assembled.pcm,
                        clipId = projectId,
                        providedScript = providedScript?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
                val loadedNow = _uiState.value as? EditorUiState.Loaded ?: return@launch
                val existingDoc = loadedNow.captionDocument
                val preset = LyricsPresetRegistry.byId(existingDoc?.presetId ?: LyricsPresetRegistry.SPEECH_ID)
                val style = existingDoc?.style ?: CaptionStyle.default()
                val newDoc = CaptionDocument(
                    projectId = projectId,
                    rawWords = result.words,
                    lines = LyricsPacingEngine.buildLines(result.words, preset),
                    presetId = preset.id,
                    globalOffsetUs = existingDoc?.globalOffsetUs ?: 0L,
                    style = style,
                )
                val saveError = try {
                    captionRepository?.saveCaptions(newDoc)
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    EditorError.SAVE_FAILED
                }
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                if (saveError != null) {
                    _uiState.value = latest.copy(
                        isTranscribingCaptions = false,
                        error = saveError,
                    )
                } else {
                    _uiState.value = latest.copy(
                        captionDocument = newDoc,
                        isTranscribingCaptions = false,
                        error = if (assembled.wasTruncated) EditorError.CAPTIONS_TRUNCATED else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = _uiState.value as? EditorUiState.Loaded
                if (latest != null) {
                    _uiState.value = latest.copy(
                        isTranscribingCaptions = false,
                        error = EditorError.CAPTIONS_FAILED,
                    )
                }
            }
        }
    }

    private fun updateCaptions(transform: (CaptionDocument) -> CaptionDocument) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        val existing = current.captionDocument ?: return
        val updated = transform(existing)
        if (updated == existing) return
        persistCaptionChange(previous = existing, updated = updated)
    }

    private fun handleImportSrt(srtText: String) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        val existing = current.captionDocument
        val preset = LyricsPresetRegistry.byId(existing?.presetId ?: LyricsPresetRegistry.SPEECH_ID)
        val style = existing?.style ?: CaptionStyle.default()
        val parsed = SrtCaptionCodec.parseSrt(
            srtText = srtText,
            projectId = projectId,
            preset = preset,
            style = style,
        )
        if (parsed.lines.isEmpty()) return
        persistCaptionChange(previous = existing, updated = parsed)
    }

    private fun persistCaptionChange(previous: CaptionDocument?, updated: CaptionDocument) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = current.copy(captionDocument = updated, error = null)
        viewModelScope.launch {
            changeMutex.withLock {
                try {
                    captionRepository?.saveCaptions(updated)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    _uiState.value = latest.copy(
                        captionDocument = previous,
                        error = EditorError.SAVE_FAILED,
                    )
                }
            }
        }
    }

    private fun handleClearCaptions() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        val previous = current.captionDocument
        captionJob?.cancel()
        _uiState.value = current.copy(
            captionDocument = null,
            isTranscribingCaptions = false,
            error = null,
        )
        viewModelScope.launch {
            changeMutex.withLock {
                try {
                    captionRepository?.deleteCaptions(projectId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    _uiState.value = latest.copy(
                        captionDocument = previous,
                        error = EditorError.SAVE_FAILED,
                    )
                }
            }
        }
    }

    private fun handleApplyFreezeFrame(
        sourceClipId: String,
        sourceTimeMs: Long,
        freezeDurationMs: Long,
    ) {
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isBusy || freezeJob?.isActive == true) return
        val extractor = freezeFrameExtractor
        val sourceLocation = initial.project.findClipLocation(sourceClipId)
        if (extractor == null || sourceLocation == null || !sourceLocation.isVideo || sourceLocation.clip.isStillImage) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.FREEZE_FRAME_FAILED)
            return
        }
        val sourceFile = mediaStorage.resolve(sourceLocation.clip.mediaUri)
        if (sourceFile == null || !sourceFile.isFile) {
            _uiState.value = initial.copy(activeDialog = null, error = EditorError.FREEZE_FRAME_FAILED)
            return
        }

        _uiState.value = initial.copy(activeDialog = null, audioPreview = null, error = null)
        freezeJob = viewModelScope.launch {
            val newClipId = idProvider()
            val imageRelPath = try {
                mediaStorage.clipRelativePath(projectId, newClipId, "png")
            } catch (_: IllegalArgumentException) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(error = EditorError.FREEZE_FRAME_FAILED)
                return@launch
            }
            val outputFile = mediaStorage.resolve(imageRelPath)
            if (outputFile == null) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(error = EditorError.FREEZE_FRAME_FAILED)
                return@launch
            }

            try {
                extractor.extractFrame(
                    videoFile = sourceFile,
                    timeUs = sourceTimeMs.coerceAtLeast(0L) * 1_000L,
                    outputFile = outputFile,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(error = EditorError.FREEZE_FRAME_FAILED)
                return@launch
            }

            var saved = false
            try {
                changeMutex.withLock {
                    val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    if (latest.isBusy) return@withLock
                    val previousProject = latest.project
                    val updatedProject = ProjectTimeline.insertFreezeFrameClip(
                        project = previousProject,
                        sourceClipId = sourceClipId,
                        newClipId = newClipId,
                        imageMediaUri = imageRelPath,
                        durationMs = freezeDurationMs,
                    )
                    if (updatedProject == previousProject) return@withLock
                    try {
                        repository.saveProject(updatedProject)
                        saved = true
                        history.pushState(updatedProject)
                        _uiState.value = latest.withSyncedProject(
                            previousProject = previousProject,
                            targetProject = updatedProject,
                            preferredSelectedId = newClipId,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
                    } catch (_: IOException) {
                        _uiState.value = latest.withSaveFailedRollback(
                            previousProject = previousProject,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
                    }
                }
            } finally {
                if (!saved) {
                    mediaStorage.deleteClipFile(imageRelPath)
                }
            }
        }
    }

    private fun handleExtractAudioFromSelected() {
        val initial = _uiState.value as? EditorUiState.Loaded ?: return
        if (initial.isBusy) return
        val location = initial.project.findClipLocation(initial.selectedClipId) ?: return
        if (!location.isVideo || location.clip.isStillImage) return
        val extractor = videoAudioExtractor
        if (extractor == null) {
            _uiState.value = initial.copy(error = EditorError.EXTRACT_AUDIO_FAILED)
            return
        }
        val sourceFile = mediaStorage.resolve(location.clip.mediaUri)
        if (sourceFile == null || !sourceFile.isFile) {
            _uiState.value = initial.copy(error = EditorError.EXTRACT_AUDIO_FAILED)
            return
        }

        val sourceClipId = location.clip.id
        val (trimStartMs, trimEndMs) = ProjectTimeline.resolvedTrimBounds(location.clip)
        _uiState.value = initial.copy(
            importingTrack = TrackType.AUDIO,
            activeDialog = null,
            audioPreview = null,
            error = null,
        )

        extractAudioJob?.cancel()
        extractAudioJob = viewModelScope.launch {
            val newAudioClipId = idProvider()
            val audioRelPath = try {
                mediaStorage.clipRelativePath(projectId, newAudioClipId, "m4a")
            } catch (_: IllegalArgumentException) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    importingTrack = null,
                    error = EditorError.EXTRACT_AUDIO_FAILED,
                )
                return@launch
            }
            val outputFile = mediaStorage.resolve(audioRelPath)
            if (outputFile == null) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    importingTrack = null,
                    error = EditorError.EXTRACT_AUDIO_FAILED,
                )
                return@launch
            }

            val extracted = try {
                extractor.extractAudio(
                    videoFile = sourceFile,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    outputFile = outputFile,
                )
            } catch (cancelled: CancellationException) {
                mediaStorage.deleteClipFile(audioRelPath)
                throw cancelled
            } catch (_: IOException) {
                mediaStorage.deleteClipFile(audioRelPath)
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    importingTrack = null,
                    error = EditorError.EXTRACT_AUDIO_FAILED,
                )
                return@launch
            }

            var saved = false
            try {
                changeMutex.withLock {
                    val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                    val previousProject = latest.project
                    val updatedProject = ProjectTimeline.extractAudioFromVideoClip(
                        project = previousProject,
                        sourceClipId = sourceClipId,
                        newAudioClipId = newAudioClipId,
                        audioMediaUri = audioRelPath,
                        audioDurationMs = extracted.durationMs,
                        newAudioTrackId = idProvider(),
                    )
                    if (updatedProject == previousProject) {
                        _uiState.value = latest.copy(importingTrack = null)
                        return@withLock
                    }
                    try {
                        repository.saveProject(updatedProject)
                        saved = true
                        history.pushState(updatedProject)
                        _uiState.value = latest.withSyncedProject(
                            previousProject = previousProject,
                            targetProject = updatedProject,
                            preferredSelectedId = newAudioClipId,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
                    } catch (_: IOException) {
                        _uiState.value = latest.withSaveFailedRollback(
                            previousProject = previousProject,
                            mediaStorage = mediaStorage,
                            history = history,
                        )
                    }
                }
            } finally {
                if (!saved) {
                    mediaStorage.deleteClipFile(audioRelPath)
                }
            }
        }
    }

    private fun handleTapBeatMarker(timeMs: Long, effect: BeatEffectKind) {
        commitUndoableEdit(
            preferredSelectedClipId = null,
            keepActiveDialog = true,
            transform = { project ->
                ProjectTimeline.addBeatMarker(
                    project = project,
                    marker = BeatMarker(
                        id = idProvider(),
                        timeMs = timeMs.coerceAtLeast(0L),
                        effect = effect,
                    ),
                )
            },
        )
    }

    private fun handleRemoveBeatMarker(markerId: String) {
        commitUndoableEdit(
            preferredSelectedClipId = null,
            keepActiveDialog = true,
            transform = { project -> ProjectTimeline.removeBeatMarker(project, markerId) },
        )
    }

    private fun handleClearBeatMarkers() {
        commitUndoableEdit(
            preferredSelectedClipId = null,
            keepActiveDialog = true,
            transform = { project -> ProjectTimeline.clearBeatMarkers(project) },
        )
    }

    private fun handleAutoCutOnBeats() {
        commitUndoableEdit(
            preferredSelectedClipId = null,
            keepActiveDialog = false,
            transform = { project -> ProjectTimeline.autoSplitVideoOnBeats(project, idProvider) },
        )
    }

    private fun handleAutoDetectBeats() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy || current.isDetectingBeats) return
        val extractor = pcmAudioExtractor
        if (extractor == null) {
            _uiState.value = current.copy(error = EditorError.BEATS_DETECT_FAILED)
            return
        }
        val projectSnapshot = current.project
        if (projectSnapshot.videoClips().isEmpty() && projectSnapshot.audioClips().isEmpty()) {
            _uiState.value = current.copy(error = EditorError.BEATS_DETECT_FAILED)
            return
        }

        _uiState.value = current.copy(isDetectingBeats = true, error = null)
        beatJob?.cancel()
        beatJob = viewModelScope.launch {
            val detectedMs = try {
                val assembled = TimelineAudioAssembler.assemble(
                    videoClips = projectSnapshot.videoClips(),
                    audioClips = projectSnapshot.audioClips(),
                    extractor = extractor,
                    resolveFile = mediaStorage::resolve,
                )
                OfflineBeatDetector.detectBeatsMs(assembled.pcm)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    isDetectingBeats = false,
                    error = EditorError.BEATS_DETECT_FAILED,
                )
                return@launch
            }

            if (detectedMs.isEmpty()) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    isDetectingBeats = false,
                    error = EditorError.BEATS_DETECT_FAILED,
                )
                return@launch
            }

            changeMutex.withLock {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@withLock
                val previousProject = latest.project
                val markers = detectedMs.map { ms ->
                    BeatMarker(
                        id = idProvider(),
                        timeMs = ms,
                        effect = BeatEffectKind.NONE,
                    )
                }
                val updatedProject = ProjectTimeline.replaceBeatMarkers(previousProject, markers)
                try {
                    repository.saveProject(updatedProject)
                    history.pushState(updatedProject)
                    _uiState.value = latest.copy(isDetectingBeats = false).withSyncedProject(
                        previousProject = previousProject,
                        targetProject = updatedProject,
                        preferredSelectedId = latest.selectedClipId,
                        mediaStorage = mediaStorage,
                        history = history,
                        keepActiveDialog = true,
                    )
                } catch (_: IOException) {
                    _uiState.value = latest.copy(isDetectingBeats = false).withSaveFailedRollback(
                        previousProject = previousProject,
                        mediaStorage = mediaStorage,
                        history = history,
                    )
                }
            }
        }
    }

    private fun handleSaveFrameSnapshot(timelinePositionMs: Long, destinationUri: String?) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val extractor = freezeFrameExtractor
        val saver = frameSnapshotSaver
        if (extractor == null || saver == null) {
            _uiState.value = current.copy(error = EditorError.SNAPSHOT_FAILED)
            return
        }
        val active = TimelineClock.activeClipAt(current.project.videoClips(), timelinePositionMs)
        val clip = active?.clip ?: current.project.findClipLocation(current.selectedClipId)?.clip
        if (clip == null || clip.isStillImage) {
            _uiState.value = current.copy(error = EditorError.SNAPSHOT_FAILED)
            return
        }
        val sourceFile = mediaStorage.resolve(clip.mediaUri)
        if (sourceFile == null || !sourceFile.isFile) {
            _uiState.value = current.copy(error = EditorError.SNAPSHOT_FAILED)
            return
        }
        val offsetInClipMs = active?.positionInClipMs ?: 0L
        val sourceTimeMs = (ProjectTimeline.effectiveTrimStartMs(clip) +
            (offsetInClipMs * ProjectTimeline.effectiveSpeed(clip)).toLong()).coerceAtLeast(0L)
        val title = current.project.title

        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            val tempPng = File(tempDirectory, "snapshot_${idProvider()}.png")
            try {
                extractor.extractFrame(
                    videoFile = sourceFile,
                    timeUs = sourceTimeMs * 1_000L,
                    outputFile = tempPng,
                )
                if (destinationUri != null) {
                    saver.savePngToUri(tempPng, destinationUri)
                } else {
                    saver.savePngToGallery(tempPng, title)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(error = EditorError.SNAPSHOT_FAILED)
            } finally {
                runCatching { tempPng.delete() }
            }
        }
    }

    private fun handleSaveAsTemplate(
        name: String,
        description: String,
        replaceableClipIds: Set<String>,
    ) {
        val saver = projectTemplateSaver ?: return
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy || current.project.videoClips().isEmpty()) return
        _uiState.value = current.copy(
            activeDialog = null,
            isSavingTemplate = true,
            savedTemplateName = null,
            error = null,
        )
        saveTemplateJob = viewModelScope.launch {
            try {
                val saved = saver.saveProjectAsTemplate(
                    project = current.project,
                    templateName = name,
                    description = description,
                    replaceableClipIds = replaceableClipIds,
                    captionDocument = current.captionDocument,
                )
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    isSavingTemplate = false,
                    savedTemplateName = saved.name,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                _uiState.value = latest.copy(
                    isSavingTemplate = false,
                    error = EditorError.SAVE_FAILED,
                )
            } finally {
                val latest = _uiState.value as? EditorUiState.Loaded
                if (latest != null && latest.isSavingTemplate) {
                    _uiState.value = latest.copy(isSavingTemplate = false)
                }
            }
        }
    }

    private fun handleOpenApplyStyle() {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy || current.project.videoClips().isEmpty()) return
        val repo = templateRepository
        if (repo == null) {
            _uiState.value = current.copy(
                activeDialog = EditorDialog.ApplyStyle,
                availableTemplates = emptyList(),
                audioPreview = null,
            )
            return
        }
        viewModelScope.launch {
            val templates = try {
                repo.listTemplates()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
            if (latest.isBusy) return@launch
            _uiState.value = latest.copy(
                activeDialog = EditorDialog.ApplyStyle,
                availableTemplates = templates,
                audioPreview = null,
                error = null,
            )
        }
    }

    private fun handleApplyTemplateStyle(templateId: String) {
        val current = _uiState.value as? EditorUiState.Loaded ?: return
        if (current.isBusy) return
        val template = current.availableTemplates.firstOrNull { it.id == templateId } ?: return
        _uiState.value = current.copy(activeDialog = null)

        commitUndoableEdit(preferredSelectedClipId = current.selectedClipId) { previous ->
            TemplateStyleApplier.applyStyle(previous, template).project
        }

        val rawCaptionStyle = template.captionStyleJson?.takeIf { it.isNotBlank() }
        if (rawCaptionStyle != null && captionRepository != null) {
            val decoded = try {
                templateStyleJson.decodeFromString(CaptionStyle.serializer(), rawCaptionStyle)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            if (decoded != null) {
                val existingDoc = current.captionDocument ?: CaptionDocument(projectId = current.project.id, rawWords = emptyList(), lines = emptyList())
                val updatedDoc = existingDoc.copy(style = decoded)
                viewModelScope.launch {
                    try {
                        captionRepository.saveCaptions(updatedDoc)
                        val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                        _uiState.value = latest.copy(
                            captionDocument = updatedDoc,
                            appliedStyleTemplateName = template.name,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        val latest = _uiState.value as? EditorUiState.Loaded ?: return@launch
                        _uiState.value = latest.copy(appliedStyleTemplateName = template.name)
                    }
                }
                return
            }
        }
        val latest = _uiState.value as? EditorUiState.Loaded ?: return
        _uiState.value = latest.copy(appliedStyleTemplateName = template.name)
    }

    override fun onCleared() {
        saveTemplateJob?.cancel()
        snapshotJob?.cancel()
        beatJob?.cancel()
        extractAudioJob?.cancel()
        freezeJob?.cancel()
        captionJob?.cancel()
        exportJob?.cancel()
        exportTempFile().delete()
        super.onCleared()
    }

    private fun exportTempFile(): File = File(tempDirectory, "$projectId.mp4")

    companion object {
        fun provideFactory(
            projectId: String,
            repository: ProjectRepository,
            videoImporter: VideoImporter,
            mediaStorage: ProjectMediaStorage,
            audioImporter: AudioImporter = FailingAudioImporter,
            overlayImporter: OverlayImporter = FailingOverlayImporter,
            videoExporter: VideoExporter? = null,
            exportSaver: ExportSaver? = null,
            cacheSpaceProvider: () -> Long = { Long.MAX_VALUE },
            tempDirectory: File = File(System.getProperty("java.io.tmpdir") ?: ".", "exports"),
            captionRepository: CaptionRepository? = null,
            pcmAudioExtractor: PcmAudioExtractor? = null,
            transcriptionEngine: TranscriptionEngine? = null,
            freezeFrameExtractor: FreezeFrameExtractor? = null,
            videoAudioExtractor: VideoAudioExtractor? = null,
            frameSnapshotSaver: FrameSnapshotSaver? = null,
            projectTemplateSaver: ProjectTemplateSaver? = null,
            templateRepository: TemplateRepository? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(
                    projectId = projectId,
                    repository = repository,
                    videoImporter = videoImporter,
                    mediaStorage = mediaStorage,
                    audioImporter = audioImporter,
                    overlayImporter = overlayImporter,
                    videoExporter = videoExporter,
                    exportSaver = exportSaver,
                    cacheSpaceProvider = cacheSpaceProvider,
                    tempDirectory = tempDirectory,
                    captionRepository = captionRepository,
                    pcmAudioExtractor = pcmAudioExtractor,
                    transcriptionEngine = transcriptionEngine,
                    freezeFrameExtractor = freezeFrameExtractor,
                    videoAudioExtractor = videoAudioExtractor,
                    frameSnapshotSaver = frameSnapshotSaver,
                    projectTemplateSaver = projectTemplateSaver,
                    templateRepository = templateRepository,
                ) as T
            }
        }
    }
}
