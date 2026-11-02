package com.drivecinema.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.details.DetailsViewModel
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState
import com.drivecinema.app.ui.search.SearchViewModel
import com.drivecinema.app.ui.settings.PcsActions
import com.drivecinema.app.ui.settings.SettingsActions
import com.drivecinema.app.ui.settings.SettingsUiState
import com.drivecinema.app.ui.setup.SetupActions
import com.drivecinema.app.ui.setup.SetupUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.lang.reflect.Proxy

/** Task M.3: a small library and the real [AppNavDisplay] over it, for the navigation tests (phone and TV). */
object NavTestLibrary {

    /** A–Z order is the grid's order: Arrival, Blade Runner, Dune, … */
    val movies = listOf("Arrival", "Blade Runner", "Dune", "Heat", "Inception", "Jaws", "Up").mapIndexed { i, title ->
        MediaItemEntity(id = "m$i", type = MediaType.MOVIE, title = title, parentFolderId = "films")
    }
    val series = listOf("Dark", "Severance", "The Wire").mapIndexed { i, title -> SeriesEntity(id = "s$i", title = title) }

    fun movie(title: String): MediaItemEntity = movies.first { it.title == title }
    fun series(title: String): SeriesEntity = series.first { it.title == title }

    fun mediaDao(): MediaDao {
        val unused = Proxy.newProxyInstance(MediaDao::class.java.classLoader, arrayOf(MediaDao::class.java)) { _, method, _ ->
            error("not used: MediaDao.${method.name}")
        } as MediaDao
        return object : MediaDao by unused {
            override fun getAllMoviesFlow(): Flow<List<MediaItemEntity>> = flowOf(movies)
            override fun getAllSeriesFlow(): Flow<List<SeriesEntity>> = flowOf(series)
            override fun searchMoviesFlow(query: String): Flow<List<MediaItemEntity>> =
                flowOf(movies.filter { it.title.contains(query, ignoreCase = true) })
            override fun searchSeriesFlow(query: String): Flow<List<SeriesEntity>> =
                flowOf(series.filter { it.title.contains(query, ignoreCase = true) })
            override suspend fun getMediaItemById(id: String): MediaItemEntity? = movies.find { it.id == id }
            override suspend fun getSeriesById(id: String): SeriesEntity? = series.find { it.id == id }
            override suspend fun getEpisodesForSeries(seriesId: String): List<MediaItemEntity> = emptyList()
        }
    }

    fun homeState() = MutableStateFlow(MobileHomeUiState(movies = movies, series = series))

    /** The app's screens as [AppNavDisplay] shows them, with [navigator]'s back stack. */
    @Composable
    fun Screens(
        navigator: AppNavigator,
        isTv: Boolean,
        searchViewModel: SearchViewModel,
        newDetailsViewModel: () -> DetailsViewModel = { DetailsViewModel(mediaDao()) },
        setup: SetupUiState = remember { SetupUiState() },
        setupActions: SetupActions = remember { setupActions(navigator) },
        settings: SettingsUiState = remember { SettingsUiState() },
        settingsActions: SettingsActions = remember { settingsActions(navigator) },
        pcsActions: PcsActions = remember { pcsActions(navigator) }
    ) {
        AppNavDisplay(
            navigator = navigator,
            isTv = isTv,
            homeState = remember { homeState() },
            searchViewModel = searchViewModel,
            newDetailsViewModel = newDetailsViewModel,
            actions = LibraryActions(play = { _, _, _ -> }, pairTv = {}),
            setup = setup,
            setupActions = setupActions,
            settings = settings,
            settingsActions = settingsActions,
            pcsActions = pcsActions
        )
    }

    /** Task P.2: Settings' actions; the ones that open screens open them on [navigator]. */
    fun settingsActions(navigator: AppNavigator) = SettingsActions(
        refreshLibrary = {}, switchLibraryFolder = { navigator.open(Screen.FolderPicker) },
        reviewDuplicates = { navigator.open(Screen.Duplicates) }, openLocalFolders = { navigator.open(Screen.LocalFolders) },
        addLocalFolder = {}, removeLocalFolder = {}, openPcs = { navigator.open(Screen.Pcs) }, pairTv = {}, setSkipIntro = {},
        openHelp = { navigator.open(Screen.SetupGuide) },
        openAbout = { navigator.open(Screen.HelpTopic(com.drivecinema.app.ui.setup.SetupGuideContent.ABOUT)) },
        onOpen = {}, signOut = {}, back = { navigator.back() }
    )

    /** Task P.3: My PCs' actions; they change nothing, Back pops [navigator]. */
    fun pcsActions(navigator: AppNavigator) = PcsActions(
        search = {}, pair = { _, _, _ -> }, setVpnAddress = { _, _ -> }, remove = {}, checkNow = {}, onOpen = {},
        back = { navigator.back() }
    )

    fun setupActions(navigator: AppNavigator, cancelLibrarySwitch: () -> Unit = {}) = SetupActions(
        signIn = {}, pair = {}, openGuide = { navigator.open(Screen.SetupGuide) },
        openHelpTopic = { navigator.open(Screen.HelpTopic(it)) }, chooseFolder = {}, reloadFolders = {},
        signOut = {}, cancelLibrarySwitch = cancelLibrarySwitch, currentFolderId = { null },
        confirmSameTitle = { _, _ -> }, keepSeparate = {}, back = { navigator.back() }
    )
}
