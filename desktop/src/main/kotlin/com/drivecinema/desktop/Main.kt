package com.drivecinema.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.drivecinema.app.core.devicesync.AppDataClient
import com.drivecinema.app.core.devicesync.DeviceSync
import com.drivecinema.app.core.devicesync.DeviceSyncController
import com.drivecinema.app.core.devicesync.DeviceSyncIdentity
import com.drivecinema.app.core.devicesync.DeviceSyncRunner.Trigger
import com.drivecinema.app.core.devicesync.DeviceSyncSwitch
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.skipintro.SkipIntroLookup
import com.drivecinema.desktop.library.DesktopSkipIntro
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.sync.DriveLibrarySyncEngine
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.naming.NamingCheckModel
import com.drivecinema.app.core.naming.NamingGuideModel
import com.drivecinema.app.core.sync.FolderQueueCrawler
import com.drivecinema.desktop.naming.DesktopNaming
import com.drivecinema.desktop.ui.DesktopNamingCheckActions
import com.drivecinema.desktop.ui.DesktopNamingHelperActions
import com.drivecinema.desktop.ui.NamingCheckScreen
import com.drivecinema.desktop.ui.NamingHelperPage
import com.drivecinema.desktop.auth.DesktopOAuthConfig
import com.drivecinema.desktop.auth.DesktopSignIn
import com.drivecinema.desktop.auth.OAuthTokenClient
import com.drivecinema.desktop.auth.SessionRepository
import com.drivecinema.desktop.auth.SignInController
import com.drivecinema.desktop.auth.SignInUiState
import com.drivecinema.desktop.browse.BrowseCard
import com.drivecinema.desktop.browse.BrowseKind
import com.drivecinema.desktop.browse.BrowseModels
import com.drivecinema.desktop.browse.BrowseOptions
import com.drivecinema.desktop.ui.AccountMenuActions
import com.drivecinema.desktop.ui.BrowseScreen
import com.drivecinema.desktop.ui.LibraryTopBar
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.drivecinema.desktop.browse.BrowseTarget
import com.drivecinema.desktop.browse.HomeContent
import com.drivecinema.desktop.browse.SearchResults
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.desktop.library.DesktopDatabase
import com.drivecinema.desktop.library.DesktopSettings
import com.drivecinema.desktop.library.LibraryService
import com.drivecinema.desktop.library.LibraryState
import com.drivecinema.desktop.library.LibraryFolderSetting
import com.drivecinema.desktop.importer.ImportFolderKind
import com.drivecinema.desktop.importer.ImportQueue
import com.drivecinema.desktop.host.MediaHost
import com.drivecinema.desktop.host.DesktopPcs
import com.drivecinema.desktop.host.DesktopPairedHostStore
import com.drivecinema.app.core.host.HostLibrarySync
import com.drivecinema.desktop.ui.PcsActions
import com.drivecinema.desktop.ui.PcsScreen
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.drivecinema.desktop.ui.SharingActions
import com.drivecinema.desktop.ui.SharingScreen
import com.drivecinema.desktop.ui.ImportActions
import com.drivecinema.desktop.ui.ImportScreen
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
import com.drivecinema.desktop.player.SlowChoice
import com.drivecinema.desktop.player.HostStreamProxy
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import com.drivecinema.desktop.ui.SearchScreen
import com.drivecinema.desktop.ui.SeriesDetailsScreen
import com.drivecinema.desktop.ui.HelpCenterScreen
import com.drivecinema.app.ui.setup.SetupGuideContent
import com.drivecinema.desktop.ui.SettingsPage
import com.drivecinema.desktop.ui.SettingsScaffold
import com.drivecinema.desktop.ui.SettingsShellActions
import com.drivecinema.desktop.ui.LocalFoldersScreen
import com.drivecinema.desktop.local.FolderChooser
import com.drivecinema.desktop.local.LocalFolders
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.drivecinema.desktop.ui.SignInScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.system.exitProcess
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
    DesktopDatabase.newerSchemaVersion(dataDir)?.let { stored ->
        AppLog.w("DriveCinemaDesktop", "Database version $stored is newer than this app (${DriveCinemaDatabase.SCHEMA_VERSION})")
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "Your DriveCinema data was saved by a newer version of the app.\n" +
                "Install the latest DriveCinema to open it. Your library and watch history are kept.",
            "DriveCinema",
            javax.swing.JOptionPane.ERROR_MESSAGE
        )
        exitProcess(1)
    }
    val database = DesktopDatabase.open(dataDir)
    val settings = DesktopSettings(File(dataDir, "settings.json"))
    val gateway = GoogleDriveGateway(httpClient)
    val tmdbClient = TmdbClient(httpClient, apiKey = DesktopBuildConfig.tmdbApiKey)
    // Milestone R: the naming helper and the Naming check (TMDB, series folders read read-only).
    val naming = DesktopNaming(tmdbClient, tmdbAvailable = DesktopBuildConfig.tmdbApiKey.isNotEmpty(), FolderQueueCrawler(httpClient))
    val syncEngine = DriveLibrarySyncEngine(FolderQueueCrawler(httpClient), database.mediaDao(), database.syncMetadataDao(), tmdbClient, httpClient)
    val browse = BrowseModels(database)
    // Task I.2: other PCs this computer streams from (pairings in a user-private file).
    val hostSync = HostLibrarySync(
        database,
        DesktopPairedHostStore(File(dataDir, "paired-hosts.json")),
        runInTransaction = { block -> database.useWriterConnection { it.immediateTransaction { block() } } }
    )
    val playback = DesktopPlayback(database, hosts = hostSync)
    val imageLoader = ImageLoader(File(dataDir, "cache/images"), httpClient)
    // Milestone O: its own plain HTTP client (never the Drive client); jpackage sets the version property.
    val skipIntro = DesktopSkipIntro(
        settings,
        SkipIntroLookup.create(
            File(dataDir, "cache/skip-intro-cache.json"),
            tmdbClient,
            userAgent = "DriveCinema/${System.getProperty("jpackage.app-version") ?: "dev"} (Desktop)"
        ),
        database.mediaDao()
    )

    application {
        val navigator = remember { DesktopNavigator() }
        val scope = rememberCoroutineScope()
        val signIn = remember { DesktopSignIn(oauthConfig) }
        val signInController = remember {
            SignInController(
                scope, oauthConfig.isConfigured,
                signIn = { onUrl -> signIn.signIn(onUrl) },
                sessions = SessionRepository(credentialStore, OAuthTokenClient(oauthConfig)),
                grantWriteAccess = { current, onUrl -> signIn.grantDriveWriteAccess(current, onUrl) },
                grantDeviceSync = { current, onUrl -> signIn.grantDeviceSync(current, onUrl) },
                revoke = { token -> OAuthTokenClient(oauthConfig).revoke(token) }
            ).also { it.restoreSession() }
        }
        // Milestone Q: watch progress through the Google account's Drive app folder, when switched on.
        val deviceSync = remember {
            DeviceSyncController(
                switch = object : DeviceSyncSwitch {
                    override fun isEnabled() = settings.deviceSyncEnabled()
                    override fun setEnabled(enabled: Boolean) = settings.setDeviceSyncEnabled(enabled)
                },
                needsPermissionText = "Google didn't give DriveCinema its app folder. Turn \"Sync with my devices\" on again to allow it.",
                accessToken = {
                    (signInController.state.value as? SignInUiState.SignedIn)?.session?.tokenProvider?.let { tokens ->
                        withContext(Dispatchers.IO) { runCatching { tokens.getAccessToken() }.getOrNull() }
                    }
                },
                sync = DeviceSync(
                    database.deviceSyncDao(),
                    AppDataClient(httpClient),
                    DeviceSyncIdentity(settings.deviceSyncId(), desktopDeviceName(), "desktop")
                )::run
            )
        }
        val library = remember { LibraryService(scope, gateway, syncEngine, settings) }
        val localFolders = remember {
            LocalFolders(scope, database, settings, afterScan = { syncEngine.enrichExistingCatalog() }).also { it.scanAll() }
        }
        val mediaHost = remember {
            MediaHost(dataDir, settings, sharedFolders = { settings.localFolders().map { java.nio.file.Path.of(it) } }).also { it.start() }
        }
        val pcs = remember { DesktopPcs(scope, hostSync, ownHostId = { runCatching { mediaHost.hostId() }.getOrNull() }) }
        val hostProxy = remember { HostStreamProxy(hostSync) }
        val importQueue = remember { ImportQueue(scope, database, settings, afterUploads = { library.refresh() }) }
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
        val toggleFullscreen = {
            windowState.placement = if (isFullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
        }
        Window(
            onCloseRequest = {
                hostProxy.close()
                mediaHost.close()
                database.close()
                exitApplication()
            },
            title = "DriveCinema",
            icon = remember { appIconPainter() },
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                when (val action = DesktopKeys.action(event.key, event.isCtrlPressed, isFullscreen, navigator.current, library.state.value)) {
                    null -> false
                    DesktopKeyAction.LeaveFullscreen -> { windowState.placement = WindowPlacement.Floating; true }
                    DesktopKeyAction.Back -> navigator.back()
                    DesktopKeyAction.CloseFolderPicker -> { library.closeFolderPicker(); true }
                    DesktopKeyAction.OpenSearch -> { navigator.navigate(DesktopScreen.Search); true }
                    is DesktopKeyAction.OpenSection -> { navigator.openSection(action.screen); true }
                    DesktopKeyAction.Ignore -> true
                }
            }
        ) {
            DriveCinemaDesktopTheme {
                CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                    DesktopApp(
                        navigator, signInController, library, browse, playback, localFolders, importQueue, mediaHost, pcs, hostProxy,
                        libraryFolder = { email -> settings.libraryFolder(email) },
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = toggleFullscreen,
                        onLeavePlayer = { if (windowState.placement == WindowPlacement.Fullscreen) windowState.placement = WindowPlacement.Floating },
                        storageNotice = if (credentialStore.isSystemKeyring) null else
                            "No system keyring found: your sign-in is kept in a file only your user can read.",
                        skipIntro = skipIntro,
                        deviceSync = deviceSync,
                        naming = naming
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
    importQueue: ImportQueue,
    mediaHost: MediaHost,
    pcs: DesktopPcs,
    hostProxy: HostStreamProxy,
    libraryFolder: (accountEmail: String) -> LibraryFolderSetting?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLeavePlayer: () -> Unit,
    storageNotice: String? = null,
    skipIntro: DesktopSkipIntro? = null,
    deviceSync: DeviceSyncController? = null,
    naming: DesktopNaming? = null
) {
    var skipIntroOn by remember { mutableStateOf(skipIntro?.isEnabled() ?: false) }
    val deviceSyncOn = deviceSync?.enabled?.collectAsState()?.value
    val deviceSyncStatus = deviceSync?.status?.collectAsState()?.value
    val backStack by navigator.backStack.collectAsState()
    val signInState by signInController.state.collectAsState()
    val libraryState by library.state.collectAsState()
    val scope = rememberCoroutineScope()
    val session = (signInState as? SignInUiState.SignedIn)?.session
    val clipboard = LocalClipboardManager.current
    // Task R.3: the naming helper's search and loaded seasons stay while the app runs (moving between pages keeps them).
    val namingGuide = remember(naming) { naming?.let { NamingGuideModel(NamingGuide(it.tmdb), scope, it.tmdbAvailable) } }

    // Task L.1: library content, scroll positions and the views' sort/filters live here, above the screens, so
    // they are kept while the app runs (opening a title and coming back returns to the same place).
    val homeContent by remember { browse.home() }.collectAsState(HomeContent())
    val homeList = rememberLazyListState()
    val browseGrids = BrowseKind.entries.associateWith { rememberLazyGridState() }
    var browseOptions by remember { mutableStateOf(BrowseKind.entries.associateWith { BrowseOptions() }) }

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

    // Task I.1: the import queue runs while signed in with a chosen library (and only with write access).
    val readyLibrary = (libraryState as? LibraryState.Ready)?.folderName
    LaunchedEffect(session, readyLibrary) {
        val folder = session?.let { libraryFolder(it.user.emailAddress) }
        if (session != null && readyLibrary != null && folder != null) {
            importQueue.attach(session.tokenProvider, folder.folderId, folder.folderName)
            pcs.syncAll()
        } else {
            importQueue.detach()
        }
    }

    // Milestone Q: sync after each library sync (start, Refresh), when a video closes and when the window gets the focus.
    val librarySyncing = (libraryState as? LibraryState.Ready)?.isSyncing
    LaunchedEffect(session, librarySyncing) {
        if (session != null && librarySyncing == false) deviceSync?.syncNow(Trigger.REFRESH)
    }
    val isPlaying = backStack.last() is DesktopScreen.Player
    var wasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) {
        if (wasPlaying && !isPlaying) deviceSync?.syncNow(Trigger.PLAYER_CLOSED)
        wasPlaying = isPlaying
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && session != null && librarySyncing == false) deviceSync?.syncNow(Trigger.FOREGROUND)
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
    val openCard = { kind: BrowseKind, card: BrowseCard ->
        if (kind == BrowseKind.FILMS) openMovie(card.id, card.title) else openSeries(card.id, card.title)
    }
    val readyState = libraryState as? LibraryState.Ready
    val topBar = LibraryTopBar(
        library = libraryState,
        account = session?.user?.let { AccountInfo(it.displayName, it.emailAddress, it.photoLink) },
        section = (DesktopNavigator.sectionOf(backStack) as? DesktopScreen.Browse)?.kind,
        onSection = { kind -> navigator.openSection(kind?.let { DesktopScreen.Browse(it) } ?: DesktopScreen.Home) },
        onSearch = { navigator.navigate(DesktopScreen.Search) },
        menu = AccountMenuActions(
            onChangeFolder = library::openFolderPicker.takeIf { readyState != null && !readyState.isSyncing },
            onRefresh = library::refresh.takeIf { readyState != null && !readyState.isSyncing },
            onLocalFolders = { navigator.openSettingsPage(DesktopScreen.LocalFolders) },
            onImport = { navigator.openSettingsPage(DesktopScreen.Import) }.takeIf { readyState != null },
            onSharing = { navigator.openSettingsPage(DesktopScreen.Sharing) },
            onPcs = { navigator.openSettingsPage(DesktopScreen.Pcs) },
            onNamingHelper = { navigator.openSettingsPage(DesktopScreen.NamingHelper) }.takeIf { naming != null },
            onOpenGuide = { navigator.openSettingsPage(DesktopScreen.SetupGuide()) },
            onSignOut = if (session != null) {
                {
                    // Milestone Q: the next sign-in is read-only; turning sync on again asks for the app folder.
                    deviceSync?.disable()
                    signInController.signOut()
                }
            } else null,
            skipIntroEnabled = if (skipIntro != null) skipIntroOn else null,
            onToggleSkipIntro = {
                skipIntroOn = !skipIntroOn
                skipIntro?.setEnabled(skipIntroOn)
            },
            deviceSyncEnabled = deviceSyncOn.takeIf { session != null },
            onToggleDeviceSync = {
                val sync = deviceSync
                if (sync != null) scope.launch {
                    if (deviceSyncOn == true) {
                        sync.disable()
                    } else {
                        sync.showStatus("Waiting for Google in your browser…")
                        val refused = signInController.requestDeviceSyncAccess { url, opened ->
                            if (!opened) sync.showStatus("Open this page to allow sync: $url")
                        }
                        if (refused == null) sync.turnOn() else sync.grantRefused(refused)
                    }
                }
            },
            deviceSyncStatus = deviceSyncStatus
        )
    )

    // Task P.6 (D-P3): the settings shell's side list; its pages take each other's place.
    val shell = SettingsShellActions(
        open = navigator::openSettingsPage,
        back = { navigator.back() },
        importAvailable = readyState != null
    )

    when (val screen = backStack.last()) {
        DesktopScreen.SignIn -> SignInScreen(
            state = signInState,
            onSignIn = signInController::startSignIn,
            onCancel = signInController::cancel,
            onPreviewLayout = { navigator.resetTo(DesktopScreen.Home) },
            storageNotice = storageNotice,
            onOpenGuide = { navigator.navigate(DesktopScreen.SetupGuide()) }
        )
        is DesktopScreen.SetupGuide -> HelpCenterScreen(onBack = { navigator.back() }, initialTopicId = screen.topicId)
        DesktopScreen.LocalFolders -> SettingsScaffold(SettingsPage.LOCAL_FOLDERS, shell) {
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
                onOpenHelp = { navigator.openSettingsPage(DesktopScreen.SetupGuide(SetupGuideContent.COMPUTER_FOLDERS)) }
            )
        }
        DesktopScreen.Pcs -> SettingsScaffold(SettingsPage.PCS, shell) {
            val state by pcs.state.collectAsState()
            PcsScreen(
                state = state,
                actions = PcsActions(
                    onSearch = pcs::search,
                    onPair = pcs::pair,
                    onSetVpnAddress = pcs::setVpnAddress,
                    onRemove = { pcs.remove(it) },
                    onCheckNow = { pcs.syncAll() }
                )
            )
        }
        DesktopScreen.Sharing -> SettingsScaffold(SettingsPage.SHARING, shell) {
            val status by mediaHost.status.collectAsState()
            val folders by localFolders.state.collectAsState()
            SharingScreen(
                status = status,
                sharedFolderCount = folders.folders.count { it.isAvailable },
                isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows"),
                actions = SharingActions(
                    onSharing = mediaHost::setSharing,
                    onAllowVpn = mediaHost::setAllowVpn,
                    onStartPairing = { mediaHost.startPairing() },
                    onCancelPairing = mediaHost::cancelPairing,
                    onRemoveDevice = mediaHost::removeDevice,
                    onLocalFolders = { navigator.openSettingsPage(DesktopScreen.LocalFolders) }
                )
            )
        }
        DesktopScreen.Import -> SettingsScaffold(SettingsPage.IMPORT, shell) {
            val status by importQueue.status.collectAsState()
            val writeAccess by signInController.writeAccess.collectAsState()
            val entries by importQueue.entries.collectAsState(emptyList())
            var importError by remember { mutableStateOf<String?>(null) }
            ImportScreen(
                status = status,
                writeAccess = writeAccess,
                entries = entries,
                errorMessage = importError,
                actions = ImportActions(
                    onGrantWriteAccess = { signInController.requestWriteAccess { scope.launch { importQueue.refreshAccess() } } },
                    onRemoveWriteAccess = signInController::removeWriteAccessAndSignOut,
                    onChooseFolder = { kind ->
                        scope.launch {
                            val title = if (kind == ImportFolderKind.MOVIES) "Choose the folder new films arrive in" else "Choose the folder new episodes arrive in (Show/Season N/…)"
                            FolderChooser.chooseFolder(title)?.let { importError = importQueue.setFolder(kind, it) }
                        }
                    },
                    onClearFolder = { kind -> scope.launch { importError = importQueue.setFolder(kind, null) } },
                    onPause = importQueue::setPaused,
                    onResumeAfterStop = importQueue::resumeAfterStop,
                    onRetry = { importQueue.retry(it) },
                    onCancel = { importQueue.cancel(it) },
                    onUploadAllSkipped = { importQueue.uploadAllSkipped() },
                    onClearFinished = { importQueue.clearFinished() }
                )
            )
        }
        DesktopScreen.NamingHelper -> SettingsScaffold(SettingsPage.NAMING_HELPER, shell) {
            val guide = namingGuide
            if (guide != null) {
                val state by guide.state.collectAsState()
                NamingHelperPage(
                    state,
                    DesktopNamingHelperActions(
                        setQuery = guide::setQuery,
                        setKind = guide::setKind,
                        search = guide::search,
                        pickSeries = guide::pickSeries,
                        pickFilm = guide::pickFilm,
                        closePick = guide::closePick,
                        toggleSeason = guide::toggleSeason,
                        setLayout = guide::setLayout,
                        retrySeason = guide::retrySeason,
                        copy = { clipboard.setText(AnnotatedString(it)) }
                    )
                )
            }
        }
        is DesktopScreen.NamingCheck -> {
            // Its own scope: leaving the page stops a check still running.
            val checkScope = rememberCoroutineScope()
            val model = remember(screen, naming) {
                naming?.let { deps ->
                    NamingCheckModel(
                        checkScope,
                        deps.tmdb,
                        deps.tmdbAvailable,
                        isDriveSeries = DesktopNaming.isDriveSeries(screen.seriesId),
                        listing = {
                            val tokens = (signInController.state.value as? SignInUiState.SignedIn)?.session?.tokenProvider
                                ?: throw java.io.IOException("Not signed in")
                            deps.readSeriesFolder(tokens, screen.seriesId)
                        }
                    ).also { it.load() }
                }
            }
            if (model != null) {
                val state by model.state.collectAsState()
                NamingCheckScreen(
                    seriesTitle = screen.title,
                    state = state,
                    actions = DesktopNamingCheckActions(
                        retry = model::load,
                        setFilter = model::setFilter,
                        toggleSeason = model::toggleSeason,
                        pick = model::pick,
                        copy = { clipboard.setText(AnnotatedString(it)) },
                        openHelper = { navigator.replace(DesktopScreen.NamingHelper) },
                        back = { navigator.back() }
                    )
                )
            }
        }
        DesktopScreen.Home -> {
            HomeScreen(
                content = homeContent,
                top = topBar,
                listState = homeList,
                onOpenCard = openCard,
                onSeeAll = { kind -> navigator.openSection(DesktopScreen.Browse(kind)) },
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
        is DesktopScreen.Browse -> BrowseScreen(
            kind = screen.kind,
            cards = homeContent.cards(screen.kind),
            options = browseOptions.getValue(screen.kind),
            onOptions = { browseOptions = browseOptions + (screen.kind to it) },
            top = topBar,
            gridState = browseGrids.getValue(screen.kind),
            onOpen = { openCard(screen.kind, it) }
        )
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
                onBack = { navigator.back() },
                onNamingCheck = if (naming != null) {
                    { navigator.navigate(DesktopScreen.NamingCheck(screen.seriesId, screen.title)) }
                } else null
            )
        }
        is DesktopScreen.Player -> {
            // Outside the key below: moving on to the next episode must not leave full screen.
            DisposableEffect(Unit) { onDispose { onLeavePlayer() } }
            // Report F-31: everything about one episode is keyed by its screen. produceState keeps its old value when
            // its keys change, so without this the next episode opened the previous one's stream (from the start)
            // under the new title, with the previous "next episode" and progress key, until a second click.
            key(screen) {
                // Bumped after "Install VLC" succeeds, to resolve the title again without leaving the screen.
                var resolveAttempt by remember(screen) { mutableIntStateOf(0) }
                var slowChoice by remember(screen) { mutableStateOf(SlowChoice.ASK) }
                val resolved by produceState(ResolvedRequest(PlaybackRequest.Loading, null), screen, proxy, resolveAttempt, slowChoice) {
                    value = playback.resolve(screen.itemId, screen.fromStart, proxy, hostProxy, slowChoice)
                }
                val recorder = remember(resolved.progressKey) {
                    resolved.progressKey?.let { ProgressRecorder(playback.database.playbackProgressDao(), it) }
                }
                val nextEpisode by produceState<Pair<String, String>?>(null, screen.itemId) {
                    value = playback.nextEpisode(screen.itemId)
                }
                PlayerScreen(
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
                    findIntro = skipIntro?.takeIf { skipIntroOn }?.let { lookup -> { lengthMs -> lookup.find(screen.itemId, lengthMs) } },
                    onRetry = { resolveAttempt++ },
                    onSlowChoice = { fromPc -> slowChoice = if (fromPc) SlowChoice.PLAY_FROM_PC else SlowChoice.PLAY_FALLBACK },
                    // Task L.3: progress is keyed by the copy's source key, so the details land on the copy that played.
                    onTechInfo = { tech ->
                        resolved.progressKey?.let { key ->
                            progressScope.launch {
                                playback.database.mediaSourceDao().updatePlayedTech(
                                    key, tech.widthPx, tech.heightPx, tech.durationMs, tech.videoCodec,
                                    tech.audioCodec, tech.audioChannels, tech.frameRate, isHdr = null
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

/** Milestone Q: how this PC is named in its sync file ("Windows 11 · HOME-PC"). */
private fun desktopDeviceName(): String {
    val host = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME")
    return listOfNotNull(System.getProperty("os.name"), host?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { "DriveCinema PC" }
}
