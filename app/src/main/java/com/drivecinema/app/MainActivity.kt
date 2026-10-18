package com.drivecinema.app

import android.accounts.Account
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivecinema.app.core.auth.GoogleAuthManager
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.getInstance
import com.drivecinema.app.core.device.SystemPlayServicesChecker
import com.drivecinema.app.core.logging.AndroidLogSink
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.device.TvDeviceDetector
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.local.AddFolderResult
import com.drivecinema.app.core.local.LocalFolder
import com.drivecinema.app.core.local.LocalFolderPreferences
import com.drivecinema.app.core.local.SystemTvStorageCapabilityChecker
import com.drivecinema.app.core.local.TvStorageCapabilityChecker
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.network.FolderValidationResult
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.pairing.PairingRejectedException
import com.drivecinema.app.core.pairing.PhonePairingClient
import com.drivecinema.app.core.pairing.TvPairingServer
import com.drivecinema.app.core.pairing.TvUnreachableException
import com.drivecinema.app.core.security.AuthCredentialStore
import com.drivecinema.app.core.security.AuthCredentials
import com.drivecinema.app.core.sync.DriveLibrarySyncEngine
import com.drivecinema.app.core.sync.FolderQueueCrawler
import com.drivecinema.app.core.sync.FolderSwitchResult
import com.drivecinema.app.core.sync.LibraryFolderSwitcher
import com.drivecinema.app.core.sync.LibraryRefreshResult
import com.drivecinema.app.core.sync.LibraryRefresher
import com.drivecinema.app.core.sync.StartupSyncGate
import com.drivecinema.app.core.sync.StartupToken
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.core.player.NextEpisode
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.details.DetailsViewModel
import com.drivecinema.app.ui.details.MovieDetailsScreen
import com.drivecinema.app.ui.details.SeriesDetailsScreen
import com.drivecinema.app.ui.mobile.grid.FullLibraryGridScreen
import com.drivecinema.app.ui.mobile.home.MobileHomeScreen
import com.drivecinema.app.ui.mobile.home.MobileHomeViewModel
import com.drivecinema.app.ui.mobile.navigation.MobileBottomNav
import com.drivecinema.app.ui.mobile.navigation.MobileTab
import com.drivecinema.app.ui.mobile.search.SearchScreen
import com.drivecinema.app.ui.player.PlayerActivity
import com.drivecinema.app.ui.search.SearchViewModel
import com.drivecinema.app.ui.mobile.home.AccountUiState
import com.drivecinema.app.ui.setup.AccountDialog
import com.drivecinema.app.ui.setup.FolderPickerScreen
import com.drivecinema.app.ui.setup.PhonePairingDialog
import com.drivecinema.app.core.local.LocalLibraryCrawler
import com.drivecinema.app.core.local.LocalLibraryIndexer
import com.drivecinema.app.core.local.indexFolder
import com.drivecinema.app.core.local.retireUnsavedFolders
import com.drivecinema.app.core.local.SystemDocumentTreeSource
import com.drivecinema.app.ui.player.PlaybackResolver
import com.drivecinema.app.ui.player.PlaybackSource
import com.drivecinema.app.ui.player.ResolvedPlayback
import com.drivecinema.app.ui.setup.DuplicateReview
import com.drivecinema.app.ui.setup.DuplicateReviews
import com.drivecinema.app.ui.setup.DuplicatesScreen
import com.drivecinema.app.ui.setup.SetupGuideScreen
import com.drivecinema.app.ui.setup.SignInOptions
import com.drivecinema.app.ui.setup.SignInScreen
import com.drivecinema.app.ui.setup.TvPairingDialog
import com.drivecinema.app.ui.tv.grid.TvCollectionGridScreen
import com.drivecinema.app.ui.tv.grid.TvGridItem
import com.drivecinema.app.ui.tv.home.TvHomeScreen
import com.drivecinema.app.ui.tv.search.TvSearchScreen
import com.drivecinema.app.ui.tv.setup.TvStorageUnsupportedDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed interface AppScreen {
    data object SignIn : AppScreen
    data object FolderPicker : AppScreen
    data object Home : AppScreen
    data class MovieDetails(val movie: MediaItemEntity) : AppScreen
    data class SeriesDetails(val series: SeriesEntity) : AppScreen
    data class TvGrid(val title: String, val items: List<TvGridItem>) : AppScreen
    data object TvSearch : AppScreen
    data class SetupGuide(val returnTo: AppScreen) : AppScreen
    data object Duplicates : AppScreen
}

class MainActivity : ComponentActivity() {

    companion object {
        /** From `GOOGLE_ANDROID_CLIENT_ID` in local.properties (Milestone J.5; see README "Build with your own keys"). */
        const val GOOGLE_CLIENT_ID = BuildConfig.GOOGLE_ANDROID_CLIENT_ID
    }

    private lateinit var credentialStore: AuthCredentialStore
    private val authManager = GoogleAuthManager()
    private val driveGateway = GoogleDriveGateway()
    private lateinit var database: DriveCinemaDatabase
    private lateinit var syncEngine: DriveLibrarySyncEngine
    private lateinit var folderSwitcher: LibraryFolderSwitcher

    // Written by sync and refresh coroutines on IO threads, read on the main thread (report F-11).
    @Volatile
    private var cachedAccessToken = ""
    private var isTvDevice = false
    private var isTvStorageSupported = true
    private var signInOptions = SignInOptions.forDevice(isTv = false, playServicesAvailable = true)

    private lateinit var homeViewModel: MobileHomeViewModel
    private val showAccountDialog = mutableStateOf(false)
    private lateinit var localFolderPreferences: LocalFolderPreferences
    private val localFoldersState = mutableStateOf<List<LocalFolder>>(emptyList())
    private lateinit var tvStorageCapabilityChecker: TvStorageCapabilityChecker
    private val showTvStorageUnsupportedDialog = mutableStateOf(false)

