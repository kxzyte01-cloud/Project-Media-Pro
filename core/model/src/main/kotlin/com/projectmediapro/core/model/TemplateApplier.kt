package com.projectmediapro.core.model

data class ResolvedTemplateMedia(
    val relativePath: String,
    val durationMs: Long,
)

/**
 * Pure domain assembly that turns a [Template] plus resolved per-slot media files into a ready-to-edit [Project].
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
            )
            project = ProjectTimeline.appendClip(
                project = project,
                clip = clip,
                newTrackId = idProvider(),
                trackType = TrackType.OVERLAY,
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
            val durationMs = media.durationMs.coerceAtLeast(0L)
            if (durationMs <= 0L) continue
            val clip = Clip(
                id = idProvider(),
                mediaUri = media.relativePath,
                startTimeMs = 0L,
                durationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                sourceDurationMs = durationMs,
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
