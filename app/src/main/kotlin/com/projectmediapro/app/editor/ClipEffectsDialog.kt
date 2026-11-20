package com.projectmediapro.app.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectmediapro.app.R
import com.projectmediapro.core.designsystem.theme.MediaProShapes
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipTransition
import com.projectmediapro.core.model.ClipVisualEffects
import com.projectmediapro.core.model.FilterGradeSpec
import com.projectmediapro.core.model.ProjectTimeline
import com.projectmediapro.core.model.VideoFilterPreset
import com.projectmediapro.core.model.gradeSpec
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val ClipVisualEffectsSaver: Saver<ClipVisualEffects, Any> = listSaver(
    save = { e ->
        listOf(
            e.filter.name,
            e.brightness,
            e.contrast,
            e.saturation,
            e.warmth,
            e.transitionIn.name,
            e.fadeInMs,
            e.transitionOut.name,
            e.fadeOutMs,
        )
    },
    restore = { raw ->
        val filter = runCatching { VideoFilterPreset.valueOf(raw[0] as String) }
            .getOrDefault(VideoFilterPreset.NONE)
        val transitionIn = runCatching { ClipTransition.valueOf(raw[5] as String) }
            .getOrDefault(ClipTransition.NONE)
        val transitionOut = runCatching { ClipTransition.valueOf(raw[7] as String) }
            .getOrDefault(ClipTransition.NONE)
        ClipVisualEffects(
            filter = filter,
            brightness = raw[1] as Float,
            contrast = raw[2] as Float,
            saturation = raw[3] as Float,
            warmth = raw[4] as Float,
            transitionIn = transitionIn,
            fadeInMs = raw[6] as Long,
            transitionOut = transitionOut,
            fadeOutMs = raw[8] as Long,
        ).sanitized()
    },
)

