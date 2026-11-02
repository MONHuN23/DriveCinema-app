package com.drivecinema.app.ui.naming

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.TmdbMetadata
import com.drivecinema.app.core.metadata.TmdbSeriesResult
import com.drivecinema.app.core.naming.NamingGuideState
import com.drivecinema.app.core.naming.NamingPick
import com.drivecinema.app.core.naming.NamingSearchKind
import com.drivecinema.app.core.naming.NamingSeasonSection
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcFocusRoomScrolling
import com.drivecinema.app.ui.design.DcTextField
import com.drivecinema.app.ui.design.DcTopBar
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.tv.TvInitialFocus
import com.drivecinema.app.ui.tv.rememberTvReturnFocus

/** What the naming helper's buttons do ([com.drivecinema.app.core.naming.NamingGuideModel]'s calls). */
class NamingHelperActions(
    val setQuery: (String) -> Unit,
    val setKind: (NamingSearchKind) -> Unit,
    val search: () -> Unit,
    val pickSeries: (TmdbSeriesResult) -> Unit,
    val pickFilm: (TmdbMetadata) -> Unit,
    val closePick: () -> Unit,
    val toggleSeason: (Int) -> Unit,
    val setLayout: (season: Int, NamingGuide.Layout) -> Unit,
    val retrySeason: (Int) -> Unit,
    /** The phone's copy buttons; the TV shows none (view-only, R.0). */
    val copy: (String) -> Unit,
    val back: () -> Unit
)

/** The TV names panel's width, beside the results (UI task ui/tv-browse-naming). */
private val TvPanelWidth = 328.dp

/** The TV picked title's poster column, beside its seasons. */
private val TvPickedSideWidth = 270.dp

/**
 * Task R.2: the naming helper (Settings → Naming helper), on the phone and the TV: search TMDB for a series or a film,
 * pick one, and see the folder and file names the app recognises (design R.0 §3). Back from a picked title returns
 * to the results. The search runs as you type (the shared model's pause), or at once on Search. The TV is
 * view-only: no copy buttons; every row can take the focus so the remote scrolls through a season, the search field
 * starts focused, a pick focuses its first season, and Back focuses the picked result again.
 *
 * The TV's layout (UI task ui/tv-browse-naming): the results on the left with a names panel for the focused result
 * on the right ([TvNamingPanel]); a picked title shows its poster and folder on the left, its seasons on the right.
 */
