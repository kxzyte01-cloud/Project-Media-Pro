package com.projectmediapro.app.editor

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ProjectTimeline
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AudioPreviewPlayer(
    audioPreview: AudioPreviewState?,
    onPlaybackEnded: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEnded by rememberUpdatedState(onPlaybackEnded)

    val liveAudioProcessor = remember(context) { LiveAudioEnhanceProcessor() }
    val player = remember(context, liveAudioProcessor) {
        ExoPlayer.Builder(context, buildLiveAudioRenderersFactory(context, liveAudioProcessor))
            .build()
            .apply {
                playWhenReady = false
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentOnEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                currentOnEnded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(audioPreview) {
        if (audioPreview == null) {
            liveAudioProcessor.update(Clip.DEFAULT_AUDIO_BOOST, null)
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
        val file = File(audioPreview.path)
        if (!file.isFile) {
            player.playWhenReady = false
            player.clearMediaItems()
            currentOnEnded()
            return@LaunchedEffect
        }
        val uri = Uri.fromFile(file)
        val mediaItem = if (audioPreview.trimEndMs > audioPreview.trimStartMs) {
            MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audioPreview.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(audioPreview.trimEndMs)
                        .build(),
                )
                .build()
        } else {
            MediaItem.fromUri(uri)
        }
        liveAudioProcessor.update(audioPreview.audioBoost, audioPreview.audioEnhance)
        player.volume = ProjectTimeline.sanitizeVolume(audioPreview.volume)
        player.setPlaybackSpeed(ProjectTimeline.sanitizeSpeed(audioPreview.playbackSpeed))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }
}
