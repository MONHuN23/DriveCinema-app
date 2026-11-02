package com.drivecinema.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.player.PlaybackTimeUtils
import com.drivecinema.desktop.auth.BrowserLauncher
import com.drivecinema.desktop.player.PlayedTech
import com.drivecinema.desktop.player.PlayerEvent
import com.drivecinema.desktop.player.VlcAvailability
import com.drivecinema.desktop.player.VlcInstaller
import com.drivecinema.desktop.player.VlcPlayer
import com.drivecinema.desktop.player.AudioTrackOption
import com.drivecinema.desktop.player.VlcTracks
import com.drivecinema.app.core.skipintro.IntroTimes
import com.drivecinema.app.core.skipintro.SkipIntroWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.widthIn
import com.drivecinema.app.core.player.TrackLabels
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.desktop.ui.design.DesktopChoiceRow

/** What the player screen needs: the stream URL and where to start, or why it can't play. */
sealed interface PlaybackRequest {
    data object Loading : PlaybackRequest
    data class Ready(val url: String, val startMs: Long) : PlaybackRequest
    data class Unavailable(val message: String) : PlaybackRequest
    /** libVLC isn't installed: offer to install it (audit F-17). */
    data object VlcMissing : PlaybackRequest
    /** Task I.2: the connection to the PC is too slow for this film; the user chooses. */
    data class SlowConnection(val message: String, val hasFallback: Boolean) : PlaybackRequest
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
    onRetry: () -> Unit = {},
    /** Task I.2: answer to [PlaybackRequest.SlowConnection]: true = play from the PC anyway, false = the other copy. */
    onSlowChoice: (playFromPc: Boolean) -> Unit = {},
    /** Task L.3: codecs and resolution of the playing copy, once VLC knows them. */
    onTechInfo: (PlayedTech) -> Unit = {},
    /**
     * Milestone O: finds the intro for this copy once VLC knows its length; null when the setting is off (then
     * nothing is asked). Playback never waits for it.
     */
    findIntro: (suspend (lengthMs: Long) -> IntroTimes?)? = null
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
        is PlaybackRequest.SlowConnection -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Overlay(Modifier) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Slow connection to the PC", color = DesktopColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(request.message, color = DesktopColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (request.hasFallback) ControlButton("Play from Drive", { onSlowChoice(false) }, primary = true)
                        ControlButton("Play from the PC anyway", { onSlowChoice(true) })
                        ControlButton("Back", onBack)
                    }
                }
            }
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
        player.onTechInfo = onTechInfo
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

    var intro by remember { mutableStateOf<IntroTimes?>(null) }
    LaunchedEffect(duration > 0) {
        if (duration > 0 && findIntro != null) intro = runCatching { findIntro(duration) }.getOrNull()
    }
    val skipIntroVisible = intro?.let { SkipIntroWindow.isVisible(it, position) } == true && !ended && error == null
    val skipIntro = { intro?.let { player.seekTo(SkipIntroWindow.seekTarget(it)) } }

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
                    Key.S -> if (skipIntroVisible) { skipIntro(); true } else false
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
                    OutlinedButton(onClick = { player.open(request.url, 0) }) {
                        ButtonLabel(DesktopIcons.Replay, "Watch again", color = DesktopColors.TextPrimary)
                    }
                    if (nextEpisodeTitle != null) {
                        Button(onClick = onNextEpisode, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)) {
                            ButtonLabel(DesktopIcons.SkipNext, "Next episode", bold = true)
                        }
                    } else {
                        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)) { Text("Back") }
                    }
                }
            }
        }

        // Paused (not before the first frame, not at the end): a big play button in the middle, like the TV.
        AnimatedVisibility(
            !isPlaying && !buffering && !ended && error == null && duration > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconActionButton(
                DesktopIcons.PlayArrow,
                tooltipText("Play", "Space"),
                onClick = { poke(); player.togglePause() },
                size = 84.dp,
                iconSize = 48.dp,
                background = Color(0x99000000),
                hoverBackground = DesktopColors.Accent
            )
        }

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(start = 16.dp, end = 24.dp, top = 14.dp, bottom = 32.dp)
            ) {
                IconActionButton(DesktopIcons.ArrowBack, tooltipText("Back", "Esc"), onBack)
                Spacer(Modifier.width(12.dp))
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        AnimatedVisibility(controlsVisible && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 12.dp)
            ) {
                val shownPosition = draggingTo?.let { (it * duration).toLong() } ?: position
                ThinSlider(
                    fraction = draggingTo ?: if (duration > 0) position.toFloat() / duration else 0f,
                    onChange = { draggingTo = it; poke() },
                    onChangeFinished = {
                        draggingTo?.let { player.seekTo((it * duration).toLong()) }
                        draggingTo = null
                    },
                    enabled = duration > 0,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconActionButton(
                        if (isPlaying) DesktopIcons.Pause else DesktopIcons.PlayArrow,
                        tooltipText(if (isPlaying) "Pause" else "Play", "Space"),
                        { player.togglePause() },
                        size = 48.dp,
                        iconSize = 32.dp
                    )
                    val back = tooltipText("Back 10 seconds", "←")
                    IconActionButton(DesktopIcons.Replay, back, { player.seekBy(-SEEK_STEP_MS) }) {
                        SeekIcon(forward = false, seconds = (SEEK_STEP_MS / 1000).toInt(), contentDescription = back)
                    }
                    val forward = tooltipText("Forward 10 seconds", "→")
                    IconActionButton(DesktopIcons.Replay, forward, { player.seekBy(SEEK_STEP_MS) }) {
                        SeekIcon(forward = true, seconds = (SEEK_STEP_MS / 1000).toInt(), contentDescription = forward)
                    }
                    VolumeControl(player)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${PlaybackTimeUtils.formatTime(shownPosition)} / ${PlaybackTimeUtils.formatTime(duration)}",
                        color = Color(0xE6FFFFFF),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (nextEpisodeTitle != null) NextEpisodeButton(nextEpisodeTitle, onNextEpisode)
                    SubtitleMenu(player)
                    AudioTrackMenu(player)
                    IconActionButton(
                        if (isFullscreen) DesktopIcons.FullscreenExit else DesktopIcons.Fullscreen,
                        tooltipText(if (isFullscreen) "Exit full screen" else "Full screen", "F"),
                        onToggleFullscreen
                    )
                }
            }
        }

        // Milestone O: shown during the intro whether the controls are showing or not (above them when they are).
        if (skipIntroVisible) {
            SkipIntroButton(
                onClick = { poke(); skipIntro() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = if (controlsVisible) 108.dp else 40.dp)
            )
        }
    }
}

