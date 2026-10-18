package com.drivecinema.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    playPauseFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onPlayPauseClick: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onAudioTrackClick: () -> Unit,
    onNextEpisodeClick: () -> Unit = {},
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("playback_overlay_container")
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xD90A0B0E),
                        Color(0x33000000),
                        Color(0xE60A0B0E)
                    )
                )
            )
    ) {
        // Top Header
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
                CinemaBackButton(
                    onClick = onBackPressed,
                    modifier = Modifier.testTag("player_back_button")
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    color = Color(0xFFF0F2F5),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("player_title")
                )
            }

            if (AudioTrackHelper.shouldShowSelector(audioTracks)) {
                Spacer(modifier = Modifier.width(16.dp))
                CinemaAudioButton(
                    trackCount = audioTracks.size,
                    onClick = onAudioTrackClick,
                    modifier = Modifier.testTag("player_audio_button")
                )
            }
        }

        // Center Controls
        if (!isEnded) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CinemaSeekButton(
                    isForward = false,
                    onClick = { onSeekRelative(-10000L) },
                    modifier = Modifier.testTag("player_rewind_button")
                )

                Spacer(modifier = Modifier.width(36.dp))

                CinemaPlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = onPlayPauseClick,
                    focusRequester = playPauseFocusRequester,
                    modifier = Modifier.testTag("player_play_pause_button")
                )

                Spacer(modifier = Modifier.width(36.dp))

                CinemaSeekButton(
                    isForward = true,
                    onClick = { onSeekRelative(10000L) },
                    modifier = Modifier.testTag("player_forward_button")
                )
            }
        } else {
            // End of episode manual prompt (No auto countdown or autoplay)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF14171F))
                    .border(1.dp, Color(0xFF2A2E3B), RoundedCornerShape(14.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Video Finished",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!nextEpisodeTitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Next up: $nextEpisodeTitle",
                        color = Color(0xFF9AA0A6),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    CinemaNextEpisodeButton(
                        onClick = onNextEpisodeClick,
                        modifier = Modifier.testTag("player_next_episode_button")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                CinemaPillButton(
                    text = "Return to Details",
                    onClick = onBackPressed
                )
            }
        }

        // Bottom Timeline & Duration
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            // Next episode sits bottom-right, just above the timeline (the end-of-video card has its own).
            if (!isEnded && !nextEpisodeTitle.isNullOrEmpty()) {
                CinemaNextEpisodeButton(
                    onClick = onNextEpisodeClick,
                    modifier = Modifier
                        .align(Alignment.End)
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
                modifier = Modifier.testTag("player_timeline_scrubber")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = PlaybackTimeUtils.formatTime(currentPositionMs),
                    color = Color(0xFFCBD2E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("player_time_current")
                )
                Text(
                    text = PlaybackTimeUtils.formatTime(durationMs),
                    color = Color(0xFF8B92A4),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("player_time_duration")
                )
            }
        }
    }
}

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

    val trackHeight = if (isFocused || isDragging) 8.dp else 5.dp
    val thumbSize = if (isFocused || isDragging) 20.dp else 14.dp

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

        // 3-layer timeline tracks (Inactive, Buffered, Active)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            val width = size.width
            val height = size.height
            val cornerRadius = CornerRadius(height / 2f, height / 2f)

            // 1. Inactive Track (Full Width)
            drawRoundRect(
                color = Color(0x3BFFFFFF),
                size = Size(width, height),
                cornerRadius = cornerRadius
            )

            // 2. Buffered Track
            if (bufferedProgress > 0f) {
                val bufferedWidth = (bufferedProgress * width).coerceIn(0f, width)
                drawRoundRect(
                    color = Color(0x7AFFFFFF),
                    size = Size(bufferedWidth, height),
                    cornerRadius = cornerRadius
                )
            }

            // 3. Active Track
            val activeWidth = (displayProgress * width).coerceIn(0f, width)
            drawRoundRect(
                color = Color(0xFFE50914), // Cinematic Red
                size = Size(activeWidth, height),
                cornerRadius = cornerRadius
            )
        }

        // Thumb indicator
        val thumbOffsetXDp = with(LocalDensity.current) {
            val clampedFraction = displayProgress.coerceIn(0f, 1f)
            val thumbPx = clampedFraction * totalWidthPx
            (thumbPx - (thumbSize.toPx() / 2f)).coerceAtLeast(0f).toDp()
        }

        Box(
            modifier = Modifier
                .padding(start = thumbOffsetXDp)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color(0xFFE50914))
                .border(
                    width = if (isFocused) 2.5.dp else 1.5.dp,
                    color = if (isFocused) Color(0xFF4D88FF) else Color.White,
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun CinemaPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val localFocusRequester = focusRequester ?: remember { androidx.compose.ui.focus.FocusRequester() }

    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color(0xFFE50914) else Color.Transparent,
                shape = CircleShape
            )
            .onKeyEvent { keyEvent ->
                android.util.Log.i("PlaybackOverlay", "CinemaPlayPauseButton onKeyEvent: type=${keyEvent.type}, key=${keyEvent.key}")
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    android.util.Log.i("PlaybackOverlay", "CinemaPlayPauseButton triggering onClick()")
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
        if (isPlaying) {
            PauseIcon(
                modifier = Modifier.size(28.dp),
                color = Color(0xFF111318)
            )
        } else {
            PlayIcon(
                modifier = Modifier.size(28.dp),
                color = Color(0xFF111318)
            )
        }
    }
}