    // Local library (Task C.4 phase 2)
    private lateinit var localIndexer: LocalLibraryIndexer
    private lateinit var playbackResolver: PlaybackResolver
    private val pendingDuplicateCount = mutableStateOf(0)
    private val duplicateReviews = mutableStateOf<List<DuplicateReview>>(emptyList())

    // App Navigation & UI state
    private val currentScreen = mutableStateOf<AppScreen>(AppScreen.SignIn)
    private val isAuthLoading = mutableStateOf(false)
    private val authErrorMessage = mutableStateOf<String?>(null)

    // Folder Picker state
    private val driveFolders = mutableStateOf<List<DriveFolder>>(emptyList())
    private val isFolderLoading = mutableStateOf(false)
    private val isFolderValidating = mutableStateOf(false)
    private val folderErrorMessage = mutableStateOf<String?>(null)

    // In-app library switching (FolderPicker opened from the account dialog while signed in)
    private val isSwitchingLibrary = mutableStateOf(false)
    private var folderSwitchJob: Job? = null
    private var librarySyncJob: Job? = null
    private val isRefreshingLibrary = mutableStateOf(false)
    private val libraryRefresher by lazy {
        LibraryRefresher(
            validateFolder = { root, token -> driveGateway.validateStreamingFolder(root, token) },
            crawl = { films, series, token, root -> syncEngine.executeInitialCrawl(films, series, token, root) },
        )
    }

    // Pairing dialog states
    private val showTvPairingDialog = mutableStateOf(false)
    private val tvPairingPin = mutableStateOf("")
    private val tvPairingStatus = mutableStateOf("Ready to pair")
    private val tvPairingAddress = mutableStateOf<String?>(null)
    private var tvPairingServer: TvPairingServer? = null

    private val showPhonePairingDialog = mutableStateOf(false)
    private val isPhonePairing = mutableStateOf(false)
    private val phonePairingError = mutableStateOf<String?>(null)

    /** Opens the phone's "Pair with Android TV" dialog without the previous attempt's error message. */
    private fun openPhonePairingDialog() {
        phonePairingError.value = null
        showPhonePairingDialog.value = true
    }

    private var pendingTvAccount: Account? = null

