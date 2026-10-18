package com.drivecinema.app.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.player.WatchState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesDetailsScreen(
    series: SeriesEntity,
    state: SeriesDetailsState,
    onSeasonSelect: (Int) -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seasonEpisodes = state.episodes.filter { it.season == state.selectedSeason }
    val nextEpisode = state.nextEpisode

    val backInteractionSource = remember { MutableInteractionSource() }
    val isBackFocused by backInteractionSource.collectIsFocusedAsState()

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayFocused by playInteractionSource.collectIsFocusedAsState()
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(nextEpisode) {
        if (nextEpisode != null && nextEpisode.isAvailable) {
            try {
                playFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            // Backdrop Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    val backdropUrl = when {
                        !series.backdropPath.isNullOrEmpty() -> "https://image.tmdb.org/t/p/w780${series.backdropPath}"
                        !series.posterPath.isNullOrEmpty() -> "https://image.tmdb.org/t/p/w780${series.posterPath}"
                        else -> null
                    }

                    if (backdropUrl != null) {
                        AsyncImage(
                            model = backdropUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF16181D))
                        )
                    }

                    // Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF0C0D10)),
                                    startY = 80f
                                )
                            )
                    )

                    // Back Button with TV Focus state and Edge-to-Edge Status Bar Insets
                    IconButton(
                        onClick = onBackClick,
                        interactionSource = backInteractionSource,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isBackFocused) Color(0xFFE50914) else Color.Black.copy(alpha = 0.5f))
                            .border(
                                border = if (isBackFocused) BorderStroke(2.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                shape = CircleShape
                            )
                    ) {
                        Text("←", color = Color.White, fontSize = 24.sp)
                    }
                }
            }

            // Series Header Info
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Poster thumbnail
                        if (!series.posterPath.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.dp, Color(0xFF2A2D35)), RoundedCornerShape(8.dp))
                                    .background(Color(0xFF16181D))
                            ) {
                                AsyncImage(
                                    model = "https://image.tmdb.org/t/p/w342${series.posterPath}",
                                    contentDescription = series.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Title & Metadata
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = series.title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (series.year != null) {
                                    Text(
                                        text = series.year.toString(),
                                        color = Color.LightGray,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (state.seasons.isNotEmpty()) {
                                    val count = state.seasons.size
                                    Text(
                                        text = "$count ${if (count == 1) "Season" else "Seasons"}",
                                        color = Color.LightGray,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Rating
                            series.voteAverage?.takeIf { it > 0.0 }?.let { voteAverage ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "★",
                                        color = Color(0xFFFFC107),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f", voteAverage),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    series.voteCount?.takeIf { it > 0 }?.let { voteCount ->
                                        Text(
                                            text = "($voteCount)",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Genres row
                    val genreList = series.genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    if (genreList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            genreList.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF1E212A), RoundedCornerShape(12.dp))
                                        .border(BorderStroke(1.dp, Color(0xFF2E3342)), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        color = Color(0xFFD0D4E0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Smart Play Next Button
                    if (nextEpisode != null && nextEpisode.isAvailable) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { onEpisodeClick(nextEpisode) },
                            interactionSource = playInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .focusRequester(playFocusRequester)
                                .border(
                                    border = if (isPlayFocused) BorderStroke(3.dp, Color.White) else BorderStroke(0.dp, Color.Transparent),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayFocused) Color(0xFFFF1E28) else Color(0xFFE50914),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val epLabel = "S${nextEpisode.season}E${nextEpisode.episode}"
                            val statusLabel = if (nextEpisode.isCompleted) "Replay" else if (nextEpisode.positionMs > 0) "Resume" else "Play"
                            val epTitle = if (nextEpisode.title.isNotEmpty()) " • ${nextEpisode.title}" else ""
                            Text(
                                text = "▶  $statusLabel $epLabel$epTitle",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Overview
                    if (!series.overview.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = series.overview.orEmpty(),
                            color = Color(0xFFB0B3B8),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Season Selector Chips
                    if (state.seasons.isNotEmpty()) {
                        Text(
                            text = "Seasons",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.seasons) { seasonNum ->
                                SeasonChip(
                                    season = seasonNum,
                                    selected = state.selectedSeason == seasonNum,
                                    onClick = { onSeasonSelect(seasonNum) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Episodes (${seasonEpisodes.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Episode List
            items(seasonEpisodes, key = { it.id ?: "s${it.season}e${it.episode}" }) { episode ->
                EpisodeRowItem(
                    episode = episode,
                    fallbackBackdropPath = series.backdropPath ?: series.posterPath,
                    onClick = { onEpisodeClick(episode) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** How a season chip looks: red = the season shown, white border = where the TV remote is. */
internal data class SeasonChipStyle(val container: Color, val label: Color, val border: BorderStroke?)

internal fun seasonChipStyle(selected: Boolean, focused: Boolean): SeasonChipStyle = SeasonChipStyle(
    container = when {
        selected -> Color(0xFFE50914)
        focused -> Color(0xFF3A3F4B)
        else -> Color(0xFF16181D)
    },
    label = if (selected || focused) Color.White else Color.LightGray,
    border = if (focused) BorderStroke(3.dp, Color.White) else null
)

@Composable
private fun SeasonChip(season: Int, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val style = seasonChipStyle(selected, isFocused)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("Season $season", fontWeight = if (isFocused || selected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = style.container,
            selectedLabelColor = style.label,
            containerColor = style.container,
            labelColor = style.label
        ),
        border = style.border ?: FilterChipDefaults.filterChipBorder(enabled = true, selected = selected),
        interactionSource = interactionSource
    )
}

@Composable
fun EpisodeRowItem(
    episode: EpisodeItem,
    fallbackBackdropPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressRatio = if (episode.durationMs > 0) episode.positionMs.toFloat() / episode.durationMs.toFloat() else 0f
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val cardBorder = if (isFocused) {
        BorderStroke(2.dp, Color.White)
    } else {
        BorderStroke(1.dp, Color(0xFF1E212A))
    }

    val cardBackground = if (isFocused) {
        Color(0xFF222633)
    } else {
        Color(0xFF13151C)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBackground)
            .border(cardBorder, RoundedCornerShape(8.dp))
            .clickable(
                enabled = episode.isAvailable,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(
                enabled = episode.isAvailable,
                interactionSource = interactionSource
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail (16:9 ratio)
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E212A))
            ) {
                val stillPath = episode.stillPath ?: fallbackBackdropPath
                if (!stillPath.isNullOrEmpty()) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w300$stillPath",
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // If not available on Drive, darken with badge
                if (!episode.isAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "UNAVAILABLE",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (episode.isCompleted) {
                    // Watched episodes are dimmed so the unwatched ones stand out (Milestone J.1).
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // In-progress indicator bar
                if (episode.positionMs > 0 && !episode.isCompleted) {
                    LinearProgressIndicator(
                        progress = { progressRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFFE50914),
                        trackColor = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            // Episode Text Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "E${episode.episode}. ${episode.title.ifEmpty { "Episode ${episode.episode}" }}",
                        color = when {
                            !episode.isAvailable -> Color.Gray
                            episode.isCompleted -> Color(0xFFB0B4BF)
                            else -> Color.White
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (episode.voteAverage != null && episode.voteAverage > 0.0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "★",
                                color = Color(0xFFFFC107),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f", episode.voteAverage),
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                val watchLabel = if (episode.isAvailable) {
                    WatchState.label(episode.positionMs, episode.durationMs, episode.isCompleted)
                } else {
                    null
                }
                if (watchLabel != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = watchLabel,
                        color = if (episode.isCompleted) Color(0xFF66BB6A) else Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!episode.overview.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = episode.overview,
                        color = Color(0xFF8F94A3),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!episode.isAvailable) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Missing on Drive",
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