@Composable
fun NamingHelperScreen(state: NamingGuideState, actions: NamingHelperActions, isTv: Boolean, modifier: Modifier = Modifier) {
    val picked = state.picked
    BackHandler(enabled = picked != null) { actions.closePick() }

    val fieldFocus = remember { FocusRequester() }
    val firstPickedFocus = remember { FocusRequester() }
    val returnFocus = rememberTvReturnFocus()
    val resultsList = rememberLazyListState()
    val pickKey = when (picked) {
        is NamingPick.Series -> "series_${picked.result.metadata.tmdbId}"
        is NamingPick.Film -> "film_${picked.film.tmdbId}"
        null -> null
    }
    val pickedList = remember(pickKey) { LazyListState() }
    val preview = remember { TvNamingPreviewState() }

    if (isTv) {
        TvInitialFocus(returnFocus) { runCatching { fieldFocus.requestFocus() } }
        val pickedReady = when (picked) {
            is NamingPick.Series -> !picked.isLoading
            is NamingPick.Film -> !picked.isLoading
            null -> false
        }
        LaunchedEffect(pickKey, pickedReady) {
            if (pickKey == null) {
                // Back from a pick: the result it came from (a frame later, once the list is placed again).
                withFrameNanos { }
                returnFocus.restore()
            } else if (pickedReady) {
                repeat(5) {
                    if (runCatching { firstPickedFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                    withFrameNanos { }
                }
            }
        }
    }

    val list: @Composable (Modifier, PaddingValues) -> Unit = { listModifier, padding ->
        DcFocusRoomScrolling {
            LazyColumn(
                state = if (picked == null) resultsList else pickedList,
                modifier = listModifier.testTag("naming_helper_list"),
                contentPadding = padding
            ) {
                when (picked) {
                    null -> searchItems(
                        state, actions, isTv, fieldFocus, returnFocus::opened, returnFocus::modifierFor, preview::onFocused
                    )
                    is NamingPick.Series -> seriesItems(picked, actions, isTv, firstPickedFocus)
                    is NamingPick.Film -> filmItems(picked, actions, isTv, firstPickedFocus)
                }
            }
        }
    }

    if (!isTv) {
        Column(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
            DcTopBar(NamingTexts.HELPER_TITLE, onBack = { if (picked != null) actions.closePick() else actions.back() })
            list(Modifier.fillMaxSize(), PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp))
        }
        return
    }

    val pad = 48.dp
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(DcColors.Canvas)
            .padding(start = pad - DcFocusRoom, end = pad),
        horizontalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        if (picked != null) TvPickedSide(picked, Modifier.width(TvPickedSideWidth).padding(start = DcFocusRoom, top = 30.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (picked == null) {
                Text(
                    NamingTexts.HELPER_TITLE,
                    style = DcTheme.type.headline,
                    color = DcColors.Text1,
                    modifier = Modifier.padding(start = DcFocusRoom, top = 30.dp, bottom = 6.dp)
                )
            }
            list(
                Modifier.fillMaxSize(),
                PaddingValues(start = DcFocusRoom, end = DcFocusRoom, top = if (picked == null) DcFocusRoom else 24.dp, bottom = 32.dp)
            )
        }
        if (picked == null) TvNamingPanel(state, preview, Modifier.width(TvPanelWidth).padding(top = 30.dp, bottom = 28.dp))
    }
}

private fun LazyListScope.searchItems(
    state: NamingGuideState,
    actions: NamingHelperActions,
    isTv: Boolean,
    fieldFocus: FocusRequester,
    opened: (String) -> Unit,
    returnModifier: (String) -> Modifier,
    focused: (String) -> Unit
) {
    item(key = "intro") {
        val keyboard = LocalSoftwareKeyboardController.current
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 760.dp)) {
            if (!state.tmdbAvailable) DcBanner(NamingTexts.NEEDS_TMDB, kind = DcBannerKind.Warning, modifier = Modifier.testTag("naming_needs_tmdb"))
            // The TV's names panel explains the names instead.
            if (!isTv) Text(NamingTexts.HELPER_INTRO, style = DcTheme.type.body, color = DcColors.Text2)
            NamingChips(
                options = NamingSearchKind.entries,
                selected = state.kind,
                label = { if (it == NamingSearchKind.SERIES) NamingTexts.SERIES else NamingTexts.FILMS },
                onSelect = actions.setKind,
                tagPrefix = "naming_kind"
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DcTextField(
                    value = state.query,
                    onValueChange = actions.setQuery,
                    label = NamingTexts.searchLabel(state.kind),
                    imeAction = ImeAction.Search,
                    onImeAction = {
                        keyboard?.hide()
                        actions.search()
                    },
                    modifier = Modifier.weight(1f).focusRequester(fieldFocus).testTag("naming_query")
                )
                DcButton(
                    NamingTexts.SEARCH,
                    onClick = actions.search,
                    icon = AppIcons.Search,
                    // Search as you type makes the button a fallback on the TV: the chips keep the one accent.
                    kind = if (isTv) DcButtonKind.Secondary else DcButtonKind.Primary,
                    enabled = state.tmdbAvailable,
                    loading = state.isSearching,
                    modifier = Modifier.testTag("naming_search")
                )
            }
            if (state.searchFailed) DcBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
            if (isTv) SearchStatus(state)
        }
    }
    when (state.kind) {
        NamingSearchKind.SERIES -> state.seriesResults?.let { results ->
            if (results.isEmpty()) item(key = "none") { NoResults(state.query) }
            items(results, key = { "s${it.metadata.tmdbId}" }) { result ->
                val key = seriesResultKey(result.metadata.tmdbId)
                ResultRow(
                    result.metadata,
                    animated = result.animated,
                    onClick = { opened(key); actions.pickSeries(result) },
                    modifier = returnModifier(key)
                        .then(if (isTv) Modifier.onFocusChanged { if (it.isFocused) focused(key) } else Modifier)
                        .testTag("naming_$key")
                )
            }
        }
        NamingSearchKind.FILMS -> state.filmResults?.let { results ->
            if (results.isEmpty()) item(key = "none") { NoResults(state.query) }
            items(results, key = { "f${it.tmdbId}" }) { film ->
                val key = filmResultKey(film.tmdbId)
                ResultRow(
                    film,
                    animated = false,
                    onClick = { opened(key); actions.pickFilm(film) },
                    modifier = returnModifier(key)
                        .then(if (isTv) Modifier.onFocusChanged { if (it.isFocused) focused(key) } else Modifier)
                        .testTag("naming_$key")
                )
            }
        }
    }
}

/** The TV's line under the field: searching (the keyboard can cover the button's spinner), or how many results. */
@Composable
private fun SearchStatus(state: NamingGuideState) {
    val count = when (state.kind) {
        NamingSearchKind.SERIES -> state.seriesResults?.size
        NamingSearchKind.FILMS -> state.filmResults?.size
    }
    when {
        state.isSearching -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("naming_searching")
        ) {
            CircularProgressIndicator(color = DcColors.Text2, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Text(NamingTexts.SEARCHING, style = DcTheme.type.caption, color = DcColors.Text3)
        }
        count != null && count > 0 -> Text(
            NamingTexts.resultCount(count, state.query.trim()),
            style = DcTheme.type.caption,
            color = DcColors.Text3,
            modifier = Modifier.testTag("naming_result_count")
        )
    }
}

@Composable
private fun NoResults(query: String) {
    Text(NamingTexts.noResults(query.trim()), style = DcTheme.type.body, color = DcColors.Text2, modifier = Modifier.padding(vertical = 12.dp))
}

