package com.drivecinema.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.ui.details.EpisodeItem
import com.drivecinema.app.ui.details.DetailsViewModel
import com.drivecinema.app.ui.details.MovieDetailsScreen
import com.drivecinema.app.ui.details.SeriesDetailsScreen
import com.drivecinema.app.ui.details.TvMovieDetailsScreen
import com.drivecinema.app.ui.details.TvSeriesDetailsScreen
import com.drivecinema.app.ui.mobile.grid.FullLibraryGridScreen
import com.drivecinema.app.ui.mobile.home.MobileHomeScreen
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState
import com.drivecinema.app.ui.mobile.navigation.MobileBottomNav
import com.drivecinema.app.ui.mobile.navigation.MobileTab
import com.drivecinema.app.ui.mobile.search.SearchScreen
import com.drivecinema.app.ui.perf.PerfScreenTimer
import com.drivecinema.app.ui.search.SearchViewModel
import com.drivecinema.app.ui.help.HelpEntry
import com.drivecinema.app.ui.naming.NamingCheckEntry
import com.drivecinema.app.ui.naming.NamingDeps
import com.drivecinema.app.ui.naming.NamingHelperEntry
import com.drivecinema.app.ui.settings.LocalFoldersEntry
import com.drivecinema.app.ui.settings.PcsActions
import com.drivecinema.app.ui.settings.PcsEntry
import com.drivecinema.app.ui.settings.SettingsActions
import com.drivecinema.app.ui.settings.SettingsEntry
import com.drivecinema.app.ui.settings.SettingsUiState
import com.drivecinema.app.ui.setup.SetupActions
import com.drivecinema.app.ui.setup.SetupEntry
import com.drivecinema.app.ui.setup.SetupUiState
import com.drivecinema.app.ui.tv.grid.TvCollectionGridScreen
import com.drivecinema.app.ui.tv.grid.TvGridItem
import com.drivecinema.app.ui.tv.home.TvHomeScreen
import com.drivecinema.app.ui.tv.search.TvSearchScreen
import kotlinx.coroutines.flow.StateFlow

private val ScreenBackground = Color(0xFF0C0D10)

/** What the library screens ask of the activity: playback and its dialogs. */
class LibraryActions(
    /** Plays a catalog title (film or episode) from its best copy. */
    val play: (mediaItemId: String, title: String, startPositionMs: Long) -> Unit,
    /** The phone's "Pair with TV" button. */
    val pairTv: () -> Unit,
    /** Milestone D: sends a title to a linked TV (phone only). */
    val playOnTv: (mediaItemId: String, title: String) -> Unit = { _, _ -> },
    /** Milestone D: true when "Play on TV" should show (a phone with a linked TV); read in composition, so backed by state. */
    val canPlayOnTv: () -> Boolean = { false },
    /** Home has content for the first time (Task M.0's "Fully drawn"). */
    val onHomeReady: () -> Unit = {}
)

/**
 * Task M.3 (F-38): the phone and TV screens, shown from [navigator]'s back stack. Back pops to the previous screen.
 * Each entry keeps its saveable state (scroll positions, the TV card to refocus) while it is under another screen,
 * and the details screens get their own ViewModel, cleared when they are popped.
 *
 * Every screen is wired here (a step toward F-08); the activity keeps the flows behind [actions], [setupActions] and
 * [settingsActions] and [pcsActions] (sign-in, sync, playback, settings, PCs) and its dialogs. Home's profile chip
 * opens Settings (Task P.2).
 */
