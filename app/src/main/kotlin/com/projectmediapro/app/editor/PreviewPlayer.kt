package com.projectmediapro.app.editor

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.delay

private const val POSITION_POLL_INTERVAL_MS = 100L

/**
 * One resolved clip for preview playback. A [trimEndMs] of 0 means play the whole file.
 */
data class PreviewClip(
    val path: String,
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
)

/**
 * Plays the project's resolved video clips in sequence using a single [ExoPlayer] instance,
 * fitting each clip inside the canvas on [backgroundColor] without cropping or stretching,
 * and reporting the current timeline position for timed overlays.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PreviewPlayer(
    previewClips: List<PreviewClip>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    onTimelinePositionChanged: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundArgb = backgroundColor.toArgb()
    val latestOnPosition by rememberUpdatedState(onTimelinePositionChanged)

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }

    val activeClipDurations = remember(previewClips) {
        previewClips.mapNotNull { clip ->
            if (File(clip.path).isFile) clip.durationMs.coerceAtLeast(0L) else null
        }
    }
    var isPlaying by remember { mutableStateOf(false) }

    fun reportPosition() {
        val timelineMs = TimelineClock.playerToTimelineMs(
            clipDurationsMs = activeClipDurations,
            mediaItemIndex = player.currentMediaItemIndex,
            positionInItemMs = player.currentPosition,
        )
        latestOnPosition(timelineMs)
    }

    LaunchedEffect(previewClips) {
        val mediaItems = previewClips.mapNotNull { clip ->
            val file = File(clip.path)
            if (!file.isFile) return@mapNotNull null
            val uri = Uri.fromFile(file)
            if (clip.trimEndMs > 0L && clip.trimEndMs > clip.trimStartMs) {
                MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                            .setEndPositionMs(clip.trimEndMs)
                            .build(),
                    )
                    .build()
            } else {
                MediaItem.fromUri(uri)
            }
        }
        player.playWhenReady = false
        if (mediaItems.isEmpty()) {
            player.clearMediaItems()
            latestOnPosition(0L)
        } else {
            player.setMediaItems(mediaItems)
            player.prepare()
            reportPosition()
        }
    }

    DisposableEffect(player, activeClipDurations) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                reportPosition()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                reportPosition()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                reportPosition()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                reportPosition()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, isPlaying, activeClipDurations) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            reportPosition()
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { playerView ->
            if (playerView.player !== player) {
                playerView.player = player
            }
            playerView.setBackgroundColor(backgroundArgb)
            playerView.setShutterBackgroundColor(backgroundArgb)
        },
        modifier = modifier,
    )
}
