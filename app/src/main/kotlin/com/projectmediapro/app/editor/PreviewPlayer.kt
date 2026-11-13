package com.projectmediapro.app.editor

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Plays the project's resolved video files in sequence using a single [ExoPlayer] instance.
 */
@Composable
fun PreviewPlayer(
    previewPaths: List<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }

    LaunchedEffect(previewPaths) {
        val existingFiles = previewPaths
            .map { File(it) }
            .filter { it.isFile }
        val mediaItems = existingFiles.map { file ->
            MediaItem.fromUri(Uri.fromFile(file))
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
                this.player = player
            }
        },
        update = { playerView ->
            if (playerView.player !== player) {
                playerView.player = player
            }
        },
        modifier = modifier,
    )
}
