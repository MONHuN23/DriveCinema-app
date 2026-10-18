package com.drivecinema.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.sync.DriveLibrarySyncEngine
import com.drivecinema.app.core.sync.FolderQueueCrawler
import com.drivecinema.desktop.auth.DesktopOAuthConfig
import com.drivecinema.desktop.auth.DesktopSignIn
import com.drivecinema.desktop.auth.OAuthTokenClient
import com.drivecinema.desktop.auth.SessionRepository
import com.drivecinema.desktop.auth.SignInController
import com.drivecinema.desktop.auth.SignInUiState
import com.drivecinema.desktop.browse.BrowseModels
import com.drivecinema.desktop.browse.BrowseTarget
import com.drivecinema.desktop.browse.HomeContent
import com.drivecinema.desktop.browse.SearchResults
import com.drivecinema.desktop.library.DesktopDatabase
import com.drivecinema.desktop.library.DesktopSettings
import com.drivecinema.desktop.library.LibraryService
import com.drivecinema.desktop.library.LibraryState
import com.drivecinema.desktop.security.CredentialStores
import com.drivecinema.desktop.ui.AccountInfo
import com.drivecinema.desktop.ui.appIconPainter
import com.drivecinema.desktop.ui.DiscoveringScreen
import com.drivecinema.desktop.ui.DriveCinemaDesktopTheme
import com.drivecinema.desktop.ui.FolderPickerScreen
import com.drivecinema.desktop.ui.HomeScreen
import com.drivecinema.desktop.ui.ImageLoader
import com.drivecinema.desktop.ui.LocalImageLoader
import com.drivecinema.desktop.ui.MovieDetailsScreen
import com.drivecinema.desktop.ui.PlaybackRequest
import com.drivecinema.desktop.ui.PlayerScreen
import com.drivecinema.desktop.player.DesktopPlayback
import com.drivecinema.desktop.player.DriveStreamProxy
import com.drivecinema.desktop.player.ProgressRecorder
import com.drivecinema.desktop.player.ResolvedRequest
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import com.drivecinema.desktop.ui.SearchScreen
import com.drivecinema.desktop.ui.SeriesDetailsScreen
import com.drivecinema.desktop.ui.SetupGuideScreen
import com.drivecinema.desktop.ui.LocalFoldersScreen
import com.drivecinema.desktop.local.FolderChooser
import com.drivecinema.desktop.local.LocalFolders
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.drivecinema.desktop.ui.SignInScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Progress writes run one at a time and outlive the player screen. */
@OptIn(ExperimentalCoroutinesApi::class)
private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

