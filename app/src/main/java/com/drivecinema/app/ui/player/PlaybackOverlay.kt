package com.drivecinema.app.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.icons.AppIcons

// Task L.5: filled, borderless controls (no outlines). On TV the focused control turns white with dark content and
// grows a little, the usual Google TV focus style; on the phone controls look the same, just without focus.
private val Accent = Color(0xFFE50914)
private val ControlIdle = Color(0x59000000)
private val PillIdle = Color(0x4D000000)
private val OnFocused = Color(0xFF111318)
private val TextIdle = Color(0xFFF0F2F5)

// The bottom row under the timeline (Previous, Play/Pause, times). Skip intro sits level with the Next episode pill,
// just above the timeline: the overlay's bottom padding, this row, the timeline (36 dp) and the spacers between them.
private val ControlsRowHeight = 44.dp
private val AboveTimelineBottom = 20.dp + ControlsRowHeight + 4.dp + 36.dp + 12.dp

@Composable
fun PlaybackOverlay(
    title: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    audioTracks: List<AudioTrackInfo>,
    nextEpisodeTitle: String? = null,
    isEnded: Boolean = false,
    playPauseFocusRequester: FocusRequester? = null,
    onPlayPauseClick: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onAudioTrackClick: () -> Unit,
    onNextEpisodeClick: () -> Unit = {},
    /** Task K.1: subtitle tracks inside the file; the button shows only when there are any. */
    subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    onSubtitleClick: () -> Unit = {},
    /** Task P.8: focus goes back to the Subtitles or Audio button when its track panel closes (TV). */
    subtitleFocusRequester: FocusRequester? = null,
    audioFocusRequester: FocusRequester? = null,
    /** The bottom row's Previous episode button; shown only when there is a previous episode. */
    hasPreviousEpisode: Boolean = false,
    onPreviousEpisodeClick: () -> Unit = {},
    /** Details (top right and on the end-of-video card): the film's page, or the series' page; null hides it. */
    onDetailsClick: (() -> Unit)? = null,
    /** The Skip intro button lives outside the overlay; the TV focus order steps to it from the centre row and the timeline. */
    skipIntroVisible: Boolean = false,
    skipIntroFocusRequester: FocusRequester? = null,
    timelineFocusRequester: FocusRequester? = null,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    // TV focus order (design: Variant B). Up/Down move one level: top bar, centre row, Skip intro / Next episode,
    // timeline, bottom row. A target is used only while it is on screen, or focus would reach nothing (F-32).
    val ownCentreFocus = remember { FocusRequester() }
    val centreFocus = playPauseFocusRequester ?: ownCentreFocus
    val ownTimelineFocus = remember { FocusRequester() }
    val timelineFocus = timelineFocusRequester ?: ownTimelineFocus
    val bottomPlayFocus = remember { FocusRequester() }
    val previousFocus = remember { FocusRequester() }
    val nextPillFocus = remember { FocusRequester() }
    val showNextPill = !isEnded && !nextEpisodeTitle.isNullOrEmpty()
    val aboveTimeline = when {
        showNextPill -> nextPillFocus
        skipIntroVisible && skipIntroFocusRequester != null -> skipIntroFocusRequester
        else -> null
    }
    val topBarFocus = Modifier.focusProperties { if (!isEnded) down = centreFocus }
    val centreRowFocus = Modifier.focusProperties { down = aboveTimeline ?: timelineFocus }

    // A Previous or Next button that disappears while focused hands the focus on (the F-32 lesson). Compose reports
    // the button as unfocused while removing it, before the effect runs, so that last report is ignored.
    val previousShown by rememberUpdatedState(hasPreviousEpisode)
    val nextPillShown by rememberUpdatedState(showNextPill)
    var previousFocused by remember { mutableStateOf(false) }
    LaunchedEffect(hasPreviousEpisode) {
        if (!hasPreviousEpisode && previousFocused) {
            previousFocused = false
            kotlinx.coroutines.yield()
            runCatching { if (isEnded) timelineFocus.requestFocus() else bottomPlayFocus.requestFocus() }
        }
    }
    var nextPillFocused by remember { mutableStateOf(false) }
    LaunchedEffect(showNextPill) {
        if (!showNextPill && nextPillFocused) {
            nextPillFocused = false
            kotlinx.coroutines.yield()
            if (!isEnded) runCatching { centreFocus.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("playback_overlay_container")
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xCC0A0B0E),
                        Color(0x14000000),
                        Color(0x14000000),
                        Color(0xE60A0B0E)
                    )
                )
            )
    ) {
        // Top: back + title, subtitle and audio buttons on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                PlayerControl(
                    onClick = onBackPressed,
                    idleBackground = ControlIdle,
                    modifier = topBarFocus.size(48.dp).testTag("player_back_button")
                ) { color ->
                    Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = color, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    color = TextIdle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("player_title")
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (subtitleTracks.isNotEmpty()) {
                    PillControl(
                        icon = AppIcons.Subtitles,
                        text = "Subtitles",
                        onClick = onSubtitleClick,
                        focusRequester = subtitleFocusRequester,
                        modifier = topBarFocus.testTag("player_subtitle_button")
                    )
                }
                if (AudioTrackHelper.shouldShowSelector(audioTracks)) {
                    PillControl(
                        icon = AppIcons.Audiotrack,
                        text = "Audio",
                        onClick = onAudioTrackClick,
                        focusRequester = audioFocusRequester,
                        modifier = topBarFocus.testTag("player_audio_button")
                    )
                }
                if (onDetailsClick != null) {
                    PillControl(
                        icon = AppIcons.Info,
                        text = "Details",
                        onClick = onDetailsClick,
                        modifier = topBarFocus.testTag("player_details_button")
                    )
                }
            }
        }

        if (!isEnded) {
            // Centre: back 10 s, play/pause, forward 10 s.
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerControl(
                    onClick = { onSeekRelative(-10000L) },
                    idleBackground = ControlIdle,
                    modifier = centreRowFocus.size(64.dp).testTag("player_rewind_button")
                ) { color -> SeekIcon(forward = false, color = color) }

                PlayerControl(
                    onClick = onPlayPauseClick,
                    idleBackground = ControlIdle,
                    focusRequester = centreFocus,
                    modifier = centreRowFocus.size(84.dp).testTag("player_play_pause_button")
                ) { color ->
                    Icon(
                        if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = color,
                        modifier = Modifier.size(48.dp)
                    )
                }

                PlayerControl(
                    onClick = { onSeekRelative(10000L) },
                    idleBackground = ControlIdle,
                    modifier = centreRowFocus.size(64.dp).testTag("player_forward_button")
                ) { color -> SeekIcon(forward = true, color = color) }
            }
        } else {
            // End of the video: a manual choice (no countdown, no autoplay). The play/pause button that had the
            // remote's focus is gone now, so the card takes it, or OK would reach nothing on the TV.
            val endChoiceFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.yield()
                try {
                    endChoiceFocusRequester.requestFocus()
                } catch (_: IllegalStateException) {}
            }
            val hasNextEpisode = !nextEpisodeTitle.isNullOrEmpty()
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF014171F))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Video Finished", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                if (hasNextEpisode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Next up: $nextEpisodeTitle", color = Color(0xFF9AA0A6), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    PillControl(
                        icon = AppIcons.SkipNext,
                        text = "Next episode",
                        onClick = onNextEpisodeClick,
                        idleBackground = Accent,
                        focusRequester = endChoiceFocusRequester,
                        modifier = Modifier.testTag("player_next_episode_button")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                PillControl(
                    icon = if (onDetailsClick != null) AppIcons.Info else AppIcons.ArrowBack,
                    text = if (onDetailsClick != null) "Details" else "Back",
                    onClick = onDetailsClick ?: onBackPressed,
                    focusRequester = if (hasNextEpisode) null else endChoiceFocusRequester,
                    modifier = Modifier.testTag("player_return_to_details_button")
                )
            }
        }

        // Bottom: next episode, timeline, then Previous, Play/Pause and the times.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            // Next episode sits bottom-right, just above the timeline (the end-of-video card has its own).
            if (showNextPill) {
                PillControl(
                    icon = AppIcons.SkipNext,
                    text = "Next episode",
                    onClick = onNextEpisodeClick,
                    focusRequester = nextPillFocus,
                    modifier = Modifier
                        .align(Alignment.End)
                        .focusProperties {
                            up = centreFocus
                            down = timelineFocus
                        }
                        .onFocusChanged { if (nextPillShown) nextPillFocused = it.isFocused }
                        .testTag("player_next_episode_button")
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            CinemaTimelineScrubber(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                onSeekTo = onSeekTo,
                onSeekRelative = onSeekRelative,
                modifier = Modifier
                    .focusRequester(timelineFocus)
                    .focusProperties {
                        if (!isEnded) {
                            up = aboveTimeline ?: centreFocus
                            down = bottomPlayFocus
                        } else if (hasPreviousEpisode) {
                            down = previousFocus
                        }
                    }
                    .testTag("player_timeline_scrubber")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Previous and Play/Pause under the timeline, bottom left, with the times beside them.
            Row(
                modifier = Modifier.fillMaxWidth().height(ControlsRowHeight),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPreviousEpisode) {
                    PlayerControl(
                        onClick = onPreviousEpisodeClick,
                        idleBackground = ControlIdle,
                        focusRequester = previousFocus,
                        modifier = Modifier
                            .size(ControlsRowHeight)
                            .focusProperties {
                                up = timelineFocus
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = if (isEnded) FocusRequester.Cancel else bottomPlayFocus
                            }
                            .onFocusChanged { if (previousShown) previousFocused = it.isFocused }
                            .testTag("player_previous_episode_button")
                    ) { color ->
                        Icon(AppIcons.SkipPrevious, contentDescription = "Previous episode", tint = color, modifier = Modifier.size(28.dp))
                    }
                }
                // At the end of the video Play does nothing (the end card offers the choices), so it steps aside.
                if (!isEnded) {
                    PlayerControl(
                        onClick = onPlayPauseClick,
                        idleBackground = ControlIdle,
                        focusRequester = bottomPlayFocus,
                        modifier = Modifier
                            .size(ControlsRowHeight)
                            .focusProperties {
                                up = timelineFocus
                                down = FocusRequester.Cancel
                                left = if (hasPreviousEpisode) previousFocus else FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            }
                            .testTag("player_bottom_play_pause_button")
                    ) { color ->
                        Icon(
                            if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = color,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = PlaybackTimeUtils.formatTime(currentPositionMs),
                        color = Color(0xFFE3E7EF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("player_time_current")
                    )
                    Text(text = " / ", color = Color(0xFF9AA0AC), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = PlaybackTimeUtils.formatTime(durationMs),
                        color = Color(0xFF9AA0AC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("player_time_duration")
                    )
                }
            }
        }
    }
}

/**
 * Milestone O: "Skip intro", bottom left just above the timeline (the same place with or without the controls; the
 * Next episode pill is on the right, Previous and Play/Pause are under the timeline), shown during the intro. On the TV it takes the remote's focus when it appears
 * ([takeFocusOnShow]), so OK skips straight away, as on Netflix. When it disappears while focused, focus moves to
 * [focusAfterHiding] (play/pause) if the controls are showing, so OK never reaches nothing (the F-32 lesson).
 */
@Composable
fun SkipIntroButton(
    visible: Boolean,
    controlsVisible: Boolean,
    focusAfterHiding: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    takeFocusOnShow: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible && takeFocusOnShow) {
            kotlinx.coroutines.yield()
            try {
                focusRequester.requestFocus()
            } catch (_: IllegalStateException) {}
        } else if (!visible && focused) {
            focused = false
            if (controlsVisible) {
                kotlinx.coroutines.yield()
                try {
                    focusAfterHiding.requestFocus()
                } catch (_: IllegalStateException) {}
            }
        }
    }
    if (!visible) return
    PillControl(
        icon = AppIcons.SkipNext,
        text = "Skip intro",
        onClick = onClick,
        // Darker than the other pills: it also shows over a bright picture, without the controls' gradient.
        idleBackground = Color(0xCC14171F),
        focusRequester = focusRequester,
        modifier = modifier
            .navigationBarsPadding()
            // Level with the Next episode pill: above the timeline, the bottom row and the overlay's padding.
            .padding(start = 28.dp, bottom = AboveTimelineBottom)
            .onFocusChanged { focused = it.isFocused }
            .testTag("player_skip_intro_button")
    )
}