@Composable
fun AppNavDisplay(
    navigator: AppNavigator,
    isTv: Boolean,
    homeState: StateFlow<MobileHomeUiState>,
    searchViewModel: SearchViewModel,
    newDetailsViewModel: () -> DetailsViewModel,
    actions: LibraryActions,
    setup: SetupUiState,
    setupActions: SetupActions,
    settings: SettingsUiState,
    settingsActions: SettingsActions,
    pcsActions: PcsActions,
    modifier: Modifier = Modifier,
    /** Milestone R: TMDB and the Drive folder reader for the naming helper and the Naming check (null: not offered). */
    naming: NamingDeps? = null
) {
    var mobileTab by rememberSaveable { mutableStateOf(MobileTab.HOME) }

    NavDisplay(
        backStack = navigator.backStack,
        modifier = modifier.fillMaxSize().background(ScreenBackground),
        onBack = { navigator.back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        // Screens switch at once, as before Navigation 3 (no cross-fade on the TV's slow main thread).
        transitionSpec = { NoTransition },
        popTransitionSpec = { NoTransition },
        predictivePopTransitionSpec = { NoTransition },
        entryProvider = entryProvider {
            entry<Screen.Home> {
                val home by homeState.collectAsStateWithLifecycle()
                PerfScreenTimer("home", key = Unit, ready = !home.isLoading, onReady = actions.onHomeReady)
                val playContinue = { item: ContinueWatchingItem ->
                    actions.play(item.id, ContinueWatching.playerTitle(item), item.positionMs)
                }
                if (isTv) {
                    TvHomeScreen(
                        state = home,
                        onMovieClick = { movie -> navigator.open(Screen.MovieDetails(movie.id)) },
                        onSeriesClick = { series -> navigator.open(Screen.SeriesDetails(series.id)) },
                        onContinueWatchingClick = playContinue,
                        onFilmsHeaderClick = { navigator.open(Screen.Grid(GridKind.FILMS)) },
                        onSeriesHeaderClick = { navigator.open(Screen.Grid(GridKind.SERIES)) },
                        onSearchClick = { navigator.open(Screen.Search) },
                        onAccountClick = { navigator.open(Screen.Settings) }
                    )
                } else {
                    if (mobileTab != MobileTab.HOME) {
                        BackHandler { mobileTab = MobileTab.HOME }
                    }
                    MobileHome(
                        tab = mobileTab,
                        onTabSelected = { mobileTab = it },
                        home = home,
                        searchViewModel = searchViewModel,
                        navigator = navigator,
                        onContinueWatchingClick = playContinue,
                        actions = actions
                    )
                }
            }

            entry<Screen.Grid> { key ->
                val home by homeState.collectAsStateWithLifecycle()
                val items = remember(home.movies, home.series, key.kind) {
                    when (key.kind) {
                        GridKind.FILMS -> home.movies.map { TvGridItem(it.id, it.title, it.posterPath, it.backdropPath) }
                        GridKind.SERIES -> home.series.map { TvGridItem(it.id, it.title, it.posterPath, it.backdropPath) }
                    }
                }
                PerfScreenTimer("grid.${key.kind.title.lowercase()}", key = key.kind, ready = true)
                TvCollectionGridScreen(
                    title = key.kind.title,
                    items = items,
                    onItemClick = { item ->
                        navigator.open(
                            when (key.kind) {
                                GridKind.FILMS -> Screen.MovieDetails(item.id)
                                GridKind.SERIES -> Screen.SeriesDetails(item.id)
                            }
                        )
                    },
                    onBackClick = { navigator.back() }
                )
            }

            entry<Screen.Search> {
                val search by searchViewModel.uiState.collectAsStateWithLifecycle()
                TvSearchScreen(
                    state = search,
                    onQueryChange = searchViewModel::onQueryChanged,
                    onClearQuery = searchViewModel::clearQuery,
                    onMovieClick = { movie -> navigator.open(Screen.MovieDetails(movie.id)) },
                    onSeriesClick = { series -> navigator.open(Screen.SeriesDetails(series.id)) },
                    onBackClick = { navigator.back() }
                )
            }

            entry<Screen.MovieDetails> { key ->
                MovieDetailsEntry(key.id, isTv, homeState, newDetailsViewModel, actions, onBack = { navigator.back() })
            }

            entry<Screen.SeriesDetails> { key ->
                SeriesDetailsEntry(
                    key.id, isTv, homeState, newDetailsViewModel, actions,
                    onBack = { navigator.back() },
                    onNamingCheck = if (naming != null) {
                        { navigator.open(Screen.NamingCheck(key.id)) }
                    } else null
                )
            }

            entry<Screen.SignIn> { SetupEntry(it, setup, setupActions, isTv) }
            entry<Screen.FolderPicker> { SetupEntry(it, setup, setupActions, isTv) }
            entry<Screen.SetupGuide> {
                HelpEntry(null, isTv, open = { id -> navigator.open(Screen.HelpTopic(id)) }, show = {}, back = { navigator.back() })
            }
            entry<Screen.HelpTopic> {
                HelpEntry(
                    it.id,
                    isTv,
                    open = { id -> navigator.open(Screen.HelpTopic(id)) },
                    show = { id -> navigator.replace(Screen.HelpTopic(id)) },
                    back = { navigator.back() }
                )
            }
            entry<Screen.Duplicates> { SetupEntry(it, setup, setupActions, isTv) }
            entry<Screen.Settings> { SettingsEntry(isTv, homeState, settings, settingsActions) }
            entry<Screen.LocalFolders> { LocalFoldersEntry(settings, settingsActions) }
            entry<Screen.Pcs> { PcsEntry(isTv, settings.pcs, pcsActions) }
            entry<Screen.NamingHelper> {
                if (naming != null) NamingHelperEntry(naming, isTv, back = { navigator.back() })
            }
            entry<Screen.NamingCheck> { key ->
                val title = remember(key.seriesId) { homeState.value.series.find { it.id == key.seriesId }?.title.orEmpty() }
                if (naming != null) {
                    NamingCheckEntry(
                        key.seriesId,
                        title,
                        naming,
                        isTv,
                        openHelper = { navigator.replace(Screen.NamingHelper) },
                        back = { navigator.back() }
                    )
                }
            }
        }
    )
}

private val NoTransition: ContentTransform = EnterTransition.None togetherWith ExitTransition.None

/** The phone's Home with its bottom tabs (Home, Library, Search); the tab survives details and rotation. */
@Composable
private fun MobileHome(
    tab: MobileTab,
    onTabSelected: (MobileTab) -> Unit,
    home: MobileHomeUiState,
    searchViewModel: SearchViewModel,
    navigator: AppNavigator,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    actions: LibraryActions
) {
    Scaffold(
        bottomBar = { MobileBottomNav(selectedTab = tab, onTabSelected = onTabSelected) },
        containerColor = ScreenBackground
    ) { innerPadding ->
        when (tab) {
            MobileTab.HOME -> MobileHomeScreen(
                state = home,
                onMovieClick = { movie -> navigator.open(Screen.MovieDetails(movie.id)) },
                onSeriesClick = { series -> navigator.open(Screen.SeriesDetails(series.id)) },
                onContinueWatchingClick = onContinueWatchingClick,
                onPairTvClick = actions.pairTv,
                onAccountClick = { navigator.open(Screen.Settings) },
                modifier = Modifier.padding(innerPadding),
                onContinueWatchingPlayOnTv = if (actions.canPlayOnTv()) {
                    { item -> actions.playOnTv(item.id, ContinueWatching.playerTitle(item)) }
                } else null,
                tvLinked = actions.canPlayOnTv()
            )
            MobileTab.LIBRARY -> FullLibraryGridScreen(
                movies = home.movies,
                series = home.series,
                onMovieClick = { movie -> navigator.open(Screen.MovieDetails(movie.id)) },
                onSeriesClick = { series -> navigator.open(Screen.SeriesDetails(series.id)) },
                modifier = Modifier.padding(innerPadding),
                filmStates = home.filmStates,
                seriesStates = home.seriesStates
            )
            MobileTab.SEARCH -> {
                val search by searchViewModel.uiState.collectAsStateWithLifecycle()
                SearchScreen(
                    state = search,
                    onQueryChange = searchViewModel::onQueryChanged,
                    onClearQuery = searchViewModel::clearQuery,
                    onMovieClick = { movie -> navigator.open(Screen.MovieDetails(movie.id)) },
                    onSeriesClick = { series -> navigator.open(Screen.SeriesDetails(series.id)) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

/**
 * A film's details, the TV's layout or the phone's (Task P.5). Its [DetailsViewModel] belongs to this back stack
 * entry: it survives a rotation and is cleared (stopping its database collectors, F-35) when the entry is popped.
 */
@Composable
private fun MovieDetailsEntry(
    id: String,
    isTv: Boolean,
    homeState: StateFlow<MobileHomeUiState>,
    newViewModel: () -> DetailsViewModel,
    actions: LibraryActions,
    onBack: () -> Unit
) {
    val viewModel = viewModel { newViewModel() }
    LaunchedEffect(viewModel, id) { viewModel.loadMovie(id) }
    val state by viewModel.movieState.collectAsStateWithLifecycle()
    // The card's film, already in memory, shows at once; the database read replaces it.
    val listed = remember(id) { homeState.value.movies.find { it.id == id } }
    val movie = state.movie ?: listed
    if (movie == null) {
        Box(Modifier.fillMaxSize().background(ScreenBackground))
    } else {
        PerfScreenTimer("movieDetails", key = id, ready = state.movie?.id == id && !state.isLoading)
        val play = { startPos: Long -> actions.play(id, movie.title, startPos) }
        if (isTv) {
            TvMovieDetailsScreen(movie = movie, progress = state.progress, infoLines = state.infoLines, onPlayClick = play)
        } else {
            MovieDetailsScreen(
                movie = movie,
                progress = state.progress,
                infoLines = state.infoLines,
                onPlayClick = play,
                onBackClick = onBack,
                onPlayOnTvClick = if (actions.canPlayOnTv()) {
                    { actions.playOnTv(id, movie.title) }
                } else null
            )
        }
    }
}

/** A series' details, the TV's layout or the phone's, with an entry-scoped [DetailsViewModel] like [MovieDetailsEntry]. */
@Composable
private fun SeriesDetailsEntry(
    id: String,
    isTv: Boolean,
    homeState: StateFlow<MobileHomeUiState>,
    newViewModel: () -> DetailsViewModel,
    actions: LibraryActions,
    onBack: () -> Unit,
    onNamingCheck: (() -> Unit)? = null
) {
    val viewModel = viewModel { newViewModel() }
    LaunchedEffect(viewModel, id) { viewModel.loadSeries(id) }
    val state by viewModel.seriesState.collectAsStateWithLifecycle()
    val listed = remember(id) { homeState.value.series.find { it.id == id } }
    val series = state.series ?: listed
    if (series == null) {
        Box(Modifier.fillMaxSize().background(ScreenBackground))
    } else {
        PerfScreenTimer("seriesDetails", key = id, ready = state.series?.id == id && !state.isLoading)
        val playEpisode = { episode: EpisodeItem ->
            val fileId = episode.id
            if (!fileId.isNullOrEmpty()) {
                actions.play(fileId, episodeTitle(series.title, episode), episode.positionMs)
            }
        }
        if (isTv) {
            TvSeriesDetailsScreen(
                series = series,
                state = state,
                onSeasonSelect = viewModel::selectSeason,
                onEpisodeClick = playEpisode,
                onNamingCheckClick = onNamingCheck
            )
        } else {
            SeriesDetailsScreen(
                series = series,
                state = state,
                onSeasonSelect = viewModel::selectSeason,
                onEpisodeClick = playEpisode,
                onBackClick = onBack,
                onPlayOnTvClick = if (actions.canPlayOnTv()) {
                    { episode -> episode.id?.takeIf { it.isNotEmpty() }?.let { actions.playOnTv(it, episodeTitle(series.title, episode)) } }
                } else null,
                onNamingCheckClick = onNamingCheck
            )
        }
    }
}

/** The player's title for an episode: "Show - S1E2 Name". */
private fun episodeTitle(seriesTitle: String, episode: EpisodeItem): String {
    val code = "S${episode.season}E${episode.episode}"
    return if (episode.title.isNotEmpty()) "$seriesTitle - $code ${episode.title}" else "$seriesTitle - $code"
}
