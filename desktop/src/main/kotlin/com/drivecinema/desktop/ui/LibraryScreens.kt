package com.drivecinema.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.browse.BrowseCard
import com.drivecinema.desktop.browse.BrowseKind
import com.drivecinema.desktop.browse.LibraryBrowse
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.metadata.MediaInfoFormat
import com.drivecinema.app.ui.player.PlaybackTimeUtils
import com.drivecinema.app.ui.player.WatchState
import com.drivecinema.desktop.browse.BrowseModels
import com.drivecinema.desktop.browse.HomeContent
import com.drivecinema.desktop.browse.MovieDetails
import com.drivecinema.desktop.browse.SearchResults
import com.drivecinema.desktop.browse.SeriesDetails
import com.drivecinema.desktop.library.LibraryState
import java.util.Locale

// ---------------------------------------------------------------- Home

/** Account shown in the Home top bar. */
data class AccountInfo(val name: String, val email: String, val photoUrl: String?)

/** Task L.1: the top bar shared by Home, Films and Series. [section] null = Home. */
data class LibraryTopBar(
    val library: LibraryState,
    val account: AccountInfo?,
    val section: BrowseKind?,
    val onSection: (BrowseKind?) -> Unit,
    val onSearch: () -> Unit,
    val menu: AccountMenuActions
)

