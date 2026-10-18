package com.drivecinema.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.model.FolderPickerSections
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

@Composable
fun HomeScreen(
    content: HomeContent,
    library: LibraryState,
    account: AccountInfo?,
    onSearch: () -> Unit,
    onChangeFolder: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: (() -> Unit)?,
    onOpenGuide: () -> Unit,
    onLocalFolders: () -> Unit,
    onOpenMovie: (MediaItemEntity) -> Unit,
    onOpenSeries: (SeriesEntity) -> Unit,
    onOpenContinue: (ContinueWatchingItem) -> Unit
) {
    val ready = library as? LibraryState.Ready
    Column(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        TopBar(
            ready = ready,
            account = account,
            onSearch = onSearch,
            menu = AccountMenuActions(
                onChangeFolder = onChangeFolder.takeIf { ready != null && !ready.isSyncing },
                onRefresh = onRefresh.takeIf { ready != null && !ready.isSyncing },
                onLocalFolders = onLocalFolders,
                onOpenGuide = onOpenGuide,
                onSignOut = onSignOut
            )
        )
        ready?.message?.let { Banner(it) }

        if (content.isEmpty) {
            EmptyLibrary(ready)
            return@Column
        }

        val gridState = rememberLazyGridState()
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(POSTER_WIDTH),
                state = gridState,
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (content.continueWatching.isNotEmpty()) {
                    sectionHeader("Continue Watching")
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(content.continueWatching, key = { it.id }) { item ->
                                ContinueCard(item, onClick = { onOpenContinue(item) })
                            }
                        }
                    }
                }
                if (content.movies.isNotEmpty()) {
                    sectionHeader("Films")
                    items(content.movies, key = { "m-${it.id}" }) { movie ->
                        PosterCard(movie.title, movie.year, TmdbImages.poster(movie.posterPath)) { onOpenMovie(movie) }
                    }
                }
                if (content.series.isNotEmpty()) {
                    sectionHeader("Series")
                    items(content.series, key = { "s-${it.id}" }) { series ->
                        PosterCard(series.title, series.year, TmdbImages.poster(series.posterPath)) { onOpenSeries(series) }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(gridState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

private val POSTER_WIDTH = 160.dp

private fun LazyGridScope.sectionHeader(title: String) {
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
    val onOpenGuide: () -> Unit,
    val onSignOut: (() -> Unit)?
)

@Composable
private fun TopBar(
    ready: LibraryState.Ready?,
    account: AccountInfo?,
    onSearch: () -> Unit,
    menu: AccountMenuActions
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 18.dp)
    ) {
        Text("DriveCinema", color = DesktopColors.Accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
        ready?.let { FolderChip(it) }
        Spacer(Modifier.weight(1f))
        SearchPill(onSearch)
        if (account != null) {
            AccountPill(account, menu)
        } else {
            PillButton(onClick = menu.onOpenGuide) {
                Text("Setup guide", color = DesktopColors.TextPrimary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FolderChip(ready: LibraryState.Ready) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DesktopColors.Surface)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        if (ready.isSyncing) {
            CircularProgressIndicator(color = DesktopColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        } else {
            Text("📁", fontSize = 13.sp)
        }
        Text(
            if (ready.isSyncing) "${ready.folderName} · syncing…" else ready.folderName,
            color = DesktopColors.TextSecondary,
            fontSize = 13.sp
        )
    }
}

/** Rounded, borderless button with a subtle hover highlight. */
@Composable
private fun PillButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (hovered) DesktopColors.SurfaceRaised else DesktopColors.Surface)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) { content() }
}

@Composable
private fun SearchPill(onSearch: () -> Unit) {
    PillButton(onClick = onSearch) {
        Text("🔍", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Text("Search films and series", color = DesktopColors.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.width(24.dp))
        Text(
            "Ctrl+F",
            color = DesktopColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .border(1.dp, DesktopColors.SurfaceRaised, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AccountPill(account: AccountInfo, menu: AccountMenuActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (hovered || open) DesktopColors.SurfaceRaised else DesktopColors.Surface)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { open = true }
                .padding(start = 5.dp, end = 16.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Avatar(account)
            Column {
                Text(account.name, color = DesktopColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(account.email, color = DesktopColors.TextSecondary, fontSize = 11.sp)
            }
            Text("▾", color = DesktopColors.TextSecondary, fontSize = 12.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = DesktopColors.Surface
        ) {
            fun pick(action: () -> Unit) { open = false; action() }
            menu.onChangeFolder?.let { MenuEntry("📁", "Change library folder") { pick(it) } }
            menu.onRefresh?.let { MenuEntry("↻", "Refresh library") { pick(it) } }
            MenuEntry("💾", "Local folders") { pick(menu.onLocalFolders) }
            MenuEntry("📖", "Setup guide") { pick(menu.onOpenGuide) }
            menu.onSignOut?.let {
                HorizontalDivider(color = DesktopColors.SurfaceRaised, modifier = Modifier.padding(vertical = 4.dp))
                MenuEntry("⎋", "Sign out", color = DesktopColors.Accent) { pick(it) }
            }
        }
    }
}

@Composable
private fun MenuEntry(icon: String, label: String, color: Color = DesktopColors.TextPrimary, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = color, fontSize = 14.sp) },
        leadingIcon = { Text(icon, color = color, fontSize = 15.sp) },
        onClick = onClick,
        modifier = Modifier.width(240.dp).pointerHoverIcon(PointerIcon.Hand)
    )
}

@Composable
private fun Avatar(account: AccountInfo) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(DesktopColors.Focus),
        contentAlignment = Alignment.Center
    ) {
        Text(account.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        account.photoUrl?.let { RemoteImage(it, account.name, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun Banner(message: String) {
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

@Composable
private fun PosterCard(title: String, year: Int?, posterUrl: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        Modifier
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
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        year?.let { Text(it.toString(), color = DesktopColors.TextSecondary, fontSize = 12.sp) }
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
            BrowseModels.formatRuntime(movie.runtimeSeconds),
            movie.voteAverage?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) }
        ),
        genres = movie.genres,
        overview = movie.overview,
        onBack = onBack
    ) {
        val resume = details.resumePositionMs
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onPlay(false) },
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)
            ) {
                Text(if (resume != null) "▶  Resume from ${PlaybackTimeUtils.formatTime(resume)}" else "▶  Play", fontWeight = FontWeight.Bold)
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
    onBack: () -> Unit
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
            "${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}",
            series.voteAverage?.takeIf { it > 0 }?.let { "★ " + String.format(Locale.US, "%.1f", it) }
        ),
        genres = series.genres,
        overview = series.overview,
        onBack = onBack
    ) {
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
                EpisodeRow(episode, details.progress[episode.id], onPlay = { onPlayEpisode(episode) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: MediaItemEntity, progress: PlaybackProgressEntity?, onPlay: () -> Unit) {
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
            modifier = Modifier.width(48.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                episode.title,
                color = if (watched) DesktopColors.TextSecondary else DesktopColors.TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (watchLabel != null) {
                Text(
                    watchLabel,
                    color = if (watched) Color(0xFF66BB6A) else DesktopColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
        Text("▶", color = DesktopColors.TextPrimary, fontSize = 18.sp)
    }
}

@Composable
private fun DetailsLayout(
    title: String,
    backdropUrl: String?,
    posterUrl: String?,
    facts: List<String>,
    genres: String?,
    overview: String?,
    onBack: () -> Unit,
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
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(facts.joinToString("   ·   "), color = DesktopColors.TextSecondary, fontSize = 15.sp)
                    }
                    genres?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = DesktopColors.TextSecondary, fontSize = 13.sp)
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
        OutlinedButton(
            onClick = onBack,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x990C0D10))
        ) { Text("←  Back  (Esc)", color = DesktopColors.TextPrimary) }
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
            OutlinedButton(onClick = onBack) { Text("←  Back", color = DesktopColors.TextPrimary) }
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
                        PosterCard(movie.title, movie.year, TmdbImages.poster(movie.posterPath)) { onOpenMovie(movie) }
                    }
                }
                if (results.series.isNotEmpty()) {
                    sectionHeader("Series")
                    items(results.series, key = { "s-${it.id}" }) { series ->
                        PosterCard(series.title, series.year, TmdbImages.poster(series.posterPath)) { onOpenSeries(series) }
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

@Composable
fun FolderPickerScreen(
    state: LibraryState.ChoosingFolder,
    onChoose: (DriveFolder) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(DesktopColors.Background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.width(640.dp).padding(vertical = 40.dp).verticalScroll(scroll)) {
            Text("Choose your library folder", color = DesktopColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pick the Drive folder that holds \"Films\" and/or \"Series\". A folder named \"Streaming\" is recommended.",
                color = DesktopColors.TextSecondary,
                fontSize = 14.sp
            )
            state.errorMessage?.let {
                Spacer(Modifier.height(16.dp))
                Banner(it)
            }
            Spacer(Modifier.height(20.dp))
            when {
                state.switchingTo != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = DesktopColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Reading \"${state.switchingTo}\"… this can take a moment for big libraries.", color = DesktopColors.TextPrimary)
                }
                state.isLoading -> CircularProgressIndicator(color = DesktopColors.Accent)
                state.sections.isEmpty -> {
                    Text("No folders found in your Drive.", color = DesktopColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRetry) { Text("Try again", color = DesktopColors.TextPrimary) }
                }
                else -> {
                    FolderSection("YOUR DRIVE", state.sections.mine, onChoose)
                    FolderSection("SHARED WITH YOU", state.sections.shared, onChoose)
                }
            }
            if (state.canCancel && state.switchingTo == null) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel", color = DesktopColors.TextPrimary) }
            }
        }
    }
}

@Composable
private fun FolderSection(title: String, folders: List<DriveFolder>, onChoose: (DriveFolder) -> Unit) {
    if (folders.isEmpty()) return
    Text(title, color = DesktopColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        folders.forEach { folder ->
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
                    .clickable { onChoose(folder) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("📁", fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, color = DesktopColors.TextPrimary, fontSize = 15.sp)
                    if (!folder.ownedByMe && folder.ownerName != null) {
                        Text("Shared by ${folder.ownerName}", color = DesktopColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                if (FolderPickerSections.isRecommended(folder)) {
                    Text(
                        "Recommended",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.background(DesktopColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