@Composable
fun ClipEffectsDialog(
    clip: Clip,
    onConfirm: (ClipVisualEffects) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipDurationMs = ProjectTimeline.effectiveDurationMs(clip)
    val maxFadeMs = minOf(ClipVisualEffects.MAX_FADE_MS, (clipDurationMs / 2L).coerceAtLeast(0L))
    val initial = (clip.visualEffects ?: ClipVisualEffects()).sanitized(clipDurationMs)

    var effectsState by rememberSaveable(clip.id, stateSaver = ClipVisualEffectsSaver) {
        mutableStateOf(initial)
    }
    val currentEffects = effectsState.sanitized(clipDurationMs)

    val previewDesc = stringResource(
        R.string.cd_effects_preview,
        stringResource(filterPresetLabelRes(currentEffects.filter)),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.editor_effects_title))
                if (!currentEffects.isIdentity) {
                    DialogActionButton(
                        label = stringResource(R.string.editor_effects_reset),
                        onClick = { effectsState = ClipVisualEffects() },
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                FilterTonePreviewCard(
                    effects = currentEffects,
                    accessibilityLabel = previewDesc,
                )

                EditorDialogSectionLabel(text = stringResource(R.string.editor_effects_filters_label))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemGestureExclusion()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    VideoFilterPreset.entries.forEach { preset ->
                        EditorOptionCard(
                            isSelected = currentEffects.filter == preset,
                            onClick = { effectsState = currentEffects.copy(filter = preset) },
                        ) { contentColor ->
                            Text(
                                text = stringResource(filterPresetLabelRes(preset)),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                modifier = Modifier.padding(
                                    horizontal = MediaProSpacing.MediumSmall,
                                    vertical = MediaProSpacing.Small,
                                ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                EditorDialogSectionLabel(text = stringResource(R.string.editor_effects_color_label))
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_effects_brightness_value,
                        formatSignedPercent((currentEffects.brightness * 200f).roundToInt()),
                    ),
                    value = currentEffects.brightness,
                    valueRange = ClipVisualEffects.MIN_BRIGHTNESS..ClipVisualEffects.MAX_BRIGHTNESS,
                    onValueChange = { effectsState = currentEffects.copy(brightness = it) },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_effects_contrast_value,
                        formatSignedPercent((currentEffects.contrast * 200f).roundToInt()),
                    ),
                    value = currentEffects.contrast,
                    valueRange = ClipVisualEffects.MIN_CONTRAST..ClipVisualEffects.MAX_CONTRAST,
                    onValueChange = { effectsState = currentEffects.copy(contrast = it) },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_effects_saturation_value,
                        formatSignedPercent(currentEffects.saturation.roundToInt()),
                    ),
                    value = currentEffects.saturation,
                    valueRange = ClipVisualEffects.MIN_SATURATION..ClipVisualEffects.MAX_SATURATION,
                    onValueChange = { effectsState = currentEffects.copy(saturation = it) },
                )
                EditorDialogSliderRow(
                    label = stringResource(
                        R.string.editor_effects_warmth_value,
                        formatSignedPercent((currentEffects.warmth * 200f).roundToInt()),
                    ),
                    value = currentEffects.warmth,
                    valueRange = ClipVisualEffects.MIN_WARMTH..ClipVisualEffects.MAX_WARMTH,
                    onValueChange = { effectsState = currentEffects.copy(warmth = it) },
                )

                if (maxFadeMs >= ClipVisualEffects.MIN_FADE_MS) {
                    Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                    FadeTransitionControl(
                        label = stringResource(R.string.editor_effects_fade_in_label),
                        selected = currentEffects.transitionIn,
                        durationMs = currentEffects.fadeInMs,
                        maxFadeMs = maxFadeMs,
                        onSelectTransition = { chosen, nextDurationMs ->
                            effectsState = currentEffects.copy(
                                transitionIn = chosen,
                                fadeInMs = nextDurationMs,
                            )
                        },
                        onDurationChange = { effectsState = currentEffects.copy(fadeInMs = it) },
                    )

                    Spacer(modifier = Modifier.height(MediaProSpacing.ExtraSmall))
                    FadeTransitionControl(
                        label = stringResource(R.string.editor_effects_fade_out_label),
                        selected = currentEffects.transitionOut,
                        durationMs = currentEffects.fadeOutMs,
                        maxFadeMs = maxFadeMs,
                        onSelectTransition = { chosen, nextDurationMs ->
                            effectsState = currentEffects.copy(
                                transitionOut = chosen,
                                fadeOutMs = nextDurationMs,
                            )
                        },
                        onDurationChange = { effectsState = currentEffects.copy(fadeOutMs = it) },
                    )
                }
            }
        },
        confirmButton = {
            DialogActionButton(
                label = stringResource(R.string.editor_apply),
                onClick = { onConfirm(currentEffects) },
            )
        },
        dismissButton = {
            DialogActionButton(
                label = stringResource(R.string.home_cancel),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun FadeTransitionControl(
    label: String,
    selected: ClipTransition,
    durationMs: Long,
    maxFadeMs: Long,
    onSelectTransition: (ClipTransition, Long) -> Unit,
    onDurationChange: (Long) -> Unit,
) {
    EditorDialogSectionLabel(text = label)
    Row(
        horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ClipTransition.entries.forEach { option ->
            EditorOptionCard(
                isSelected = selected == option,
                onClick = {
                    val nextMs = when {
                        option == ClipTransition.NONE -> 0L
                        durationMs < ClipVisualEffects.MIN_FADE_MS ->
                            minOf(ClipVisualEffects.DEFAULT_FADE_MS, maxFadeMs)
                        else -> durationMs.coerceIn(ClipVisualEffects.MIN_FADE_MS, maxFadeMs)
                    }
                    onSelectTransition(option, nextMs)
                },
                modifier = Modifier.weight(1f),
            ) { contentColor ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(transitionLabelRes(option)),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        modifier = Modifier.padding(
                            horizontal = MediaProSpacing.ExtraSmall,
                            vertical = MediaProSpacing.Small,
                        ),
                    )
                }
            }
        }
    }
    if (selected != ClipTransition.NONE) {
        val minMs = ClipVisualEffects.MIN_FADE_MS
        EditorDialogSliderRow(
            label = stringResource(
                R.string.editor_effects_duration_seconds,
                formatSeconds(durationMs),
            ),
            value = durationMs.coerceIn(minMs, maxFadeMs).toFloat(),
            valueRange = minMs.toFloat()..maxFadeMs.toFloat().coerceAtLeast(minMs.toFloat() + 1f),
            onValueChange = { onDurationChange(it.roundToLong().coerceIn(minMs, maxFadeMs)) },
        )
    }
}