/** Home (Task L.1): Continue Watching, then a short row of films and one of series, each with "See all". */
@Composable
fun HomeScreen(
    content: HomeContent,
    top: LibraryTopBar,
    listState: LazyListState,
    onOpenCard: (BrowseKind, BrowseCard) -> Unit,
    onOpenContinue: (ContinueWatchingItem) -> Unit,
    onSeeAll: (BrowseKind) -> Unit
) {
    val ready = top.library as? LibraryState.Ready
    Column(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        TopBar(top)
        ready?.message?.let { Banner(it) }

        if (content.isEmpty) {
            EmptyLibrary(ready)
            return@Column
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (content.continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        Column {
                            RowHeader("Continue Watching")
                            ScrollingRow {
                                items(content.continueWatching, key = { it.id }) { item ->
                                    ContinueCard(item, onClick = { onOpenContinue(item) })
                                }
                            }
                        }
                    }
                }
                BrowseKind.entries.forEach { kind ->
                    val cards = content.cards(kind)
                    if (cards.isNotEmpty()) {
                        item(key = kind.name) {
                            Column {
                                RowHeader(kind.label, count = cards.size, onSeeAll = { onSeeAll(kind) })
                                ScrollingRow {
                                    items(cards.take(LibraryBrowse.HOME_ROW_LIMIT), key = { it.id }) { card ->
                                        PosterCard(card, Modifier.width(POSTER_WIDTH)) { onOpenCard(kind, card) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

internal val POSTER_WIDTH = 160.dp
private val RatingStar = Color(0xFFFFC107)
internal val WatchedGreen = Color(0xFF66BB6A)

/** Title of a Home row; with [onSeeAll], the count and a "See all" link that opens the full view. */
@Composable
private fun RowHeader(title: String, count: Int? = null, onSeeAll: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Text(title, color = DesktopColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        count?.let {
            Spacer(Modifier.width(10.dp))
            Text(it.toString(), color = DesktopColors.TextSecondary, fontSize = 15.sp)
        }
        Spacer(Modifier.weight(1f))
        onSeeAll?.let { open ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (hovered) DesktopColors.SurfaceRaised else Color.Transparent)
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = open)
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text("See all", color = DesktopColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(DesktopIcons.ChevronRight, contentDescription = null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A horizontal row with a scrollbar under it, so mouse users can scroll it without Shift+wheel. */
@Composable
private fun ScrollingRow(content: LazyListScope.() -> Unit) {
    val rowState = rememberLazyListState()
    Column {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(rowState),
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 6.dp)
        )
    }
}

internal fun LazyGridScope.sectionHeader(title: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            title,
            color = DesktopColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** What the account menu offers; null entries are hidden (e.g. no Refresh while syncing). */
data class AccountMenuActions(
    val onChangeFolder: (() -> Unit)?,
    val onRefresh: (() -> Unit)?,
    val onLocalFolders: () -> Unit,
    /** Task I.1; only once a Drive library is chosen. */
    val onImport: (() -> Unit)? = null,
    /** Task I.2. */
    val onSharing: (() -> Unit)? = null,
    val onPcs: (() -> Unit)? = null,
    /** Task R.3: the naming helper. */
    val onNamingHelper: (() -> Unit)? = null,
    val onOpenGuide: () -> Unit,
    val onSignOut: (() -> Unit)?,
    /** Milestone O: the "Skip intro" switch (null hides it); toggling keeps the menu open. */
    val skipIntroEnabled: Boolean? = null,
    val onToggleSkipIntro: () -> Unit = {},
    /** Milestone Q: "Sync with my devices" (null hides it); [deviceSyncStatus] is shown under it. */
    val deviceSyncEnabled: Boolean? = null,
    val onToggleDeviceSync: () -> Unit = {},
    val deviceSyncStatus: String? = null
)

@Composable
internal fun Banner(message: String) {
    Text(
        message,
        color = DesktopColors.TextPrimary,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .background(Color(0x33D97706), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFD97706), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun EmptyLibrary(ready: LibraryState.Ready?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(480.dp)) {
            if (ready?.isSyncing == true) {
                CircularProgressIndicator(color = DesktopColors.Accent)
                Spacer(Modifier.height(16.dp))
                Text("Indexing your library…", color = DesktopColors.TextPrimary, fontSize = 18.sp)
                Text("Films and series appear as soon as the folder has been read.", color = DesktopColors.TextSecondary, fontSize = 13.sp)
            } else {
                Text("No films or series yet", color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Put films in a \"Films\" folder and series in \"Series/<Show>/Season 1\" inside " +
                        (ready?.folderName?.let { "\"$it\"" } ?: "your library folder") + ", then press Refresh.",
                    color = DesktopColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Poster with watch badges (Task L.1): a check when watched, a progress bar for a started film, and
 * "3 of 10 watched" under a series.
 */
@Composable
internal fun PosterCard(card: BrowseCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val subtitle = listOfNotNull(card.year?.toString(), LibraryBrowse.seriesProgressLabel(card)).joinToString(" · ").ifEmpty { null }
    PosterCard(card.title, subtitle, TmdbImages.poster(card.posterPath), modifier, onClick) {
        if (card.watchState == WatchState.WATCHED) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color(0xCC000000))
            ) {
                Icon(DesktopIcons.CheckCircle, contentDescription = "Watched", tint = WatchedGreen, modifier = Modifier.size(22.dp))
            }
        }
        if (card.progress > 0f) {
            LinearProgressIndicator(
                progress = { card.progress },
                color = DesktopColors.Accent,
                trackColor = Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
            )
        }
    }
}

@Composable
private fun PosterCard(
    title: String,
    subtitle: String?,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .border(if (hovered) 3.dp else 0.dp, if (hovered) DesktopColors.Focus else Color.Transparent, RoundedCornerShape(8.dp))
                .background(DesktopColors.Surface)
        ) {
            RemoteImage(posterUrl, title, Modifier.fillMaxSize())
            if (posterUrl == null) {
                Text(
                    title,
                    color = DesktopColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp)
                )
            }
            overlay()
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, color = DesktopColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ContinueCard(item: ContinueWatchingItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier
            .width(300.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .border(if (hovered) 3.dp else 0.dp, if (hovered) DesktopColors.Focus else Color.Transparent, RoundedCornerShape(8.dp))
                .background(DesktopColors.Surface)
        ) {
            RemoteImage(TmdbImages.backdrop(item.backdropPath) ?: TmdbImages.poster(item.posterPath), item.title, Modifier.fillMaxSize())
            if (!item.isUpNext) LinearProgressIndicator(
                progress = { BrowseModels.progressFraction(item.positionMs, item.durationMs) },
                color = DesktopColors.Accent,
                trackColor = Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            ContinueWatching.subtitle(item)
                ?: "${PlaybackTimeUtils.formatTime(item.positionMs)} / ${PlaybackTimeUtils.formatTime(item.durationMs)}",
            color = if (item.isUpNext) DesktopColors.Accent else DesktopColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

// ---------------------------------------------------------------- Details

@Composable
fun MovieDetailsScreen(
    details: MovieDetails?,
    onPlay: (fromStart: Boolean) -> Unit,
    onBack: () -> Unit
) {
    if (details == null) {
        LoadingOrMissing(onBack)
        return
    }
    val movie = details.movie
    DetailsLayout(
        title = movie.title,
        backdropUrl = TmdbImages.backdrop(movie.backdropPath) ?: TmdbImages.poster(movie.posterPath),
        posterUrl = TmdbImages.poster(movie.posterPath),
        facts = listOfNotNull(
            movie.year?.toString(),
            BrowseModels.formatRuntime(movie.runtimeSeconds)
        ),
        rating = movie.voteAverage,
        genres = movie.genres,
        infoLines = details.sources.mapNotNull { MediaInfoFormat.infoLine(it) },
        overview = movie.overview,
        onBack = onBack
    ) {
        val resume = details.resumePositionMs
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onPlay(false) },
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)
            ) {
                ButtonLabel(
                    DesktopIcons.PlayArrow,
                    if (resume != null) "Resume from ${PlaybackTimeUtils.formatTime(resume)}" else "Play",
                    bold = true
                )
            }
            if (resume != null) {
                OutlinedButton(onClick = { onPlay(true) }) { Text("Start over", color = DesktopColors.TextPrimary) }
            }
        }
    }
}

@Composable
fun SeriesDetailsScreen(
    details: SeriesDetails?,
    onPlayEpisode: (MediaItemEntity) -> Unit,
    onBack: () -> Unit,
    /** Task R.3: the series' Naming check; null hides the button. No badge yet (Task R.6). */
    onNamingCheck: (() -> Unit)? = null
) {
    if (details == null) {
        LoadingOrMissing(onBack)
        return
    }
    val series = details.series
    var selectedSeason by remember(series.id) { mutableIntStateOf(details.seasons.keys.firstOrNull() ?: 1) }
    DetailsLayout(
        title = series.title,
        backdropUrl = TmdbImages.backdrop(series.backdropPath) ?: TmdbImages.poster(series.posterPath),
        posterUrl = TmdbImages.poster(series.posterPath),
        facts = listOfNotNull(
            series.year?.toString(),
            "${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}"
        ),
        rating = series.voteAverage,
        genres = series.genres,
        overview = series.overview,
        onBack = onBack
    ) {
        if (onNamingCheck != null) {
            DesktopButton(
                SettingsLabels.NAMING_CHECK,
                onClick = onNamingCheck,
                kind = DcButtonKind.Secondary,
                icon = DesktopIcons.InsertDriveFile,
                small = true
            )
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            details.seasons.keys.forEach { season ->
                val selected = season == selectedSeason
                OutlinedButton(
                    onClick = { selectedSeason = season },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) DesktopColors.Accent else Color.Transparent
                    )
                ) { Text("Season $season", color = DesktopColors.TextPrimary) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            details.seasons[selectedSeason].orEmpty().forEach { episode ->
                EpisodeRow(
                    episode,
                    details.progress[episode.id],
                    info = details.sources[episode.id]?.let { MediaInfoFormat.shortLine(it) },
                    onPlay = { onPlayEpisode(episode) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: MediaItemEntity, progress: PlaybackProgressEntity?, info: String?, onPlay: () -> Unit) {
    // Milestone J.1: watched rows are dimmed and labelled; started rows show the time left and a thin bar.
    val watched = progress?.isCompleted == true
    val watchLabel = progress?.let { WatchState.label(it.positionMs, it.durationMs, it.isCompleted) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) DesktopColors.SurfaceRaised else DesktopColors.Surface)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "E${episode.episodeNumber ?: "?"}",
            color = if (watched) DesktopColors.TextSecondary else DesktopColors.Accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            // Wider for running numbers: "E1141" ran into the title in 48 dp (F-48).
            modifier = Modifier.widthIn(min = 48.dp).padding(end = 8.dp)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    episode.title,
                    color = if (watched) DesktopColors.TextSecondary else DesktopColors.TextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Task L.3: "48 min · 1080p" when known.
                info?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(it, color = DesktopColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
            if (watched) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(DesktopIcons.CheckCircle, contentDescription = null, tint = WatchedGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watched", color = WatchedGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else if (watchLabel != null) {
                Text(watchLabel, color = DesktopColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (progress != null && !watched && progress.positionMs > 0) {
                LinearProgressIndicator(
                    progress = { BrowseModels.progressFraction(progress.positionMs, progress.durationMs) },
                    color = DesktopColors.Accent,
                    trackColor = Color(0x33FFFFFF),
                    modifier = Modifier.padding(top = 4.dp).width(160.dp).height(3.dp)
                )
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = DesktopColors.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (hovered) DesktopColors.Accent else DesktopColors.SurfaceRaised)
        ) {
            Icon(DesktopIcons.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DetailsLayout(
    title: String,
    backdropUrl: String?,
    posterUrl: String?,
    facts: List<String>,
    /** TMDB rating, shown with a star; hidden when missing or 0. */
    rating: Double?,
    genres: String?,
    overview: String?,
    onBack: () -> Unit,
    /** Task L.3: one technical line per copy ("2 h 12 min · 2160p · HEVC · … · Drive"). */
    infoLines: List<String> = emptyList(),
    actions: @Composable () -> Unit
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(360.dp)) {
                RemoteImage(backdropUrl, null, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x660C0D10), DesktopColors.Background))
                    )
                )
            }
            Row(Modifier.padding(horizontal = 48.dp).offset(y = (-140).dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.width(220.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(DesktopColors.Surface)) {
                    RemoteImage(posterUrl, title, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(top = 100.dp)) {
                    Text(title, color = DesktopColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    val shownRating = rating?.takeIf { it > 0 }
                    if (facts.isNotEmpty() || shownRating != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val separator = "   ·   "
                            Text(facts.joinToString(separator), color = DesktopColors.TextSecondary, fontSize = 15.sp)
                            shownRating?.let {
                                if (facts.isNotEmpty()) Text(separator, color = DesktopColors.TextSecondary, fontSize = 15.sp)
                                Icon(DesktopIcons.Star, contentDescription = "Rating", tint = RatingStar, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(String.format(Locale.US, "%.1f", it), color = DesktopColors.TextPrimary, fontSize = 15.sp)
                            }
                        }
                    }
                    genres?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = DesktopColors.TextSecondary, fontSize = 13.sp)
                    }
                    if (infoLines.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        infoLines.forEach { line ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(DesktopIcons.Info, contentDescription = null, tint = DesktopColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(line, color = DesktopColors.TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    actions()
                    overview?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(24.dp))
                        Text("Overview", color = DesktopColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = DesktopColors.TextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
        }
        BackButtonOverlay(onBack)
    }
}

@Composable
private fun LoadingOrMissing(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DesktopColors.Background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DesktopColors.Accent)
        BackButtonOverlay(onBack)
    }
}

@Composable
private fun BackButtonOverlay(onBack: () -> Unit) {
    Box(Modifier.padding(24.dp)) {
        BackButton(onBack, containerColor = Color(0x990C0D10))
    }
}

// ---------------------------------------------------------------- Search

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: SearchResults,
    onOpenMovie: (MediaItemEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    onBack: () -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().background(DesktopColors.Background).padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BackButton(onBack)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search films and series") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DesktopColors.TextPrimary,
                    unfocusedTextColor = DesktopColors.TextPrimary,
                    focusedBorderColor = DesktopColors.Focus
                ),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        Spacer(Modifier.height(24.dp))
        when {
            query.isBlank() -> Text("Type a title.", color = DesktopColors.TextSecondary)
            results.isEmpty && results.query == query.trim() ->
                Text("Nothing found for \"${query.trim()}\".", color = DesktopColors.TextSecondary)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(POSTER_WIDTH),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (results.movies.isNotEmpty()) {
                    sectionHeader("Films")
                    items(results.movies, key = { "m-${it.id}" }) { movie ->
                        PosterCard(movie.title, movie.year?.toString(), TmdbImages.poster(movie.posterPath), onClick = { onOpenMovie(movie) })
                    }
                }
                if (results.series.isNotEmpty()) {
                    sectionHeader("Series")
                    items(results.series, key = { "s-${it.id}" }) { series ->
                        PosterCard(series.title, series.year?.toString(), TmdbImages.poster(series.posterPath), onClick = { onOpenSeries(series) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Library folder

@Composable
fun DiscoveringScreen() {
    Box(Modifier.fillMaxSize().background(DesktopColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DesktopColors.Accent)
            Spacer(Modifier.height(16.dp))
            Text("Looking for your \"Streaming\" folder…", color = DesktopColors.TextPrimary, fontSize = 16.sp)
        }
    }
}
