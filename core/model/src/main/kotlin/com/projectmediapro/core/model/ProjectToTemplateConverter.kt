package com.projectmediapro.core.model

/**
 * Pure domain converter that captures a live edited [Project] into a reusable [Template],
 * preserving per-clip visual effects, transitions, speed ramps, audio polish, overlay styles, and text overlays.
 */
object ProjectToTemplateConverter {

    fun fromProject(
        baseTemplate: Template,
        project: Project,
        description: String,
        replaceableClipIds: Set<String>,
        lockedMediaPathsByClipId: Map<String, String>,
        captionStyleJson: String? = null,
        idProvider: () -> String,
    ): Template {
        val slots = mutableListOf<Slot>()

        val videoClips = project.videoClips()
        videoClips.forEachIndexed { index, clip ->
            val isReplaceable = clip.id in replaceableClipIds
            val lockedPath = if (isReplaceable) null else lockedMediaPathsByClipId[clip.id]
            slots.add(
                Slot(
                    id = idProvider(),
                    label = "Clip ${index + 1}",
                    requiredType = TrackType.VIDEO,
                    filledMediaUri = lockedPath,
                    role = SlotRole.MAIN_VIDEO,
                    isReplaceable = isReplaceable || lockedPath == null,
                    durationMs = clip.durationMs.coerceAtLeast(0L),
                    visualEffects = clip.visualEffects,
                    playbackSpeed = clip.playbackSpeed,
                    speedRamp = clip.speedRamp,
                    volume = clip.volume,
                    audioBoost = clip.audioBoost,
                    audioFadeInMs = clip.audioFadeInMs,
                    audioFadeOutMs = clip.audioFadeOutMs,
                    audioEnhance = clip.audioEnhance,
                    rotationDegrees = clip.rotationDegrees,
                    flipHorizontal = clip.flipHorizontal,
                    flipVertical = clip.flipVertical,
                    crop = clip.crop,
                ),
            )
        }

        val audioClips = project.audioClips()
        audioClips.forEachIndexed { index, clip ->
            val isReplaceable = clip.id in replaceableClipIds
            val lockedPath = if (isReplaceable) null else lockedMediaPathsByClipId[clip.id]
            slots.add(
                Slot(
                    id = idProvider(),
                    label = "Audio ${index + 1}",
                    requiredType = TrackType.AUDIO,
                    filledMediaUri = lockedPath,
                    role = SlotRole.AUDIO,
                    isReplaceable = isReplaceable || lockedPath == null,
                    durationMs = clip.durationMs.coerceAtLeast(0L),
                    playbackSpeed = clip.playbackSpeed,
                    volume = clip.volume,
                    audioBoost = clip.audioBoost,
                    audioFadeInMs = clip.audioFadeInMs,
                    audioFadeOutMs = clip.audioFadeOutMs,
                    audioEnhance = clip.audioEnhance,
                ),
            )
        }

        val overlayClips = project.overlayClips()
        overlayClips.forEachIndexed { index, clip ->
            val isReplaceable = clip.id in replaceableClipIds
            val lockedPath = if (isReplaceable) null else lockedMediaPathsByClipId[clip.id]
            slots.add(
                Slot(
                    id = idProvider(),
                    label = "Overlay ${index + 1}",
                    requiredType = TrackType.OVERLAY,
                    filledMediaUri = lockedPath,
                    role = SlotRole.OVERLAY,
                    isReplaceable = isReplaceable || lockedPath == null,
                    durationMs = clip.durationMs.coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS),
                    startTimeMs = clip.startTimeMs.coerceAtLeast(0L),
                    overlayCenterX = clip.overlayCenterX,
                    overlayCenterY = clip.overlayCenterY,
                    overlayWidthFraction = clip.overlayWidthFraction,
                    overlayStyle = clip.overlayStyle,
                ),
            )
        }

        val textClips = project.textClips()
            .filter { it.textContent.isNotBlank() }
            .map { clip ->
                TemplateTextClip(
                    id = idProvider(),
                    textContent = clip.textContent,
                    startTimeMs = clip.startTimeMs.coerceAtLeast(0L),
                    durationMs = clip.durationMs.coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS),
                    centerX = clip.overlayCenterX,
                    centerY = clip.overlayCenterY,
                    widthFraction = clip.overlayWidthFraction,
                    textStyle = clip.textStyle,
                )
            }

        return baseTemplate.copy(
            description = description.trim(),
            slots = slots,
            canvasAspect = project.canvasAspect,
            canvasBackground = project.canvasBackground,
            textClips = textClips,
            soundReactiveEnabled = project.soundReactiveEnabled,
            opticalFlowEnabled = project.opticalFlowEnabled,
            captionStyleJson = captionStyleJson,
        )
    }
}
