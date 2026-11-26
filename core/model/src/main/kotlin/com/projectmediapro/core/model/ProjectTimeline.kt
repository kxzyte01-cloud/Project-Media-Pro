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
    const val AUDIO_SPLIT_MIN_HALF_MS: Long = 100L
    const val DEFAULT_OVERLAY_DURATION_MS: Long = 5_000L
    const val DEFAULT_TEXT_DURATION_MS: Long = 4_000L
    const val DEFAULT_FREEZE_DURATION_MS: Long = 3_000L
    const val MAX_FREEZE_DURATION_MS: Long = 10_000L

    private const val SPEED_EPSILON: Float = 0.001f

    fun saturatingAdd(a: Long, b: Long): Long {
        val safeA = a.coerceAtLeast(0L)
        val safeB = b.coerceAtLeast(0L)
        return if (safeA > Long.MAX_VALUE - safeB) Long.MAX_VALUE else safeA + safeB
    }

    fun videoDurationMs(project: Project): Long {
        var total = 0L
        for (clip in project.videoClips()) {
            total = saturatingAdd(total, effectiveDurationMs(clip))
        }
        return total
    }

    fun sanitizeVolume(volume: Float): Float {
        return if (volume.isNaN() || volume.isInfinite()) 1.0f else volume.coerceIn(0f, 1f)
    }

    fun sanitizeAudioBoost(boost: Float): Float {
        return if (boost.isNaN() || boost.isInfinite()) {
            Clip.DEFAULT_AUDIO_BOOST
        } else {
            boost.coerceIn(Clip.DEFAULT_AUDIO_BOOST, Clip.MAX_AUDIO_BOOST)
        }
    }

    fun sanitizeAudioFadeMs(fadeMs: Long, clipDurationMs: Long): Long {
        val maxFade = (clipDurationMs / 2L).coerceIn(0L, Clip.MAX_AUDIO_FADE_MS)
        return fadeMs.coerceIn(0L, maxFade)
    }

    fun setSoundReactiveEnabled(project: Project, enabled: Boolean): Project =
        if (project.soundReactiveEnabled == enabled) project else project.copy(soundReactiveEnabled = enabled)

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
        val startMs = effectiveTrimStartMs(clip).coerceAtMost(sourceDurationMs)
        return if (clip.trimEndMs > startMs) {
            clip.trimEndMs.coerceAtMost(sourceDurationMs)
        } else {
            saturatingAdd(startMs, clip.durationMs.coerceAtLeast(0L)).coerceAtMost(sourceDurationMs)
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
        return SpeedRampEvaluator.scaledDurationMs(sourceSpanMs, clip.playbackSpeed, clip.speedRamp)
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

    fun updateOverlayStyle(
        project: Project,
        clipId: String,
        style: OverlayVisualStyle,
    ): Project = modifyClipTrack(project, clipId, requiredTrackType = TrackType.OVERLAY) { clips, clipIndex ->
        val clip = clips[clipIndex]
        val sanitized = style.sanitized()
        val normalized: OverlayVisualStyle? = if (sanitized.isIdentity) null else sanitized
        if (clip.overlayStyle == normalized) return@modifyClipTrack null
        clips.toMutableList().apply {
            set(clipIndex, clip.copy(overlayStyle = normalized))
        }
    }

    /**
     * Applies [effects] to every video clip in the project, sanitizing transition durations per clip.
     */
    fun applyVisualEffectsToAllVideoClips(
        project: Project,
        effects: ClipVisualEffects,
    ): Project {
        val trackIndex = project.tracks.indexOfFirst { it.type == TrackType.VIDEO }
        if (trackIndex < 0) return project
        val currentClips = project.tracks[trackIndex].clips
        if (currentClips.isEmpty()) return project
        val updatedClips = currentClips.map { clip ->
            val sanitized = effects.sanitized(effectiveDurationMs(clip))
            clip.copy(visualEffects = sanitized.takeUnless { it.isIdentity })
        }
        if (updatedClips == currentClips) return project
        return updateTrack(project, trackIndex, updatedClips)
    }

    /**
     * Applies audio mix settings (volume, boost, sanitized fades, and enhance config) to every
     * eligible clip on [trackType] (all audio clips on AUDIO, or all non-still clips on VIDEO).
     */
    fun applyAudioMixToAllClips(
        project: Project,
        trackType: TrackType,
        volume: Float,
        audioBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        audioEnhance: AudioEnhanceConfig? = null,
    ): Project {
        if (trackType != TrackType.VIDEO && trackType != TrackType.AUDIO) return project
        val trackIndex = project.tracks.indexOfFirst { it.type == trackType }
        if (trackIndex < 0) return project
        val currentClips = project.tracks[trackIndex].clips
        if (currentClips.isEmpty()) return project
        val safeVolume = sanitizeVolume(volume)
        val safeBoost = if (safeVolume < 1.0f) Clip.DEFAULT_AUDIO_BOOST else sanitizeAudioBoost(audioBoost)
        val updatedClips = currentClips.map { clip ->
            if (trackType == TrackType.VIDEO && clip.isStillImage) {
                clip
            } else {
                val clipDurationMs = effectiveDurationMs(clip)
                val safeFadeIn = sanitizeAudioFadeMs(fadeInMs, clipDurationMs)
                val safeFadeOut = sanitizeAudioFadeMs(fadeOutMs, clipDurationMs)
                val sanitizedEnhance = audioEnhance?.let { cfg ->
                    if (trackType == TrackType.VIDEO) cfg.copy(autoDuckMusicEnabled = false) else cfg
                }?.takeUnless { it.isIdentity }
                clip.copy(
                    volume = safeVolume,
                    audioBoost = safeBoost,
                    audioFadeInMs = safeFadeIn,
                    audioFadeOutMs = safeFadeOut,
                    audioEnhance = sanitizedEnhance,
                )
            }
        }
        if (updatedClips == currentClips) return project
        return updateTrack(project, trackIndex, updatedClips)
    }

    /**
     * Splits the video clip spanning [timelinePositionMs] directly at the playhead.
     * Prefers [targetClipId] when it spans [timelinePositionMs]; enforces [MIN_CLIP_DURATION_MS]
     * on both resulting halves.
     */
    fun splitVideoClipAtPlayhead(
        project: Project,
        timelinePositionMs: Long,
        newClipId: String,
        targetClipId: String? = null,
    ): Project {
        val clip = resolveVideoClipAtPlayhead(project, timelinePositionMs, targetClipId) ?: return project
        if (!canSplitClip(clip)) return project
        val deltaTimelineMs = (timelinePositionMs - clip.startTimeMs).coerceAtLeast(0L)
        val sourceSpanMs = effectiveSourceSpanMs(clip)
        val sourceOffsetMs = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
            timelineOffsetMs = deltaTimelineMs,
            sourceSpanMs = sourceSpanMs,
            baseSpeed = clip.playbackSpeed,
            preset = clip.speedRamp,
        )
        return splitClip(project, clip.id, sourceOffsetMs, newClipId)
    }

    /**
     * Trims the start of the video clip spanning [timelinePositionMs] up to the playhead
     * (removes content before the playhead). Enforces [MIN_CLIP_DURATION_MS] remaining span.
     */
    fun trimVideoClipLeftToPlayhead(
        project: Project,
        timelinePositionMs: Long,
        targetClipId: String? = null,
    ): Project {
        val clip = resolveVideoClipAtPlayhead(project, timelinePositionMs, targetClipId) ?: return project
        if (!canTrimClip(clip)) return project
        val deltaTimelineMs = timelinePositionMs - clip.startTimeMs
        if (deltaTimelineMs <= 0L) return project
        val currentStart = effectiveTrimStartMs(clip)
        val currentEnd = effectiveTrimEndMs(clip)
        val sourceSpanMs = (currentEnd - currentStart).coerceAtLeast(0L)
        val sourceDeltaMs = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
            timelineOffsetMs = deltaTimelineMs,
            sourceSpanMs = sourceSpanMs,
            baseSpeed = clip.playbackSpeed,
            preset = clip.speedRamp,
        )
        val newStart = currentStart + sourceDeltaMs
        if (currentEnd - newStart < MIN_CLIP_DURATION_MS) return project
        return trimClip(project, clip.id, newStart, currentEnd)
    }

    /**
     * Trims the end of the video clip spanning [timelinePositionMs] back to the playhead
     * ("Cut Till Here" — removes content after the playhead). Enforces [MIN_CLIP_DURATION_MS].
     */
    fun trimVideoClipRightToPlayhead(
        project: Project,
        timelinePositionMs: Long,
        targetClipId: String? = null,
    ): Project {
        val clip = resolveVideoClipAtPlayhead(project, timelinePositionMs, targetClipId) ?: return project
        if (!canTrimClip(clip)) return project
        val deltaTimelineMs = timelinePositionMs - clip.startTimeMs
        if (deltaTimelineMs <= 0L) return project
        val currentStart = effectiveTrimStartMs(clip)
        val currentEnd = effectiveTrimEndMs(clip)
        val sourceSpanMs = (currentEnd - currentStart).coerceAtLeast(0L)
        val sourceDeltaMs = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
            timelineOffsetMs = deltaTimelineMs,
            sourceSpanMs = sourceSpanMs,
            baseSpeed = clip.playbackSpeed,
            preset = clip.speedRamp,
        )
        val newEnd = currentStart + sourceDeltaMs
        if (newEnd >= currentEnd || newEnd - currentStart < MIN_CLIP_DURATION_MS) return project
        return trimClip(project, clip.id, currentStart, newEnd)
    }

    /**
     * Removes all `selected == true` [silences] (measured relative to the clip's active playback
     * start) from video clip [clipId], producing trimmed clips each >= [MIN_CLIP_DURATION_MS] and
     * repositioning the video track in a single undoable operation.
     */
    fun removeSilencesFromVideoClip(
        project: Project,
        clipId: String,
        silences: List<SilenceSegment>,
        idProvider: () -> String,
    ): Project {
        val trackIndex = project.tracks.indexOfFirst { it.type == TrackType.VIDEO }
        if (trackIndex < 0) return project
        val videoClips = project.tracks[trackIndex].clips
        val clipIndex = videoClips.indexOfFirst { it.id == clipId }
        if (clipIndex < 0) return project
        val clip = videoClips[clipIndex]
        if (!canTrimClip(clip)) return project

        val activeSilences = silences.filter { it.selected && it.durationMs > 0L }
        if (activeSilences.isEmpty()) return project

        val trimStart = effectiveTrimStartMs(clip)
        val trimEnd = effectiveTrimEndMs(clip)
        val sourceSpanMs = (trimEnd - trimStart).coerceAtLeast(0L)
        if (sourceSpanMs < MIN_CLIP_DURATION_MS) return project

        val speed = effectiveSpeed(clip)
        val sourceSilences = activeSilences.map { seg ->
            val sStart = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
                timelineOffsetMs = seg.startMs,
                sourceSpanMs = sourceSpanMs,
                baseSpeed = clip.playbackSpeed,
                preset = clip.speedRamp,
            ).coerceIn(0L, sourceSpanMs)
            val sEnd = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
                timelineOffsetMs = seg.endMs,
                sourceSpanMs = sourceSpanMs,
                baseSpeed = clip.playbackSpeed,
                preset = clip.speedRamp,
            ).coerceIn(sStart, sourceSpanMs)
            seg.copy(startMs = sStart, endMs = sEnd)
        }
        val speechIntervals = SilenceDetector.computeSpeechIntervals(sourceSpanMs, sourceSilences)
            .filter { (it.last - it.first) >= MIN_CLIP_DURATION_MS }
        if (speechIntervals.isEmpty()) return project
        if (speechIntervals.size == 1 && speechIntervals.first().first == 0L && speechIntervals.first().last == sourceSpanMs) {
            return project
        }

        val replacementClips = speechIntervals.mapIndexed { idx, interval ->
            val segStart = trimStart + interval.first
            val segEnd = trimStart + interval.last
            val segSourceSpan = (segEnd - segStart).coerceAtLeast(MIN_CLIP_DURATION_MS)
            val segDurationMs = SpeedRampEvaluator.scaledDurationMs(
                sourceSpanMs = segSourceSpan,
                baseSpeed = speed,
                preset = SpeedRampPreset.NONE,
            )
            clip.copy(
                id = if (idx == 0) clip.id else idProvider(),
                trimStartMs = segStart,
                trimEndMs = segEnd,
                durationMs = segDurationMs,
                speedRamp = SpeedRampPreset.NONE,
            )
        }

        val updatedClips = buildList(videoClips.size - 1 + replacementClips.size) {
            addAll(videoClips.subList(0, clipIndex))
            addAll(replacementClips)
            addAll(videoClips.subList(clipIndex + 1, videoClips.size))
        }
        return updateTrack(project, trackIndex, repositionClips(updatedClips))
    }

    private fun resolveVideoClipAtPlayhead(
        project: Project,
        timelinePositionMs: Long,
        targetClipId: String?,
    ): Clip? {
        if (timelinePositionMs < 0L) return null
        val videoClips = project.videoClips()
        if (videoClips.isEmpty()) return null
        if (targetClipId != null) {
            val preferred = videoClips.firstOrNull { it.id == targetClipId }
            if (preferred != null) {
                val endMs = saturatingAdd(preferred.startTimeMs, effectiveDurationMs(preferred))
                if (timelinePositionMs in preferred.startTimeMs..endMs) return preferred
            }
        }
        return videoClips.firstOrNull { clip ->
            val endMs = saturatingAdd(clip.startTimeMs, effectiveDurationMs(clip))
            timelinePositionMs >= clip.startTimeMs && timelinePositionMs < endMs
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

        val timelineDurationMs = SpeedRampEvaluator.scaledDurationMs(trimmedSourceMs, clip.playbackSpeed, clip.speedRamp)
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
            val firstTimelineDurationMs = SpeedRampEvaluator.scaledDurationMs(offsetMs, clip.playbackSpeed, clip.speedRamp)
            val secondTimelineDurationMs = SpeedRampEvaluator.scaledDurationMs(secondSourceDurationMs, clip.playbackSpeed, clip.speedRamp)
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

    fun setClipAudioMix(
        project: Project,
        clipId: String,
        volume: Float,
        audioBoost: Float = Clip.DEFAULT_AUDIO_BOOST,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        audioEnhance: AudioEnhanceConfig? = null,
    ): Project {
        val clampedVolume = sanitizeVolume(volume)
        val clampedBoost = if (clampedVolume < 1.0f) Clip.DEFAULT_AUDIO_BOOST else sanitizeAudioBoost(audioBoost)
        val normalizedEnhance = audioEnhance?.takeUnless { it.isIdentity }
        var changed = false
        val updatedTracks = project.tracks.map { track ->
            if (track.type != TrackType.VIDEO && track.type != TrackType.AUDIO) {
                track
            } else {
                val updatedClips = track.clips.map { clip ->
                    if (clip.id != clipId) {
                        clip
                    } else {
                        val durMs = effectiveDurationMs(clip)
                        val cleanIn = sanitizeAudioFadeMs(fadeInMs, durMs)
                        val cleanOut = sanitizeAudioFadeMs(fadeOutMs, durMs)
                        val resolvedEnhance = if (audioEnhance != null) normalizedEnhance else clip.audioEnhance
                        val nextMuted = if (clampedVolume > 0f) false else clip.muted
                        if (clip.volume == clampedVolume &&
                            clip.muted == nextMuted &&
                            clip.audioBoost == clampedBoost &&
                            clip.audioFadeInMs == cleanIn &&
                            clip.audioFadeOutMs == cleanOut &&
                            clip.audioEnhance == resolvedEnhance
                        ) {
                            clip
                        } else {
                            changed = true
                            clip.copy(
                                volume = clampedVolume,
                                muted = nextMuted,
                                volumeBeforeMute = if (clampedVolume > 0f) clampedVolume else clip.volumeBeforeMute,
                                audioBoost = clampedBoost,
                                audioFadeInMs = cleanIn,
                                audioFadeOutMs = cleanOut,
                                audioEnhance = resolvedEnhance,
                            )
                        }
                    }
                }
                if (updatedClips == track.clips) track else track.copy(clips = updatedClips)
            }
        }
        return if (!changed) project else project.copy(tracks = updatedTracks)
    }

    fun setClipVolume(
        project: Project,
        clipId: String,
        volume: Float,
    ): Project {
        val clampedVolume = sanitizeVolume(volume)
        return modifyClipTrack(project, clipId) { clips, clipIndex ->
            val clip = clips[clipIndex]
            val nextMuted = if (clampedVolume > 0f) false else clip.muted
            if (clip.isStillImage || (clip.volume == clampedVolume && clip.muted == nextMuted)) {
                return@modifyClipTrack null
            }
            clips.toMutableList().apply {
                set(
                    clipIndex,
                    clip.copy(
                        volume = clampedVolume,
                        muted = nextMuted,
                        volumeBeforeMute = if (clampedVolume > 0f) clampedVolume else clip.volumeBeforeMute,
                    ),
                )
            }
        }
    }

    fun toggleClipMute(project: Project, clipId: String): Project =
        toggleMuteOnTrack(project, clipId) { trackType ->
            trackType == TrackType.VIDEO || trackType == TrackType.AUDIO || trackType == TrackType.OVERLAY
        }

    fun toggleVideoClipMute(project: Project, clipId: String): Project =
        toggleMuteOnTrack(project, clipId) { it == TrackType.VIDEO }

    fun toggleOverlayVideoMute(project: Project, clipId: String): Project =
        toggleMuteOnTrack(project, clipId) { it == TrackType.OVERLAY }

    fun toggleMuteAllVideoClips(project: Project): Project {
        val trackIndex = project.tracks.indexOfFirst { it.type == TrackType.VIDEO }
        if (trackIndex < 0) return project
        val clips = project.tracks[trackIndex].clips
        val eligible = clips.filter { !it.isStillImage }
        if (eligible.isEmpty()) return project
        val shouldMute = eligible.any { !it.muted }
        val updated = clips.map { clip ->
            if (clip.isStillImage) {
                clip
            } else if (shouldMute) {
                if (clip.muted) {
                    clip
                } else {
                    clip.copy(
                        muted = true,
                        volumeBeforeMute = sanitizeVolume(clip.volume),
                        volume = 0f,
                    )
                }
            } else {
                if (!clip.muted) {
                    clip
                } else {
                    clip.copy(
                        muted = false,
                        volume = restoreVolume(clip.volumeBeforeMute),
                    )
                }
            }
        }
        if (updated == clips) return project
        return updateTrack(project, trackIndex, updated)
    }

    private fun toggleMuteOnTrack(
        project: Project,
        clipId: String,
        trackFilter: (TrackType) -> Boolean,
    ): Project = modifyClipTrack(project, clipId, trackFilter = trackFilter) { clips, index ->
        val clip = clips[index]
        if (clip.isStillImage) return@modifyClipTrack null
        val next = if (!clip.muted) {
            clip.copy(
                muted = true,
                volumeBeforeMute = sanitizeVolume(clip.volume),
                volume = 0f,
            )
        } else {
            clip.copy(
                muted = false,
                volume = restoreVolume(clip.volumeBeforeMute),
            )
        }
        if (next == clip) null else clips.toMutableList().apply { set(index, next) }
    }

    private fun restoreVolume(stored: Float): Float {
        val clean = sanitizeVolume(stored)
        return if (clean <= 0f) 1.0f else clean
    }

    fun canSplitAudioClipAt(project: Project, clipId: String, splitPositionMs: Long): Boolean {
        val clip = project.audioClips().firstOrNull { it.id == clipId } ?: return false
        if (clip.isStillImage) return false
        val durationMs = effectiveDurationMs(clip)
        val offsetMs = splitPositionMs - clip.startTimeMs
        return offsetMs >= AUDIO_SPLIT_MIN_HALF_MS &&
            (durationMs - offsetMs) >= AUDIO_SPLIT_MIN_HALF_MS
    }

    fun splitAudioClipAt(
        project: Project,
        clipId: String,
        splitPositionMs: Long,
        newClipId: String,
    ): Project {
        if (newClipId.isBlank() || newClipId == clipId) return project
        if (project.tracks.any { track -> track.clips.any { it.id == newClipId } }) return project
        if (!canSplitAudioClipAt(project, clipId, splitPositionMs)) return project
        return modifyClipTrack(project, clipId, requiredTrackType = TrackType.AUDIO) { clips, index ->
            val clip = clips[index]
            val offsetTimelineMs = splitPositionMs - clip.startTimeMs
            val sourceSpanMs = effectiveSourceSpanMs(clip)
            val sourceOffsetMs = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
                timelineOffsetMs = offsetTimelineMs,
                sourceSpanMs = sourceSpanMs,
                baseSpeed = clip.playbackSpeed,
                preset = clip.speedRamp,
            )
            val currentStart = effectiveTrimStartMs(clip)
            val currentEnd = effectiveTrimEndMs(clip)
            val splitSourceMs = currentStart + sourceOffsetMs
            val leftSpanMs = sourceOffsetMs
            val rightSpanMs = (currentEnd - splitSourceMs).coerceAtLeast(0L)
            if (leftSpanMs < AUDIO_SPLIT_MIN_HALF_MS || rightSpanMs < AUDIO_SPLIT_MIN_HALF_MS) {
                return@modifyClipTrack null
            }
            val leftDurationMs = SpeedRampEvaluator.scaledDurationMs(leftSpanMs, clip.playbackSpeed, clip.speedRamp)
            val rightDurationMs = SpeedRampEvaluator.scaledDurationMs(rightSpanMs, clip.playbackSpeed, clip.speedRamp)
            val left = clip.copy(
                durationMs = leftDurationMs,
                trimStartMs = currentStart,
                trimEndMs = splitSourceMs,
                audioFadeOutMs = 0L,
            )
            val right = clip.copy(
                id = newClipId,
                durationMs = rightDurationMs,
                trimStartMs = splitSourceMs,
                trimEndMs = currentEnd,
                audioFadeInMs = 0L,
            )
            buildList(clips.size + 1) {
                clips.forEachIndexed { i, existing ->
                    if (i == index) {
                        add(left)
                        add(right)
                    } else {
                        add(existing)
                    }
                }
            }
        }
    }

    fun addVideoOverlayClip(
        project: Project,
        clipId: String,
        mediaUri: String,
        newTrackId: String,
        sourceDurationMs: Long,
    ): Project {
        if (mediaUri.isBlank() || sourceDurationMs <= 0L) return project
        val totalVideoMs = videoDurationMs(project)
        val initialDurationMs = if (totalVideoMs > 0L) {
            minOf(DEFAULT_OVERLAY_DURATION_MS, totalVideoMs, sourceDurationMs)
        } else {
            minOf(DEFAULT_OVERLAY_DURATION_MS, sourceDurationMs)
        }.coerceAtLeast(MIN_CLIP_DURATION_MS)
        val overlayClip = Clip(
            id = clipId,
            mediaUri = mediaUri,
            startTimeMs = 0L,
            durationMs = initialDurationMs,
            trimStartMs = 0L,
            trimEndMs = initialDurationMs,
            sourceDurationMs = sourceDurationMs,
            isVideo = true,
            muted = true,
            volumeBeforeMute = 1.0f,
            volume = 0f,
        )
        return appendClip(project, overlayClip, newTrackId, trackType = TrackType.OVERLAY)
    }

    fun setOverlayChromaKey(project: Project, clipId: String, config: ChromaKeyConfig): Project =
        modifyClipTrack(project, clipId, requiredTrackType = TrackType.OVERLAY) { clips, index ->
            val clip = clips[index]
            if (!clip.isVideo) return@modifyClipTrack null
            val clean = config.sanitized()
            if (clip.chromaKey == clean) return@modifyClipTrack null
            clips.toMutableList().apply { set(index, clip.copy(chromaKey = clean)) }
        }

    fun setClipSpeed(
        project: Project,
        clipId: String,
        speed: Float,
        speedRamp: SpeedRampPreset = SpeedRampPreset.NONE,
    ): Project {
        val safeSpeed = if (speedRamp == SpeedRampPreset.NONE) {
            sanitizeSpeed(speed)
        } else {
            Clip.DEFAULT_PLAYBACK_SPEED
        }
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
            val newDurationMs = SpeedRampEvaluator.scaledDurationMs(sourceSpanMs, safeSpeed, speedRamp)
            val updatedEffects = clip.visualEffects?.sanitized(newDurationMs)?.takeUnless { it.isIdentity }

            if (clip.playbackSpeed == safeSpeed &&
                clip.speedRamp == speedRamp &&
                clip.durationMs == newDurationMs &&
                clip.visualEffects == updatedEffects
            ) {
                return@modifyClipTrack null
            }

            val updatedClip = clip.copy(
                playbackSpeed = safeSpeed,
                speedRamp = speedRamp,
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

    /**
     * Appends an extracted audio clip to the Audio track and mutes the source video clip in one
     * atomic, undoable edit so users can trim, speed-ramp, or replace the video's sound separately.
     */
    /**
     * Adds or updates a [BeatMarker] on the project timeline, deduplicating markers within 120 ms
     * so live-performed FX pads snap cleanly onto existing detected beats.
     */
    fun addBeatMarker(project: Project, marker: BeatMarker): Project {
        val safe = marker.sanitized()
        val dedupWindowMs = 120L
        val existingIndex = project.beatMarkers.indexOfFirst {
            kotlin.math.abs(it.timeMs - safe.timeMs) <= dedupWindowMs
        }
        val updated = if (existingIndex >= 0) {
            val existing = project.beatMarkers[existingIndex]
            val merged = existing.copy(
                effect = if (safe.effect != BeatEffectKind.NONE) safe.effect else existing.effect,
                durationMs = safe.durationMs,
                intensity = safe.intensity,
            )
            project.beatMarkers.toMutableList().apply { set(existingIndex, merged) }
        } else {
            (project.beatMarkers + safe).sortedBy { it.timeMs }
        }
        return project.copy(beatMarkers = updated)
    }

    fun replaceBeatMarkers(project: Project, markers: List<BeatMarker>): Project {
        val cleaned = markers
            .map { it.sanitized() }
            .sortedBy { it.timeMs }
        return project.copy(beatMarkers = cleaned)
    }

    fun removeBeatMarker(project: Project, markerId: String): Project {
        val filtered = project.beatMarkers.filterNot { it.id == markerId }
        return if (filtered.size == project.beatMarkers.size) project else project.copy(beatMarkers = filtered)
    }

    fun clearBeatMarkers(project: Project): Project =
        if (project.beatMarkers.isEmpty()) project else project.copy(beatMarkers = emptyList())

    /**
     * Splits video clips at each [Project.beatMarkers] timestamp in ascending order, skipping any
     * beat too close to an existing clip boundary (< [MIN_CLIP_DURATION_MS]).
     */
    fun autoSplitVideoOnBeats(
        project: Project,
        idProvider: () -> String,
    ): Project {
        if (project.beatMarkers.isEmpty()) return project
        var working = project
        val sortedTimes = project.beatMarkers.map { it.timeMs }.distinct().sorted()
        for (beatMs in sortedTimes) {
            val clips = working.videoClips()
            var cursorMs = 0L
            for (clip in clips) {
                val clipDurMs = effectiveDurationMs(clip)
                val clipEndMs = cursorMs + clipDurMs
                if (beatMs > cursorMs && beatMs < clipEndMs && !clip.isStillImage) {
                    val offsetTimelineMs = beatMs - cursorMs
                    val sourceOffsetMs = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
                        timelineOffsetMs = offsetTimelineMs,
                        sourceSpanMs = effectiveSourceSpanMs(clip),
                        baseSpeed = clip.playbackSpeed,
                        preset = clip.speedRamp,
                    )
                    val candidate = splitClip(
                        project = working,
                        clipId = clip.id,
                        offsetMs = sourceOffsetMs,
                        newClipId = idProvider(),
                    )
                    if (candidate != working) {
                        working = candidate
                    }
                    break
                }
                cursorMs = clipEndMs
            }
        }
        return working
    }

    fun extractAudioFromVideoClip(
        project: Project,
        sourceClipId: String,
        newAudioClipId: String,
        audioMediaUri: String,
        audioDurationMs: Long,
        newAudioTrackId: String,
    ): Project {
        if (audioMediaUri.isBlank() || audioDurationMs <= 0L) return project
        val videoTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO } ?: return project
        val sourceClip = videoTrack.clips.firstOrNull { it.id == sourceClipId } ?: return project
        if (sourceClip.isStillImage) return project

        val mutedProject = setClipVolume(project, sourceClipId, 0f)
        val preservedVolume = sanitizeVolume(sourceClip.volume).let { if (it <= 0f) 1.0f else it }
        val extractedClip = Clip(
            id = newAudioClipId,
            mediaUri = audioMediaUri,
            startTimeMs = 0L,
            durationMs = audioDurationMs,
            sourceDurationMs = audioDurationMs,
            trimStartMs = 0L,
            trimEndMs = audioDurationMs,
            volume = preservedVolume,
            playbackSpeed = effectiveSpeed(sourceClip),
        )
        return appendClip(
            project = mutedProject,
            clip = extractedClip,
            newTrackId = newAudioTrackId,
            trackType = TrackType.AUDIO,
        )
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
            val nextTimelineDuration = SpeedRampEvaluator.scaledDurationMs(nextSourceSpan, clip.playbackSpeed, clip.speedRamp)
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
        val currentStartMs = effectiveTrimStartMs(clip).coerceAtMost(sourceDurationMs)
        val currentEndMs = effectiveTrimEndMs(clip).coerceAtLeast(currentStartMs)
        val clipTimelineDurationMs = effectiveDurationMs(clip)
        val offsetInClipMs = safePlayheadMs - clip.startTimeMs
        if (offsetInClipMs <= 0L || offsetInClipMs >= clipTimelineDurationMs || currentEndMs <= currentStartMs) {
            return clip.copy(id = newClipId)
        }
        val sourceOffsetMs = SpeedRampEvaluator.timelineOffsetToSourceOffsetMs(
            timelineOffsetMs = offsetInClipMs,
            sourceSpanMs = effectiveSourceSpanMs(clip),
            baseSpeed = clip.playbackSpeed,
            preset = clip.speedRamp,
        ).coerceAtLeast(0L)
        val cutSourceMs = saturatingAdd(currentStartMs, sourceOffsetMs).coerceIn(currentStartMs, currentEndMs)
        val (nextStartMs, nextEndMs) = if (fromPlayhead) {
            cutSourceMs to currentEndMs
        } else {
            currentStartMs to cutSourceMs
        }
        val nextSpanMs = nextEndMs - nextStartMs
        if (nextSpanMs < MIN_CLIP_DURATION_MS) return clip.copy(id = newClipId)
        val nextDurationMs = SpeedRampEvaluator.scaledDurationMs(nextSpanMs, speed, clip.speedRamp)
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
            nextStartMs = saturatingAdd(nextStartMs, clip.durationMs.coerceAtLeast(0L))
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
            .maxOfOrNull { saturatingAdd(it.startTimeMs, it.durationMs) }
            ?: 0L
    }

    fun addNewsOverlay(project: Project, item: NewsOverlayItem): Project {
        val sanitized = item.copy(
            headline = item.headline.trim().take(180),
            subline = item.subline.trim().take(180),
            badgeLabel = item.badgeLabel.trim().take(48),
            tickerText = item.tickerText.trim().take(400),
            startTimeMs = item.startTimeMs.coerceAtLeast(0L),
            durationMs = item.durationMs.coerceIn(NewsOverlayItem.MIN_DURATION_MS, NewsOverlayItem.MAX_DURATION_MS),
        )
        return project.copy(newsOverlays = project.newsOverlays + sanitized)
    }

    fun updateNewsOverlay(project: Project, item: NewsOverlayItem): Project {
        if (project.newsOverlays.none { it.id == item.id }) return project
        val sanitized = item.copy(
            headline = item.headline.trim().take(180),
            subline = item.subline.trim().take(180),
            badgeLabel = item.badgeLabel.trim().take(48),
            tickerText = item.tickerText.trim().take(400),
            startTimeMs = item.startTimeMs.coerceAtLeast(0L),
            durationMs = item.durationMs.coerceIn(NewsOverlayItem.MIN_DURATION_MS, NewsOverlayItem.MAX_DURATION_MS),
        )
        return project.copy(
            newsOverlays = project.newsOverlays.map { existing ->
                if (existing.id == sanitized.id) sanitized else existing
            },
        )
    }

    fun removeNewsOverlay(project: Project, overlayId: String): Project {
        if (project.newsOverlays.none { it.id == overlayId }) return project
        return project.copy(newsOverlays = project.newsOverlays.filterNot { it.id == overlayId })
    }

    fun applyNewsBrandPresetToAll(project: Project, preset: NewsBrandPreset): Project {
        if (project.newsOverlays.isEmpty()) return project
        return project.copy(
            newsOverlays = project.newsOverlays.map { it.withBrandPreset(preset) },
        )
    }
}
