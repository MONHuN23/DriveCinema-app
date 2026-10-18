package com.drivecinema.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun MinimalPlayerView(
    player: Player,
    title: String,
    onBackPressed: () -> Unit,
    nextEpisodeTitle: String? = null,
    onNextEpisodeClick: () -> Unit = {},
    controlsVisible: Boolean? = null,
    onControlsVisibilityChange: ((Boolean) -> Unit)? = null,
    lastInteractionTimeMs: Long = 0L,
    onUserInteraction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var internalControlsVisible by remember { mutableStateOf(true) }
    val effectiveControlsVisible = controlsVisible ?: internalControlsVisible
    val updateControlsVisible: (Boolean) -> Unit = { newVisible ->
        if (onControlsVisibilityChange != null) {
            onControlsVisibilityChange(newVisible)
        } else {
            internalControlsVisible = newVisible
        }
    }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var isBuffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var isEnded by remember { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var bufferedPositionMs by remember { mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0L)) }
    var durationMs by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackInfo>>(emptyList()) }
    var selectedAudioTrackIndex by remember { mutableIntStateOf(0) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var unsupportedAudioMessage by remember { mutableStateOf<String?>(null) }
    var unsupportedVideoMessage by remember { mutableStateOf<String?>(null) }

    // Listen to player state changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = player.duration.coerceAtLeast(0L)
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                isEnded = (playbackState == Player.STATE_ENDED)
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (isEnded) {
                    updateControlsVisible(true)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
                val decoderFailure = VideoTrackHelper.classifyDecoderFailure(error)
                if (decoderFailure == DecoderFailure.VIDEO) {
                    errorMessage = VideoTrackHelper.videoDecoderErrorMessage(error)
                    return
                }
                if (decoderFailure == DecoderFailure.AUDIO) {
                    android.util.Log.w("MinimalPlayerView", "Audio decoder failed; falling back to video-only playback", error)
                    AudioTrackHelper.disableAudio(player)
                    unsupportedAudioMessage = "Audio track is unsupported on this device. Playing video without audio."
                    errorMessage = null
                    player.prepare()
                    player.play()
                    return
                }
                errorMessage = error.localizedMessage ?: "Playback error (${error.errorCodeName})"
            }

            override fun onTracksChanged(tracks: Tracks) {
                unsupportedVideoMessage = VideoTrackHelper.unsupportedVideoMessage(tracks)
                audioTracks = AudioTrackHelper.extractAudioTracks(tracks)
                when (val result = AudioTrackHelper.resolveAudioTracks(player, tracks)) {
                    is AudioSelectionResult.SupportedTrackSelected -> {
                        selectedAudioTrackIndex = result.track.index
                        unsupportedAudioMessage = null
                    }
                    is AudioSelectionResult.NoSupportedTrack -> {
                        val codecName = result.allTracks.firstOrNull()?.codecDisplay ?: "AC-3"
                        unsupportedAudioMessage = "Audio format ($codecName) is unsupported on this device. Playing video without audio."
                    }
                    is AudioSelectionResult.NoAudioTracks -> {
                        unsupportedAudioMessage = null
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Progress updater coroutine
    LaunchedEffect(player, isPlaying) {
        while (true) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide controls timer (4 seconds of inactivity while playing)
    LaunchedEffect(effectiveControlsVisible, isPlaying, isEnded, lastInteractionTimeMs) {
        if (effectiveControlsVisible && isPlaying && !isEnded) {
            delay(4000)
            updateControlsVisible(false)
        }
    }

    // Intercept Back button: if controls are visible, hide them first before exiting
    androidx.activity.compose.BackHandler(enabled = effectiveControlsVisible && !isEnded) {
        updateControlsVisible(false)
    }

    val playPauseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(effectiveControlsVisible) {
        if (effectiveControlsVisible) {
            kotlinx.coroutines.yield()
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                updateControlsVisible(!effectiveControlsVisible)
                onUserInteraction?.invoke()
            }
    ) {
        // Video Surface (Disabled focus so it never steals remote keys from Compose)
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    this.useController = false
                    this.isFocusable = false
                    this.isFocusableInTouchMode = false
                    (this as? android.view.ViewGroup)?.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering && errorMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFE50914),
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // Error message card
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16181D))
                        .border(1.dp, Color(0xFFE50914), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Playback Error",
                        color = Color(0xFFE50914),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFF0F2F5),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A2D35))
                            .clickable(onClick = onBackPressed)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Back",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = effectiveControlsVisible || isEnded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlaybackOverlay(
                title = title,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                audioTracks = audioTracks,
                nextEpisodeTitle = nextEpisodeTitle,
                isEnded = isEnded,
                playPauseFocusRequester = playPauseFocusRequester,
                onPlayPauseClick = {
                    android.util.Log.i("MinimalPlayerView", "onPlayPauseClick invoked! isPlaying=${player.isPlaying}")
                    onUserInteraction?.invoke()
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeekRelative = { deltaMs ->
                    onUserInteraction?.invoke()
                    val newPos = (player.currentPosition + deltaMs).coerceIn(0L, durationMs)
                    player.seekTo(newPos)
                },
                onSeekTo = { targetMs ->
                    onUserInteraction?.invoke()
                    player.seekTo(targetMs.coerceIn(0L, durationMs))
                },
                onAudioTrackClick = {
                    onUserInteraction?.invoke()
                    showAudioTrackDialog = true
                },
                onNextEpisodeClick = {
                    onUserInteraction?.invoke()
                    onNextEpisodeClick()
                },
                onBackPressed = onBackPressed
            )
        }

        // Unsupported Video / Audio Notification Banners
        if (errorMessage == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                unsupportedVideoMessage?.let { message ->
                    UnsupportedFormatBanner(message = message, onDismiss = { unsupportedVideoMessage = null })
                }
                if (unsupportedVideoMessage != null && unsupportedAudioMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                unsupportedAudioMessage?.let { message ->
                    UnsupportedFormatBanner(message = message, onDismiss = { unsupportedAudioMessage = null })
                }
            }
        }

        if (showAudioTrackDialog) {
            AudioTrackSelectorDialog(
                tracks = audioTracks,
                selectedTrackIndex = selectedAudioTrackIndex,
                onTrackSelected = { track ->
                    selectedAudioTrackIndex = track.index
                    AudioTrackHelper.selectAudioTrack(player, track)
                    unsupportedAudioMessage = null
                },
                onDismissRequest = {
                    showAudioTrackDialog = false
                }
            )
        }
    }
}

/** Amber, dismissable warning about a format this device cannot play (shown above the controls). */
@Composable
fun UnsupportedFormatBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xEB1C2028))
            .border(1.dp, Color(0xFFD97706), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚠️  $message",
            color = Color(0xFFFCD34D),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x33FFFFFF))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Dismiss",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