fun main() {
    LinuxUiScale.apply()?.let { AppLog.i("DriveCinemaDesktop", "UI scale ${it}x from Xft.dpi") }
    val dataDir = AppDataDirectory.ensure()
    AppLog.i("DriveCinemaDesktop", "Data directory: ${dataDir.absolutePath}")

    val oauthConfig = DesktopOAuthConfig.load()
    val credentialStore = CredentialStores.forCurrentOs(dataDir)
    AppLog.i("DriveCinemaDesktop", "Sign-in is stored in the ${credentialStore.description}")
    if (!oauthConfig.isConfigured) AppLog.w("DriveCinemaDesktop", "Desktop OAuth client not configured (local.properties)")

    // Library: the shared database and sync engine, the same code as on Android.
    // Library calls (Drive listing, TMDB, images); large folders can answer slowly. Streaming uses its own client.
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    val database = DesktopDatabase.open(dataDir)
    val settings = DesktopSettings(File(dataDir, "settings.json"))
    val gateway = GoogleDriveGateway(httpClient)
    val tmdbClient = TmdbClient(httpClient, apiKey = DesktopBuildConfig.tmdbApiKey)
    val syncEngine = DriveLibrarySyncEngine(FolderQueueCrawler(httpClient), database.mediaDao(), database.syncMetadataDao(), tmdbClient, httpClient)
    val browse = BrowseModels(database)
    val playback = DesktopPlayback(database)
    val imageLoader = ImageLoader(File(dataDir, "cache/images"), httpClient)

    application {
        val navigator = remember { DesktopNavigator() }
        val scope = rememberCoroutineScope()
        val signIn = remember { DesktopSignIn(oauthConfig) }
        val signInController = remember {
            SignInController(
                scope, oauthConfig.isConfigured,
                signIn = { onUrl -> signIn.signIn(onUrl) },
                sessions = SessionRepository(credentialStore, OAuthTokenClient(oauthConfig))
            ).also { it.restoreSession() }
        }
        val library = remember { LibraryService(scope, gateway, syncEngine, settings) }
        val localFolders = remember {
            LocalFolders(scope, database, settings, afterScan = { syncEngine.enrichExistingCatalog() }).also { it.scanAll() }
        }
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
        val toggleFullscreen = {
            windowState.placement = if (isFullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
        }
        Window(
            onCloseRequest = {
                database.close()
                exitApplication()
            },
            title = "DriveCinema",
            icon = remember { appIconPainter() },
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                when {
                    // Esc leaves full screen first, then goes back one screen (like Back on Android).
                    event.key == Key.Escape && isFullscreen -> { windowState.placement = WindowPlacement.Floating; true }
                    event.key == Key.Escape -> navigator.back()
                    event.key == Key.F && event.isCtrlPressed && navigator.current != DesktopScreen.SignIn -> {
                        navigator.navigate(DesktopScreen.Search)
                        true
                    }
                    else -> false
                }
            }
        ) {
            DriveCinemaDesktopTheme {
                CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                    DesktopApp(
                        navigator, signInController, library, browse, playback, localFolders,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = toggleFullscreen,
                        onLeavePlayer = { if (windowState.placement == WindowPlacement.Fullscreen) windowState.placement = WindowPlacement.Floating },
                        storageNotice = if (credentialStore.isSystemKeyring) null else
                            "No system keyring found: your sign-in is kept in a file only your user can read."
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopApp(
    navigator: DesktopNavigator,
    signInController: SignInController,
    library: LibraryService,
    browse: BrowseModels,
    playback: DesktopPlayback,
    localFolders: LocalFolders,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLeavePlayer: () -> Unit,
    storageNotice: String? = null
) {
    val backStack by navigator.backStack.collectAsState()
    val signInState by signInController.state.collectAsState()
    val libraryState by library.state.collectAsState()
    val scope = rememberCoroutineScope()
    val session = (signInState as? SignInUiState.SignedIn)?.session

    // One streaming proxy per signed-in session (it streams with that session's tokens).
    val proxy = remember(session) { session?.let { DriveStreamProxy(it.tokenProvider) } }
    DisposableEffect(proxy) { onDispose { proxy?.close() } }

    // Signing in starts the library and opens Home; signing out stops it and returns to sign-in.
    LaunchedEffect(session) {
        if (session != null) {
            library.start(session)
            navigator.resetTo(DesktopScreen.Home)
        } else {
            library.stop()
            if (navigator.current != DesktopScreen.SignIn && signInState !is SignInUiState.NotConfigured) {
                navigator.resetTo(DesktopScreen.SignIn)
            }
        }
    }

    // Folder discovery and choice take over the window until a library exists.
    if (session != null) {
        when (val state = libraryState) {
            LibraryState.Discovering -> { DiscoveringScreen(); return }
            is LibraryState.ChoosingFolder -> {
                FolderPickerScreen(
                    state = state,
                    onChoose = library::chooseFolder,
                    onCancel = library::closeFolderPicker,
                    onRetry = library::openFolderPicker
                )
                return
            }
            else -> Unit
        }
    }

    val searchQuery = remember { MutableStateFlow("") }
    val openMovie = { id: String, title: String -> navigator.navigate(DesktopScreen.MovieDetails(id, title)) }
    val openSeries = { id: String, title: String -> navigator.navigate(DesktopScreen.SeriesDetails(id, title)) }
    val play = { id: String, title: String, fromStart: Boolean -> navigator.navigate(DesktopScreen.Player(id, title, fromStart)) }

    when (val screen = backStack.last()) {
        DesktopScreen.SignIn -> SignInScreen(
            state = signInState,
            onSignIn = signInController::startSignIn,
            onCancel = signInController::cancel,
            onPreviewLayout = { navigator.resetTo(DesktopScreen.Home) },
            storageNotice = storageNotice,
            onOpenGuide = { navigator.navigate(DesktopScreen.SetupGuide) }
        )
        DesktopScreen.SetupGuide -> SetupGuideScreen(onBack = { navigator.back() })
        DesktopScreen.LocalFolders -> {
            val state by localFolders.state.collectAsState()
            var addError by remember { mutableStateOf<String?>(null) }
            LocalFoldersScreen(
                state = state,
                errorMessage = addError,
                onAdd = {
                    scope.launch {
                        FolderChooser.chooseFolder()?.let { addError = localFolders.add(it) }
                    }
                },
                onRemove = { addError = null; localFolders.remove(it) },
                onRescan = { addError = null; localFolders.scanAll() },
                onBack = { navigator.back() }
            )
        }
        DesktopScreen.Home -> {
            val content by remember { browse.home() }.collectAsState(HomeContent())
            HomeScreen(
                content = content,
                library = libraryState,
                account = session?.user?.let { AccountInfo(it.displayName, it.emailAddress, it.photoLink) },
                onSearch = { navigator.navigate(DesktopScreen.Search) },
                onChangeFolder = library::openFolderPicker,
                onRefresh = library::refresh,
                onSignOut = if (session != null) signInController::signOut else null,
                onOpenGuide = { navigator.navigate(DesktopScreen.SetupGuide) },
                onLocalFolders = { navigator.navigate(DesktopScreen.LocalFolders) },
                onOpenMovie = { openMovie(it.id, it.title) },
                onOpenSeries = { openSeries(it.id, it.title) },
                onOpenContinue = { item ->
                    scope.launch {
                        when (val target = browse.target(item)) {
                            is BrowseTarget.Movie -> openMovie(target.id, target.title)
                            is BrowseTarget.Series -> openSeries(target.id, target.title)
                            null -> Unit
                        }
                    }
                }
            )
        }
        DesktopScreen.Search -> {
            val query by searchQuery.collectAsState()
            val results by remember { browse.search(searchQuery) }.collectAsState(SearchResults())
            SearchScreen(
                query = query,
                onQueryChange = { searchQuery.value = it },
                results = results,
                onOpenMovie = { openMovie(it.id, it.title) },
                onOpenSeries = { openSeries(it.id, it.title) },
                onBack = { navigator.back() }
            )
        }
        is DesktopScreen.MovieDetails -> {
            val details by remember(screen.itemId) { browse.movie(screen.itemId) }.collectAsState(null)
            MovieDetailsScreen(
                details = details,
                onPlay = { fromStart -> play(screen.itemId, screen.title, fromStart) },
                onBack = { navigator.back() }
            )
        }
        is DesktopScreen.SeriesDetails -> {
            val details by remember(screen.seriesId) { browse.series(screen.seriesId) }.collectAsState(null)
            SeriesDetailsScreen(
                details = details,
                onPlayEpisode = { episode -> play(episode.id, "${screen.title} · ${episode.title}", false) },
                onBack = { navigator.back() }
            )
        }
        is DesktopScreen.Player -> {
            // Bumped after "Install VLC" succeeds, to resolve the title again without leaving the screen.
            var resolveAttempt by remember(screen) { mutableIntStateOf(0) }
            val resolved by produceState(ResolvedRequest(PlaybackRequest.Loading, null), screen, proxy, resolveAttempt) {
                value = playback.resolve(screen.itemId, screen.fromStart, proxy)
            }
            val recorder = remember(resolved.progressKey) {
                resolved.progressKey?.let { ProgressRecorder(playback.database.playbackProgressDao(), it) }
            }
            val nextEpisode by produceState<Pair<String, String>?>(null, screen.itemId) {
                value = playback.nextEpisode(screen.itemId)
            }
            DisposableEffect(Unit) { onDispose { onLeavePlayer() } }
            // Keyed by item so moving to the next episode starts a fresh player.
            key(screen.itemId) { PlayerScreen(
                title = screen.title,
                request = resolved.request,
                nextEpisodeTitle = nextEpisode?.second,
                onNextEpisode = {
                    nextEpisode?.let { (id, title) ->
                        // Moving on counts as having watched this one (Milestone J.1).
                        recorder?.let { progressScope.launch { it.markWatched() } }
                        navigator.replace(DesktopScreen.Player(id, title, fromStart = false))
                    }
                },
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                // Saved outside the screen's lifetime so the last position survives leaving the player.
                onProgress = { position, duration -> recorder?.let { progressScope.launch { it.record(position, duration) } } },
                onFinished = { duration -> recorder?.let { progressScope.launch { it.recordFinished(duration) } } },
                onBack = { navigator.back() },
                onRetry = { resolveAttempt++ }
            ) }
        }
    }
}