@Composable
private fun FilterTonePreviewCard(
    effects: ClipVisualEffects,
    accessibilityLabel: String,
) {
    val swatches = listOf(
        Color(0xFF0EA5E9),
        Color(0xFFF59E0B),
        Color(0xFFEF4444),
        Color(0xFF10B981),
        Color(0xFF6366F1),
    ).map { applyPreviewColorApproximation(it, effects) }

    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(MediaProShapes.Small)
            .background(Brush.horizontalGradient(swatches))
            .border(
                width = MediaProSpacing.BorderThin,
                color = MaterialTheme.colorScheme.outline,
                shape = MediaProShapes.Small,
            )
            .semantics { contentDescription = accessibilityLabel },
    ) {
        Text(
            text = stringResource(filterPresetLabelRes(effects.filter)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .padding(MediaProSpacing.Small)
                .clip(MediaProShapes.Small)
                .background(Color.Black.copy(alpha = 0.56f))
                .padding(
                    horizontal = MediaProSpacing.Small,
                    vertical = MediaProSpacing.ExtraSmall,
                ),
        )
    }
}

private fun applyPreviewColorApproximation(
    base: Color,
    effects: ClipVisualEffects,
): Color {
    var r = base.red
    var g = base.green
    var b = base.blue
    val spec = effects.filter.gradeSpec()

    if (spec.isGrayscale) {
        val lum = 0.299f * r + 0.587f * g + 0.114f * b
        r = lum
        g = lum
        b = lum
    }
    if (spec.isSepia) {
        val nr = FilterGradeSpec.SEPIA_RR * r + FilterGradeSpec.SEPIA_RG * g + FilterGradeSpec.SEPIA_RB * b
        val ng = FilterGradeSpec.SEPIA_GR * r + FilterGradeSpec.SEPIA_GG * g + FilterGradeSpec.SEPIA_GB * b
        val nb = FilterGradeSpec.SEPIA_BR * r + FilterGradeSpec.SEPIA_BG * g + FilterGradeSpec.SEPIA_BB * b
        r = nr
        g = ng
        b = nb
    }
    if (spec.hasRgbScale) {
        r *= spec.redScale
        g *= spec.greenScale
        b *= spec.blueScale
    }

    val totalSat = (spec.saturationDelta + effects.saturation).coerceIn(-100f, 100f)
    if (totalSat != 0f) {
        val satFactor = (1f + totalSat / 100f).coerceIn(0f, 2f)
        val lum = 0.299f * r + 0.587f * g + 0.114f * b
        r = lum + (r - lum) * satFactor
        g = lum + (g - lum) * satFactor
        b = lum + (b - lum) * satFactor
    }

    if (effects.warmth != 0f) {
        val (redScale, blueScale) = effects.warmthRgbScales()
        r *= redScale
        b *= blueScale
    }

    val totalContrast = (spec.contrastDelta + effects.contrast).coerceIn(-0.8f, 0.8f)
    val totalBrightness = (spec.brightnessDelta + effects.brightness).coerceIn(-0.8f, 0.8f)
    val contrastFactor = (1f + totalContrast).coerceIn(0.4f, 1.8f)
    r = (r - 0.5f) * contrastFactor + 0.5f + totalBrightness
    g = (g - 0.5f) * contrastFactor + 0.5f + totalBrightness
    b = (b - 0.5f) * contrastFactor + 0.5f + totalBrightness

    return Color(
        red = r.coerceIn(0f, 1f),
        green = g.coerceIn(0f, 1f),
        blue = b.coerceIn(0f, 1f),
        alpha = 1f,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorDialogSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatSignedPercent(value: Int): String =
    if (value > 0) "+$value%" else "$value%"

private fun formatSeconds(ms: Long): String =
    String.format(Locale.US, "%.1fs", ms.coerceAtLeast(0L) / 1_000f)

@StringRes
internal fun filterPresetLabelRes(preset: VideoFilterPreset): Int = when (preset) {
    VideoFilterPreset.NONE -> R.string.editor_filter_none
    VideoFilterPreset.VIVID -> R.string.editor_filter_vivid
    VideoFilterPreset.WARM -> R.string.editor_filter_warm
    VideoFilterPreset.COOL -> R.string.editor_filter_cool
    VideoFilterPreset.CINEMA -> R.string.editor_filter_cinema
    VideoFilterPreset.VINTAGE -> R.string.editor_filter_vintage
    VideoFilterPreset.BW -> R.string.editor_filter_bw
    VideoFilterPreset.FADE_FILM -> R.string.editor_filter_fade_film
}

@StringRes
private fun transitionLabelRes(transition: ClipTransition): Int = when (transition) {
    ClipTransition.NONE -> R.string.editor_transition_none
    ClipTransition.FADE_BLACK -> R.string.editor_transition_fade_black
    ClipTransition.FADE_WHITE -> R.string.editor_transition_fade_white
}
