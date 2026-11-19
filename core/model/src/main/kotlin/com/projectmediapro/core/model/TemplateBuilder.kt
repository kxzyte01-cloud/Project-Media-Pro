package com.projectmediapro.core.model

fun Template.introSlot(): Slot? = slots.firstOrNull { it.role == SlotRole.INTRO }

fun Template.mainVideoSlots(): List<Slot> = slots.filter { it.role == SlotRole.MAIN_VIDEO }

fun Template.outroSlot(): Slot? = slots.firstOrNull { it.role == SlotRole.OUTRO }

fun Template.overlaySlots(): List<Slot> = slots.filter { it.role == SlotRole.OVERLAY }

fun Template.audioSlots(): List<Slot> = slots.filter { it.role == SlotRole.AUDIO }

object TemplateBuilder {

    const val MIN_OVERLAY_DURATION_MS: Long = ProjectTimeline.MIN_CLIP_DURATION_MS
    const val DEFAULT_OVERLAY_DURATION_MS: Long = ProjectTimeline.DEFAULT_OVERLAY_DURATION_MS
    const val MAX_OVERLAY_DURATION_MS: Long = 30_000L

    fun createDefaultSlots(
        introId: String,
        mainVideoId: String,
        outroId: String,
        introLabel: String = "Intro",
        videoLabel: String = "Video 1",
        outroLabel: String = "Outro",
    ): List<Slot> = listOf(
        Slot(
            id = introId,
            label = introLabel,
            requiredType = TrackType.VIDEO,
            role = SlotRole.INTRO,
            isReplaceable = false,
        ),
        Slot(
            id = mainVideoId,
            label = videoLabel,
            requiredType = TrackType.VIDEO,
            role = SlotRole.MAIN_VIDEO,
            isReplaceable = true,
        ),
        Slot(
            id = outroId,
            label = outroLabel,
            requiredType = TrackType.VIDEO,
            role = SlotRole.OUTRO,
            isReplaceable = false,
        ),
    )

    fun isMediaReferenced(template: Template, mediaUri: String): Boolean =
        template.slots.any { it.filledMediaUri == mediaUri }

    fun setSlotMedia(
        template: Template,
        slotId: String,
        mediaUri: String,
        durationMs: Long,
    ): Template {
        val index = template.slots.indexOfFirst { it.id == slotId }
        if (index < 0) return template
        val slot = template.slots[index]
        val resolvedDurationMs = if (slot.role == SlotRole.OVERLAY) {
            slot.durationMs.coerceIn(MIN_OVERLAY_DURATION_MS, MAX_OVERLAY_DURATION_MS)
        } else {
            durationMs.coerceAtLeast(0L)
        }
        val updated = slot.copy(
            filledMediaUri = mediaUri,
            durationMs = resolvedDurationMs,
        )
        return replaceSlotAt(template, index, updated)
    }

    fun clearSlotMedia(
        template: Template,
        slotId: String,
    ): Template {
        val index = template.slots.indexOfFirst { it.id == slotId }
        if (index < 0) return template
        val slot = template.slots[index]
        if (slot.filledMediaUri == null) return template
        val updated = slot.copy(
            filledMediaUri = null,
            durationMs = if (slot.role == SlotRole.OVERLAY) slot.durationMs else 0L,
        )
        return replaceSlotAt(template, index, updated)
    }

    fun addVideoSlot(
        template: Template,
        slotId: String,
        label: String,
    ): Template {
        if (template.slots.any { it.id == slotId }) return template
        val newSlot = Slot(
            id = slotId,
            label = label,
            requiredType = TrackType.VIDEO,
            role = SlotRole.MAIN_VIDEO,
            isReplaceable = true,
        )
        val outroIndex = template.slots.indexOfFirst { it.role == SlotRole.OUTRO }
        val updatedSlots = template.slots.toMutableList()
        if (outroIndex >= 0) {
            updatedSlots.add(outroIndex, newSlot)
        } else {
            updatedSlots.add(newSlot)
        }
        return template.copy(slots = updatedSlots)
    }

