package com.projectmediapro.core.model

/**
 * Result of applying a [Template]'s visual and audio Style DNA onto an existing [Project].
 */
data class StyleApplicationResult(
    val project: Project,
    val styledVideoClips: Int,
    val styledAudioClips: Int,
    val styledOverlayClips: Int,
)

/**
 * Pure domain engine that transfers a [Template]'s visual & audio Style DNA
 * (LUTs/color grade, transitions, speed ramps, Audio Studio EQ/Auto-Duck, overlay blend styles,
 * and project-level reactive/optical-flow flags) onto an existing [Project] without altering
 * any user media URIs, trims, or clip order.
 */
object TemplateStyleApplier {

    fun applyStyle(
        project: Project,
        template: Template,
    ): StyleApplicationResult {
        val videoSlots = buildList {
            template.introSlot()?.let(::add)
            addAll(template.mainVideoSlots())
            template.outroSlot()?.let(::add)
        }
        val audioSlots = template.audioSlots()
        val overlaySlots = template.overlaySlots()

        val fallbackVideoSlot = videoSlots.firstOrNull {
            it.visualEffects != null || it.speedRamp != SpeedRampPreset.NONE
        } ?: videoSlots.firstOrNull()
        val fallbackAudioSlot = audioSlots.firstOrNull {
            it.audioEnhance != null || it.audioFadeInMs > 0L || it.audioFadeOutMs > 0L
        } ?: audioSlots.firstOrNull()
        val fallbackOverlaySlot = overlaySlots.firstOrNull {
            it.overlayStyle != null
        } ?: overlaySlots.firstOrNull()

        var styledVideos = 0
        var styledAudios = 0
        var styledOverlays = 0
        var videoIdx = 0
        var audioIdx = 0
        var overlayIdx = 0

        val updatedTracks = project.tracks.map { track ->
            when (track.type) {
                TrackType.VIDEO -> track.copy(
                    clips = track.clips.map { clip ->
                        val sourceSlot = videoSlots.getOrNull(videoIdx) ?: fallbackVideoSlot
                        videoIdx++
                        if (sourceSlot != null && (sourceSlot.visualEffects != null || sourceSlot.speedRamp != SpeedRampPreset.NONE)) {
                            styledVideos++
                            clip.copy(
                                visualEffects = sourceSlot.visualEffects ?: clip.visualEffects,
                                speedRamp = if (sourceSlot.speedRamp != SpeedRampPreset.NONE) {
                                    sourceSlot.speedRamp
                                } else {
                                    clip.speedRamp
                                },
                            )
                        } else {
                            clip
                        }
                    },
                )

                TrackType.AUDIO -> track.copy(
                    clips = track.clips.map { clip ->
                        val sourceSlot = audioSlots.getOrNull(audioIdx) ?: fallbackAudioSlot
                        audioIdx++
                        if (sourceSlot != null && (sourceSlot.audioEnhance != null || sourceSlot.audioFadeInMs > 0L || sourceSlot.audioFadeOutMs > 0L)) {
                            styledAudios++
                            clip.copy(
                                audioEnhance = sourceSlot.audioEnhance ?: clip.audioEnhance,
                                audioFadeInMs = if (sourceSlot.audioFadeInMs > 0L) sourceSlot.audioFadeInMs else clip.audioFadeInMs,
                                audioFadeOutMs = if (sourceSlot.audioFadeOutMs > 0L) sourceSlot.audioFadeOutMs else clip.audioFadeOutMs,
                            )
                        } else {
                            clip
                        }
                    },
                )

                TrackType.OVERLAY -> track.copy(
                    clips = track.clips.map { clip ->
                        val sourceSlot = overlaySlots.getOrNull(overlayIdx) ?: fallbackOverlaySlot
                        overlayIdx++
                        if (sourceSlot?.overlayStyle != null) {
                            styledOverlays++
                            clip.copy(overlayStyle = sourceSlot.overlayStyle)
                        } else {
                            clip
                        }
                    },
                )

                TrackType.TEXT -> track
            }
        }

        val updatedProject = project.copy(
            tracks = updatedTracks,
            soundReactiveEnabled = template.soundReactiveEnabled || project.soundReactiveEnabled,
            opticalFlowEnabled = template.opticalFlowEnabled || project.opticalFlowEnabled,
        )
        return StyleApplicationResult(
            project = updatedProject,
            styledVideoClips = styledVideos,
            styledAudioClips = styledAudios,
            styledOverlayClips = styledOverlays,
        )
    }
}