/** Thin red timeline; thicker with a thumb while focused (TV) or dragged (phone). Left/right seek 10 s on TV. */
@Composable
fun CinemaTimelineScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentProgress = PlaybackTimeUtils.calculateProgress(currentPositionMs, durationMs)
    val bufferedProgress = PlaybackTimeUtils.calculateProgress(bufferedPositionMs, durationMs)
    val displayProgress = if (isDragging) dragFraction else currentProgress

    val active = isFocused || isDragging
    val trackHeight by animateDpAsState(if (active) 8.dp else 4.dp, label = "trackHeight")
    val thumbSize = if (active) 18.dp else 12.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onSeekRelative(-10000L)
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekRelative(10000L)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val fraction = if (size.width > 0) (offset.x / size.width).coerceIn(0f, 1f) else 0f
                    val targetMs = PlaybackTimeUtils.calculateSeekFromProgress(fraction, durationMs)
                    onSeekTo(targetMs)
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = if (size.width > 0) (offset.x / size.width).coerceIn(0f, 1f) else 0f
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = if (size.width > 0) (change.position.x / size.width).coerceIn(0f, 1f) else 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        val targetMs = PlaybackTimeUtils.calculateSeekFromProgress(dragFraction, durationMs)
                        onSeekTo(targetMs)
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = displayProgress,
                    range = 0f..1f
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()

        // Inactive, buffered and played parts of the track.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            val width = size.width
            val height = size.height
            val cornerRadius = CornerRadius(height / 2f, height / 2f)
            drawRoundRect(color = Color(0x40FFFFFF), size = Size(width, height), cornerRadius = cornerRadius)
            if (bufferedProgress > 0f) {
                val bufferedWidth = (bufferedProgress * width).coerceIn(0f, width)
                drawRoundRect(color = Color(0x66FFFFFF), size = Size(bufferedWidth, height), cornerRadius = cornerRadius)
            }
            val activeWidth = (displayProgress * width).coerceIn(0f, width)
            drawRoundRect(color = Accent, size = Size(activeWidth, height), cornerRadius = cornerRadius)
        }

        val thumbOffsetX = with(LocalDensity.current) {
            val thumbPx = displayProgress.coerceIn(0f, 1f) * totalWidthPx
            (thumbPx - (thumbSize.toPx() / 2f)).coerceAtLeast(0f).toDp()
        }
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Accent)
                .border(width = if (isFocused) 3.dp else 0.dp, color = Color.White, shape = CircleShape)
        )
    }
}

