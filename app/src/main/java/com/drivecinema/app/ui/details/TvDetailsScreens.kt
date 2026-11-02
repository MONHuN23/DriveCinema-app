package com.drivecinema.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.TmdbImages
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcControlColors
import com.drivecinema.app.ui.design.DcFocusIndication
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcFocusRoomScrolling
import com.drivecinema.app.ui.design.dcBleed
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.player.WatchState
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.tv.home.TvAmbientBackdropDefaults
import com.drivecinema.app.ui.tv.home.collectAfterRest

// Task P.5 (was M.4): the TV's film and series pages. The phone's layout put a full-width Play under a tall
// backdrop and four-line episode cards, so on the TV Play spanned the screen and the first episodes needed scrolling
// (user's photos, 2026-10-28). Here: the backdrop on the right, a left column, Play sized to its text, compact rows.

/** The left column's width (design §5: about 470 dp at the TV's 960 × 540 dp). */
private val COLUMN_WIDTH = 470.dp

/** Play is sized to its label, at least this wide and never wider than [PLAY_MAX_WIDTH] (about 40 % of the screen). */
private val PLAY_MIN_WIDTH = 200.dp
internal val PLAY_MAX_WIDTH = 380.dp

/**
 * The details header's picture (`TmdbImages.detailsHeader`, w1280, as on Home) on the right two thirds, fading into
 * the canvas to the left and at the bottom so the left column reads on it.
 */
@Composable
private fun TvDetailsBackdrop(url: String?, contentDescription: String) {
    if (url == null) return
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f).fillMaxHeight(0.78f)) {
            AsyncImage(model = url, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to DcColors.Canvas, 0.55f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to DcColors.Canvas)))
        }
    }
}

