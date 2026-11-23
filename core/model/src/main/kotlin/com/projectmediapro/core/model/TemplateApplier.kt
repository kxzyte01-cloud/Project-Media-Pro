package com.projectmediapro.core.model

data class ResolvedTemplateMedia(
    val relativePath: String,
    val durationMs: Long,
)

/**
 * Pure domain assembly that turns a [Template] plus resolved per-slot media files into a ready-to-edit [Project].
 * Preserves per-slot visual effects, LUTs, transitions, speed ramps, audio polish, overlay styles, and text overlays.
 */
object TemplateApplier {

    fun buildProjectFromTemplate(
        baseProject: Project,
        template: Template,
        resolvedMediaBySlotId: Map<String, ResolvedTemplateMedia>,
        idProvider: () -> String,
    ): Project {
        var project = baseProject.copy(
            canvasAspect = template.canvasAspect,
            canvasBackground = template.canvasBackground,
            soundReactiveEnabled = template.soundReactiveEnabled,
            opticalFlowEnabled = template.opticalFlowEnabled,
        )

        val orderedVideoSlots = buildList {
            template.introSlot()?.let(::add)
            addAll(template.mainVideoSlots())
            template.outroSlot()?.let(::add)
        }
        project = appendSequentialSlots(
            project = project,
            slots = orderedVideoSlots,
            trackType = TrackType.VIDEO,
            resolvedMediaBySlotId = resolvedMediaBySlotId,
            idProvider = idProvider,
        )
        project = appendSequentialSlots(
            project = project,
            slots = template.audioSlots(),
            trackType = TrackType.AUDIO,
            resolvedMediaBySlotId = resolvedMediaBySlotId,
            idProvider = idProvider,
        )

        val totalVideoMs = ProjectTimeline.videoDurationMs(project)
        for (slot in template.overlaySlots()) {
            val media = resolvedMediaBySlotId[slot.id] ?: continue
            val rawDurationMs = slot.durationMs.coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
            val resolvedDurationMs = if (totalVideoMs > 0L) {
                minOf(rawDurationMs, totalVideoMs)
            } else {
                rawDurationMs
            }
            val clip = Clip(
                id = idProvider(),
                mediaUri = media.relativePath,
                startTimeMs = slot.startTimeMs.coerceAtLeast(0L),
                durationMs = resolvedDurationMs,
                overlayCenterX = slot.overlayCenterX,
                overlayCenterY = slot.overlayCenterY,
                overlayWidthFraction = slot.overlayWidthFraction,
                overlayStyle = slot.overlayStyle,
            )
            project = ProjectTimeline.appendClip(
                project = project,
                clip = clip,
                newTrackId = idProvider(),
                trackType = TrackType.OVERLAY,
            )
        }

        for (textTemplate in template.textClips) {
            if (textTemplate.textContent.isBlank()) continue
            val rawDurationMs = textTemplate.durationMs.coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
            val resolvedDurationMs = if (totalVideoMs > 0L) {
                minOf(rawDurationMs, totalVideoMs)
            } else {
                rawDurationMs
            }
            val textClip = Clip(
                id = idProvider(),
                mediaUri = "",
                startTimeMs = textTemplate.startTimeMs.coerceAtLeast(0L),
                durationMs = resolvedDurationMs,
                overlayCenterX = textTemplate.centerX,
                overlayCenterY = textTemplate.centerY,
                overlayWidthFraction = textTemplate.widthFraction,
                textContent = textTemplate.textContent,
                textStyle = textTemplate.textStyle,
            )
            project = ProjectTimeline.appendClip(
                project = project,
                clip = textClip,
                newTrackId = idProvider(),
                trackType = TrackType.TEXT,
            )
        }

        return project
    }

    private fun appendSequentialSlots(
        project: Project,
        slots: List<Slot>,
        trackType: TrackType,
        resolvedMediaBySlotId: Map<String, ResolvedTemplateMedia>,
        idProvider: () -> String,
    ): Project {
        var updated = project
        for (slot in slots) {
            val media = resolvedMediaBySlotId[slot.id] ?: continue
            val sourceMs = media.durationMs.coerceAtLeast(0L)
            if (sourceMs <= 0L) continue

            // Never stretch a shorter clip; compute required source span for slot timeline duration at safeSpeed.
            val safeSpeed = slot.playbackSpeed.coerceIn(Clip.MIN_PLAYBACK_SPEED, Clip.MAX_PLAYBACK_SPEED)
            val desiredSourceMs = if (slot.durationMs >= ProjectTimeline.MIN_CLIP_DURATION_MS) {
                (slot.durationMs * safeSpeed).toLong().coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
            } else {
                sourceMs
            }
            val targetTrimEndMs = minOf(sourceMs, desiredSourceMs)
            val effectiveDurationMs = (targetTrimEndMs / safeSpeed).toLong()
                .coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)

            val clip = Clip(
                id = idProvider(),
                mediaUri = media.relativePath,
                startTimeMs = 0L,
                durationMs = effectiveDurationMs,
                trimStartMs = 0L,
                trimEndMs = targetTrimEndMs,
                sourceDurationMs = sourceMs,
                playbackSpeed = safeSpeed,
                speedRamp = slot.speedRamp,
                volume = slot.volume.coerceIn(0f, 1f),
                audioBoost = slot.audioBoost.coerceIn(Clip.DEFAULT_AUDIO_BOOST, Clip.MAX_AUDIO_BOOST),
                audioFadeInMs = slot.audioFadeInMs.coerceIn(0L, Clip.MAX_AUDIO_FADE_MS),
                audioFadeOutMs = slot.audioFadeOutMs.coerceIn(0L, Clip.MAX_AUDIO_FADE_MS),
                audioEnhance = slot.audioEnhance,
                visualEffects = slot.visualEffects,
                rotationDegrees = slot.rotationDegrees,
                flipHorizontal = slot.flipHorizontal,
                flipVertical = slot.flipVertical,
                crop = slot.crop,
            )
            updated = ProjectTimeline.appendClip(
                project = updated,
                clip = clip,
                newTrackId = idProvider(),
                trackType = trackType,
            )
        }
        return updated
    }
}
