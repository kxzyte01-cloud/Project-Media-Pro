package com.projectmediapro.app.editor

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.projectmediapro.app.R
import com.projectmediapro.core.model.Clip
import com.projectmediapro.core.model.ClipCrop
import com.projectmediapro.core.model.ProjectTimeline
import java.io.File
import kotlin.math.roundToLong
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
    val volume: Float = 1.0f,
    val playbackSpeed: Float = Clip.DEFAULT_PLAYBACK_SPEED,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: ClipCrop? = null,
    val isStillImage: Boolean = false,
)

data class PreviewSeekRequest(
    val timelinePositionMs: Long,
    val requestId: Long,
)

/**
 * Plays the project's resolved video clips in sequence using [ExoPlayer], applying per-clip
 * playback speed, volume/mute, and live TextureView rotation/flip while synchronizing an optional
 * companion audio-track [ExoPlayer] for play/pause, seek, speed, and volume.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PreviewPlayer(
    previewClips: List<PreviewClip>,
    modifier: Modifier = Modifier,
    audioClips: List<PreviewClip> = emptyList(),
    backgroundColor: Color = Color.Black,
    seekRequest: PreviewSeekRequest? = null,
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
    val audioPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }

    val activeVideoClips = remember(previewClips) {
        previewClips.filter { File(it.path).isFile }
    }
    val activeClipDurations = remember(activeVideoClips) {
        activeVideoClips.map { it.durationMs.coerceAtLeast(0L) }
    }
    val activeAudioClips = remember(audioClips) {
        audioClips.filter { File(it.path).isFile }
    }
    val activeAudioDurations = remember(activeAudioClips) {
        activeAudioClips.map { it.durationMs.coerceAtLeast(0L) }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var currentVideoIndex by remember(activeVideoClips) { mutableIntStateOf(0) }

    fun applyActiveVideoParams(index: Int) {
        val clip = activeVideoClips.getOrNull(index) ?: return
        val targetVolume = if (clip.isStillImage) 0f else ProjectTimeline.sanitizeVolume(clip.volume)
        if (player.volume != targetVolume) {
            player.volume = targetVolume
        }
        val targetSpeed = if (clip.isStillImage) {
            Clip.DEFAULT_PLAYBACK_SPEED
        } else {
            ProjectTimeline.sanitizeSpeed(clip.playbackSpeed)
        }
        if (player.playbackParameters.speed != targetSpeed) {
            player.setPlaybackSpeed(targetSpeed)
        }
    }

    fun currentTimelinePositionMs(): Long {
        val index = player.currentMediaItemIndex
        val activeSpeed = activeVideoClips.getOrNull(index)
            ?.let { ProjectTimeline.sanitizeSpeed(it.playbackSpeed) }
            ?: Clip.DEFAULT_PLAYBACK_SPEED
        val positionInTimelineMs = (player.currentPosition.coerceAtLeast(0L) / activeSpeed).roundToLong()
        return TimelineClock.playerToTimelineMs(
            clipDurationsMs = activeClipDurations,
            mediaItemIndex = index,
            positionInItemMs = positionInTimelineMs,
        )
    }

    fun syncAudioToTimeline(timelineMs: Long, shouldPlay: Boolean) {
        if (activeAudioDurations.isEmpty() || audioPlayer.mediaItemCount == 0) {
            audioPlayer.playWhenReady = false
            return
        }
        val totalAudioMs = activeAudioDurations.sum()
        if (timelineMs >= totalAudioMs) {
            audioPlayer.playWhenReady = false
            return
        }
        val mapped = TimelineClock.timelineToPlayerPosition(activeAudioDurations, timelineMs)
        if (mapped != null && mapped.first in 0 until audioPlayer.mediaItemCount) {
            val clip = activeAudioClips.getOrNull(mapped.first)
            val clipVol = clip?.let { ProjectTimeline.sanitizeVolume(it.volume) } ?: 1f
            val clipSpeed = clip?.let { ProjectTimeline.sanitizeSpeed(it.playbackSpeed) }
                ?: Clip.DEFAULT_PLAYBACK_SPEED
            audioPlayer.volume = clipVol
            if (audioPlayer.playbackParameters.speed != clipSpeed) {
                audioPlayer.setPlaybackSpeed(clipSpeed)
            }
            val sourceSeekMs = (mapped.second * clipSpeed).roundToLong().coerceAtLeast(0L)
            audioPlayer.seekTo(mapped.first, sourceSeekMs)
            audioPlayer.playWhenReady = shouldPlay
        } else {
            audioPlayer.playWhenReady = false
        }
    }

    fun reportPosition(syncAudioSeek: Boolean = false) {
        val index = player.currentMediaItemIndex
        if (index in activeVideoClips.indices && currentVideoIndex != index) {
            currentVideoIndex = index
        }
        applyActiveVideoParams(index)
        val timelineMs = currentTimelinePositionMs()
        latestOnPosition(timelineMs)
        if (syncAudioSeek) {
            syncAudioToTimeline(timelineMs, player.isPlaying)
        } else if (activeAudioClips.isNotEmpty() && audioPlayer.mediaItemCount > 0) {
            val audioIndex = audioPlayer.currentMediaItemIndex
            val clip = activeAudioClips.getOrNull(audioIndex)
            val clipVol = clip?.let { ProjectTimeline.sanitizeVolume(it.volume) } ?: 1f
            val clipSpeed = clip?.let { ProjectTimeline.sanitizeSpeed(it.playbackSpeed) }
                ?: Clip.DEFAULT_PLAYBACK_SPEED
            if (audioPlayer.volume != clipVol) {
                audioPlayer.volume = clipVol
            }
            if (audioPlayer.playbackParameters.speed != clipSpeed) {
                audioPlayer.setPlaybackSpeed(clipSpeed)
            }
        }
    }

    LaunchedEffect(activeVideoClips) {
        val mediaItems = buildPreviewMediaItems(activeVideoClips)
        player.playWhenReady = false
        audioPlayer.playWhenReady = false
        currentVideoIndex = 0
        if (mediaItems.isEmpty()) {
            player.clearMediaItems()
            latestOnPosition(0L)
        } else {
            player.setMediaItems(mediaItems)
            applyActiveVideoParams(0)
            player.prepare()
            reportPosition(syncAudioSeek = true)
        }
    }

    LaunchedEffect(activeAudioClips) {
        val mediaItems = buildPreviewMediaItems(activeAudioClips)
        audioPlayer.playWhenReady = false
        if (mediaItems.isEmpty()) {
            audioPlayer.clearMediaItems()
        } else {
            audioPlayer.setMediaItems(mediaItems)
            audioPlayer.prepare()
            reportPosition(syncAudioSeek = true)
        }
    }

    LaunchedEffect(seekRequest?.requestId) {
        val request = seekRequest ?: return@LaunchedEffect
        val mapped = TimelineClock.timelineToPlayerPosition(
            clipDurationsMs = activeClipDurations,
            timelinePositionMs = request.timelinePositionMs,
        ) ?: return@LaunchedEffect
        if (mapped.first in 0 until player.mediaItemCount) {
            currentVideoIndex = mapped.first
            applyActiveVideoParams(mapped.first)
            val targetSpeed = activeVideoClips.getOrNull(mapped.first)
                ?.let { ProjectTimeline.sanitizeSpeed(it.playbackSpeed) }
                ?: Clip.DEFAULT_PLAYBACK_SPEED
            val sourcePosMs = (mapped.second * targetSpeed).roundToLong().coerceAtLeast(0L)
            player.seekTo(mapped.first, sourcePosMs)
            val clampedMs = request.timelinePositionMs.coerceAtLeast(0L)
            latestOnPosition(clampedMs)
            syncAudioToTimeline(clampedMs, player.isPlaying)
        }
    }

    DisposableEffect(player, audioPlayer, activeVideoClips, activeAudioClips) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                applyActiveVideoParams(player.currentMediaItemIndex)
                val timelineMs = currentTimelinePositionMs()
                latestOnPosition(timelineMs)
                if (playing) {
                    syncAudioToTimeline(timelineMs, shouldPlay = true)
                } else {
                    audioPlayer.pause()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                reportPosition(syncAudioSeek = true)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                reportPosition(syncAudioSeek = true)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    audioPlayer.pause()
                }
                reportPosition()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, isPlaying, activeVideoClips) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            reportPosition()
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }

    DisposableEffect(lifecycleOwner, player, audioPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                audioPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player, audioPlayer) {
        onDispose {
            audioPlayer.release()
            player.release()
        }
    }

    val activeClip = activeVideoClips.getOrNull(currentVideoIndex)
    val activeRotation = activeClip?.rotationDegrees ?: 0
    val activeFlipH = activeClip?.flipHorizontal == true
    val activeFlipV = activeClip?.flipVertical == true
    val activeCrop = activeClip?.crop?.sanitized()?.takeUnless { it.isIdentity }

    AndroidView(
        factory = { viewContext ->
            val inflated = LayoutInflater.from(viewContext)
                .inflate(R.layout.view_preview_player, null, false) as PlayerView
            inflated.apply {
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
            // Apply crop zoom/pan and rotate/flip directly on the TextureView surface so playback controls stay upright.
            playerView.videoSurfaceView?.let { surface ->
                applySurfaceTransform(surface, activeRotation, activeFlipH, activeFlipV, activeCrop)
                if (activeCrop != null && (surface.width == 0 || surface.height == 0)) {
                    surface.post {
                        applySurfaceTransform(surface, activeRotation, activeFlipH, activeFlipV, activeCrop)
                    }
                }
            }
        },
        modifier = modifier,
    )
}

private fun buildPreviewMediaItems(clips: List<PreviewClip>): List<MediaItem> =
    clips.mapNotNull { clip ->
        val file = File(clip.path)
        if (!file.isFile) return@mapNotNull null
        val uri = Uri.fromFile(file)
        if (clip.isStillImage) {
            MediaItem.Builder()
                .setUri(uri)
                .setImageDurationMs(clip.durationMs.coerceAtLeast(1L))
                .build()
        } else if (clip.trimEndMs > 0L && clip.trimEndMs > clip.trimStartMs) {
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

private fun applySurfaceTransform(
    surface: View,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    crop: ClipCrop?,
) {
    val cropZoomX = if (crop != null && crop.widthFraction > 0f) 1f / crop.widthFraction else 1f
    val cropZoomY = if (crop != null && crop.heightFraction > 0f) 1f / crop.heightFraction else 1f
    val flipSignX = if (flipHorizontal) -1f else 1f
    val flipSignY = if (flipVertical) -1f else 1f
    surface.rotation = rotationDegrees.toFloat()
    surface.scaleX = flipSignX * cropZoomX
    surface.scaleY = flipSignY * cropZoomY
    surface.translationX = if (crop != null && surface.width > 0) {
        (0.5f - crop.centerX) * surface.width * cropZoomX * flipSignX
    } else {
        0f
    }
    surface.translationY = if (crop != null && surface.height > 0) {
        (0.5f - crop.centerY) * surface.height * cropZoomY * flipSignY
    } else {
        0f
    }
}
