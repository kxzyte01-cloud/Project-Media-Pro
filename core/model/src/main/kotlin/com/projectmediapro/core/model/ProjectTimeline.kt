package com.projectmediapro.core.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

fun Project.videoClips(): List<Clip> =
    tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips.orEmpty()

fun Project.audioClips(): List<Clip> =
    tracks.firstOrNull { it.type == TrackType.AUDIO }?.clips.orEmpty()

fun Project.overlayClips(): List<Clip> =
    tracks.firstOrNull { it.type == TrackType.OVERLAY }?.clips.orEmpty()

fun Project.textClips(): List<Clip> =
    tracks.firstOrNull { it.type == TrackType.TEXT }?.clips.orEmpty()

object ProjectTimeline {

    const val MIN_CLIP_DURATION_MS: Long = 500L
    const val DEFAULT_OVERLAY_DURATION_MS: Long = 5_000L
    const val DEFAULT_TEXT_DURATION_MS: Long = 4_000L
    const val DEFAULT_FREEZE_DURATION_MS: Long = 3_000L
    const val MAX_FREEZE_DURATION_MS: Long = 10_000L

    private const val SPEED_EPSILON: Float = 0.001f

    fun videoDurationMs(project: Project): Long =
        project.videoClips().sumOf { effectiveDurationMs(it) }

    fun sanitizeVolume(volume: Float): Float {
        return if (volume.isNaN() || volume.isInfinite()) 1.0f else volume.coerceIn(0f, 1f)
    }

    fun sanitizeSpeed(speed: Float): Float {
        if (speed.isNaN() || speed.isInfinite() || speed <= 0f) {
            return Clip.DEFAULT_PLAYBACK_SPEED
        }
        val clamped = speed.coerceIn(Clip.MIN_PLAYBACK_SPEED, Clip.MAX_PLAYBACK_SPEED)
        return if (abs(clamped - Clip.DEFAULT_PLAYBACK_SPEED) < SPEED_EPSILON) {
            Clip.DEFAULT_PLAYBACK_SPEED
        } else {
            clamped
        }
    }

    fun normalizeRotationDegrees(degrees: Int): Int {
        val mod = ((degrees % 360) + 360) % 360
        return ((mod + 45) / 90 * 90) % 360
    }

    fun effectiveSpeed(clip: Clip): Float = sanitizeSpeed(clip.playbackSpeed)

    fun effectiveSourceDurationMs(clip: Clip): Long {
        return if (clip.sourceDurationMs > 0L) {
            clip.sourceDurationMs
        } else {
            maxOf(clip.trimEndMs, clip.durationMs).coerceAtLeast(0L)
        }
    }

    fun effectiveTrimStartMs(clip: Clip): Long = clip.trimStartMs.coerceAtLeast(0L)

    fun effectiveTrimEndMs(clip: Clip): Long {
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val startMs = effectiveTrimStartMs(clip)
        return if (clip.trimEndMs > startMs) {
            clip.trimEndMs.coerceAtMost(sourceDurationMs)
        } else {
            (startMs + clip.durationMs.coerceAtLeast(0L)).coerceAtMost(sourceDurationMs)
        }
    }

    fun effectiveSourceSpanMs(clip: Clip): Long {
        return if (clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs) {
            clip.trimEndMs - clip.trimStartMs
        } else {
            clip.durationMs.coerceAtLeast(0L)
        }
    }

    fun scaledDurationMs(sourceSpanMs: Long, speed: Float): Long {
        if (sourceSpanMs <= 0L) return 0L
        val safeSpeed = sanitizeSpeed(speed)
        if (safeSpeed == Clip.DEFAULT_PLAYBACK_SPEED) return sourceSpanMs
        return (sourceSpanMs / safeSpeed).roundToLong().coerceAtLeast(1L)
    }

    fun effectiveDurationMs(clip: Clip): Long {
        val sourceSpanMs = effectiveSourceSpanMs(clip)
        return scaledDurationMs(sourceSpanMs, clip.playbackSpeed)
    }

    fun isClipTrimmed(clip: Clip): Boolean {
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val hasValidTrimRange = clip.trimStartMs >= 0L && clip.trimEndMs > clip.trimStartMs
        return hasValidTrimRange &&
            (clip.trimStartMs > 0L || clip.trimEndMs < sourceDurationMs)
    }