/** Title, the facts line and the overview, at the top of the left column. */
@Composable
private fun TvHeader(title: String, facts: Facts, overview: String?, overviewLines: Int) {
    Text(title, style = DcTheme.type.display, color = DcColors.Text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
    FactsText(facts, Modifier.padding(top = 6.dp).testTag("details_facts"))
    overview?.takeIf { it.isNotEmpty() }?.let {
        Text(
            it,
            style = DcTheme.type.body,
            color = DcColors.Text2,
            maxLines = overviewLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * Play, sized to its label; Up goes nowhere (the remote's Back leaves the page); Down goes to [downTo] when given (the
 * selected season's tab: the nearest tab below a wide button could be another season).
 */
@Composable
private fun TvPlayButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    downTo: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    DcButton(
        label,
        onClick = onClick,
        icon = AppIcons.PlayArrow,
        modifier = modifier
            .padding(top = 18.dp)
            .widthIn(min = PLAY_MIN_WIDTH, max = PLAY_MAX_WIDTH)
            .focusRequester(focusRequester)
            .focusProperties {
                up = FocusRequester.Cancel
                if (downTo != null) down = downTo
            }
            .testTag("details_play")
    )
}

/** Focuses Play once it is placed (it can be a frame late), then never again on this page (F-37). */
@Composable
private fun FocusPlayOnce(pageKey: String, hasPlay: Boolean, play: FocusRequester) {
    var done by remember(pageKey) { mutableStateOf(false) }
    LaunchedEffect(pageKey, hasPlay) {
        if (!hasPlay || done) return@LaunchedEffect
        repeat(5) {
            if (runCatching { play.requestFocus() }.getOrDefault(false)) {
                done = true
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }
}

/** A film's details on the TV: the backdrop on the right; title, facts, the copy lines, overview and Play on the left. */
@Composable
fun TvMovieDetailsScreen(
    movie: MediaItemEntity,
    progress: PlaybackProgressEntity?,
    onPlayClick: (startPositionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    infoLines: List<String> = emptyList()
) {
    val (playLabel, startPos) = moviePlay(progress)
    val play = remember { FocusRequester() }
    FocusPlayOnce(movie.id, hasPlay = true, play)

    Box(modifier.fillMaxSize().background(DcColors.Canvas)) {
        TvDetailsBackdrop(TmdbImages.detailsHeader(movie.backdropPath, movie.posterPath), movie.title)
        TvColumn {
            TvHeader(
                movie.title,
                facts(movie.year?.toString(), runtimeText(movie.runtimeSeconds), voteAverage = movie.voteAverage),
                movie.overview,
                overviewLines = 5
            )
            genresText(movie.genres)?.let { GenresText(it, Modifier.padding(top = 8.dp)) }
            if (infoLines.isNotEmpty()) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    infoLines.forEach { line ->
                        Text(line, style = DcTheme.type.caption, color = DcColors.Text2, modifier = Modifier.testTag("movie_info_line"))
                    }
                }
            }
            TvPlayButton(playLabel, onClick = { onPlayClick(startPos) }, focusRequester = play)
        }
    }
}

@Composable
private fun TvColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight().width(COLUMN_WIDTH + 48.dp).padding(start = 48.dp, top = 36.dp),
        content = content
    )
}

/**
 * Which episode the panel shows (Task P.5, the TV home's backdrop rule, M.1): rows report focus on every move, and the
 * panel follows only once focus has rested [restMillis] with no D-pad arrow held, so a held key loads no stills. Only
 * the panel reads [shownKey], so a focus move recomposes nothing else.
 */
@Stable
class EpisodePanelState(private val restMillis: Long = TvAmbientBackdropDefaults.RestMillis) {
    var focusedKey: String? by mutableStateOf(null)
        private set

    var shownKey: String? by mutableStateOf(null)
        private set

    private var isDpadHeld by mutableStateOf(false)
    private val heldKeys = mutableSetOf<Key>()

    fun onEpisodeFocused(key: String) {
        focusedKey = key
    }

    /** The episodes lost the focus (Up to the seasons): the panel goes at once. */
    fun clear() {
        focusedKey = null
        shownKey = null
    }

    /** Sees every key event on the page; never consumes it. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.key !in DpadArrows) return false
        when (event.type) {
            KeyEventType.KeyDown -> heldKeys += event.key
            KeyEventType.KeyUp -> heldKeys -= event.key
        }
        isDpadHeld = heldKeys.isNotEmpty()
        return false
    }

    suspend fun followFocus() {
        snapshotFlow { focusedKey to isDpadHeld }.collectAfterRest(restMillis) { (key, held) ->
            if (!held) shownKey = key
        }
    }

    private companion object {
        val DpadArrows = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
    }
}

internal fun episodeKey(episode: EpisodeItem): String = episode.id ?: "s${episode.season}e${episode.episode}"

/**
 * A series' details on the TV (design §5): the backdrop on the right; on the left the title, facts, three lines of
 * overview, Play with the episode code and time left, the season tabs, and the season's episodes as compact rows,
 * the first ones on screen without scrolling. The focused episode's still, title, facts, overview and progress show
 * in a panel on the right once focus rests there.
 */
@Composable
fun TvSeriesDetailsScreen(
    series: SeriesEntity,
    state: SeriesDetailsState,
    onSeasonSelect: (Int) -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier,
    panel: EpisodePanelState = remember { EpisodePanelState() },
    /** Task R.2: the Naming check, right of Play (view-only on the TV); null hides it. */
    onNamingCheckClick: (() -> Unit)? = null
) {
    val seasonEpisodes = state.episodes.filter { it.season == state.selectedSeason }
    val nextEpisode = state.nextEpisode?.takeIf { it.isAvailable }
    val play = remember { FocusRequester() }
    val selectedTab = remember { FocusRequester() }
    val tabs = selectedTab.takeIf { state.seasons.isNotEmpty() }
    FocusPlayOnce(series.id, hasPlay = nextEpisode != null, play)
    LaunchedEffect(panel) { panel.followFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(DcColors.Canvas)
            .onPreviewKeyEvent { panel.onKeyEvent(it) }
    ) {
        TvDetailsBackdrop(TmdbImages.detailsHeader(series.backdropPath, series.posterPath), series.title)
        TvColumn {
            TvHeader(
                series.title,
                facts(series.year?.toString(), seasonCountText(state.seasons.size), voteAverage = series.voteAverage),
                series.overview,
                overviewLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (nextEpisode != null) {
                    // Measured after the Naming check, so that button always shows its whole label.
                    TvPlayButton(
                        tvPlayEpisodeLabel(nextEpisode),
                        onClick = { onEpisodeClick(nextEpisode) },
                        focusRequester = play,
                        downTo = tabs,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (onNamingCheckClick != null) {
                    DcButton(
                        SettingsLabels.NAMING_CHECK,
                        onClick = onNamingCheckClick,
                        kind = DcButtonKind.Secondary,
                        icon = AppIcons.InsertDriveFile,
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                if (tabs != null) down = tabs
                            }
                            .testTag("details_naming_check")
                    )
                }
            }
            if (state.seasons.isNotEmpty()) {
                SeasonTabs(
                    state.seasons,
                    selected = state.selectedSeason,
                    onSelect = onSeasonSelect,
                    modifier = Modifier.padding(top = 14.dp).width(COLUMN_WIDTH).dcBleed(DcFocusRoom),
                    tabModifier = { season -> if (season == state.selectedSeason) Modifier.focusRequester(selectedTab) else Modifier }
                )
            }
            key(state.selectedSeason) {
                // Room at the list's ends and when scrolling, so the focused row's scale and ring aren't clipped (P.7).
                DcFocusRoomScrolling { LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(COLUMN_WIDTH)
                        .weight(1f)
                        .onFocusChanged { if (!it.hasFocus) panel.clear() }
                        .testTag("tv_episode_list"),
                    contentPadding = PaddingValues(top = DcFocusRoom, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(seasonEpisodes, key = { _, it -> episodeKey(it) }, contentType = { _, _ -> "episode" }) { index, episode ->
                        TvEpisodeRow(
                            episode,
                            onClick = { onEpisodeClick(episode) },
                            onFocused = panel::onEpisodeFocused,
                            upTo = tabs?.takeIf { index == 0 }
                        )
                    }
                } }
            }
        }
        EpisodePanel(
            panel,
            seasonEpisodes,
            fallbackBackdropPath = series.backdropPath ?: series.posterPath,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 27.dp).width(360.dp)
        )
    }
}

/** A compact row's look: like the Settings rows, white with dark text when focused. */
private val tvEpisodeRowColors = DcControlColors(
    container = Color.Transparent,
    content = DcColors.Text1,
    focusedContainer = DcColors.Focus,
    focusedContent = DcColors.Ink
)

/**
 * One episode in one 48 dp line: number, title, its copy's length, the watched check or the time left. [upTo]: the
 * first row's Up goes to the selected season's tab.
 */
@Composable
private fun TvEpisodeRow(episode: EpisodeItem, onClick: () -> Unit, onFocused: (String) -> Unit, upTo: FocusRequester?) {
    val interactionSource = remember { MutableInteractionSource() }
    val key = episodeKey(episode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { if (it.isFocused) onFocused(key) }
            .focusProperties { if (upTo != null) up = upTo }
            .testTag("tv_episode_$key")
            .indication(
                interactionSource,
                DcFocusIndication(tvEpisodeRowColors, DcTokens.Radius.CONTROL.dp, focusedScale = DcTokens.Focus.ROW_SCALE)
            )
            .clickable(enabled = episode.isAvailable, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // One line, wider for running numbers: One Piece's "1141" wrapped to "11 / 41" in 24 dp (F-48).
        Text(
            "${episode.episode}", style = DcTheme.type.label, color = DcColors.Text3, maxLines = 1, softWrap = false,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        Text(
            episode.title.ifEmpty { "Episode ${episode.episode}" },
            style = DcTheme.type.body,
            color = if (episode.isAvailable) DcColors.Text1 else DcColors.Text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        when {
            !episode.isAvailable -> Text("Missing on Drive", style = DcTheme.type.caption, color = DcColors.Danger)
            episode.isCompleted -> Icon(AppIcons.CheckCircle, contentDescription = "Watched", tint = DcColors.Text2, modifier = Modifier.size(20.dp))
            else -> WatchState.label(episode.positionMs, episode.durationMs, false)?.let {
                Text(it, style = DcTheme.type.caption, color = DcColors.Danger)
            } ?: episode.info?.let { Text(it.substringBefore(" · "), style = DcTheme.type.caption, color = DcColors.Text3) }
        }
    }
}

/** The focused episode, once focus rests: still, title, facts, overview and progress. */
@Composable
private fun EpisodePanel(
    panel: EpisodePanelState,
    episodes: List<EpisodeItem>,
    fallbackBackdropPath: String?,
    modifier: Modifier = Modifier
) {
    val episode = panel.shownKey?.let { shown -> episodes.find { episodeKey(it) == shown } } ?: return
    Column(
        modifier = modifier
            .background(DcColors.Surface1.copy(alpha = 0.94f), RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(14.dp)
            .testTag("tv_episode_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        EpisodeStill(episode, fallbackBackdropPath, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Text(episodeTitle(episode), style = DcTheme.type.rowTitle, color = DcColors.Text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
        episodeFacts(episode)?.let { FactsText(it, style = DcTheme.type.caption) }
        // The progress is the bar on the still; a second bar here showed it twice (P.7).
        episode.overview?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = DcTheme.type.caption, color = DcColors.Text2, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
