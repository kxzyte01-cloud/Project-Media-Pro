package com.projectmediapro.app.editor

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * One resolved clip for preview playback. A [trimEndMs] of 0 means play the whole file.
 */
data class PreviewClip(
    val path: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
)

/**
 * Plays the project's resolved video clips in sequence using a single [ExoPlayer] instance,
 * fitting each clip inside the canvas on [backgroundColor] without cropping or stretching.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PreviewPlayer(
    previewClips: List<PreviewClip>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundArgb = backgroundColor.toArgb()

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
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
        } else {
            player.setMediaItems(mediaItems)
            player.prepare()
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
