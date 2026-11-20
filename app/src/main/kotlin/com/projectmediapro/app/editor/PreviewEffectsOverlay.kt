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
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipTransition
import com.projectmediapro.core.model.VideoFilterPreset

/**
 * Renders live fade/transition scrims and an unobtrusive active-effect badge over [PreviewPlayer]
 * without calling `ExoPlayer.setVideoEffects` (which freezes Media3 1.4.x during rapid updates).
 */
@Composable
internal fun PreviewEffectsOverlay(
    videoClips: List<Clip>,
    timelinePositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val active = TimelineClock.activeClipAt(videoClips, timelinePositionMs) ?: return
    val effects = active.clip.visualEffects ?: return
    if (effects.isIdentity) return

    val fade = effects.fadeOverlayAt(active.positionInClipMs, active.clipDurationMs)

    Box(modifier = modifier.fillMaxSize()) {
        if (fade != null) {
            val (transition, alpha) = fade
            val scrimColor = when (transition) {
                ClipTransition.FADE_BLACK -> Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))
                ClipTransition.FADE_WHITE -> Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
                ClipTransition.NONE -> Color.Transparent
            }
            if (scrimColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor),
                )
            }
        }

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
