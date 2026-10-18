package com.drivecinema.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.player.PlaybackTimeUtils
import com.drivecinema.desktop.auth.BrowserLauncher
import com.drivecinema.desktop.player.PlayerEvent
import com.drivecinema.desktop.player.VlcAvailability
import com.drivecinema.desktop.player.VlcInstaller
import com.drivecinema.desktop.player.VlcPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/** What the player screen needs: the stream URL and where to start, or why it can't play. */
sealed interface PlaybackRequest {
    data object Loading : PlaybackRequest
    data class Ready(val url: String, val startMs: Long) : PlaybackRequest
    data class Unavailable(val message: String) : PlaybackRequest
    /** libVLC isn't installed: offer to install it (audit F-17). */
    data object VlcMissing : PlaybackRequest
}

private const val CONTROLS_HIDE_DELAY_MS = 3_000L
private const val SEEK_STEP_MS = 10_000L

private val HiddenCursor: PointerIcon by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "hidden")
    )
}

/**
 * Full-window player. [onProgress] is called every few seconds while playing and on pause/finish/close so the
 * caller can save watch progress; [onFinished] when the end is reached.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    title: String,
    request: PlaybackRequest,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onFinished: (durationMs: Long) -> Unit,
    onBack: () -> Unit,
    nextEpisodeTitle: String? = null,
    onNextEpisode: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    when (request) {
        PlaybackRequest.Loading -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesktopColors.Accent)
            }
            return
        }
        is PlaybackRequest.Unavailable -> {
            PlayerMessage(title, request.message, onBack)
            return
        }
        PlaybackRequest.VlcMissing -> {
            VlcMissingMessage(title, onRetry, onBack)
            return
        }
        is PlaybackRequest.Ready -> Unit
    }

    val player = remember { VlcPlayer() }
    val focus = remember { FocusRequester() }
    var lastActivity by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var draggingTo by remember { mutableStateOf<Float?>(null) }

    val isPlaying by player.isPlaying
    val position by player.positionMs
    val duration by player.durationMs
    val ended by player.hasEnded
    val error by player.error
    val buffering by player.isBuffering
    val frame by player.frame

    DisposableEffect(player) {
        player.onEvent = { event ->
            when (event) {
                PlayerEvent.PAUSED -> onProgress(player.positionMs.longValue, player.durationMs.longValue)
                PlayerEvent.FINISHED -> onFinished(player.durationMs.longValue)
            }
        }
        player.open(request.url, request.startMs)
        onDispose {
            if (!player.hasEnded.value) onProgress(player.positionMs.longValue, player.durationMs.longValue)
            // Stopping libVLC can block for a moment; never on the UI thread.
            Thread({ player.close() }, "vlc-release").apply { isDaemon = true }.start()
        }
    }

    // A resume point past the end of this copy starts over (same rule as Android).
    LaunchedEffect(duration > 0) {
        if (duration > 0 && request.startMs > 0 && PlaybackTimeUtils.shouldRestartFromBeginning(request.startMs, duration)) {
            player.seekTo(0)
        }
    }

    // Save progress regularly while playing.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(5_000)
            onProgress(player.positionMs.longValue, player.durationMs.longValue)
        }
    }

    // Clock for hiding the controls and the mouse pointer.
    LaunchedEffect(Unit) {
        focus.requestFocus()
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }

    val controlsVisible = !isPlaying || ended || error != null || draggingTo != null ||
        now - lastActivity < CONTROLS_HIDE_DELAY_MS
    val poke = { lastActivity = System.currentTimeMillis(); now = lastActivity }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPointerEvent(PointerEventType.Move) { poke() }
            .pointerHoverIcon(if (controlsVisible) PointerIcon.Default else HiddenCursor)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke()
                when (event.key) {
                    Key.Spacebar, Key.K -> { player.togglePause(); true }
                    Key.DirectionLeft, Key.J -> { player.seekBy(-SEEK_STEP_MS); true }
                    Key.DirectionRight, Key.L -> { player.seekBy(SEEK_STEP_MS); true }
                    Key.DirectionUp -> { player.setVolume(player.volume.intValue + 5); true }
                    Key.DirectionDown -> { player.setVolume(player.volume.intValue - 5); true }
                    Key.M -> { player.toggleMute(); true }
                    Key.F -> { onToggleFullscreen(); true }
                    Key.N -> if (nextEpisodeTitle != null) { onNextEpisode(); true } else false
                    else -> false
                }
            }
    ) {
        frame?.let { image ->
            Image(
                bitmap = image,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        poke(); player.togglePause()
                    }
            )
        }

        if (buffering && error == null && !ended) {
            CircularProgressIndicator(color = DesktopColors.Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }

        error?.let { message ->
            Overlay(Modifier.align(Alignment.Center)) {
                Text("Playback error", color = DesktopColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(message, color = DesktopColors.TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onBack) { Text("Back", color = DesktopColors.TextPrimary) }
            }
        }

        if (ended) {
            Overlay(Modifier.align(Alignment.Center)) {
                Text("Finished", color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { player.open(request.url, 0) }) { Text("Watch again", color = DesktopColors.TextPrimary) }
                    if (nextEpisodeTitle != null) {
                        Button(onClick = onNextEpisode, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)) { Text("Next episode  ⏭") }
                    } else {
                        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)) { Text("Back") }
                    }
                }
            }
        }

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                ControlButton("←  Back", onBack)
                Spacer(Modifier.width(16.dp))
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        AnimatedVisibility(controlsVisible && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                val shownPosition = draggingTo?.let { (it * duration).toLong() } ?: position
                Slider(
                    value = draggingTo ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { draggingTo = it; poke() },
                    onValueChangeFinished = {
                        draggingTo?.let { player.seekTo((it * duration).toLong()) }
                        draggingTo = null
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = DesktopColors.Accent,
                        activeTrackColor = DesktopColors.Accent,
                        inactiveTrackColor = Color(0x55FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlButton("⏪ 10", { player.seekBy(-SEEK_STEP_MS) })
                    ControlButton(if (isPlaying) "⏸  Pause" else "▶  Play", { player.togglePause() }, primary = true)
                    ControlButton("10 ⏩", { player.seekBy(SEEK_STEP_MS) })
                    if (nextEpisodeTitle != null) ControlButton("Next episode  ⏭", onNextEpisode)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${PlaybackTimeUtils.formatTime(shownPosition)} / ${PlaybackTimeUtils.formatTime(duration)}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.weight(1f))
                    VolumeControl(player)
                    AudioTrackMenu(player)
                    ControlButton(if (isFullscreen) "⤡  Exit full screen" else "⤢  Full screen", onToggleFullscreen)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Space pause · ←/→ 10 s · ↑/↓ volume · M mute · F full screen" +
                        (if (nextEpisodeTitle != null) " · N next episode" else "") + " · Esc back",
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun VolumeControl(player: VlcPlayer) {
    val volume by player.volume
    val muted by player.isMuted
    Row(verticalAlignment = Alignment.CenterVertically) {
        ControlButton(if (muted || volume == 0) "🔇" else "🔊", { player.toggleMute() })
        Slider(
            value = if (muted) 0f else volume / 100f,
            onValueChange = { player.setVolume((it * 100).toInt()) },
            valueRange = 0f..1.5f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x55FFFFFF)),
            modifier = Modifier.width(110.dp)
        )
    }
}

@Composable
private fun AudioTrackMenu(player: VlcPlayer) {
    val tracks by player.audioTracks
    val selected by player.selectedAudioTrack
    if (tracks.size < 2) return
    var open by remember { mutableStateOf(false) }
    Box {
        ControlButton("🎧  Audio", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = DesktopColors.Surface) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = {
                        Text(
                            (if (track.id == selected) "✓  " else "     ") + track.label,
                            color = DesktopColors.TextPrimary
                        )
                    },
                    onClick = { player.selectAudioTrack(track.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun ControlButton(label: String, onClick: () -> Unit, primary: Boolean = false) {
    Text(
        label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(if (primary) DesktopColors.Accent else Color(0x33FFFFFF), RoundedCornerShape(50))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun Overlay(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(460.dp)
            .background(Color(0xEE16181D), RoundedCornerShape(12.dp))
            .padding(24.dp)
    ) { content() }
}

@Composable
private fun PlayerMessage(title: String, message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Overlay(Modifier) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(message, color = DesktopColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text("Back", color = DesktopColors.TextPrimary) }
        }
    }
}

private const val VLC_MISSING_TEXT = "VLC is not installed, so DriveCinema can't play videos yet."

/** VLC missing: install it in place (apt, after the system password dialog) or open the download page. */
@Composable
private fun VlcMissingMessage(title: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val plan = remember { VlcInstaller.plan() }
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Overlay(Modifier) {
            Text(title, color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            val prompt = when (plan) {
                is VlcInstaller.Plan.Install -> "$VLC_MISSING_TEXT\n\nInstall VLC now? Your computer will ask for your password."
                is VlcInstaller.Plan.OpenDownloadPage -> "$VLC_MISSING_TEXT\n\nDownload VLC from videolan.org, install it, then restart DriveCinema."
            }
            Text(prompt, color = DesktopColors.TextSecondary, textAlign = TextAlign.Center)
            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = DesktopColors.TextPrimary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            if (installing) {
                CircularProgressIndicator(color = DesktopColors.Accent)
                Spacer(Modifier.height(8.dp))
                Text("Installing VLC…", color = DesktopColors.TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Back", color = DesktopColors.TextPrimary) }
                    when (plan) {
                        is VlcInstaller.Plan.Install -> Button(
                            onClick = {
                                installing = true
                                status = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { VlcInstaller.install(plan.command) }
                                    val found = result == VlcInstaller.Result.Installed && withContext(Dispatchers.IO) { VlcAvailability.recheck() }
                                    installing = false
                                    status = when (result) {
                                        VlcInstaller.Result.Installed ->
                                            if (found) null else "VLC is installed. Restart DriveCinema to start playing."
                                        VlcInstaller.Result.Cancelled -> "Installation cancelled. Nothing was changed."
                                        is VlcInstaller.Result.Failed ->
                                            "Installing VLC failed:\n${result.detail}\n\nYou can also run in a terminal: sudo apt install vlc"
                                    }
                                    if (found) onRetry()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)
                        ) { Text("Install VLC") }
                        is VlcInstaller.Plan.OpenDownloadPage -> Button(
                            onClick = {
                                if (!BrowserLauncher.open(plan.url)) status = "Open ${plan.url} in your browser."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)
                        ) { Text("Open download page") }
                    }
                }
            }
        }
    }
}
