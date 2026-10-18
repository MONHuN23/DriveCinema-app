package com.drivecinema.app.ui.tv.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

object TvPosterCardDefaults {
    const val FocusedScale: Float = 1.06f
    const val FocusedBorderWidthDp: Float = 3.5f
    val FocusedBorderColor = Color(0xFF4D88FF)
}

@Composable
fun TvPosterCard(
    title: String,
    posterPath: String?,
    backdropPath: String?,
    onClick: () -> Unit,
    onFocusChange: (Boolean, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) TvPosterCardDefaults.FocusedScale else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_card_scale"
    )

    LaunchedEffect(isFocused) {
        onFocusChange(isFocused, backdropPath)
    }

    Column(
        modifier = modifier
            .width(150.dp)
            .scale(scale)
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
                .height(225.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    border = if (isFocused) {
                        BorderStroke(
                            TvPosterCardDefaults.FocusedBorderWidthDp.dp,
                            TvPosterCardDefaults.FocusedBorderColor
                        )
                    } else {
                        BorderStroke(0.dp, Color.Transparent)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .background(Color(0xFF16181D))
        ) {
            val fullUrl = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else null
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
                            Brush.verticalGradient(listOf(Color(0xFF2A2D35), Color(0xFF16181D)))
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            color = if (isFocused) Color.White else Color(0xFFB0B3B8),
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TvContinueWatchingCard(
    title: String,
    subtitle: String?,
    posterPath: String?,
    backdropPath: String?,
    progressFraction: Float,
    onClick: () -> Unit,
    onFocusChange: (Boolean, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) TvPosterCardDefaults.FocusedScale else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_continue_card_scale"
    )

    LaunchedEffect(isFocused) {
        onFocusChange(isFocused, backdropPath)
    }

    Column(
        modifier = modifier
            .width(220.dp)
            .scale(scale)
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
                .clip(RoundedCornerShape(8.dp))
                .border(
                    border = if (isFocused) {
                        BorderStroke(
                            TvPosterCardDefaults.FocusedBorderWidthDp.dp,
                            TvPosterCardDefaults.FocusedBorderColor
                        )
                    } else {
                        BorderStroke(0.dp, Color.Transparent)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .background(Color(0xFF16181D))
        ) {
            val imagePath = backdropPath ?: posterPath
            val fullUrl = if (imagePath != null) "https://image.tmdb.org/t/p/w500$imagePath" else null
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
                            Brush.verticalGradient(listOf(Color(0xFF2A2D35), Color(0xFF16181D)))
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
                color = Color(0xFFE50914),
                trackColor = Color.DarkGray.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            color = if (isFocused) Color.White else Color(0xFFB0B3B8),
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = Color(0xFF9AA0A6),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

