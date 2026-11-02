package com.drivecinema.app.ui.mobile.home

import com.drivecinema.app.ui.icons.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.drivecinema.app.ui.TmdbImages
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcIconButton
import com.drivecinema.app.ui.design.DcTokens


@Composable
fun MobileHomeScreen(
    state: MobileHomeUiState,
    onMovieClick: (MediaItemEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onPairTvClick: (() -> Unit)? = null,
    onAccountClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Milestone D: a cast button on each Continue Watching card; null hides it (no TV linked). */
    onContinueWatchingPlayOnTv: ((ContinueWatchingItem) -> Unit)? = null,
    /** A TV is linked: the top bar's cast button gets a green dot. */
    tvLinked: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header Bar
            item {
                MobileHomeTopBar(
                    account = state.account,
                    tvLinked = tvLinked,
                    onPairTvClick = onPairTvClick,
                    onAccountClick = onAccountClick
                )
            }

            // Continue Watching Section
            if (state.hasContinueWatching) {
                item {
                    Text(
                        text = "Continue Watching",
                        color = Color(0xFFF0F2F5),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.continueWatching, key = { it.id }) { item ->
                            ContinueWatchingCard(
                                item = item,
                                onClick = { onContinueWatchingClick(item) },
                                onPlayOnTvClick = onContinueWatchingPlayOnTv?.let { send -> { send(item) } }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Films Section
            item {
                Text(
                    text = "Films",
                    color = Color(0xFFF0F2F5),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.movies, key = { it.id }) { movie ->
                        PosterCard(
                            title = movie.title,
                            posterPath = movie.posterPath,
                            onClick = { onMovieClick(movie) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Series Section
            item {
                Text(
                    text = "Series",
                    color = Color(0xFFF0F2F5),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.series, key = { it.id }) { seriesItem ->
                        PosterCard(
                            title = seriesItem.title,
                            posterPath = seriesItem.posterPath,
                            onClick = { onSeriesClick(seriesItem) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayOnTvClick: (() -> Unit)? = null
) {
    val progress = if (item.durationMs > 0) item.positionMs.toFloat() / item.durationMs.toFloat() else 0f

    Column(
        modifier = modifier
            .width(200.dp)
            .clickable(onClick = onClick)
    ) {
        // 16:9 artwork; its only action is play (the whole card).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(DcTokens.Radius.CONTROL.dp))
                .background(DcColors.Surface1)
        ) {
            val backdropUrl = TmdbImages.wideCard(item.backdropPath)
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = item.title,
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
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Play Icon overlay
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            // Progress bar (an "Up next" card hasn't been started yet)
            if (!item.isUpNext) LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = DcColors.Brand,
                trackColor = Color.White.copy(alpha = 0.2f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = DcTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = DcColors.Text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ContinueWatching.subtitle(item)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = DcTheme.type.caption.copy(
                            fontWeight = if (item.isUpNext) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (item.isUpNext) DcColors.Brand else DcColors.Text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Milestone D: send this title to the linked TV instead, beside the title so the artwork keeps one action.
            if (onPlayOnTvClick != null) {
                DcIconButton(
                    icon = AppIcons.Cast,
                    contentDescription = "Play on TV",
                    onClick = onPlayOnTvClick,
                    modifier = Modifier.offset(x = 10.dp).testTag("continue_play_on_tv")
                )
            }
        }
    }
}

/**
 * Home's top bar: the logo, then two icon buttons with 48 dp targets: cast (pair a TV; a green dot once one is
 * linked) and the account avatar (Settings).
 */
@Composable
private fun MobileHomeTopBar(
    account: AccountUiState,
    tvLinked: Boolean,
    onPairTvClick: (() -> Unit)?,
    onAccountClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 20.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "DriveCinema",
            color = DcColors.Brand,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onPairTvClick != null) {
                Box {
                    DcIconButton(
                        icon = AppIcons.Cast,
                        contentDescription = if (tvLinked) "Pair TV (a TV is linked)" else "Pair TV",
                        onClick = onPairTvClick,
                        modifier = Modifier.testTag("home_pair_tv")
                    )
                    if (tvLinked) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 10.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(DcColors.Canvas)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(DcColors.SuccessDot)
                                .testTag("home_tv_linked_dot")
                        )
                    }
                }
            }

            if (onAccountClick != null || account.email != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = onAccountClick != null, role = Role.Button, onClick = { onAccountClick?.invoke() })
                        .semantics { contentDescription = "Account and settings" }
                        .testTag("home_account"),
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = Modifier.size(32.dp).clip(CircleShape)
                    if (!account.photoLink.isNullOrEmpty()) {
                        AsyncImage(
                            model = account.photoLink,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = avatar
                        )
                    } else {
                        Box(avatar.background(DcColors.Surface3), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Person, contentDescription = null, tint = DcColors.Text1, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    posterPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF16181D))
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
                            Brush.verticalGradient(listOf(Color(0xFF2A2D35), Color(0xFF16181D)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = Color(0xFFF0F2F5),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