/**
 * One player control: [idleBackground] normally, white with dark content when focused by the remote, slightly
 * larger. OK/Enter on the remote clicks it (same key handling as before L.5, checked on the TV in E.5).
 */
@Composable
private fun PlayerControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    idleBackground: Color = Color.Transparent,
    focusRequester: FocusRequester? = null,
    content: @Composable (contentColor: Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "focusScale")
    val localFocusRequester = focusRequester ?: remember { FocusRequester() }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (isFocused) Color.White else idleBackground)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusRequester(localFocusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content(if (isFocused) OnFocused else Color.White)
    }
}

/** Rounded control with an icon and a short label (Subtitles, Audio, Details, Next episode). */
@Composable
private fun PillControl(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleBackground: Color = PillIdle,
    focusRequester: FocusRequester? = null
) {
    PlayerControl(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        idleBackground = idleBackground,
        focusRequester = focusRequester,
        modifier = modifier
    ) { color ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The Material replay arrow (mirrored for forward) with "10" inside. */
@Composable
private fun SeekIcon(forward: Boolean, color: Color, size: Dp = 38.dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Icon(
            AppIcons.Replay,
            contentDescription = if (forward) "Forward 10 seconds" else "Back 10 seconds",
            tint = color,
            modifier = Modifier
                .size(size)
                .graphicsLayer { if (forward) scaleX = -1f }
        )
        // The arrow's circle is centred a little below the middle of the 24×24 icon (y = 13).
        Text(
            text = "10",
            color = color,
            fontSize = (size.value * 0.28f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = size / 24)
        )
    }
}
