package com.drivecinema.app.ui.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TvHomeScreen(
    state: MobileHomeUiState,
    onMovieClick: (MediaItemEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onFilmsHeaderClick: () -> Unit,
    onSeriesHeaderClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAccountClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeBackdropPath by remember { mutableStateOf<String?>(null) }
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        initialFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
    ) {
        // Ambient Backdrop Crossfade on focused media
        Crossfade(
            targetState = activeBackdropPath,
            animationSpec = tween(durationMillis = 300),
            label = "tv_home_backdrop_crossfade"
        ) { backdrop ->
            if (!backdrop.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w1280$backdrop",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x550C0D10),
                                        Color(0xFF0C0D10)
                                    )
                                )
                            )
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DriveCinema",
                        color = Color(0xFFE50914),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (onAccountClick != null) {
                            val label = state.account.displayName ?: state.account.email ?: "Account"
                            TvAccountChip(
                                label = label,
                                photoLink = state.account.photoLink,
                                onClick = onAccountClick
                            )
                        }

                        TvSearchButton(
                            onClick = onSearchClick,
                            modifier = Modifier.focusRequester(initialFocusRequester)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Continue Watching Section
            if (state.hasContinueWatching) {
                item {
                    Text(
                        text = "Continue Watching",
                        color = Color(0xFFF0F2F5),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(state.continueWatching, key = { it.id }) { item ->
                            val progressFraction = if (item.durationMs > 0) {
                                item.positionMs.toFloat() / item.durationMs.toFloat()
                            } else {
                                0f
                            }

                            // "S1E4 · 12 min left" / "Up next · S1E5" / "45 min left" (Milestone J.2)
                            val subtitle = com.drivecinema.app.core.player.ContinueWatching.subtitle(item)
                                ?: if (item.type == com.drivecinema.app.core.database.entity.MediaType.EPISODE) "Episode" else "Movie"

                            TvContinueWatchingCard(
                                title = item.title,
                                subtitle = subtitle,
                                posterPath = item.posterPath,
                                backdropPath = item.backdropPath,
                                progressFraction = progressFraction,
                                onClick = { onContinueWatchingClick(item) },
                                onFocusChange = { isFocused, path ->
                                    if (isFocused && path != null) {
                                        activeBackdropPath = path
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Films Section
            item {
                TvSectionHeader(
                    title = "Films",
                    actionText = "See all A–Z →",
                    onClick = onFilmsHeaderClick,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                )

                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(state.movies, key = { it.id }) { movie ->
                        TvPosterCard(
                            title = movie.title,
                            posterPath = movie.posterPath,
                            backdropPath = movie.backdropPath,
                            onClick = { onMovieClick(movie) },
                            onFocusChange = { isFocused, path ->
                                if (isFocused && path != null) {
                                    activeBackdropPath = path
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Series Section
            item {
                TvSectionHeader(
                    title = "Series",
                    actionText = "See all A–Z →",
                    onClick = onSeriesHeaderClick,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                )

                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(state.series, key = { it.id }) { seriesItem ->
                        TvPosterCard(
                            title = seriesItem.title,
                            posterPath = seriesItem.posterPath,
                            backdropPath = seriesItem.backdropPath,
                            onClick = { onSeriesClick(seriesItem) },
                            onFocusChange = { isFocused, path ->
                                if (isFocused && path != null) {
                                    activeBackdropPath = path
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2A2D35) else Color(0xFF16181D))
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) TvPosterCardDefaults.FocusedBorderColor else Color.Transparent,
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
            text = "🔍 Search",
            color = if (isFocused) Color.White else Color(0xFFB0B3B8),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TvSectionHeader(
    title: String,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isFocused) Color.White else Color(0xFFF0F2F5),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = actionText,
            color = if (isFocused) TvPosterCardDefaults.FocusedBorderColor else Color(0xFF9AA0A6),
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun TvAccountChip(
    label: String,
    photoLink: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isFocused) Color(0xFF2A2E39) else Color(0xFF1E2128))
            .border(
                width = 2.dp,
                color = if (isFocused) Color.White else Color(0xFF2A2E39),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = onClick != null,
                onClick = { onClick?.invoke() }
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick?.invoke()
                    true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!photoLink.isNullOrEmpty()) {
            AsyncImage(
                model = photoLink,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
        } else {
            Text(text = "👤", fontSize = 14.sp)
        }

        Text(
            text = label,
            color = if (isFocused) Color.White else Color(0xFFF0F2F5),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