    /**
     * Returns `(trimStartMs, trimEndMs)` when [isClipTrimmed] is true, or `(0L, 0L)` when the full
     * file should be played or exported without clipping.
     */
    fun resolvedTrimBounds(clip: Clip): Pair<Long, Long> {
        return if (isClipTrimmed(clip)) {
            clip.trimStartMs to clip.trimEndMs
        } else {
            0L to 0L
        }
    }

    fun canTrimClip(clip: Clip): Boolean =
        !clip.isStillImage && effectiveSourceDurationMs(clip) >= MIN_CLIP_DURATION_MS

    fun canSplitClip(clip: Clip): Boolean =
        !clip.isStillImage && effectiveSourceSpanMs(clip) >= MIN_CLIP_DURATION_MS * 2L

    fun isMediaReferenced(project: Project, mediaUri: String): Boolean {
        if (mediaUri.isBlank()) return false
        return project.tracks.asSequence().flatMap { it.clips.asSequence() }.any { it.mediaUri == mediaUri }
    }

    fun appendClip(
        project: Project,
        clip: Clip,
        newTrackId: String,
        trackType: TrackType = TrackType.VIDEO,
    ): Project {
        val trackIndex = project.tracks.indexOfFirst { it.type == trackType }
        if (trackIndex >= 0) {
            val track = project.tracks[trackIndex]
            return updateTrack(project, trackIndex, normalizeTrackClips(trackType, track.clips + clip))
        }
        val newTrack = Track(
            id = newTrackId,
            type = trackType,
            clips = normalizeTrackClips(trackType, listOf(clip)),
        )
        val updatedTracks = project.tracks + newTrack
        return project.copy(
            tracks = updatedTracks,
            durationMs = latestClipEndMs(updatedTracks),
        )
    }

    fun addOverlayClip(
        project: Project,
        clipId: String,
        mediaUri: String,
        newTrackId: String,
    ): Project {
        val totalVideoMs = videoDurationMs(project)
        val initialDurationMs = if (totalVideoMs > 0L) {
            minOf(DEFAULT_OVERLAY_DURATION_MS, totalVideoMs)
        } else {
            DEFAULT_OVERLAY_DURATION_MS
        }
        val overlayClip = Clip(
            id = clipId,
            mediaUri = mediaUri,
            startTimeMs = 0L,
            durationMs = initialDurationMs,
        )
        return appendClip(
            project = project,
            clip = overlayClip,
            newTrackId = newTrackId,
            trackType = TrackType.OVERLAY,
        )
    }

    fun addTextClip(
        project: Project,
        clipId: String,
        text: String,
        style: TextOverlayStyle = TextOverlayStyle(),
        newTrackId: String,
        startTimeMs: Long = 0L,
        durationMs: Long = DEFAULT_TEXT_DURATION_MS,
        centerX: Float = Clip.DEFAULT_TEXT_CENTER_X,
        centerY: Float = Clip.DEFAULT_TEXT_CENTER_Y,
        widthFraction: Float = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
    ): Project {
        val cleanedText = text.trim()
        if (cleanedText.isEmpty()) return project
        val totalVideoMs = videoDurationMs(project)
        val (clampedStartMs, clampedDurationMs) = if (totalVideoMs >= MIN_CLIP_DURATION_MS) {
            clampTimedRange(totalVideoMs, startTimeMs, durationMs)
        } else {
            startTimeMs.coerceAtLeast(0L) to durationMs.coerceAtLeast(MIN_CLIP_DURATION_MS)
        }
        val (clampedX, clampedY, clampedWidth) = sanitizeOverlayTransform(
            centerX = centerX,
            centerY = centerY,
            widthFraction = widthFraction,
            defaultCenterX = Clip.DEFAULT_TEXT_CENTER_X,
            defaultCenterY = Clip.DEFAULT_TEXT_CENTER_Y,
            defaultWidthFraction = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
        )
        val textClip = Clip(
            id = clipId,
            mediaUri = "",
            startTimeMs = clampedStartMs,
            durationMs = clampedDurationMs,
            overlayCenterX = clampedX,
            overlayCenterY = clampedY,
            overlayWidthFraction = clampedWidth,
            textContent = cleanedText,
            textStyle = style.sanitized(),
        )
        return appendClip(
            project = project,
            clip = textClip,
            newTrackId = newTrackId,
            trackType = TrackType.TEXT,
        )
    }

