package com.drivecinema.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.TmdbImages
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcControlColors
import com.drivecinema.app.ui.design.DcFocusIndication
import com.drivecinema.app.ui.design.DcIconButton
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.player.WatchState
import com.drivecinema.app.ui.setup.SettingsLabels

/**
 * A series' details on the phone (Task P.5, design §5): the backdrop, the poster over it with the title, one facts
 * line ("2012 · 3 seasons · [star] 7.2") and the genres as text; Play with today's labels; the overview; season tabs; the
 * season's episodes as borderless rows.
 */
@Composable
fun SeriesDetailsScreen(
    series: SeriesEntity,
    state: SeriesDetailsState,
    onSeasonSelect: (Int) -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Milestone D: the phone's "Play on TV" for an episode; null hides it (TV, or no TV linked). */
    onPlayOnTvClick: ((EpisodeItem) -> Unit)? = null,
    /** Task R.2: the Naming check (this series' files against TMDB); null hides it. No badge yet (R.6). */
    onNamingCheckClick: (() -> Unit)? = null
) {
    val seasonEpisodes = state.episodes.filter { it.season == state.selectedSeason }
    val nextEpisode = state.nextEpisode
    val playFocusRequester = remember { FocusRequester() }

    // F-37: Play gets the initial focus once per series. Progress and copies re-emit (player returned, a sync) with
    // new EpisodeItems; keying on nextEpisode moved the focus back to Play while the user scrolled the episodes.
    val hasPlay = nextEpisode != null && nextEpisode.isAvailable
    var initialFocusDone by remember(series.id) { mutableStateOf(false) }
    LaunchedEffect(series.id, hasPlay) {
        if (!hasPlay || initialFocusDone) return@LaunchedEffect
        // Play is in a lazy item, which can be laid out a frame after this effect starts: try on the next frames.
        repeat(5) {
            if (runCatching { playFocusRequester.requestFocus() }.getOrDefault(false)) {
                initialFocusDone = true
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "backdrop", contentType = "backdrop") {
                DetailsBackdrop(
                    TmdbImages.detailsHeader(series.backdropPath, series.posterPath),
                    contentDescription = series.title,
                    modifier = Modifier.fillMaxWidth().height(PHONE_BACKDROP_HEIGHT)
                )
            }
            item(key = "header", contentType = "header") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PhoneTitleBlock(
                        title = series.title,
                        posterPath = series.posterPath,
                        facts = facts(series.year?.toString(), seasonCountText(state.seasons.size), voteAverage = series.voteAverage),
                        genres = genresText(series.genres)
                    )
                    if (nextEpisode != null && hasPlay) {
                        DcButton(
                            playEpisodeLabel(nextEpisode),
                            onClick = { onEpisodeClick(nextEpisode) },
                            icon = AppIcons.PlayArrow,
                            modifier = Modifier.fillMaxWidth().focusRequester(playFocusRequester)
                        )
                        if (onPlayOnTvClick != null) {
                            PlayOnTvButton(
                                label = "Play S${nextEpisode.season}E${nextEpisode.episode} on TV",
                                onClick = { onPlayOnTvClick(nextEpisode) }
                            )
                        }
                    }
                    series.overview?.takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = DcTheme.type.body, color = DcColors.Text2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (onNamingCheckClick != null) {
                        DcButton(
                            SettingsLabels.NAMING_CHECK,
                            onClick = onNamingCheckClick,
                            kind = DcButtonKind.Secondary,
                            icon = AppIcons.InsertDriveFile,
                            small = true,
                            modifier = Modifier.testTag("details_naming_check")
                        )
                    }
                }
            }
            if (state.seasons.isNotEmpty()) {
                item(key = "seasons", contentType = "seasons") {
                    SeasonTabs(
                        state.seasons,
                        selected = state.selectedSeason,
                        onSelect = onSeasonSelect,
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
                    )
                }
            }
            items(seasonEpisodes, key = { it.id ?: "s${it.season}e${it.episode}" }, contentType = { "episode" }) { episode ->
                EpisodeRowItem(
                    episode = episode,
                    fallbackBackdropPath = series.backdropPath ?: series.posterPath,
                    onClick = { onEpisodeClick(episode) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onPlayOnTvClick = onPlayOnTvClick?.takeIf { episode.isAvailable }?.let { send -> { send(episode) } }
                )
            }
        }
        DcIconButton(
            AppIcons.ArrowBack,
            contentDescription = "Back",
            onClick = onBackClick,
            onImage = true,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
        )
    }
}