    private val authResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val dataIntent = result.data
        if (dataIntent != null) {
            isAuthLoading.value = true
            authManager.handleAuthResponse(this, dataIntent) { exchangeResult ->
                runOnUiThread {
                    isAuthLoading.value = false
                    exchangeResult.fold(
                        onSuccess = { (accessToken, refreshToken) ->
                            cachedAccessToken = accessToken
                            credentialStore.saveCredentials(AuthCredentials(refreshToken = refreshToken, rootFolderId = ""))
                            authErrorMessage.value = null
                            loadFoldersAndProceed(accessToken)
                        },
                        onFailure = { error ->
                            authErrorMessage.value = "Sign in failed: ${error.message}"
                        }
                    )
                }
            }
        } else {
            isAuthLoading.value = false
        }
    }

    private val tvConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val account = pendingTvAccount
        if (result.resultCode == RESULT_OK && account != null) {
            isAuthLoading.value = true
            authManager.fetchTvAccessToken(this, account) { exchangeResult ->
                runOnUiThread {
                    isAuthLoading.value = false
                    exchangeResult.fold(
                        onSuccess = { (accessToken, refreshToken) ->
                            cachedAccessToken = accessToken
                            credentialStore.saveCredentials(AuthCredentials(refreshToken = refreshToken, rootFolderId = ""))
                            authErrorMessage.value = null
                            loadFoldersAndProceed(accessToken)
                        },
                        onFailure = { error ->
                            authErrorMessage.value = "TV authorization failed: ${error.message}"
                        }
                    )
                }
            }
        } else {
            isAuthLoading.value = false
            if (result.resultCode != RESULT_OK) {
                authErrorMessage.value = "Google Drive authorization was not granted"
            }
        }
    }

    private val tvSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val dataIntent = result.data
        if (dataIntent != null) {
            isAuthLoading.value = true
            authManager.handleTvSignInResult(this, dataIntent) { exchangeResult ->
                runOnUiThread {
                    exchangeResult.fold(
                        onSuccess = { (accessToken, refreshToken) ->
                            isAuthLoading.value = false
                            cachedAccessToken = accessToken
                            credentialStore.saveCredentials(AuthCredentials(refreshToken = refreshToken, rootFolderId = ""))
                            authErrorMessage.value = null
                            loadFoldersAndProceed(accessToken)
                        },
                        onFailure = { error ->
                            if (error is UserRecoverableAuthException) {
                                val targetAccount = try {
                                    val task = GoogleSignIn.getSignedInAccountFromIntent(dataIntent)
                                    val acc = task.getResult(ApiException::class.java)
                                    acc.account ?: Account(acc.email ?: "", "com.google")
                                } catch (_: Exception) { null }

                                if (targetAccount != null) {
                                    pendingTvAccount = targetAccount
                                    val consentIntent = error.intent
                                    if (consentIntent != null) {
                                        tvConsentLauncher.launch(consentIntent)
                                    } else {
                                        isAuthLoading.value = false
                                        authErrorMessage.value = "Missing consent intent for TV authorization"
                                    }
                                } else {
                                    isAuthLoading.value = false
                                    authErrorMessage.value = "Could not resolve TV Google account"
                                }
                            } else {
                                isAuthLoading.value = false
                                authErrorMessage.value = "Sign in failed: ${error.message}"
                            }
                        }
                    )
                }
            }
        } else {
            isAuthLoading.value = false
        }
    }

    private val localFolderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Storage work on IO; Compose state and Toasts back on the main thread.
            lifecycleScope.launch {
                val (result, folderName, updated) = withContext(Dispatchers.IO) {
                    val result = localFolderPreferences.addFolderUri(uri)
                    val name = if (result is AddFolderResult.Success) localFolderPreferences.getDisplayName(uri) else ""
                    Triple(result, name, localFolderPreferences.getAllFolders())
                }
                localFoldersState.value = updated
                val message = when (result) {
                    is AddFolderResult.Success -> "Local folder '$folderName' added — scanning for videos…"
                    is AddFolderResult.NotLocalStorage -> "Please pick a folder on this device's storage"
                    is AddFolderResult.PermissionDenied -> "Failed to acquire read permission for the selected folder"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                if (result is AddFolderResult.Success) {
                    scanLocalFolders(announce = true)
                }
            }
        } else {
            // Cancelled, or a placeholder picker returned nothing: never fail silently.
            Toast.makeText(this, "No folder was selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshLocalFolders() {
        lifecycleScope.launch {
            localFoldersState.value = withContext(Dispatchers.IO) { localFolderPreferences.getAllFolders() }
            scanLocalFolders(announce = false)
        }
    }

    /**
     * Re-indexes every saved local folder (folders that fail to list are left untouched) and retires copies in
     * folders that were removed or lost their permission. Never deletes anything.
     */
    private fun scanLocalFolders(announce: Boolean) {
        lifecycleScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val saved = localFolderPreferences.getAllFolderUris()
                localIndexer.retireUnsavedFolders(saved)
                var found = 0
                var linked = 0
                var review = 0
                var failed = 0
                for (tree in saved) {
                    try {
                        val r = localIndexer.indexFolder(tree)
                        found += r.videosFound
                        linked += r.linkedToExisting
                        review += r.possibleDuplicates
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                        android.util.Log.w("MainActivity", "Local folder scan failed: ${e.message}")
                    }
                }
                listOf(found, linked, review, failed)
            }
            if (announce) {
                val (found, linked, review, failed) = summary
                val text = buildString {
                    append(if (found == 0) "No videos found in your local folders" else "Found $found local videos")
                    if (linked > 0) append(", $linked matched titles you already have")
                    if (review > 0) append(" — $review to review in Account")
                    if (failed > 0) append(" ($failed folder(s) could not be read)")
                }
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openDuplicateReview() {
        lifecycleScope.launch {
            duplicateReviews.value = withContext(Dispatchers.IO) { DuplicateReviews.load(database.mediaSourceDao()) }
            currentScreen.value = AppScreen.Duplicates
        }
    }

    private fun resolveDuplicate(action: suspend () -> Unit) {
        lifecycleScope.launch {
            duplicateReviews.value = withContext(Dispatchers.IO) {
                action()
                DuplicateReviews.load(database.mediaSourceDao())
            }
        }
    }

    /** Some OEM images ship without a document-tree picker; never crash, explain instead. */
    private fun launchLocalFolderPicker() {
        try {
            localFolderPickerLauncher.launch(null)
        } catch (_: ActivityNotFoundException) {
            if (isTvDevice) {
                showTvStorageUnsupportedDialog.value = true
            } else {
                Toast.makeText(this, "This device has no system folder picker, so local folders can't be added", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.sink = AndroidLogSink
        enableEdgeToEdge()

        credentialStore = AuthCredentialStore(this)
        database = DriveCinemaDatabase.getInstance(this)
        val tmdbApiKey = credentialStore.getCredentials()?.tmdbApiKey?.ifEmpty { null }
            ?: BuildConfig.TMDB_API_KEY.ifEmpty { null }
        val tmdbClient = TmdbClient(apiKey = tmdbApiKey ?: "")
        syncEngine = DriveLibrarySyncEngine(
            crawler = FolderQueueCrawler(),
            mediaDao = database.mediaDao(),
            syncMetadataDao = database.syncMetadataDao(),
            tmdbClient = tmdbClient
        )
        folderSwitcher = LibraryFolderSwitcher(driveGateway, syncEngine, credentialStore)
        localFolderPreferences = LocalFolderPreferences(this)
        tvStorageCapabilityChecker = SystemTvStorageCapabilityChecker(packageManager)
        localIndexer = LocalLibraryIndexer(database, LocalLibraryCrawler(SystemDocumentTreeSource(contentResolver)))
        playbackResolver = PlaybackResolver(database.mediaSourceDao())
        lifecycleScope.launch {
            database.mediaSourceDao().getPendingMatchesFlow().collect { pendingDuplicateCount.value = it.size }
        }
        refreshLocalFolders()

        isTvDevice = TvDeviceDetector.isTv(this)
        signInOptions = SignInOptions.forDevice(isTvDevice, SystemPlayServicesChecker(this).isAvailable())
        // Queried once (PackageManager IPC), not on every recomposition of the account dialog.
        isTvStorageSupported = !isTvDevice || tvStorageCapabilityChecker.isOpenDocumentTreeSupported()

        // Check stored credentials
        val creds = credentialStore.getCredentials()
        val emailFromToken = if (creds?.userEmail.isNullOrEmpty() && creds?.refreshToken?.startsWith(GoogleAuthManager.TV_ACCOUNT_PREFIX) == true) {
            creds.refreshToken.removePrefix(GoogleAuthManager.TV_ACCOUNT_PREFIX)
        } else {
            creds?.userEmail
        }
        val initialAccount = AccountUiState(
            displayName = creds?.userDisplayName,
            email = emailFromToken,
            photoLink = creds?.userPhotoLink,
            isConnected = creds != null && creds.refreshToken.isNotEmpty()
        )
        homeViewModel = MobileHomeViewModel(
            database.mediaDao(),
            initialAccount = initialAccount,
            continueWatching = ContinueWatching.flow(database)
        )

        if (creds != null && creds.refreshToken.isNotEmpty()) {
            if (creds.rootFolderId.isNotEmpty()) {
                currentScreen.value = AppScreen.Home
                // A rotation recreates this activity; don't re-read the whole library each time (report F-04).
                if (StartupSyncGate.process.shouldSync(creds.rootFolderId, isRecreated = savedInstanceState != null)) {
                    triggerBackgroundSync(creds.refreshToken, creds.rootFolderId)
                }
            } else {
                currentScreen.value = AppScreen.FolderPicker
                refreshTokenAndLoadFolders(creds.refreshToken)
            }
        } else {
            currentScreen.value = AppScreen.SignIn
        }

        setContent {
            DriveCinemaTheme {
                MainAppContent()
            }
        }
    }

    @Composable
    private fun MainAppContent() {
        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
        val searchViewModel = remember { SearchViewModel(database.mediaDao()) }
        val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
        var selectedMobileTab by remember { mutableStateOf(MobileTab.HOME) }

        // System Back / Predictive Back Gesture & Remote Key Handling
        when {
            showTvStorageUnsupportedDialog.value -> {
                BackHandler { showTvStorageUnsupportedDialog.value = false }
            }
            showAccountDialog.value -> {
                BackHandler { showAccountDialog.value = false }
            }
            showPhonePairingDialog.value -> {
                BackHandler { showPhonePairingDialog.value = false }
            }
            showTvPairingDialog.value -> {
                BackHandler {
                    tvPairingServer?.stopServer()
                    showTvPairingDialog.value = false
                }
            }
            currentScreen.value is AppScreen.Duplicates -> {
                BackHandler { currentScreen.value = AppScreen.Home }
            }
            currentScreen.value is AppScreen.SetupGuide -> {
                BackHandler { closeSetupGuide() }
            }
            currentScreen.value is AppScreen.FolderPicker && isSwitchingLibrary.value -> {
                BackHandler { cancelLibrarySwitch() }
            }
            currentScreen.value is AppScreen.MovieDetails -> {
                BackHandler { currentScreen.value = AppScreen.Home }
            }
            currentScreen.value is AppScreen.SeriesDetails -> {
                BackHandler { currentScreen.value = AppScreen.Home }
            }
            currentScreen.value is AppScreen.TvGrid -> {
                BackHandler { currentScreen.value = AppScreen.Home }
            }
            currentScreen.value is AppScreen.TvSearch -> {
                BackHandler { currentScreen.value = AppScreen.Home }
            }
            currentScreen.value is AppScreen.Home && !isTvDevice && selectedMobileTab != MobileTab.HOME -> {
                BackHandler { selectedMobileTab = MobileTab.HOME }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0D10))) {
            when (val screen = currentScreen.value) {
                is AppScreen.SignIn -> {
                    SignInScreen(
                        isLoading = isAuthLoading.value,
                        errorMessage = authErrorMessage.value,
                        onSignInClick = { launchGoogleSignIn() },
                        onPairingClick = {
                            if (isTvDevice) {
                                startTvPairing()
                            } else {
                                openPhonePairingDialog()
                            }
                        },
                        pairingButtonText = if (isTvDevice) "Pair with Phone" else "Pair with TV",
                        onOpenGuideClick = { openSetupGuide() },
                        options = signInOptions
                    )
                }

                is AppScreen.FolderPicker -> {
                    FolderPickerScreen(
                        folders = driveFolders.value,
                        isLoading = isFolderLoading.value,
                        isValidating = isFolderValidating.value,
                        errorMessage = folderErrorMessage.value,
                        onFolderSelected = { folder -> onFolderChosen(folder) },
                        onRefreshClick = {
                            val creds = credentialStore.getCredentials()
                            if (creds != null) {
                                if (isSwitchingLibrary.value) {
                                    loadFoldersForSwitch(creds.refreshToken)
                                } else {
                                    refreshTokenAndLoadFolders(creds.refreshToken)
                                }
                            }
                        },
                        onSignOutClick = {
                            handleSignOut()
                        },
                        currentFolderId = if (isSwitchingLibrary.value) {
                            credentialStore.getCredentials()?.rootFolderId
                        } else null,
                        busyMessage = if (isSwitchingLibrary.value) "Indexing library folder..." else null,
                        onCancelClick = if (isSwitchingLibrary.value) {
                            { cancelLibrarySwitch() }
                        } else null,
                        onOpenGuideClick = { openSetupGuide() }
                    )
                }

                is AppScreen.Home -> {
                    if (isTvDevice) {
                        TvHomeScreen(
                            state = homeState,
                            onMovieClick = { movie -> currentScreen.value = AppScreen.MovieDetails(movie) },
                            onSeriesClick = { series -> currentScreen.value = AppScreen.SeriesDetails(series) },
                            onContinueWatchingClick = { item ->
                                launchPlayer(item.id, ContinueWatching.playerTitle(item), item.positionMs)
                            },
                            onFilmsHeaderClick = {
                                val gridItems = homeState.movies.map {
                                    TvGridItem(it.id, it.title, it.posterPath, it.backdropPath)
                                }
                                currentScreen.value = AppScreen.TvGrid("Films", gridItems)
                            },
                            onSeriesHeaderClick = {
                                val gridItems = homeState.series.map {
                                    TvGridItem(it.id, it.title, it.posterPath, it.backdropPath)
                                }
                                currentScreen.value = AppScreen.TvGrid("Series", gridItems)
                            },
                            onSearchClick = {
                                currentScreen.value = AppScreen.TvSearch
                            },
                            onAccountClick = {
                                showAccountDialog.value = true
                            }
                        )
                    } else {
                        // Mobile Layout with Bottom Navigation
                        Scaffold(
                            bottomBar = {
                                MobileBottomNav(
                                    selectedTab = selectedMobileTab,
                                    onTabSelected = { selectedMobileTab = it }
                                )
                            },
                            containerColor = Color(0xFF0C0D10)
                        ) { innerPadding ->
                            when (selectedMobileTab) {
                                MobileTab.HOME -> {
                                    MobileHomeScreen(
                                        state = homeState,
                                        onMovieClick = { movie -> currentScreen.value = AppScreen.MovieDetails(movie) },
                                        onSeriesClick = { series -> currentScreen.value = AppScreen.SeriesDetails(series) },
                                        onContinueWatchingClick = { item ->
                                            launchPlayer(item.id, ContinueWatching.playerTitle(item), item.positionMs)
                                        },
                                        onPairTvClick = { openPhonePairingDialog() },
                                        onAccountClick = { showAccountDialog.value = true },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                MobileTab.LIBRARY -> {
                                    FullLibraryGridScreen(
                                        movies = homeState.movies,
                                        series = homeState.series,
                                        onMovieClick = { movie -> currentScreen.value = AppScreen.MovieDetails(movie) },
                                        onSeriesClick = { series -> currentScreen.value = AppScreen.SeriesDetails(series) },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                                MobileTab.SEARCH -> {
                                    SearchScreen(
                                        state = searchState,
                                        onQueryChange = { q -> searchViewModel.onQueryChanged(q) },
                                        onClearQuery = { searchViewModel.clearQuery() },
                                        onMovieClick = { movie -> currentScreen.value = AppScreen.MovieDetails(movie) },
                                        onSeriesClick = { series -> currentScreen.value = AppScreen.SeriesDetails(series) },
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }
                        }
                    }
                }

                is AppScreen.MovieDetails -> {
                    val detailsViewModel = remember {
                        DetailsViewModel(database.mediaDao(), database.playbackProgressDao()) { database.mediaSourceDao().getLatestProgressForItem(it) }
                    }
                    LaunchedEffect(screen.movie.id) {
                        detailsViewModel.loadMovie(screen.movie.id)
                    }
                    val detailsState by detailsViewModel.movieState.collectAsStateWithLifecycle()

                    MovieDetailsScreen(
                        movie = detailsState.movie ?: screen.movie,
                        progress = detailsState.progress,
                        onPlayClick = { startPos ->
                            launchPlayer(screen.movie.id, screen.movie.title, startPos)
                        },
                        onBackClick = { currentScreen.value = AppScreen.Home }
                    )
                }

                is AppScreen.SeriesDetails -> {
                    val detailsViewModel = remember {
                        DetailsViewModel(database.mediaDao(), database.playbackProgressDao()) { database.mediaSourceDao().getLatestProgressForItem(it) }
                    }
                    LaunchedEffect(screen.series.id) {
                        detailsViewModel.loadSeries(screen.series.id)
                    }
                    val detailsState by detailsViewModel.seriesState.collectAsStateWithLifecycle()

                    SeriesDetailsScreen(
                        series = detailsState.series ?: screen.series,
                        state = detailsState,
                        onSeasonSelect = { season -> detailsViewModel.selectSeason(season) },
                        onEpisodeClick = { episode ->
                            val fileId = episode.id
                            if (!fileId.isNullOrEmpty()) {
                                val currentSeries = detailsState.series ?: screen.series
                                val epTitle = if (episode.title.isNotEmpty()) {
                                    "${currentSeries.title} - S${episode.season}E${episode.episode} ${episode.title}"
                                } else {
                                    "${currentSeries.title} - S${episode.season}E${episode.episode}"
                                }
                                launchPlayer(fileId, epTitle, episode.positionMs)
                            }
                        },
                        onBackClick = { currentScreen.value = AppScreen.Home }
                    )
                }

                is AppScreen.TvGrid -> {
                    TvCollectionGridScreen(
                        title = screen.title,
                        items = screen.items,
                        onItemClick = { gridItem ->
                            val movie = homeState.movies.find { it.id == gridItem.id }
                            if (movie != null) {
                                currentScreen.value = AppScreen.MovieDetails(movie)
                            } else {
                                val series = homeState.series.find { it.id == gridItem.id }
                                if (series != null) {
                                    currentScreen.value = AppScreen.SeriesDetails(series)
                                }
                            }
                        },
                        onBackClick = { currentScreen.value = AppScreen.Home }
                    )
                }

                is AppScreen.Duplicates -> {
                    DuplicatesScreen(
                        reviews = duplicateReviews.value,
                        onSameTitle = { sourceKey, itemId ->
                            resolveDuplicate { database.mediaSourceDao().confirmSameTitle(sourceKey, itemId) }
                        },
                        onKeepSeparate = { sourceKey ->
                            resolveDuplicate { database.mediaSourceDao().deletePendingMatch(sourceKey) }
                        },
                        onBackClick = { currentScreen.value = AppScreen.Home }
                    )
                }

                is AppScreen.SetupGuide -> {
                    SetupGuideScreen(onBackClick = { closeSetupGuide() })
                }

                is AppScreen.TvSearch -> {
                    TvSearchScreen(
                        state = searchState,
                        onQueryChange = { q -> searchViewModel.onQueryChanged(q) },
                        onClearQuery = { searchViewModel.clearQuery() },
                        onMovieClick = { movie -> currentScreen.value = AppScreen.MovieDetails(movie) },
                        onSeriesClick = { series -> currentScreen.value = AppScreen.SeriesDetails(series) },
                        onBackClick = { currentScreen.value = AppScreen.Home }
                    )
                }
            }

            // TV Pairing Dialog
            if (showTvPairingDialog.value) {
                TvPairingDialog(
                    pin = tvPairingPin.value,
                    statusText = tvPairingStatus.value,
                    serverAddress = tvPairingAddress.value,
                    onDismissRequest = {
                        tvPairingServer?.stopServer()
                        showTvPairingDialog.value = false
                    }
                )
            }

            // Phone Pairing Dialog
            if (showPhonePairingDialog.value) {
                PhonePairingDialog(
                    isPairing = isPhonePairing.value,
                    errorMessage = phonePairingError.value,
                    onPairSubmit = { pin, host, port ->
                        val creds = credentialStore.getCredentials()
                        if (creds != null && creds.refreshToken.isNotEmpty()) {
                            isPhonePairing.value = true
                            phonePairingError.value = null
                            lifecycleScope.launch {
                                val client = PhonePairingClient(this@MainActivity)
                                val result = client.pairWithTv(host, port, pin, creds)
                                isPhonePairing.value = false
                                result.fold(
                                    onSuccess = {
                                        showPhonePairingDialog.value = false
                                        Toast.makeText(this@MainActivity, "Paired successfully with TV!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { err ->
                                        phonePairingError.value = when (err) {
                                            is PairingRejectedException, is TvUnreachableException -> err.message
                                            else -> "Pairing failed: ${err.message}"
                                        }
                                    }
                                )
                            }
                        } else {
                            phonePairingError.value = "Sign in on phone first before pairing"
                        }
                    },
                    onDismissRequest = { showPhonePairingDialog.value = false }
                )
            }

            // Google Account Dialog
            if (showAccountDialog.value) {
                AccountDialog(
                    account = homeState.account,
                    onSignOutClick = {
                        showAccountDialog.value = false
                        handleSignOut()
                    },
                    onSwitchLibraryClick = if (homeState.account.isConnected) {
                        { startLibrarySwitch() }
                    } else null,
                    onRefreshLibraryClick = if (homeState.account.isConnected) {
                        { refreshLibrary() }
                    } else null,
                    isRefreshingLibrary = isRefreshingLibrary.value,
                    onOpenGuideClick = {
                        showAccountDialog.value = false
                        openSetupGuide()
                    },
                    onAddLocalFolderClick = { launchLocalFolderPicker() },
                    savedLocalFolders = localFoldersState.value,
                    onRemoveLocalFolderClick = { folderUri ->
                        lifecycleScope.launch {
                            localFoldersState.value = withContext(Dispatchers.IO) {
                                localFolderPreferences.removeFolderUri(folderUri)
                                // Copies in the removed folder stop being playable; titles/progress are kept.
                                localIndexer.retireUnsavedFolders(localFolderPreferences.getAllFolderUris())
                                localFolderPreferences.getAllFolders()
                            }
                        }
                    },
                    pendingDuplicateCount = pendingDuplicateCount.value,
                    onReviewDuplicatesClick = {
                        showAccountDialog.value = false
                        openDuplicateReview()
                    },
                    isTvDevice = isTvDevice,
                    isTvStorageSupported = isTvStorageSupported,
                    onTvStorageUnsupportedClick = {
                        showTvStorageUnsupportedDialog.value = true
                    },
                    onDismissRequest = { showAccountDialog.value = false }
                )
            }

            // TV Storage Unsupported Dialog
            if (showTvStorageUnsupportedDialog.value) {
                TvStorageUnsupportedDialog(
                    onDismissRequest = { showTvStorageUnsupportedDialog.value = false }
                )
            }
        }
    }

    private fun launchGoogleSignIn() {
        if (!signInOptions.showGoogleSignIn) {
            // TV without Play Services: the Google sign-in flow would fail, pairing is the way in.
            authErrorMessage.value = SignInOptions.NO_PLAY_SERVICES_NOTICE
            return
        }
        try {
            isAuthLoading.value = true
            authErrorMessage.value = null
            if (isTvDevice) {
                val intent = authManager.getTvSignInIntent(this)
                tvSignInLauncher.launch(intent)
            } else {
                val intent = authManager.createAuthRequestIntent(this, GOOGLE_CLIENT_ID)
                authResultLauncher.launch(intent)
            }
        } catch (e: Exception) {
            isAuthLoading.value = false
            authErrorMessage.value = "Failed to launch sign-in: ${e.message}"
        }
    }

    private fun loadFoldersAndProceed(accessToken: String) {
        isFolderLoading.value = true
        folderErrorMessage.value = null
        fetchAndCacheUserIdentity(accessToken)

        lifecycleScope.launch {
            // 1. Attempt zero-click auto-discovery for standard Streaming folder
            val autoDiscovered = driveGateway.autoDiscoverStreamingFolder(accessToken).getOrNull()
            if (autoDiscovered != null) {
                val existingCreds = credentialStore.getCredentials()
                if (existingCreds != null) {
                    credentialStore.saveCredentials(existingCreds.copy(rootFolderId = autoDiscovered.id))
                }
                isFolderLoading.value = false
                currentScreen.value = AppScreen.Home
                triggerBackgroundSync(existingCreds?.refreshToken ?: "", autoDiscovered.id)
                return@launch
            }

            // 2. Fall back to manual folder picker if auto-discovery finds 0 or ambiguous matches
            currentScreen.value = AppScreen.FolderPicker
            val result = driveGateway.listRootFolders(accessToken)
            isFolderLoading.value = false
            result.fold(
                onSuccess = { folders ->
                    driveFolders.value = folders
                },
                onFailure = { err ->
                    folderErrorMessage.value = "Could not list Google Drive folders: ${err.message}"
                }
            )
        }
    }

    private fun refreshTokenAndLoadFolders(refreshToken: String) {
        isFolderLoading.value = true
        authManager.refreshAccessToken(this, GOOGLE_CLIENT_ID, refreshToken) { result ->
            result.fold(
                onSuccess = { token ->
                    cachedAccessToken = token
                    loadFoldersAndProceed(token)
                },
                onFailure = { err ->
                    runOnUiThread {
                        isFolderLoading.value = false
                        folderErrorMessage.value = "Token refresh failed: ${err.message}"
                    }
                }
            )
        }
    }

    private fun onFolderChosen(folder: DriveFolder) {
        if (isSwitchingLibrary.value) {
            switchLibraryFolder(folder)
            return
        }
        val token = cachedAccessToken
        if (token.isEmpty()) {
            folderErrorMessage.value = "Missing session token. Please re-authenticate."
            return
        }

        isFolderValidating.value = true
        folderErrorMessage.value = null

        lifecycleScope.launch {
            val result = driveGateway.validateStreamingFolder(folder.id, token)
            isFolderValidating.value = false

            result.fold(
                onSuccess = { validation ->
                    when (validation) {
                        is FolderValidationResult.Valid -> {
                            // Save folder and proceed to Home
                            val existingCreds = credentialStore.getCredentials()
                            if (existingCreds != null) {
                                credentialStore.saveCredentials(
                                    existingCreds.copy(rootFolderId = folder.id)
                                )
                            }
                            currentScreen.value = AppScreen.Home
                            triggerBackgroundSync(existingCreds?.refreshToken ?: "", folder.id)
                        }
                        is FolderValidationResult.Invalid -> {
                            folderErrorMessage.value = "Selected folder '${folder.name}' is invalid: ${validation.reason}. Ensure it contains video files or 'Films' / 'Series' subfolders."
                        }
                    }
                },
                onFailure = { err ->
                    folderErrorMessage.value = "Validation failed: ${err.message}"
                }
            )
        }
    }

    private fun openSetupGuide() {
        val from = currentScreen.value
        if (from !is AppScreen.SetupGuide) {
            currentScreen.value = AppScreen.SetupGuide(returnTo = from)
        }
    }

    private fun closeSetupGuide() {
        (currentScreen.value as? AppScreen.SetupGuide)?.let { currentScreen.value = it.returnTo }
    }

    private fun startLibrarySwitch() {
        showAccountDialog.value = false
        val creds = credentialStore.getCredentials()
        if (creds == null || creds.refreshToken.isEmpty()) {
            handleSignOut()
            return
        }
        isSwitchingLibrary.value = true
        driveFolders.value = emptyList()
        folderErrorMessage.value = null
        currentScreen.value = AppScreen.FolderPicker
        loadFoldersForSwitch(creds.refreshToken)
    }

    /** Lists owned + shared folders without auto-discovery, reusing the stored session (no re-auth). */
    private fun loadFoldersForSwitch(refreshToken: String) {
        folderSwitchJob?.cancel()
        isFolderValidating.value = false
        isFolderLoading.value = true
        folderErrorMessage.value = null
        folderSwitchJob = lifecycleScope.launch {
            val token = obtainAccessToken(refreshToken).getOrNull()
            if (token.isNullOrEmpty()) {
                isFolderLoading.value = false
                folderErrorMessage.value = "Couldn't refresh your Google session. Check your connection and try again."
                return@launch
            }
            cachedAccessToken = token
            val result = driveGateway.listRootFolders(token)
            isFolderLoading.value = false
            result.fold(
                onSuccess = { folders -> driveFolders.value = folders },
                onFailure = { err ->
                    folderErrorMessage.value = LibraryFolderSwitcher.describeDriveError("your Google Drive", err)
                }
            )
        }
    }

    private fun switchLibraryFolder(folder: DriveFolder) {
        if (folder.id == credentialStore.getCredentials()?.rootFolderId) {
            finishLibrarySwitch()
            return
        }
        folderSwitchJob?.cancel()
        // Stop any in-flight sync of the previous folder so it can't write stale results after the switch.
        librarySyncJob?.cancel()
        isFolderValidating.value = true
        folderErrorMessage.value = null
        folderSwitchJob = lifecycleScope.launch {
            val result = folderSwitcher.switchTo(folder, cachedAccessToken)
            isFolderValidating.value = false
            when (result) {
                is FolderSwitchResult.Switched -> {
                    finishLibrarySwitch()
                    val message = if (result.itemCount == 0) {
                        "Switched to '${folder.name}'. No videos found yet."
                    } else {
                        "Switched to '${folder.name}'"
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    librarySyncJob = lifecycleScope.launch(Dispatchers.IO) {
                        syncEngine.enrichExistingCatalog()
                    }
                }
                is FolderSwitchResult.Rejected -> {
                    folderErrorMessage.value = result.message
                }
            }
        }
    }

    private fun cancelLibrarySwitch() {
        folderSwitchJob?.cancel()
        folderSwitchJob = null
        finishLibrarySwitch()
    }

    private fun finishLibrarySwitch() {
        isSwitchingLibrary.value = false
        isFolderLoading.value = false
        isFolderValidating.value = false
        folderErrorMessage.value = null
        currentScreen.value = AppScreen.Home
    }

    private suspend fun obtainAccessToken(refreshToken: String): Result<String> =
        suspendCancellableCoroutine { cont ->
            authManager.refreshAccessToken(this, GOOGLE_CLIENT_ID, refreshToken) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }

    /** "Refresh Library" in the Account dialog: same sync as app start, on demand (Task H.1). */
    private fun refreshLibrary() {
        val credentials = credentialStore.getCredentials() ?: return
        if (isRefreshingLibrary.value || credentials.rootFolderId.isEmpty()) return
        librarySyncJob?.cancel()
        isRefreshingLibrary.value = true
        librarySyncJob = lifecycleScope.launch {
            // Always fetch a fresh token: the cached one may have expired while the app was open.
            val token = obtainAccessToken(credentials.refreshToken).getOrNull()
            if (!token.isNullOrEmpty()) cachedAccessToken = token
            val result = withContext(Dispatchers.IO) { libraryRefresher.refresh(credentials.rootFolderId, token) }
            isRefreshingLibrary.value = false
            val message = when (result) {
                LibraryRefreshResult.Refreshed -> "Library refreshed"
                is LibraryRefreshResult.Failed -> result.message
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }.also { job -> job.invokeOnCompletion { isRefreshingLibrary.value = false } }
    }

    private fun triggerBackgroundSync(refreshToken: String, rootFolderId: String) {
        librarySyncJob?.cancel()
        librarySyncJob = lifecycleScope.launch(Dispatchers.IO) {
            // Enrich any existing catalog items in SQLite immediately
            syncEngine.enrichExistingCatalog()

            // Suspends instead of blocking a thread, and waits long enough for a slow network (report F-06).
            val token = StartupToken.obtain(cachedAccessToken, refresh = { obtainAccessToken(refreshToken) })
            if (token != null) {
                cachedAccessToken = token
                fetchAndCacheUserIdentity(token)
                val validation = driveGateway.validateStreamingFolder(rootFolderId, token).getOrNull()
                if (validation is FolderValidationResult.Valid) {
                    syncEngine.executeInitialCrawl(validation.filmsFolderId, validation.seriesFolderId, token, rootFolderId)
                        .onSuccess { StartupSyncGate.process.markSynced(rootFolderId) }
                }
            }
        }
    }

    private fun startTvPairing() {
        val server = TvPairingServer(this) { creds ->
            runOnUiThread {
                credentialStore.saveCredentials(creds)
                showTvPairingDialog.value = false
                if (creds.rootFolderId.isNotEmpty()) {
                    currentScreen.value = AppScreen.Home
                    triggerBackgroundSync(creds.refreshToken, creds.rootFolderId)
                } else {
                    currentScreen.value = AppScreen.FolderPicker
                    refreshTokenAndLoadFolders(creds.refreshToken)
                }
            }
        }
        tvPairingServer = server
        val pin = server.generatePin()
        tvPairingPin.value = pin
        tvPairingStatus.value = "Starting pairing server..."
        tvPairingAddress.value = null
        showTvPairingDialog.value = true

        lifecycleScope.launch(Dispatchers.IO) {
            server.startServer(
                pin = pin,
                onListening = { port ->
                    runOnUiThread {
                        val ip = server.serverIp ?: "192.168.1.154"
                        tvPairingAddress.value = if (port == TvPairingServer.DEFAULT_PORT) ip else "$ip:$port"
                        tvPairingStatus.value = "Listening on Wi-Fi ($ip:$port)..."
                    }
                },
                onSuccess = { creds ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "TV Paired Successfully!", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        tvPairingStatus.value = err
                    }
                }
            )
        }
    }

    /** The player returns the next episode's catalog ID when "Next Episode" is pressed; play it like any other title. */
    private val playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        val nextId = data.getStringExtra(PlayerActivity.EXTRA_NEXT_EPISODE_ID) ?: return@registerForActivityResult
        launchPlayer(nextId, data.getStringExtra(PlayerActivity.EXTRA_NEXT_EPISODE_TITLE) ?: "Next Episode")
    }

    /** (catalog ID, label) of the episode after [mediaItemId], or null for films and last episodes. */
    private suspend fun findNextEpisode(mediaItemId: String): Pair<String, String>? {
        val dao = database.mediaDao()
        val seriesId = dao.getMediaItemById(mediaItemId)?.seriesId ?: return null
        val next = NextEpisode.after(dao.getEpisodesForSeries(seriesId), mediaItemId) ?: return null
        val seriesTitle = dao.getSeriesById(seriesId)?.title.orEmpty()
        return next.id to NextEpisode.label(seriesTitle, next)
    }

    /** Plays a catalog title from its best available copy (local first, then Drive), resuming across copies. */
    private fun launchPlayer(mediaItemId: String, title: String, startPosMs: Long = 0L) {
        lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) { playbackResolver.resolve(mediaItemId, startPosMs) }
            val next = withContext(Dispatchers.IO) { findNextEpisode(mediaItemId) }
            when (resolved) {
                is ResolvedPlayback.Unavailable -> Toast.makeText(
                    this@MainActivity,
                    "'$title' isn't available right now — its local folder may be removed or disconnected.",
                    Toast.LENGTH_LONG
                ).show()
                is ResolvedPlayback.Playable -> {
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
                        when (val source = resolved.source) {
                            is PlaybackSource.Local -> putExtra(PlayerActivity.EXTRA_LOCAL_CONTENT_URI, source.contentUri)
                            is PlaybackSource.Drive -> putExtra(PlayerActivity.EXTRA_DRIVE_FILE_ID, source.fileId)
                        }
                        putExtra(PlayerActivity.EXTRA_TITLE, title)
                        putExtra(PlayerActivity.EXTRA_START_POSITION_MS, resolved.resumePositionMs)
                        putExtra(PlayerActivity.EXTRA_ACCESS_TOKEN, cachedAccessToken)
                        if (next != null) {
                            putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_ID, next.first)
                            putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_TITLE, next.second)
                        }
                    }
                    playerLauncher.launch(intent)
                }
            }
        }
    }

    private fun handleSignOut() {
        if (isTvDevice) {
            try {
                val client = GoogleSignIn.getClient(this, authManager.buildTvSignInOptions())
                client.signOut()
            } catch (_: Exception) {}
        }
        folderSwitchJob?.cancel()
        librarySyncJob?.cancel()
        isSwitchingLibrary.value = false
        credentialStore.clear()
        cachedAccessToken = ""
        homeViewModel.updateAccount(AccountUiState(isConnected = false))
        currentScreen.value = AppScreen.SignIn
    }

    private fun fetchAndCacheUserIdentity(accessToken: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            android.util.Log.i("MainActivity", "fetchAndCacheUserIdentity invoked")
            val result = driveGateway.getAboutUser(accessToken)
            result.fold(
                onSuccess = { user ->
                    android.util.Log.i("MainActivity", "fetchAndCacheUserIdentity succeeded: name=${user.displayName}, email=${user.emailAddress}")
                    val existing = credentialStore.getCredentials()
                    if (existing != null) {
                        credentialStore.saveCredentials(
                            existing.copy(
                                userDisplayName = user.displayName,
                                userEmail = user.emailAddress,
                                userPhotoLink = user.photoLink
                            )
                        )
                    }
                    homeViewModel.updateAccount(
                        AccountUiState(
                            displayName = user.displayName,
                            email = user.emailAddress,
                            photoLink = user.photoLink,
                            isConnected = true
                        )
                    )
                },
                onFailure = { error ->
                    android.util.Log.w("MainActivity", "fetchAndCacheUserIdentity failed: ${error.message}")
                    val existing = credentialStore.getCredentials()
                    if (existing != null && existing.refreshToken.isNotEmpty()) {
                        val fallbackEmail = if (existing.refreshToken.startsWith(GoogleAuthManager.TV_ACCOUNT_PREFIX)) {
                            existing.refreshToken.removePrefix(GoogleAuthManager.TV_ACCOUNT_PREFIX)
                        } else null
                        homeViewModel.updateAccount(
                            AccountUiState(
                                displayName = existing.userDisplayName,
                                email = existing.userEmail ?: fallbackEmail,
                                photoLink = existing.userPhotoLink,
                                isConnected = true
                            )
                        )
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tvPairingServer?.stopServer()
    }
}