@Composable
private fun SkipIntroButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    WithTooltip(tooltipText("Skip intro", "S"), modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (hovered) Color.White else Color(0xCC14171F))
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
        ) {
            val content = if (hovered) Color(0xFF111318) else Color.White
            Icon(DesktopIcons.SkipNext, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Skip intro", color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Pill with the skip icon and a label: an icon alone isn't clear enough for this one (L.2). */
@Composable
private fun NextEpisodeButton(nextTitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    WithTooltip(tooltipText("Next: $nextTitle", "N")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (hovered) Color(0x40FFFFFF) else Color(0x26FFFFFF))
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Icon(DesktopIcons.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text("Next episode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VolumeControl(player: VlcPlayer) {
    val volume by player.volume
    val muted by player.isMuted
    val silent = muted || volume == 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconActionButton(
            if (silent) DesktopIcons.VolumeOff else DesktopIcons.VolumeUp,
            tooltipText(if (muted) "Unmute" else "Mute", "M"),
            { player.toggleMute() }
        )
        // VLC allows up to 150 %; the bar covers 0–150 like before.
        ThinSlider(
            fraction = if (muted) 0f else volume / MAX_VOLUME.toFloat(),
            onChange = { player.setVolume((it * MAX_VOLUME).toInt()) },
            color = Color.White,
            modifier = Modifier.width(96.dp)
        )
    }
}

private const val MAX_VOLUME = 150

/** Task K.1: Off + the subtitle tracks VLC found; hidden when there are none. */
@Composable
private fun SubtitleMenu(player: VlcPlayer) {
    val tracks by player.subtitleTracks
    val selected by player.selectedSubtitle
    if (tracks.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconActionButton(DesktopIcons.Subtitles, "Subtitles", { open = true })
        TrackMenu("Subtitles", open, listOf(AudioTrackOption(VlcTracks.OFF, TrackLabels.OFF)) + tracks, selected, onDismiss = { open = false }) {
            player.selectSubtitle(it)
        }
    }
}

@Composable
private fun AudioTrackMenu(player: VlcPlayer) {
    val tracks by player.audioTracks
    val selected by player.selectedAudioTrack
    if (tracks.size < 2) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconActionButton(DesktopIcons.Audiotrack, "Audio track", { open = true })
        TrackMenu("Audio", open, tracks, selected, onDismiss = { open = false }) { player.selectAudioTrack(it) }
    }
}

/**
 * P.8: the track menu above its button: a heading, then one row per track (the language, then the format), the
 * chosen one checked. Up/Down move through the rows, Enter picks one, Esc closes the menu.
 */
@Composable
private fun TrackMenu(title: String, open: Boolean, tracks: List<AudioTrackOption>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        containerColor = DesktopColors.Surface,
        shape = RoundedCornerShape(DcTokens.Radius.CARD.dp),
        modifier = Modifier.widthIn(min = 260.dp, max = 380.dp)
    ) {
        Text(
            title,
            style = DesktopType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = DesktopColors.TextTertiary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp)
        )
        tracks.forEach { track ->
            DesktopChoiceRow(
                title = track.label,
                supporting = track.detail,
                selected = track.id == selected,
                onClick = { onSelect(track.id); onDismiss() },
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

/**
 * Thin bar for seeking and volume: thicker on hover, with a thumb while hovered or dragged. Click or drag anywhere;
 * reports fractions 0–1 through [onChange], then [onChangeFinished] on release.
 */
@Composable
private fun ThinSlider(
    fraction: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    color: Color = DesktopColors.Accent
) {
    // The gesture handlers outlive recompositions; always call the latest callbacks (they capture the duration).
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(1) }
    val active = enabled && (hovered || dragging)
    val barHeight by animateDpAsState(if (active) 6.dp else 4.dp)
    val thumbSize = 14.dp
    val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }
    val current = fraction.coerceIn(0f, 1f)
    fun at(x: Float) = (x / widthPx).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .hoverable(interaction, enabled)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { change(at(it.x)); finished() }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; change(at(it.x)) },
                    onDragEnd = { dragging = false; finished() },
                    onDragCancel = { dragging = false; finished() }
                ) { pointer, _ -> pointer.consume(); change(at(pointer.position.x)) }
            }
    ) {
        Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50)).background(Color(0x4DFFFFFF)))
        Box(Modifier.fillMaxWidth(current).height(barHeight).clip(RoundedCornerShape(50)).background(color))
        if (active) {
            Box(
                Modifier
                    .offset { IntOffset((current * widthPx - thumbPx / 2).roundToInt(), 0) }
                    .size(thumbSize)
                    .background(color, CircleShape)
            )
        }
    }
}

/** Text pill for the dialog-style screens (slow connection). */
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

/** VLC missing: install it in place (apt/dnf after the password dialog, winget after the Windows prompt) or open the download page. */
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
                is VlcInstaller.Plan.Install -> "$VLC_MISSING_TEXT\n\nInstall VLC now? ${plan.confirmNote}"
                is VlcInstaller.Plan.OpenDownloadPage -> "$VLC_MISSING_TEXT\n\nDownload VLC (64-bit) from videolan.org, install it, then restart DriveCinema."
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
                Text("Installing VLC… This can take a few minutes.", color = DesktopColors.TextSecondary)
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
                                            "Installing VLC failed:\n${result.detail}\n\n${plan.manualHint}"
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
