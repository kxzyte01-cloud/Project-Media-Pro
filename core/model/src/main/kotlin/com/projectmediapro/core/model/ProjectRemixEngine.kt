package com.projectmediapro.core.model

/**
 * Deterministic, rule-based remix presets that generate isolated alternative edits
 * without modifying the user's original project.
 */
enum class RemixPreset(val suffixLabel: String) {
    STORY_CUT("Story Cut"),
    RHYTHM_CUT("Rhythm Cut"),
    MINIMAL_CUT("Minimal Cut"),
}

/**
 * Pure domain engine that transforms a cloned [Project] according to a [RemixPreset].
 * Never mutates source media URIs, never stretches short clips, and keeps all tracks within valid timeline bounds.
 */
object ProjectRemixEngine {

    private const val RHYTHM_TARGET_CLIP_MS = 2_400L
    private const val RHYTHM_MAX_UNTRIMMED_MS = 3_200L

    fun buildRemix(
        source: Project,
        preset: RemixPreset,
    ): Project {
        val rhythmTransitions = listOf(
            ClipTransition.ZOOM_IN,
            ClipTransition.WHIP_LEFT,
            ClipTransition.WHIP_RIGHT,
            ClipTransition.ZOOM_OUT,
        )
        val rhythmRamps = listOf(
            SpeedRampPreset.MONTAGE,
            SpeedRampPreset.JUMP_CUT,
            SpeedRampPreset.HERO,
        )

        var videoIndex = 0
        val passOneTracks = source.tracks.map { track ->
            when (track.type) {
                TrackType.VIDEO -> {
                    var cursorMs = 0L
                    val updatedClips = track.clips.map { clip ->
                        val idx = videoIndex++
                        val baseFx = clip.visualEffects ?: ClipVisualEffects()
                        val updated = when (preset) {
                            RemixPreset.STORY_CUT -> {
                                val resolvedFilter = if (baseFx.filter == VideoFilterPreset.NONE) {
                                    VideoFilterPreset.CINEMA
                                } else {
                                    baseFx.filter
                                }
                                clip.copy(
                                    startTimeMs = cursorMs,
                                    speedRamp = SpeedRampPreset.NONE,
                                    visualEffects = baseFx.copy(
                                        filter = resolvedFilter,
                                        transitionIn = ClipTransition.FADE_BLACK,
                                        fadeInMs = 450L,
                                    ),
                                )
                            }

                            RemixPreset.RHYTHM_CUT -> {
                                val safeDuration = clip.durationMs.coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
                                val trimmedDuration = if (safeDuration > RHYTHM_MAX_UNTRIMMED_MS) {
                                    RHYTHM_TARGET_CLIP_MS
                                } else {
                                    safeDuration
                                }
                                val shouldTrim = trimmedDuration < safeDuration
                                val updatedTrimEnd = if (shouldTrim && !clip.isStillImage) {
                                    val speed = clip.playbackSpeed.coerceIn(Clip.MIN_PLAYBACK_SPEED, Clip.MAX_PLAYBACK_SPEED)
                                    val maxSource = if (clip.sourceDurationMs > 0L) clip.sourceDurationMs else Long.MAX_VALUE
                                    (clip.trimStartMs + (trimmedDuration * speed).toLong())
                                        .coerceAtMost(maxSource)
                                        .coerceAtLeast(clip.trimStartMs + ProjectTimeline.MIN_CLIP_DURATION_MS)
                                } else {
                                    clip.trimEndMs
                                }
                                clip.copy(
                                    startTimeMs = cursorMs,
                                    durationMs = if (shouldTrim) trimmedDuration else safeDuration,
                                    trimEndMs = updatedTrimEnd,
                                    speedRamp = rhythmRamps[idx % rhythmRamps.size],
                                    visualEffects = baseFx.copy(
                                        transitionIn = rhythmTransitions[idx % rhythmTransitions.size],
                                        fadeInMs = 240L,
                                    ),
                                )
                            }

                            RemixPreset.MINIMAL_CUT -> {
                                val resolvedFilter = if (baseFx.filter == VideoFilterPreset.NONE) {
                                    VideoFilterPreset.FADE_FILM
                                } else {
                                    baseFx.filter
                                }
                                clip.copy(
                                    startTimeMs = cursorMs,
                                    speedRamp = SpeedRampPreset.NONE,
                                    visualEffects = baseFx.copy(
                                        filter = resolvedFilter,
                                        transitionIn = ClipTransition.NONE,
                                        fadeInMs = 0L,
                                        transitionOut = ClipTransition.NONE,
                                        fadeOutMs = 0L,
                                    ),
                                )
                            }
                        }
                        cursorMs += updated.durationMs.coerceAtLeast(0L)
                        updated
                    }
                    track.copy(clips = updatedClips)
                }

                TrackType.AUDIO -> track.copy(
                    clips = track.clips.map { clip ->
                        val baseEnhance = clip.audioEnhance ?: AudioEnhanceConfig()
                        when (preset) {
                            RemixPreset.STORY_CUT -> clip.copy(
                                audioEnhance = baseEnhance.copy(
                                    eqPreset = AudioEqPreset.STUDIO_WARMTH,
                                    autoDuckMusicEnabled = true,
                                ),
                                audioFadeInMs = clip.audioFadeInMs.coerceAtLeast(400L),
                                audioFadeOutMs = clip.audioFadeOutMs.coerceAtLeast(600L),
                            )

                            RemixPreset.RHYTHM_CUT -> clip.copy(
                                audioEnhance = baseEnhance.copy(
                                    eqPreset = AudioEqPreset.BASS_BOOST,
                                ),
                            )

                            RemixPreset.MINIMAL_CUT -> clip.copy(
                                audioEnhance = baseEnhance.copy(
                                    eqPreset = AudioEqPreset.VOCAL_CLARITY,
                                    noiseGateEnabled = true,
                                ),
                            )
                        }
                    },
                )

                TrackType.OVERLAY, TrackType.TEXT -> track
            }
        }

        val recomputedDurationMs = passOneTracks
            .filter { it.type == TrackType.VIDEO }
            .flatMap { it.clips }
            .sumOf { it.durationMs.coerceAtLeast(0L) }

        // Keep overlay and text tracks strictly inside the remixed video timeline when Rhythm Cut shortens it.
        val finalTracks = if (recomputedDurationMs > 0L) {
            passOneTracks.map { track ->
                if (track.type == TrackType.OVERLAY || track.type == TrackType.TEXT) {
                    track.copy(
                        clips = track.clips.mapNotNull { clip ->
                            val start = clip.startTimeMs.coerceAtLeast(0L)
                            if (start >= recomputedDurationMs) {
                                null
                            } else {
                                val maxDur = (recomputedDurationMs - start).coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS)
                                val clampedDur = minOf(clip.durationMs.coerceAtLeast(ProjectTimeline.MIN_CLIP_DURATION_MS), maxDur)
                                clip.copy(startTimeMs = start, durationMs = clampedDur)
                            }
                        },
                    )
                } else {
                    track
                }
            }
        } else {
            passOneTracks
        }

        return source.copy(
            tracks = finalTracks,
            durationMs = recomputedDurationMs,
            soundReactiveEnabled = when (preset) {
                RemixPreset.RHYTHM_CUT -> true
                RemixPreset.MINIMAL_CUT -> false
                RemixPreset.STORY_CUT -> source.soundReactiveEnabled
            },
        )
    }
}
