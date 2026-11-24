package com.projectmediapro.app.template

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.CanvasAspect
import com.projectmediapro.core.model.ClipTransition
import com.projectmediapro.core.model.SpeedRampPreset
import com.projectmediapro.core.model.Template
import com.projectmediapro.core.model.VideoFilterPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatePreviewBanner(
    template: Template,
    modifier: Modifier = Modifier,
) {
    val primaryFilter = remember(template) {
        template.slots.firstNotNullOfOrNull { it.visualEffects?.filter?.takeIf { f -> f != VideoFilterPreset.NONE } }
            ?: VideoFilterPreset.NONE
    }
    val gradientColors = remember(primaryFilter) { paletteForFilter(primaryFilter) }
    val aspectLabel = remember(template.canvasAspect) { aspectLabelFor(template.canvasAspect) }

    val hasLut = primaryFilter != VideoFilterPreset.NONE
    val hasTransitions = remember(template) {
        template.slots.any {
            val fx = it.visualEffects
            fx != null && (fx.transitionIn != ClipTransition.NONE || fx.transitionOut != ClipTransition.NONE)
        }
    }
    val hasSpeedRamp = remember(template) {
        template.slots.any { it.speedRamp != SpeedRampPreset.NONE }
    }
    val hasAudioStudio = remember(template) {
        template.slots.any { it.audioEnhance?.let { cfg -> !cfg.isIdentity } == true }
    }
    val hasLyrics = !template.captionStyleJson.isNullOrBlank()
    val hasText = template.textClips.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(MediaProShapes.Small)
            .background(Brush.linearGradient(colors = gradientColors))
            .padding(MediaProSpacing.Small),
    ) {
        Surface(
            shape = MediaProShapes.Small,
            color = Color.Black.copy(alpha = 0.56f),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text(
                text = aspectLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            if (hasLut) {
                BannerPill(text = stringResource(R.string.templates_badge_lut))
            }
            if (hasTransitions) {
                BannerPill(text = stringResource(R.string.templates_badge_transitions))
            }
            if (hasSpeedRamp) {
                BannerPill(text = stringResource(R.string.templates_badge_speed_ramp))
            }
            if (hasAudioStudio) {
                BannerPill(text = stringResource(R.string.templates_badge_audio_studio))
            }
            if (hasLyrics) {
                BannerPill(text = stringResource(R.string.templates_badge_lyrics))
            }
            if (hasText) {
                BannerPill(text = stringResource(R.string.templates_badge_text))
            }
            if (template.soundReactiveEnabled) {
                BannerPill(text = stringResource(R.string.templates_badge_beat_sync))
            }
        }
    }
}

@Composable
private fun BannerPill(text: String) {
    Surface(
        shape = MediaProShapes.Small,
        color = Color.Black.copy(alpha = 0.56f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

private fun aspectLabelFor(aspect: CanvasAspect): String = when (aspect) {
    CanvasAspect.RATIO_9_16 -> "9:16"
    CanvasAspect.RATIO_16_9 -> "16:9"
    CanvasAspect.RATIO_1_1 -> "1:1"
    CanvasAspect.RATIO_4_5 -> "4:5"
    CanvasAspect.ORIGINAL -> "Auto"
}

private fun paletteForFilter(filter: VideoFilterPreset): List<Color> = when (filter) {
    VideoFilterPreset.MISTY_MOSS -> listOf(Color(0xFF132A24), Color(0xFF2D594B), Color(0xFF6B8E78))
    VideoFilterPreset.GOLDEN_SUNSET -> listOf(Color(0xFF2D142C), Color(0xFFA83E2B), Color(0xFFE89F43))
    VideoFilterPreset.CRIMSON_MOOD -> listOf(Color(0xFF1A090D), Color(0xFF6E1423), Color(0xFFB22234))
    VideoFilterPreset.CYBER_NEON -> listOf(Color(0xFF0A1026), Color(0xFF1A2A6C), Color(0xFF00B4D8))
    VideoFilterPreset.NOIR_PUNCH, VideoFilterPreset.BW -> listOf(Color(0xFF0B0D12), Color(0xFF262B36), Color(0xFF4B5563))
    VideoFilterPreset.DREAMY_GLOW -> listOf(Color(0xFF1F1638), Color(0xFF5B3A78), Color(0xFFC08497))
    VideoFilterPreset.RETRO_FILM, VideoFilterPreset.VINTAGE, VideoFilterPreset.FADE_FILM -> listOf(Color(0xFF291F18), Color(0xFF6E533B), Color(0xFFB8966E))
    VideoFilterPreset.CINEMA -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
    VideoFilterPreset.VIVID -> listOf(Color(0xFF1E1B4B), Color(0xFF4338CA), Color(0xFF0EA5E9))
    VideoFilterPreset.WARM -> listOf(Color(0xFF3B1D0A), Color(0xFF9A3412), Color(0xFFF59E0B))
    VideoFilterPreset.COOL -> listOf(Color(0xFF0C1E35), Color(0xFF1D4E89), Color(0xFF38BDF8))
    VideoFilterPreset.NONE -> listOf(Color(0xFF111827), Color(0xFF1E293B), Color(0xFF334155))
}