@Composable
private fun CinemaSeekButton(
    isForward: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (isFocused) Color(0xFF2A2E3B) else Color(0x9916181F))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color(0x3BFFFFFF),
                shape = CircleShape
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        SeekArrowIcon(
            isForward = isForward,
            color = if (isFocused) Color.White else Color(0xFFF0F2F5),
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun CinemaBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isFocused) Color(0xFF2A2E3B) else Color(0x8816181F))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color(0x33FFFFFF),
                shape = CircleShape
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BackChevronIcon(
            modifier = Modifier.size(20.dp),
            color = if (isFocused) Color.White else Color(0xFFF0F2F5)
        )
    }
}

@Composable
private fun CinemaAudioButton(
    trackCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isFocused) Color(0xFF2A2E3B) else Color(0x8816181F))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color(0x33FFFFFF),
                shape = RoundedCornerShape(20.dp)
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerIcon(
            modifier = Modifier.size(16.dp),
            color = if (isFocused) Color.White else Color(0xFFF0F2F5)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Audio ($trackCount)",
            color = if (isFocused) Color.White else Color(0xFFF0F2F5),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CinemaNextEpisodeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isFocused) Color(0xFF2A2E3B) else Color(0x9916181F))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color(0x44FFFFFF),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Next Episode",
            color = if (isFocused) Color.White else Color(0xFFF0F2F5),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(8.dp))
        NextTrackIcon(
            modifier = Modifier.size(14.dp),
            color = if (isFocused) Color.White else Color(0xFFF0F2F5)
        )
    }
}

@Composable
private fun CinemaPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2A2E3B) else Color(0x9916181F))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color(0x3BFFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.White else Color(0xFFF0F2F5),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// -------------------------------------------------------------
// Vector Icon Components (Canvas-rendered for crisp performance)
// -------------------------------------------------------------

@Composable
fun PlayIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.50f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.24f
        val barHeight = size.height * 0.64f
        val top = size.height * 0.18f
        val corner = CornerRadius(barWidth / 3f, barWidth / 3f)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.20f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = corner
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.56f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = corner
        )
    }
}

@Composable
fun BackChevronIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.5.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.22f)
            lineTo(size.width * 0.35f, size.height * 0.50f)
            lineTo(size.width * 0.62f, size.height * 0.78f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun SeekArrowIcon(
    isForward: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val diameter = size.minDimension * 0.72f
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            val startAngle = if (isForward) 220f else 140f
            val sweepAngle = if (isForward) 280f else -280f

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val arrowPath = Path().apply {
                val arrowTipX = if (isForward) center.x + radius * 0.3f else center.x - radius * 0.3f
                val arrowTipY = center.y - radius * 0.95f
                moveTo(arrowTipX, arrowTipY)
                if (isForward) {
                    lineTo(arrowTipX + 5.dp.toPx(), arrowTipY - 5.dp.toPx())
                    lineTo(arrowTipX + 7.dp.toPx(), arrowTipY + 2.dp.toPx())
                } else {
                    lineTo(arrowTipX - 5.dp.toPx(), arrowTipY - 5.dp.toPx())
                    lineTo(arrowTipX - 7.dp.toPx(), arrowTipY + 2.dp.toPx())
                }
                close()
            }
            drawPath(arrowPath, color = color)
        }

        Text(
            text = "10",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SpeakerIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.35f)
            lineTo(size.width * 0.38f, size.height * 0.35f)
            lineTo(size.width * 0.70f, size.height * 0.15f)
            lineTo(size.width * 0.70f, size.height * 0.85f)
            lineTo(size.width * 0.38f, size.height * 0.65f)
            lineTo(size.width * 0.15f, size.height * 0.65f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun NextTrackIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.18f)
            lineTo(size.width * 0.65f, size.height * 0.50f)
            lineTo(size.width * 0.15f, size.height * 0.82f)
            close()
        }
        drawPath(path, color = color)
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.74f, size.height * 0.18f),
            size = Size(size.width * 0.16f, size.height * 0.64f)
        )
    }
}