/** An episode row's focus look: no border or card (design §5); a light fill and the white ring when focused. */
private val episodeRowColors = DcControlColors(
    container = Color.Transparent,
    content = DcColors.Text1,
    focusedContainer = DcColors.Surface2,
    focusedContent = DcColors.Text1
)

/**
 * One episode on the phone (borderless, design §5): the still with the watched check or the progress bar, "E3. Title"
 * on up to two lines, its facts ("21 min · 720p · [star] 7.8"), the time left, two lines of overview. An episode missing
 * on Drive can't be opened.
 */
@Composable
fun EpisodeRowItem(
    episode: EpisodeItem,
    fallbackBackdropPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Milestone D: a TV button at the end of the row (phone with a linked TV only). */
    onPlayOnTvClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .indication(
                interactionSource,
                DcFocusIndication(episodeRowColors, DcTokens.Radius.CARD.dp, focusedScale = 1f, recolorContent = false)
            )
            .clickable(enabled = episode.isAvailable, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EpisodeStill(episode, fallbackBackdropPath, Modifier.size(width = 128.dp, height = 72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                episodeTitle(episode),
                style = DcTheme.type.rowTitle,
                color = when {
                    !episode.isAvailable -> DcColors.Text3
                    episode.isCompleted -> DcColors.Text2
                    else -> DcColors.Text1
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            episodeFacts(episode)?.let {
                FactsText(it, Modifier.testTag("episode_info_line"), style = DcTheme.type.caption)
            }
            if (episode.isAvailable && !episode.isCompleted) {
                WatchState.label(episode.positionMs, episode.durationMs, episode.isCompleted)?.let {
                    Text(it, style = DcTheme.type.caption, color = DcColors.Danger)
                }
            }
            episode.overview?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = DcTheme.type.caption, color = DcColors.Text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!episode.isAvailable) {
                Text("Missing on Drive", style = DcTheme.type.caption, color = DcColors.Danger)
            }
        }
        if (onPlayOnTvClick != null) {
            DcIconButton(AppIcons.Tv, contentDescription = "Play on TV", onClick = onPlayOnTvClick, modifier = Modifier.testTag("episode_play_on_tv"))
        }
    }
}

/**
 * An episode's still (Home's wide-card size, `TmdbImages.wideCard`), dimmed with a check when watched, "Unavailable"
 * when missing on Drive, a red progress bar when started.
 */
@Composable
internal fun EpisodeStill(episode: EpisodeItem, fallbackBackdropPath: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(DcTokens.Radius.CONTROL.dp)).background(DcColors.Surface2)) {
        TmdbImages.wideCard(episode.stillPath ?: fallbackBackdropPath)?.let { url ->
            AsyncImage(model = url, contentDescription = episode.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (!episode.isAvailable) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Text("Unavailable", style = DcTheme.type.caption, color = DcColors.Text2)
            }
        } else if (episode.isCompleted) {
            // Watched episodes are dimmed so the unwatched ones stand out (Milestone J.1); one marker only (L.5).
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.CheckCircle, contentDescription = "Watched", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        if (episode.positionMs > 0 && !episode.isCompleted && episode.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (episode.positionMs.toFloat() / episode.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                color = DcColors.Brand,
                trackColor = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
}