private fun LazyListScope.seriesItems(picked: NamingPick.Series, actions: NamingHelperActions, isTv: Boolean, firstFocus: FocusRequester) {
    val show = picked.result.metadata
    // On the TV the title and the folder are on the left (TvPickedSide).
    if (!isTv) {
        item(key = "title") {
            Text(
                show.title + (show.year?.let { " ($it)" } ?: ""),
                style = DcTheme.type.title,
                color = DcColors.Text1,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
    val plan = picked.plan
    when {
        picked.isLoading -> item(key = "loading") { NamingLoading(NamingTexts.LOADING) }
        plan == null -> item(key = "failed") {
            DcBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
        }
        else -> {
            if (!isTv) {
                item(key = "folder") {
                    NamingRow(
                        trailing = { CopyButton("folder name") { actions.copy(plan.folderName) } },
                        focusableOnTv = false
                    ) {
                        NamingName(NamingTexts.seriesFolderPath(plan.folderName))
                        NamingTexts.folderCheck(plan.folderFindsShow)?.let { NamingNote(it, warning = true) }
                    }
                }
            }
            picked.orderedSections.forEachIndexed { index, section ->
                val tmdbSeason = plan.seasons.firstOrNull { it.seasonNumber == section.season }
                item(key = "season_${section.season}") {
                    SeasonHeader(
                        title = NamingTexts.seasonTitle(section.season, tmdbSeason?.name),
                        supporting = NamingTexts.episodeCount(tmdbSeason?.episodeCount ?: 0),
                        isOpen = section.isOpen,
                        onToggle = { actions.toggleSeason(section.season) },
                        style = NamingRowStyle.RAISED,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .testTag("naming_season_${section.season}")
                    )
                }
                if (section.isOpen) seasonItems(plan, section, actions, isTv)
            }
        }
    }
}

private fun LazyListScope.seasonItems(plan: NamingGuide.SeriesPlan, section: NamingSeasonSection, actions: NamingHelperActions, isTv: Boolean) {
    val season = section.season
    val layouts = NamingTexts.layouts(section)
    if (layouts.size > 1) {
        item(key = "layouts_$season") {
            NamingChips(
                options = layouts,
                selected = section.table?.layout ?: section.layout,
                label = NamingTexts::layoutLabel,
                onSelect = { actions.setLayout(season, it) },
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                tagPrefix = "naming_layout_$season"
            )
        }
    }
    val table = section.table
    when {
        section.isLoading && table == null -> item(key = "loading_$season") { NamingLoading(NamingTexts.LOADING) }
        section.failed && table == null -> item(key = "failed_$season") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DcBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
                DcButton(NamingTexts.RETRY, onClick = { actions.retrySeason(season) }, kind = DcButtonKind.Secondary, small = true)
            }
        }
        table != null -> {
            if (table.seriesFolder != plan.folderName) {
                // The arc's own folder: its path, and whether the sync's search finds the show with it.
                item(key = "arcFolder_$season") {
                    NamingRow(
                        trailing = if (isTv) null else ({ CopyButton("folder name") { actions.copy(table.seriesFolder) } }),
                        focusableOnTv = false
                    ) {
                        NamingName(NamingTexts.seriesFolderPath(table.seriesFolder))
                        NamingTexts.folderCheck(table.folderFindsShow)?.let { NamingNote(it, warning = true) }
                    }
                }
            }
            items(table.rows, key = { "row_${season}_${it.episode.episodeNumber}" }) { row ->
                NamingRow(
                    modifier = Modifier.testTag("naming_row_${season}_${row.episode.episodeNumber}"),
                    style = NamingRowStyle.RAISED,
                    trailing = if (isTv || !row.readsBack) null else ({ CopyButton(row.fileName) { actions.copy(row.fileName) } })
                ) {
                    NamingLine(NamingTexts.episodeLine(row.episode))
                    if (row.readsBack) NamingName("${row.seasonFolder}/${row.fileName}") else NamingNote(NamingTexts.CANT_READ, warning = true)
                }
            }
        }
    }
}

private fun LazyListScope.filmItems(picked: NamingPick.Film, actions: NamingHelperActions, isTv: Boolean, firstFocus: FocusRequester) {
    val film = picked.film
    // On the TV the title is on the left (TvPickedSide).
    if (!isTv) {
        item(key = "title") {
            Text(
                film.title + (film.year?.let { " ($it)" } ?: ""),
                style = DcTheme.type.title,
                color = DcColors.Text1,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
    val plan = picked.plan
    if (picked.isLoading || plan == null) {
        item(key = "loading") { NamingLoading(NamingTexts.LOADING) }
    } else {
        item(key = "film") {
            NamingRow(
                modifier = Modifier.focusRequester(firstFocus).testTag("naming_film_name"),
                style = NamingRowStyle.RAISED,
                trailing = if (isTv) null else ({ CopyButton(plan.fileName) { actions.copy(plan.fileName) } })
            ) {
                NamingName(NamingTexts.filmPath(plan))
                NamingTexts.filmCheck(plan)?.let { NamingNote(it, warning = true) }
            }
        }
    }
}