    fun updateTextClip(
        project: Project,
        clipId: String,
        text: String,
        style: TextOverlayStyle,
    ): Project {
        val cleanedText = text.trim()
        if (cleanedText.isEmpty()) return project
        val sanitizedStyle = style.sanitized()
        return modifyClipTrack(project, clipId, requiredTrackType = TrackType.TEXT) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.textContent == cleanedText && clip.textStyle == sanitizedStyle) {
                return@modifyClipTrack null
            }
            clips.toMutableList().apply {
                set(
                    clipIndex,
                    clip.copy(
                        textContent = cleanedText,
                        textStyle = sanitizedStyle,
                    ),
                )
            }
        }
    }

    fun updateClipVisualEffects(
        project: Project,
        clipId: String,
        effects: ClipVisualEffects,
    ): Project = modifyClipTrack(project, clipId, requiredTrackType = TrackType.VIDEO) { clips, clipIndex ->
        val clip = clips[clipIndex]
        val sanitized = effects.sanitized(effectiveDurationMs(clip))
        val normalized: ClipVisualEffects? = if (sanitized.isIdentity) null else sanitized
        if (clip.visualEffects == normalized) return@modifyClipTrack null
        clips.toMutableList().apply {
            set(clipIndex, clip.copy(visualEffects = normalized))
        }
    }

    fun updateOverlayTransform(
        project: Project,
        clipId: String,
        centerX: Float,
        centerY: Float,
        widthFraction: Float,
    ): Project = updatePositionedTransform(
        project = project,
        clipId = clipId,
        trackType = TrackType.OVERLAY,
        centerX = centerX,
        centerY = centerY,
        widthFraction = widthFraction,
        defaultCenterX = Clip.DEFAULT_OVERLAY_CENTER_X,
        defaultCenterY = Clip.DEFAULT_OVERLAY_CENTER_Y,
        defaultWidthFraction = Clip.DEFAULT_OVERLAY_WIDTH_FRACTION,
    )

    fun updateTextTransform(
        project: Project,
        clipId: String,
        centerX: Float,
        centerY: Float,
        widthFraction: Float,
    ): Project = updatePositionedTransform(
        project = project,
        clipId = clipId,
        trackType = TrackType.TEXT,
        centerX = centerX,
        centerY = centerY,
        widthFraction = widthFraction,
        defaultCenterX = Clip.DEFAULT_TEXT_CENTER_X,
        defaultCenterY = Clip.DEFAULT_TEXT_CENTER_Y,
        defaultWidthFraction = Clip.DEFAULT_TEXT_WIDTH_FRACTION,
    )

    fun updateOverlayTiming(
        project: Project,
        clipId: String,
        startTimeMs: Long,
        durationMs: Long,
    ): Project = updateTimedClipTiming(
        project = project,
        clipId = clipId,
        trackType = TrackType.OVERLAY,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
    )

    fun updateTextTiming(
        project: Project,
        clipId: String,
        startTimeMs: Long,
        durationMs: Long,
    ): Project = updateTimedClipTiming(
        project = project,
        clipId = clipId,
        trackType = TrackType.TEXT,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
    )

    fun updateClipKeyframes(
        project: Project,
        clipId: String,
        keyframes: List<OverlayKeyframe>,
    ): Project = modifyClipTrack(
        project = project,
        clipId = clipId,
        trackFilter = { it == TrackType.OVERLAY || it == TrackType.TEXT },
    ) { clips, clipIndex ->
        val clip = clips[clipIndex]
        val sanitized = KeyframeInterpolator.sanitizeList(
            keyframes = keyframes,
            defaultCenterX = clip.overlayCenterX,
            defaultCenterY = clip.overlayCenterY,
        )
        if (clip.keyframes == sanitized) return@modifyClipTrack null
        clips.toMutableList().apply {
            set(clipIndex, clip.copy(keyframes = sanitized))
        }
    }

    fun removeClip(
        project: Project,
        clipId: String,
    ): Project = modifyClipTrack(project, clipId) { clips, clipIndex ->
        clips.toMutableList().apply { removeAt(clipIndex) }
    }

    fun trimClip(
        project: Project,
        clipId: String,
        newStartMs: Long,
        newEndMs: Long,
    ): Project = modifyClipTrack(project, clipId) { clips, clipIndex ->
        val clip = clips[clipIndex]
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val trimmedSourceMs = newEndMs - newStartMs
        if (newStartMs < 0L ||
            newEndMs <= newStartMs ||
            newEndMs > sourceDurationMs ||
            trimmedSourceMs < MIN_CLIP_DURATION_MS
        ) {
            return@modifyClipTrack null
        }

        val timelineDurationMs = scaledDurationMs(trimmedSourceMs, clip.playbackSpeed)
        val updatedEffects = clip.visualEffects?.sanitized(timelineDurationMs)?.takeUnless { it.isIdentity }
        val updatedClip = clip.copy(
            durationMs = timelineDurationMs,
            trimStartMs = newStartMs,
            trimEndMs = newEndMs,
            sourceDurationMs = sourceDurationMs,
            visualEffects = updatedEffects,
        )
        clips.toMutableList().apply { set(clipIndex, updatedClip) }
    }

    fun splitClip(
        project: Project,
        clipId: String,
        offsetMs: Long,
        newClipId: String,
    ): Project {
        if (newClipId.isBlank() || newClipId == clipId) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.isStillImage) return project
            val sourceDurationMs = effectiveSourceDurationMs(clip)
            val currentStartMs = effectiveTrimStartMs(clip)
            val currentEndMs = effectiveTrimEndMs(clip)
            val currentDurationMs = currentEndMs - currentStartMs
            val secondSourceDurationMs = currentDurationMs - offsetMs

            if (offsetMs < MIN_CLIP_DURATION_MS || secondSourceDurationMs < MIN_CLIP_DURATION_MS) {
                return@modifyClipTrack null
            }

            val splitPointMs = currentStartMs + offsetMs
            val firstTimelineDurationMs = scaledDurationMs(offsetMs, clip.playbackSpeed)
            val secondTimelineDurationMs = scaledDurationMs(secondSourceDurationMs, clip.playbackSpeed)
            val firstPart = clip.copy(
                durationMs = firstTimelineDurationMs,
                trimStartMs = currentStartMs,
                trimEndMs = splitPointMs,
                sourceDurationMs = sourceDurationMs,
                visualEffects = clip.visualEffects?.sanitized(firstTimelineDurationMs)?.takeUnless { it.isIdentity },
            )
            val secondPart = clip.copy(
                id = newClipId,
                durationMs = secondTimelineDurationMs,
                trimStartMs = splitPointMs,
                trimEndMs = currentEndMs,
                sourceDurationMs = sourceDurationMs,
                visualEffects = clip.visualEffects?.sanitized(secondTimelineDurationMs)?.takeUnless { it.isIdentity },
            )

            buildList(clips.size + 1) {
                clips.forEachIndexed { index, existing ->
                    if (index == clipIndex) {
                        add(firstPart)
                        add(secondPart)
                    } else {
                        add(existing)
                    }
                }
            }
        }
    }

    fun moveClip(
        project: Project,
        clipId: String,
        delta: Int,
    ): Project {
        if (delta != -1 && delta != 1) return project
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val targetIndex = clipIndex + delta
            if (targetIndex !in clips.indices) return@modifyClipTrack null
            clips.toMutableList().apply {
                val moved = removeAt(clipIndex)
                add(targetIndex, moved)
            }
        }
    }

    fun duplicateClip(
        project: Project,
        clipId: String,
        newClipId: String,
    ): Project {
        if (newClipId.isBlank() || newClipId == clipId) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val duplicate = clips[clipIndex].copy(id = newClipId)
            buildList(clips.size + 1) {
                clips.forEachIndexed { index, existing ->
                    add(existing)
                    if (index == clipIndex) {
                        add(duplicate)
                    }
                }
            }
        }
    }

    fun setClipVolume(
        project: Project,
        clipId: String,
        volume: Float,
    ): Project {
        val clampedVolume = sanitizeVolume(volume)
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.isStillImage || clip.volume == clampedVolume) return@modifyClipTrack null
            clips.toMutableList().apply {
                set(clipIndex, clip.copy(volume = clampedVolume))
            }
        }
    }

    fun setClipSpeed(
        project: Project,
        clipId: String,
        speed: Float,
    ): Project {
        val safeSpeed = sanitizeSpeed(speed)
        return modifyClipTrack(
            project = project,
            clipId = clipId,
            trackFilter = { it == TrackType.VIDEO || it == TrackType.AUDIO },
        ) { clips, clipIndex ->
            val clip = clips[clipIndex]
            val sourceDurationMs = effectiveSourceDurationMs(clip)
            val sourceSpanMs = effectiveSourceSpanMs(clip)
            val trimStartMs = effectiveTrimStartMs(clip)
            val trimEndMs = effectiveTrimEndMs(clip)
            val newDurationMs = scaledDurationMs(sourceSpanMs, safeSpeed)
            val updatedEffects = clip.visualEffects?.sanitized(newDurationMs)?.takeUnless { it.isIdentity }

            if (clip.playbackSpeed == safeSpeed &&
                clip.durationMs == newDurationMs &&
                clip.visualEffects == updatedEffects
            ) {
                return@modifyClipTrack null
            }

            val updatedClip = clip.copy(
                playbackSpeed = safeSpeed,
                durationMs = newDurationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                sourceDurationMs = sourceDurationMs,
                visualEffects = updatedEffects,
            )
            clips.toMutableList().apply { set(clipIndex, updatedClip) }
        }
    }

    fun setClipTransform(
        project: Project,
        clipId: String,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
    ): Project {
        val normalizedRotation = normalizeRotationDegrees(rotationDegrees)
        return modifyClipTrack(project, clipId, requiredTrackType = TrackType.VIDEO) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.rotationDegrees == normalizedRotation &&
                clip.flipHorizontal == flipHorizontal &&
                clip.flipVertical == flipVertical
            ) {
                return@modifyClipTrack null
            }
            clips.toMutableList().apply {
                set(
                    clipIndex,
                    clip.copy(
                        rotationDegrees = normalizedRotation,
                        flipHorizontal = flipHorizontal,
                        flipVertical = flipVertical,
                    ),
                )
            }
        }
    }

    fun setClipCrop(
        project: Project,
        clipId: String,
        crop: ClipCrop?,
    ): Project {
        val normalized = crop?.sanitized()?.takeUnless { it.isIdentity }
        return modifyClipTrack(project, clipId, requiredTrackType = TrackType.VIDEO) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.crop == normalized) return@modifyClipTrack null
            clips.toMutableList().apply {
                set(clipIndex, clip.copy(crop = normalized))
            }
        }
    }

    fun insertFreezeFrameClip(
        project: Project,
        sourceClipId: String,
        newClipId: String,
        imageMediaUri: String,
        durationMs: Long = DEFAULT_FREEZE_DURATION_MS,
    ): Project {
        if (newClipId.isBlank() || newClipId == sourceClipId || imageMediaUri.isBlank()) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        val clampedDurationMs = durationMs.coerceIn(MIN_CLIP_DURATION_MS, MAX_FREEZE_DURATION_MS)
        return modifyClipTrack(project, sourceClipId, requiredTrackType = TrackType.VIDEO) { clips, clipIndex ->
            val source = clips[clipIndex]
            val freezeClip = Clip(
                id = newClipId,
                mediaUri = imageMediaUri,
                startTimeMs = 0L,
                durationMs = clampedDurationMs,
                trimStartMs = 0L,
                trimEndMs = clampedDurationMs,
                sourceDurationMs = clampedDurationMs,
                volume = 0f,
                visualEffects = source.visualEffects?.sanitized(clampedDurationMs)?.takeUnless { it.isIdentity },
                rotationDegrees = normalizeRotationDegrees(source.rotationDegrees),
                flipHorizontal = source.flipHorizontal,
                flipVertical = source.flipVertical,
                crop = source.crop?.sanitized()?.takeUnless { it.isIdentity },
                isStillImage = true,
            )
            buildList(clips.size + 1) {
                clips.forEachIndexed { index, existing ->
                    add(existing)
                    if (index == clipIndex) {
                        add(freezeClip)
                    }
                }
            }
        }
    }

    fun replaceClipMedia(
        project: Project,
        clipId: String,
        newMediaUri: String,
        newDurationMs: Long,
    ): Project {
        if (newMediaUri.isBlank() || newDurationMs <= 0L) return project
        return modifyClipTrack(
            project = project,
            clipId = clipId,
            trackFilter = { it == TrackType.VIDEO || it == TrackType.AUDIO },
        ) { clips, clipIndex ->
            val clip = clips[clipIndex]
            val oldStartMs = effectiveTrimStartMs(clip)
            val oldEndMs = effectiveTrimEndMs(clip)
            val (nextTrimStart, nextTrimEnd) = if (
                oldStartMs + MIN_CLIP_DURATION_MS <= newDurationMs &&
                oldEndMs > oldStartMs
            ) {
                oldStartMs to oldEndMs.coerceAtMost(newDurationMs).coerceAtLeast(oldStartMs + MIN_CLIP_DURATION_MS)
            } else {
                0L to newDurationMs
            }
            val nextSourceSpan = (nextTrimEnd - nextTrimStart).coerceAtLeast(1L)
            val nextTimelineDuration = scaledDurationMs(nextSourceSpan, clip.playbackSpeed)
            val nextEffects = clip.visualEffects?.sanitized(nextTimelineDuration)?.takeUnless { it.isIdentity }
            val replaced = clip.copy(
                mediaUri = newMediaUri,
                durationMs = nextTimelineDuration,
                trimStartMs = nextTrimStart,
                trimEndMs = nextTrimEnd,
                sourceDurationMs = newDurationMs,
                visualEffects = nextEffects,
                isStillImage = false,
            )
            clips.toMutableList().apply { set(clipIndex, replaced) }
        }
    }

    fun copyClipFromPlayhead(
        project: Project,
        clipId: String,
        playheadMs: Long,
        newClipId: String,
    ): Project {
        if (newClipId.isBlank() || newClipId == clipId) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val clip = clips[clipIndex]
            val copied = buildPlayheadSliceCopy(
                project = project,
                clip = clip,
                playheadMs = playheadMs,
                newClipId = newClipId,
                fromPlayhead = true,
            )
            buildList(clips.size + 1) {
                clips.forEachIndexed { index, existing ->
                    add(existing)
                    if (index == clipIndex) add(copied)
                }
            }
        }
    }

    fun copyClipTillPlayhead(
        project: Project,
        clipId: String,
        playheadMs: Long,
        newClipId: String,
    ): Project {
        if (newClipId.isBlank() || newClipId == clipId) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val clip = clips[clipIndex]
            val copied = buildPlayheadSliceCopy(
                project = project,
                clip = clip,
                playheadMs = playheadMs,
                newClipId = newClipId,
                fromPlayhead = false,
            )
            buildList(clips.size + 1) {
                clips.forEachIndexed { index, existing ->
                    add(existing)
                    if (index == clipIndex) add(copied)
                }
            }
        }
    }

    fun copyClipToPlayhead(
        project: Project,
        clipId: String,
        playheadMs: Long,
        newClipId: String,
    ): Project {
        if (newClipId.isBlank() || newClipId == clipId) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        val safePlayheadMs = playheadMs.coerceAtLeast(0L)
        for (trackIndex in project.tracks.indices) {
            val track = project.tracks[trackIndex]
            val clips = track.clips
            val clipIndex = clips.indexOfFirst { it.id == clipId }
            if (clipIndex < 0) continue
            val clip = clips[clipIndex]
            val updatedClips = if (track.type == TrackType.OVERLAY || track.type == TrackType.TEXT) {
                val totalVideoMs = videoDurationMs(project)
                val (clampedStart, clampedDuration) = if (totalVideoMs >= MIN_CLIP_DURATION_MS) {
                    clampTimedRange(totalVideoMs, safePlayheadMs, clip.durationMs)
                } else {
                    safePlayheadMs to clip.durationMs.coerceAtLeast(MIN_CLIP_DURATION_MS)
                }
                clips + clip.copy(
                    id = newClipId,
                    startTimeMs = clampedStart,
                    durationMs = clampedDuration,
                )
            } else {
                val targetIndex = clips.indexOfFirst { existing ->
                    safePlayheadMs < existing.startTimeMs + effectiveDurationMs(existing)
                }.let { idx -> if (idx < 0) clips.size else (idx + 1).coerceAtMost(clips.size) }
                clips.toMutableList().apply {
                    add(targetIndex, clip.copy(id = newClipId))
                }
            }
            return updateTrack(project, trackIndex, normalizeTrackClips(track.type, updatedClips))
        }
        return project
    }

    private fun buildPlayheadSliceCopy(
        project: Project,
        clip: Clip,
        playheadMs: Long,
        newClipId: String,
        fromPlayhead: Boolean,
    ): Clip {
        val trackType = project.tracks.firstOrNull { t -> t.clips.any { it.id == clip.id } }?.type
        val safePlayheadMs = playheadMs.coerceAtLeast(0L)
        if (trackType == TrackType.OVERLAY || trackType == TrackType.TEXT) {
            val clipEndMs = clip.startTimeMs + clip.durationMs
            return if (fromPlayhead) {
                val remainingMs = clipEndMs - safePlayheadMs
                if (safePlayheadMs > clip.startTimeMs && remainingMs >= MIN_CLIP_DURATION_MS) {
                    clip.copy(id = newClipId, startTimeMs = safePlayheadMs, durationMs = remainingMs)
                } else {
                    clip.copy(id = newClipId)
                }
            } else {
                val leadingMs = safePlayheadMs - clip.startTimeMs
                if (safePlayheadMs < clipEndMs && leadingMs >= MIN_CLIP_DURATION_MS) {
                    clip.copy(id = newClipId, startTimeMs = clip.startTimeMs, durationMs = leadingMs)
                } else {
                    clip.copy(id = newClipId)
                }
            }
        }

        if (clip.isStillImage) return clip.copy(id = newClipId)
        val speed = effectiveSpeed(clip)
        val sourceDurationMs = effectiveSourceDurationMs(clip)
        val currentStartMs = effectiveTrimStartMs(clip)
        val currentEndMs = effectiveTrimEndMs(clip)
        val clipTimelineDurationMs = effectiveDurationMs(clip)
        val offsetInClipMs = safePlayheadMs - clip.startTimeMs
        if (offsetInClipMs <= 0L || offsetInClipMs >= clipTimelineDurationMs) {
            return clip.copy(id = newClipId)
        }
        val sourceOffsetMs = (offsetInClipMs * speed).roundToLong()
        val cutSourceMs = (currentStartMs + sourceOffsetMs).coerceIn(currentStartMs, currentEndMs)
        val (nextStartMs, nextEndMs) = if (fromPlayhead) {
            cutSourceMs to currentEndMs
        } else {
            currentStartMs to cutSourceMs
        }
        val nextSpanMs = nextEndMs - nextStartMs
        if (nextSpanMs < MIN_CLIP_DURATION_MS) return clip.copy(id = newClipId)
        val nextDurationMs = scaledDurationMs(nextSpanMs, speed)
        return clip.copy(
            id = newClipId,
            durationMs = nextDurationMs,
            trimStartMs = nextStartMs,
            trimEndMs = nextEndMs,
            sourceDurationMs = sourceDurationMs,
            visualEffects = clip.visualEffects?.sanitized(nextDurationMs)?.takeUnless { it.isIdentity },
        )
    }

    fun formatPreciseDuration(ms: Long): String {
        if (ms <= 0L) return "0:00.0"
        val tenths = (ms % 1_000L) / 100L
        val totalSeconds = ms / 1_000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3_600L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths)
        } else {
            String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSeconds = ms / 1_000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3_600L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun updatePositionedTransform(
        project: Project,
        clipId: String,
        trackType: TrackType,
        centerX: Float,
        centerY: Float,
        widthFraction: Float,
        defaultCenterX: Float,
        defaultCenterY: Float,
        defaultWidthFraction: Float,
    ): Project {
        val (clampedX, clampedY, clampedWidth) = sanitizeOverlayTransform(
            centerX = centerX,
            centerY = centerY,
            widthFraction = widthFraction,
            defaultCenterX = defaultCenterX,
            defaultCenterY = defaultCenterY,
            defaultWidthFraction = defaultWidthFraction,
        )
        return modifyClipTrack(project, clipId, requiredTrackType = trackType) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.overlayCenterX == clampedX &&
                clip.overlayCenterY == clampedY &&
                clip.overlayWidthFraction == clampedWidth
            ) {
                return@modifyClipTrack null
            }
            val dx = clampedX - clip.overlayCenterX
            val dy = clampedY - clip.overlayCenterY
            val shiftedKeyframes = if (clip.keyframes.isNotEmpty() && (dx != 0f || dy != 0f)) {
                clip.keyframes.map { kf ->
                    kf.copy(
                        centerX = (kf.centerX + dx).coerceIn(0f, 1f),
                        centerY = (kf.centerY + dy).coerceIn(0f, 1f),
                    )
                }
            } else {
                clip.keyframes
            }
            clips.toMutableList().apply {
                set(
                    clipIndex,
                    clip.copy(
                        overlayCenterX = clampedX,
                        overlayCenterY = clampedY,
                        overlayWidthFraction = clampedWidth,
                        keyframes = shiftedKeyframes,
                    ),
                )
            }
        }
    }

    private fun updateTimedClipTiming(
        project: Project,
        clipId: String,
        trackType: TrackType,
        startTimeMs: Long,
        durationMs: Long,
    ): Project {
        val totalVideoMs = videoDurationMs(project)
        if (totalVideoMs < MIN_CLIP_DURATION_MS) return project
        val (clampedStartMs, clampedDurationMs) = clampTimedRange(totalVideoMs, startTimeMs, durationMs)

        return modifyClipTrack(project, clipId, requiredTrackType = trackType) { clips, clipIndex ->
            val clip = clips[clipIndex]
            if (clip.startTimeMs == clampedStartMs && clip.durationMs == clampedDurationMs) {
                return@modifyClipTrack null
            }
            clips.toMutableList().apply {
                set(
                    clipIndex,
                    clip.copy(
                        startTimeMs = clampedStartMs,
                        durationMs = clampedDurationMs,
                    ),
                )
            }
        }
    }

    private fun sanitizeOverlayTransform(
        centerX: Float,
        centerY: Float,
        widthFraction: Float,
        defaultCenterX: Float,
        defaultCenterY: Float,
        defaultWidthFraction: Float,
    ): Triple<Float, Float, Float> {
        val clampedX = if (centerX.isNaN()) defaultCenterX else centerX.coerceIn(0f, 1f)
        val clampedY = if (centerY.isNaN()) defaultCenterY else centerY.coerceIn(0f, 1f)
        val clampedWidth = if (widthFraction.isNaN()) {
            defaultWidthFraction
        } else {
            widthFraction.coerceIn(Clip.MIN_OVERLAY_WIDTH_FRACTION, Clip.MAX_OVERLAY_WIDTH_FRACTION)
        }
        return Triple(clampedX, clampedY, clampedWidth)
    }

    private fun clampTimedRange(
        totalVideoMs: Long,
        startTimeMs: Long,
        durationMs: Long,
    ): Pair<Long, Long> {
        val maxStartMs = (totalVideoMs - MIN_CLIP_DURATION_MS).coerceAtLeast(0L)
        val clampedStartMs = startTimeMs.coerceIn(0L, maxStartMs)
        val maxDurationMs = (totalVideoMs - clampedStartMs).coerceAtLeast(MIN_CLIP_DURATION_MS)
        val clampedDurationMs = durationMs.coerceIn(MIN_CLIP_DURATION_MS, maxDurationMs)
        return clampedStartMs to clampedDurationMs
    }

    private inline fun modifyClipTrack(
        project: Project,
        clipId: String,
        requiredTrackType: TrackType? = null,
        trackFilter: (TrackType) -> Boolean = { true },
        transform: (clips: List<Clip>, clipIndex: Int) -> List<Clip>?,
    ): Project {
        for (trackIndex in project.tracks.indices) {
            val track = project.tracks[trackIndex]
            if (requiredTrackType != null && track.type != requiredTrackType) continue
            if (!trackFilter(track.type)) continue
            val clips = track.clips
            val clipIndex = clips.indexOfFirst { it.id == clipId }
            if (clipIndex >= 0) {
                val updatedClips = transform(clips, clipIndex) ?: return project
                return updateTrack(
                    project,
                    trackIndex,
                    normalizeTrackClips(track.type, updatedClips),
                )
            }
        }
        return project
    }

    private fun normalizeTrackClips(trackType: TrackType, clips: List<Clip>): List<Clip> {
        return if (trackType == TrackType.OVERLAY || trackType == TrackType.TEXT) {
            clips
        } else {
            repositionClips(clips)
        }
    }

    private fun repositionClips(clips: List<Clip>): List<Clip> {
        var nextStartMs = 0L
        return clips.map { clip ->
            val positioned = clip.copy(startTimeMs = nextStartMs)
            nextStartMs += clip.durationMs
            positioned
        }
    }

    private fun updateTrack(
        project: Project,
        trackIndex: Int,
        clips: List<Clip>,
    ): Project {
        val track = project.tracks[trackIndex]
        val updatedTracks = project.tracks.toMutableList().apply {
            set(trackIndex, track.copy(clips = clips))
        }
        return project.copy(
            tracks = updatedTracks,
            durationMs = latestClipEndMs(updatedTracks),
        )
    }

    private fun latestClipEndMs(tracks: List<Track>): Long {
        return tracks
            .asSequence()
            .flatMap { it.clips.asSequence() }
            .maxOfOrNull { it.startTimeMs + it.durationMs }
            ?: 0L
    }
}
