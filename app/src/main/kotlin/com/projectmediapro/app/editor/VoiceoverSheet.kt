package com.projectmediapro.app.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectmediapro.app.R
import com.projectmediapro.app.data.media.ProjectMediaStorage
import com.projectmediapro.app.data.media.RecordedVoiceover
import com.projectmediapro.app.data.media.VoiceoverRecorder
import com.projectmediapro.core.designsystem.theme.MediaProSpacing
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * In-app voiceover recorder sheet. Requests RECORD_AUDIO at runtime with a clear denied
 * fallback, shows live waveform bars + tabular elapsed timer + optional 3-2-1 countdown,
 * and inserts the finished take onto the audio track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceoverSheet(
    projectId: String,
    storage: ProjectMediaStorage,
    playheadMs: Long,
    onInsert: (RecordedVoiceover, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recorder = remember(projectId) { VoiceoverRecorder(context, storage, projectId) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    var countdownEnabled by remember { mutableStateOf(true) }
    var countdown by remember { mutableIntStateOf(0) }
    var recordStartPlayheadMs by remember { mutableLongStateOf(playheadMs) }
    val bars = remember { mutableStateListOf<Float>() }
    val state by recorder.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val rec = state as? VoiceoverRecorder.State.Recording ?: return@LaunchedEffect
        bars.add(rec.amplitudeLevel)
        if (bars.size > 28) bars.removeAt(0)
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000L)
            countdown -= 1
            if (countdown == 0) {
                recordStartPlayheadMs = playheadMs
                recorder.start()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { recorder.cancel(); onDismiss() }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MediaProSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MediaProSpacing.Small),
        ) {
            Text(stringResource(R.string.voiceover_title), style = MaterialTheme.typography.titleLarge)

            when {
                !hasPermission -> {
                    Text(
                        stringResource(
                            if (permissionDenied) R.string.voiceover_denied else R.string.voiceover_permission_rationale,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(stringResource(R.string.voiceover_grant))
                    }
                }
                countdown > 0 -> {
                    Text(
                        countdown.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                else -> {
                    val recording = state as? VoiceoverRecorder.State.Recording
                    Text(
                        formatElapsed(recording?.elapsedMs ?: 0L),
                        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                        color = if (recording != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    WaveformBars(bars)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.voiceover_countdown), modifier = Modifier.weight(1f))
                        Switch(
                            checked = countdownEnabled,
                            onCheckedChange = { countdownEnabled = it },
                            enabled = recording == null,
                        )
                    }
                    val errorState = state as? VoiceoverRecorder.State.Error
                    if (errorState != null) {
                        Text(
                            stringResource(R.string.voiceover_error, errorState.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MediaProSpacing.Small)) {
                        if (recording == null) {
                            Button(
                                onClick = {
                                    bars.clear()
                                    if (countdownEnabled) {
                                        countdown = 3
                                    } else {
                                        recordStartPlayheadMs = playheadMs
                                        recorder.start()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) { Text(stringResource(R.string.voiceover_record)) }
                        } else {
                            Button(
                                onClick = {
                                    val result = recorder.stop()
                                    result.getOrNull()?.let {
                                        onInsert(it, recordStartPlayheadMs)
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) { Text(stringResource(R.string.voiceover_stop)) }
                            OutlinedButton(
                                onClick = {
                                    recorder.cancel()
                                    bars.clear()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp),
                            ) {
                                Text(stringResource(R.string.voiceover_cancel))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(MediaProSpacing.Large))
        }
    }
}

@Composable
private fun WaveformBars(bars: List<Float>) {
    val color = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        val count = 28
        val gap = 4.dp.toPx()
        val barW = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val level = bars.getOrElse(bars.size - count + i) { 0.04f }
            val h = (size.height * (0.08f + 0.92f * level)).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val tenths = (ms % 1_000L) / 100L
    val totalSeconds = ms / 1_000L
    val seconds = totalSeconds % 60L
    val minutes = totalSeconds / 60L
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}
