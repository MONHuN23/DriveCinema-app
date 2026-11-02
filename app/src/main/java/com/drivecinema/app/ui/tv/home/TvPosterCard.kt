package com.drivecinema.app.ui.tv.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.ui.TmdbImages
import com.drivecinema.app.ui.design.DcTokens

object TvPosterCardDefaults {
    const val FocusedScale: Float = 1.06f
    val CornerRadius: Dp = 8.dp
    val Width: Dp = 150.dp
}

/**
 * The focused card's ring (UI task ui/tv-browse-naming): white, with a small gap, outside the image, like Milestone P's
 * controls. It replaced the 3.5 dp blue border (decision D-P1), which was thin against the backdrop. Goes before the
 * image's clip, so the ring draws outside it.
 */
private fun Modifier.cardFocusRing(isFocused: Boolean): Modifier = drawWithContent {
    drawContent()
    if (isFocused) {
        val ring = DcTokens.Focus.RING_WIDTH.dp.toPx()
        val outset = DcTokens.Focus.RING_GAP.dp.toPx() + ring / 2
        drawRoundRect(
            color = DcColors.Focus,
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + 2 * outset, size.height + 2 * outset),
            cornerRadius = CornerRadius(TvPosterCardDefaults.CornerRadius.toPx() + outset),
            style = Stroke(ring)
        )
    }
}

/**
 * A poster card on the TV (Home, the A–Z grids, search). [showTitle] = false leaves the title off under the poster:
 * TV Home's spotlight names the focused card (a card without a poster still shows its title on the tile).
 */
@Composable
fun TvPosterCard(
    title: String,
    posterPath: String?,
    backdropPath: String?,
    onClick: () -> Unit,
    onFocusChange: (Boolean, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    width: Dp = TvPosterCardDefaults.Width,
    showTitle: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) TvPosterCardDefaults.FocusedScale else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_card_scale"
    )

    // Synchronous, after every recomposition (isFocused is read below, so a focus change recomposes the card).
    // Was a LaunchedEffect: a coroutine per focus change per card (Task M.1).
    SideEffect {
        onFocusChange(isFocused, backdropPath)
    }

    Column(
        modifier = modifier
            .width(width)
            // Read in the draw phase: the scale animation no longer recomposes the card on every frame (Task M.1)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width * 1.5f)
                .cardFocusRing(isFocused)
                .clip(RoundedCornerShape(TvPosterCardDefaults.CornerRadius))
                .background(DcColors.Surface1)
        ) {
            val fullUrl = TmdbImages.poster(posterPath)
            if (fullUrl != null) {
                AsyncImage(
                    model = fullUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(DcColors.Surface3, DcColors.Surface1))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        if (showTitle) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = if (isFocused) Color.White else DcColors.Text2,
                fontSize = 14.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A Continue Watching card: the wide image with its progress bar; [showText] adds the title and "S1E2 · 48 min left". */
@Composable
fun TvContinueWatchingCard(
    title: String,
    subtitle: String?,
    posterPath: String?,
    backdropPath: String?,
    progressFraction: Float,
    onClick: () -> Unit,
    onFocusChange: (Boolean, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    showText: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) TvPosterCardDefaults.FocusedScale else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_continue_card_scale"
    )

    SideEffect {
        onFocusChange(isFocused, backdropPath)
    }

    Column(
        modifier = modifier
            .width(220.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .cardFocusRing(isFocused)
                .clip(RoundedCornerShape(TvPosterCardDefaults.CornerRadius))
                .background(DcColors.Surface1)
        ) {
            val fullUrl = TmdbImages.wideCard(backdropPath ?: posterPath)
            if (fullUrl != null) {
                AsyncImage(
                    model = fullUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(DcColors.Surface3, DcColors.Surface1))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Progress bar
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = DcColors.Brand,
                trackColor = Color.DarkGray.copy(alpha = 0.5f)
            )
        }

        if (showText) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = if (isFocused) Color.White else DcColors.Text2,
                fontSize = 14.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    color = DcColors.Text3,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
