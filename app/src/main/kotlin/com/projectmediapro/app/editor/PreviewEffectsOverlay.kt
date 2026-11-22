package com.projectmediapro.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.BeatEffectEvaluator
import com.projectmediapro.core.model.BeatMarker
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.VideoFilterPreset

/**
 * Renders live fade/transition scrims and an unobtrusive active-effect badge over [PreviewPlayer]
 * without calling `ExoPlayer.setVideoEffects` (which freezes Media3 1.4.x during rapid updates).
 */
@Composable
internal fun PreviewEffectsOverlay(
    videoClips: List<Clip>,
    timelinePositionMs: Long,
    beatMarkers: List<BeatMarker> = emptyList(),
    soundReactiveEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val beatSample = BeatEffectEvaluator.evaluate(beatMarkers, timelinePositionMs, soundReactiveEnabled)
    val active = TimelineClock.activeClipAt(videoClips, timelinePositionMs)
    val effects = active?.clip?.visualEffects?.takeUnless { it.isIdentity }
    if (effects == null && beatSample.flashAlpha <= 0.001f && beatSample.glitchAmount <= 0.001f && beatSample.reactiveGlow <= 0.001f) return

    val transitionTransform = if (effects != null) {
        effects.transitionTransformAt(active.positionInClipMs, active.clipDurationMs)
    } else {
        null
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (transitionTransform != null && transitionTransform.scrimAlpha > 0.001f) {
            val baseColor = if (transitionTransform.scrimIsWhite) Color.White else Color.Black
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseColor.copy(alpha = transitionTransform.scrimAlpha.coerceIn(0f, 1f))),
            )
        }

        val whitePulseAlpha = (beatSample.flashAlpha + beatSample.reactiveGlow * 0.08f).coerceIn(0f, 0.9f)
        if (whitePulseAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = whitePulseAlpha)),
            )
        }
        if (beatSample.glitchAmount > 0.001f) {
            val glitchAlpha = (beatSample.glitchAmount * 0.34f).coerceIn(0f, 0.4f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFC084FC).copy(alpha = glitchAlpha)),
            )
        }

        if (effects != null) {
            val badgeLabel = if (effects.filter != VideoFilterPreset.NONE) {
                stringResource(filterPresetLabelRes(effects.filter))
            } else {
                stringResource(R.string.editor_effects_badge_adjusted)
            }
            Surface(
                shape = MediaProShapes.Small,
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(MediaProSpacing.Small),
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(
                        horizontal = MediaProSpacing.Small,
                        vertical = MediaProSpacing.ExtraSmall,
                    ),
                )
            }
        }
    }
}