    fun addAudioSlot(
        template: Template,
        slotId: String,
        label: String,
    ): Template {
        if (template.slots.any { it.id == slotId }) return template
        val newSlot = Slot(
            id = slotId,
            label = label,
            requiredType = TrackType.AUDIO,
            role = SlotRole.AUDIO,
            isReplaceable = true,
        )
        return template.copy(slots = template.slots + newSlot)
    }

    fun addOverlaySlot(
        template: Template,
        slotId: String,
        label: String,
        mediaUri: String,
        durationMs: Long = DEFAULT_OVERLAY_DURATION_MS,
    ): Template {
        if (template.slots.any { it.id == slotId }) return template
        val newSlot = Slot(
            id = slotId,
            label = label,
            requiredType = TrackType.OVERLAY,
            filledMediaUri = mediaUri,
            role = SlotRole.OVERLAY,
            isReplaceable = false,
            durationMs = durationMs.coerceIn(MIN_OVERLAY_DURATION_MS, MAX_OVERLAY_DURATION_MS),
            startTimeMs = 0L,
        )
        return template.copy(slots = template.slots + newSlot)
    }

    fun updateOverlayTiming(
        template: Template,
        slotId: String,
        startTimeMs: Long,
        durationMs: Long,
    ): Template {
        val index = template.slots.indexOfFirst { it.id == slotId && it.role == SlotRole.OVERLAY }
        if (index < 0) return template
        val slot = template.slots[index]
        val clampedStart = startTimeMs.coerceAtLeast(0L)
        val clampedDuration = durationMs.coerceIn(MIN_OVERLAY_DURATION_MS, MAX_OVERLAY_DURATION_MS)
        if (slot.startTimeMs == clampedStart && slot.durationMs == clampedDuration) return template
        return replaceSlotAt(
            template,
            index,
            slot.copy(startTimeMs = clampedStart, durationMs = clampedDuration),
        )
    }

    fun toggleSlotReplaceable(
        template: Template,
        slotId: String,
    ): Template {
        val index = template.slots.indexOfFirst { it.id == slotId }
        if (index < 0) return template
        val slot = template.slots[index]
        return replaceSlotAt(template, index, slot.copy(isReplaceable = !slot.isReplaceable))
    }

    fun moveAudioSlot(
        template: Template,
        slotId: String,
        delta: Int,
    ): Template {
        if (delta != -1 && delta != 1) return template
        val audioIndices = template.slots.indices.filter { template.slots[it].role == SlotRole.AUDIO }
        val posInAudio = audioIndices.indexOfFirst { template.slots[it].id == slotId }
        if (posInAudio < 0) return template
        val targetPosInAudio = posInAudio + delta
        if (targetPosInAudio !in audioIndices.indices) return template

        val fromIndex = audioIndices[posInAudio]
        val toIndex = audioIndices[targetPosInAudio]
        val updated = template.slots.toMutableList()
        val temp = updated[fromIndex]
        updated[fromIndex] = updated[toIndex]
        updated[toIndex] = temp
        return template.copy(slots = updated)
    }

    fun removeSlot(
        template: Template,
        slotId: String,
    ): Template {
        val index = template.slots.indexOfFirst { it.id == slotId }
        if (index < 0) return template
        val slot = template.slots[index]
        if (slot.role == SlotRole.INTRO || slot.role == SlotRole.OUTRO) {
            return clearSlotMedia(template, slotId)
        }
        if (slot.role == SlotRole.MAIN_VIDEO && template.mainVideoSlots().size <= 1) {
            return clearSlotMedia(template, slotId)
        }
        val updated = template.slots.toMutableList().apply { removeAt(index) }
        return template.copy(slots = updated)
    }

    private fun replaceSlotAt(
        template: Template,
        index: Int,
        slot: Slot,
    ): Template {
        val updated = template.slots.toMutableList().apply { set(index, slot) }
        return template.copy(slots = updated)
    }
}
