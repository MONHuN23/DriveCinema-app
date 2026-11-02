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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.drivecinema.app.core.auth.GoogleAuthManager
import com.drivecinema.app.core.devicesync.AppDataClient
import com.drivecinema.app.core.devicesync.DeviceSync
import com.drivecinema.app.core.devicesync.DeviceSyncController
import com.drivecinema.app.core.devicesync.DeviceSyncRunner.Trigger
import com.drivecinema.app.core.devicesync.DeviceSyncSetting
import com.drivecinema.app.ui.setup.DeviceSyncTexts
import com.drivecinema.app.core.auth.TvSignInErrors
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.getInstance
import com.drivecinema.app.core.device.SystemPlayServicesChecker
import com.drivecinema.app.core.logging.AndroidLogSink
import com.drivecinema.app.core.logging.AndroidPerfTracer
import com.drivecinema.app.core.logging.DebugPerf
import com.drivecinema.app.core.logging.Perf
import com.drivecinema.app.core.logging.PerfSpan
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.device.TvDeviceDetector
import com.drivecinema.app.core.local.AddFolderResult
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
import com.drivecinema.app.core.remote.DeviceLink
import com.drivecinema.app.core.remote.DeviceLinkStore
import com.drivecinema.app.core.remote.MediaDaoPlayCatalog
import com.drivecinema.app.core.remote.PairingLink
import com.drivecinema.app.core.remote.PendingPlay
import com.drivecinema.app.core.remote.PhonePlayOnTv
import com.drivecinema.app.core.remote.PlayOnTvMessages
import com.drivecinema.app.core.remote.PlayOnTvOutcome
import com.drivecinema.app.core.remote.PlayOnTvReceiver
import com.drivecinema.app.core.remote.PlayOnTvRouting
import com.drivecinema.app.core.remote.PlayStatus
import com.drivecinema.app.core.security.AuthCredentialStore
import com.drivecinema.app.core.security.AuthCredentials
import com.drivecinema.app.core.sync.DriveLibrarySyncEngine
import com.drivecinema.app.core.sync.FolderQueueCrawler
import com.drivecinema.app.ui.naming.NamingDeps
import com.drivecinema.app.core.sync.FolderSwitchResult
import com.drivecinema.app.core.sync.LibraryFolderSwitcher
import com.drivecinema.app.core.sync.LibraryRefreshResult
import com.drivecinema.app.core.sync.LibraryRefresher
import com.drivecinema.app.core.host.HostConnections
import com.drivecinema.app.core.host.HostDiscoveryClient
import com.drivecinema.app.core.host.HostLibrarySync
import com.drivecinema.app.core.host.HostPairingException
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.settings.PcConnection
import com.drivecinema.app.ui.settings.PcRow
import com.drivecinema.app.ui.settings.PcsActions
import com.drivecinema.app.ui.settings.PcsNotice
import com.drivecinema.app.core.sync.StartupSync
import com.drivecinema.app.core.sync.StartupSyncGate
import com.drivecinema.app.core.sync.StartupToken
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.core.player.NextEpisode
import com.drivecinema.app.core.player.PreviousEpisode
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.details.DetailsViewModel
import com.drivecinema.app.ui.details.screenViewModel
import com.drivecinema.app.ui.navigation.AppNavDisplay
import com.drivecinema.app.ui.navigation.AppNavigator
import com.drivecinema.app.ui.navigation.LibraryActions
import com.drivecinema.app.ui.navigation.Screen
import com.drivecinema.app.ui.mobile.home.MobileHomeViewModel
import com.drivecinema.app.ui.player.PlayerActivity
import com.drivecinema.app.ui.player.PlayerExit
import com.drivecinema.app.ui.search.SearchViewModel
import com.drivecinema.app.ui.mobile.home.AccountUiState
import android.net.Uri
import com.drivecinema.app.core.skipintro.SkipIntroSetting
import com.drivecinema.app.ui.settings.SettingsActions
import com.drivecinema.app.ui.settings.SettingsUiState
import com.drivecinema.app.ui.setup.PhonePairingDialog
import com.drivecinema.app.core.local.LocalLibraryCrawler
import com.drivecinema.app.core.local.LocalLibraryIndexer
import com.drivecinema.app.core.local.indexFolder
import com.drivecinema.app.core.local.retireUnsavedFolders
import com.drivecinema.app.core.local.SystemDocumentTreeSource
import com.drivecinema.app.ui.player.PlaybackResolver
import com.drivecinema.app.ui.player.PlaybackSource
import com.drivecinema.app.ui.player.ResolvedPlayback
import com.drivecinema.app.ui.setup.DuplicateReviews
import com.drivecinema.app.ui.setup.SetupActions
import com.drivecinema.app.ui.setup.SetupGuideContent
import com.drivecinema.app.ui.setup.SetupUiState
import com.drivecinema.app.ui.setup.SignInOptions
import com.drivecinema.app.ui.setup.TvPairingDialog
import com.drivecinema.app.ui.setup.LinkedDevicesDialog
import com.drivecinema.app.ui.setup.LinkedDevicesTexts
import com.drivecinema.app.ui.player.withStartPosition
import com.drivecinema.app.ui.tv.setup.TvStorageUnsupportedDialog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    companion object {
        /** From `GOOGLE_ANDROID_CLIENT_ID` in local.properties (Milestone J.5; see README "Build with your own keys"). */
        const val GOOGLE_CLIENT_ID = BuildConfig.GOOGLE_ANDROID_CLIENT_ID
    }

    private lateinit var credentialStore: AuthCredentialStore
    private val authManager = GoogleAuthManager()
    /** One client for every Drive metadata request (folder check, crawl, sync), so they share connections (N.1, F-12). */
    private val driveHttpClient = DebugPerf.httpClient("drive")
    private val driveGateway = GoogleDriveGateway(httpClient = driveHttpClient)
    private lateinit var database: DriveCinemaDatabase
    private lateinit var syncEngine: DriveLibrarySyncEngine
    private lateinit var folderSwitcher: LibraryFolderSwitcher
    /** Milestone R: what the naming helper and the Naming check use (TMDB, and series folders read read-only). */
    private lateinit var namingDeps: NamingDeps

    // Written by sync and refresh coroutines on IO threads, read on the main thread (report F-11).
    @Volatile
    private var cachedAccessToken = ""
    private var isTvDevice = false

    private lateinit var homeViewModel: MobileHomeViewModel
    /** Task P.2: what the Settings and Local folders screens show (was the Account dialog's state). */
    private val settings = SettingsUiState()
    /** Task I.2: what the last check found for each paired PC, by host id (My PCs' state is `settings.pcs`). */
    private val pcStatus = mutableMapOf<String, PcConnection>()
    /** Milestone Q: "Sync with my devices" (watch progress through the Drive app folder). */
    private lateinit var deviceSync: DeviceSyncController
    private var hasStartedOnce = false
    private lateinit var localFolderPreferences: LocalFolderPreferences
    private lateinit var tvStorageCapabilityChecker: TvStorageCapabilityChecker
    private val showTvStorageUnsupportedDialog = mutableStateOf(false)

    // Local library (Task C.4 phase 2)
    private lateinit var localIndexer: LocalLibraryIndexer
    private lateinit var playbackResolver: PlaybackResolver
    /** Task I.2: paired PCs (pairing, catalog sync, streaming). */
    private lateinit var hostSync: HostLibrarySync

    // App navigation (Task M.3: a Navigation 3 back stack, saved across rotation) & UI state
    private lateinit var navigator: AppNavigator
    private lateinit var searchViewModel: SearchViewModel
    /** Sign-in, folder picker (first run and library switching) and duplicate review state. */
    private val setup = SetupUiState()

    // In-app library switching (FolderPicker opened from Settings while signed in)
    private var folderSwitchJob: Job? = null
    private var librarySyncJob: Job? = null
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

    // Milestone D (Play on TV): the phone's linked TVs, the TV's linked phones, and the list dialog for either.
    private val linkedTvs = mutableStateOf<List<DeviceLink>>(emptyList())
    private val linkedPhones = mutableStateOf<List<DeviceLink>>(emptyList())
    private val showLinkedDevicesDialog = mutableStateOf(false)
    /** The TV's pairing dialog links a phone only (the TV is already signed in). */
    private val tvPairingLinkOnly = mutableStateOf(false)

    /** Opens the phone's "Pair with Android TV" dialog without the previous attempt's error message. */
    private fun openPhonePairingDialog() {
        phonePairingError.value = null
        showPhonePairingDialog.value = true
    }

    private var pendingTvAccount: Account? = null

    private val authResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val dataIntent = result.data
        if (dataIntent != null) {
            setup.isAuthLoading.value = true
            authManager.handleAuthResponse(this, dataIntent) { exchangeResult ->
                runOnUiThread {
                    setup.isAuthLoading.value = false
                    exchangeResult.fold(
                        onSuccess = { (accessToken, refreshToken) ->
                            cachedAccessToken = accessToken
                            credentialStore.saveCredentials(AuthCredentials(refreshToken = refreshToken, rootFolderId = ""))
                            setup.authErrorMessage.value = null
                            loadFoldersAndProceed(accessToken)
                        },
                        onFailure = { error ->
                            setup.authErrorMessage.value = "Sign in failed: ${error.message}"
                        }
                    )
                }
            }
        } else {
            setup.isAuthLoading.value = false
        }
    }

    /** Milestone Q: Google's consent page for the app folder scope, opened by turning "Sync with my devices" on (phone). */
    private val deviceSyncGrantLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val dataIntent = result.data
        if (dataIntent == null) {
            deviceSync.grantRefused("Sync stays off: the Google page was closed.")
            return@registerForActivityResult
        }
        authManager.handleAuthResponse(this, dataIntent) { exchangeResult ->
            lifecycleScope.launch {
                exchangeResult.fold(
                    onSuccess = { (accessToken, refreshToken) ->
                        val email = withContext(Dispatchers.IO) { driveGateway.getAboutUser(accessToken).getOrNull()?.emailAddress }
                        deviceSync.completeGrant(email, credentialStore.getCredentials()?.userEmail) {
                            // The new grant covers read-only + the app folder; folder and library stay as they are.
                            credentialStore.getCredentials()?.let { credentialStore.saveCredentials(it.copy(refreshToken = refreshToken)) }
                            cachedAccessToken = accessToken
                        }
                    },
                    onFailure = { error -> deviceSync.grantRefused("Sync stays off: ${error.message}") }
                )
            }
        }
    }

    private fun onDeviceSyncChange(enabled: Boolean) {
        when {
            !enabled -> deviceSync.disable()
            // TVs sign in by pairing only (J.6): they can sync when the phone's sign-in had the scope when it paired.
            isTvDevice -> lifecycleScope.launch { deviceSync.turnOn() }
            else -> try {
                val email = credentialStore.getCredentials()?.userEmail
                deviceSyncGrantLauncher.launch(
                    authManager.createAuthRequestIntent(this, GOOGLE_CLIENT_ID, GoogleAuthManager.DEVICE_SYNC_SCOPES, email)
                )
            } catch (e: Exception) {
                deviceSync.grantRefused("Couldn't open Google's page: ${e.message}")
            }
        }
    }

    private fun runDeviceSync(trigger: Trigger) {
        lifecycleScope.launch { deviceSync.syncNow(trigger) }
    }

    private val tvConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val account = pendingTvAccount
        if (result.resultCode == RESULT_OK && account != null) {
            setup.isAuthLoading.value = true
            authManager.fetchTvAccessToken(this, account) { exchangeResult ->
                runOnUiThread {
                    setup.isAuthLoading.value = false
                    exchangeResult.fold(
                        onSuccess = { (accessToken, refreshToken) ->
                            cachedAccessToken = accessToken
                            credentialStore.saveCredentials(AuthCredentials(refreshToken = refreshToken, rootFolderId = ""))
                            setup.authErrorMessage.value = null
                            loadFoldersAndProceed(accessToken)
                        },
                        onFailure = { error ->
                            AppLog.e("TvSignIn", "Drive token after TV consent failed", error)
                            setup.authErrorMessage.value = "TV authorization failed: ${error.message}"
                        }
                    )
                }
            }
        } else {
            setup.isAuthLoading.value = false
            if (result.resultCode != RESULT_OK) {
                setup.authErrorMessage.value = "Google Drive authorization was not granted"
            }
        }
    }

    private val tvSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val dataIntent = result.data
        if (dataIntent != null) {
            setup.isAuthLoading.value = true
            authManager.handleTvSignInResult(this, dataIntent) { exchangeResult ->
                runOnUiThread {
                    exchangeResult.fold(
                        onSuccess = { (accessToken, refreshToken) ->
                            setup.isAuthLoading.value = false
                            cachedAccessToken = accessToken
                            credentialStore.saveCredentials(AuthCredentials(refreshToken = refreshToken, rootFolderId = ""))
                            setup.authErrorMessage.value = null
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
                                        setup.isAuthLoading.value = false
                                        setup.authErrorMessage.value = "Missing consent intent for TV authorization"
                                    }
                                } else {
                                    setup.isAuthLoading.value = false
                                    setup.authErrorMessage.value = "Could not resolve TV Google account"
                                }
                            } else {
                                setup.isAuthLoading.value = false
                                AppLog.e("TvSignIn", "Google sign-in on the TV failed", error)
                                setup.authErrorMessage.value = TvSignInErrors.describe(error)
                            }
                        }
                    )
                }
            }
        } else {
            setup.isAuthLoading.value = false
            AppLog.w("TvSignIn", "Google sign-in returned no data (result code ${result.resultCode})")
            setup.authErrorMessage.value = TvSignInErrors.NO_RESULT
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
                settings.localFolders.value = updated
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
            settings.localFolders.value = withContext(Dispatchers.IO) { localFolderPreferences.getAllFolders() }
            scanLocalFolders(announce = false)
        }
    }

    /**
     * Re-indexes every saved local folder (folders that fail to list are left untouched) and retires copies in
     * folders that were removed or lost their permission. Never deletes anything.
     */
    private fun scanLocalFolders(announce: Boolean) {
        lifecycleScope.launch {
            settings.isScanningLocalFolders.value = true
            val counts = mutableMapOf<String, Int>()
            val summary = try {
                withContext(Dispatchers.IO) {
                    val saved = localFolderPreferences.getAllFolderUris()
                    localIndexer.retireUnsavedFolders(saved)
                    var found = 0
                    var linked = 0
                    var review = 0
                    var failed = 0
                    for (tree in saved) {
                        try {
                            val r = localIndexer.indexFolder(tree)
                            counts[tree.toString()] = r.videosFound
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
            } finally {
                // Local folders shows each folder's count; a folder that failed to list is left out ("Couldn't read").
                settings.localFolderVideoCounts.value = counts.toMap()
                settings.isScanningLocalFolders.value = false
            }
            if (announce) {
                val (found, linked, review, failed) = summary
                val text = buildString {
                    append(if (found == 0) "No videos found in your local folders" else "Found $found local videos")
                    if (linked > 0) append(", $linked matched titles you already have")
                    if (review > 0) append(" — $review to review in Settings")
                    if (failed > 0) append(" ($failed folder(s) could not be read)")
                }
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openDuplicateReview() {
        lifecycleScope.launch {
            setup.duplicateReviews.value = withContext(Dispatchers.IO) { DuplicateReviews.load(database.mediaSourceDao()) }
            navigator.open(Screen.Duplicates)
        }
    }

    private fun resolveDuplicate(action: suspend () -> Unit) {
        lifecycleScope.launch {
            setup.duplicateReviews.value = withContext(Dispatchers.IO) {
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
        Perf.tracer = AndroidPerfTracer
        DebugPerf.installImageLoader(this)
        enableEdgeToEdge()

        credentialStore = AuthCredentialStore(this)
        database = DriveCinemaDatabase.getInstance(this)
        val tmdbApiKey = credentialStore.getCredentials()?.tmdbApiKey?.ifEmpty { null }
            ?: BuildConfig.TMDB_API_KEY.ifEmpty { null }
        val tmdbClient = TmdbClient(httpClient = DebugPerf.httpClient("tmdb"), apiKey = tmdbApiKey ?: "")
        syncEngine = DriveLibrarySyncEngine(
            crawler = FolderQueueCrawler(httpClient = driveHttpClient),
            mediaDao = database.mediaDao(),
            syncMetadataDao = database.syncMetadataDao(),
            tmdbClient = tmdbClient,
            httpClient = driveHttpClient
        )
        folderSwitcher = LibraryFolderSwitcher(driveGateway, syncEngine, credentialStore)
        val namingCrawler = FolderQueueCrawler(httpClient = driveHttpClient)
        namingDeps = NamingDeps(
            tmdb = tmdbClient,
            tmdbAvailable = tmdbApiKey != null,
            readSeriesFolder = { seriesFolderId ->
                withContext(Dispatchers.IO) {
                    val refreshToken = credentialStore.getCredentials()?.refreshToken?.takeIf { it.isNotEmpty() }
                        ?: throw IOException("Not signed in")
                    val token = obtainAccessToken(refreshToken).getOrThrow()
                    cachedAccessToken = token
                    namingCrawler.readSeriesFolder(seriesFolderId, token)
                }
            }
        )
        localFolderPreferences = LocalFolderPreferences(this)
        tvStorageCapabilityChecker = SystemTvStorageCapabilityChecker(packageManager)
        localIndexer = LocalLibraryIndexer(database, LocalLibraryCrawler(SystemDocumentTreeSource(contentResolver)))
        hostSync = HostConnections.get(this)
        playbackResolver = PlaybackResolver(
            database.mediaSourceDao(),
            hostUrl = { key -> hostSync.stream(key, recheck = true)?.first },
            checkHost = { itemId, key -> hostSync.checkConnection(itemId, key) }
        )
        lifecycleScope.launch {
            database.mediaSourceDao().getPendingMatchesFlow().collect { settings.pendingDuplicateCount.value = it.size }
        }
        refreshLocalFolders()
        syncPcs()

        isTvDevice = TvDeviceDetector.isTv(this)
        // Milestone D: the TV listens for Play on TV while any of its screens is visible; the phone loads its TVs.
        if (isTvDevice) PlayOnTvReceiver.install(application) else refreshLinkedDevices()
        setup.signInOptions.value = SignInOptions.forDevice(isTvDevice, SystemPlayServicesChecker(this).isAvailable())
        // Queried once (PackageManager IPC), not on every recomposition of Settings.
        settings.canAddLocalFolders.value = !isTvDevice || tvStorageCapabilityChecker.isOpenDocumentTreeSupported()

        val deviceSyncSetting = DeviceSyncSetting(this)
        val appFolderSync = DeviceSync(database.deviceSyncDao(), AppDataClient(driveHttpClient), deviceSyncSetting.identity(isTvDevice))
        deviceSync = DeviceSyncController(
            switch = deviceSyncSetting,
            needsPermissionText = if (isTvDevice) DeviceSyncTexts.TV_NEEDS_PAIRING else DeviceSyncTexts.PHONE_NEEDS_PERMISSION,
            accessToken = {
                credentialStore.getCredentials()?.refreshToken?.takeIf { it.isNotEmpty() }
                    ?.let { obtainAccessToken(it).getOrNull() }
                    ?.also { cachedAccessToken = it }
            },
            sync = appFolderSync::run
        )
        // Settings shows the switch and its status line.
        lifecycleScope.launch { deviceSync.enabled.collect { settings.deviceSyncEnabled.value = it } }
        lifecycleScope.launch { deviceSync.status.collect { settings.deviceSyncStatus.value = it } }
        // Back in front (not the first start, which syncs after the library): pick up what other devices played.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            if (hasStartedOnce) runDeviceSync(Trigger.FOREGROUND)
            hasStartedOnce = true
        })

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
        // Kept in the activity's ViewModelStore (Task M.3): a rotation keeps Home's rows and the search text.
        homeViewModel = screenViewModel(viewModelStore, MobileHomeViewModel::class.java) {
            MobileHomeViewModel(
                database.mediaDao(),
                initialAccount = initialAccount,
                continueWatching = ContinueWatching.flow(database),
                // Only the phone's Library has watch-state chips.
                watchHistory = if (isTvDevice) kotlinx.coroutines.flow.flowOf(emptyList())
                else database.mediaSourceDao().getWatchHistoryFlow()
            )
        }
        searchViewModel = screenViewModel(viewModelStore, SearchViewModel::class.java) { SearchViewModel(database.mediaDao()) }

        val root = when {
            creds == null || creds.refreshToken.isEmpty() -> Screen.SignIn
            creds.rootFolderId.isNotEmpty() -> Screen.Home
            else -> Screen.FolderPicker
        }
        navigator = AppNavigator.attach(savedStateRegistry, root)
        if (creds != null && root == Screen.Home) {
            // A rotation recreates this activity; don't re-read the whole library each time (report F-04).
            if (StartupSyncGate.process.shouldSync(creds.rootFolderId, isRecreated = savedInstanceState != null)) {
                triggerBackgroundSync(creds.refreshToken, creds.rootFolderId)
            }
        } else if (creds != null && root == Screen.FolderPicker) {
            refreshTokenAndLoadFolders(creds.refreshToken)
        }

        setContent {
            DriveCinemaTheme(isTv = isTvDevice) {
                MainAppContent()
            }
        }
        handlePlayOnTvIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayOnTvIntent(intent)
    }

    /** Milestone D, TV: plays what the receiver accepted, at the phone's position (only with the receiver's token). */
    private fun handlePlayOnTvIntent(intent: Intent?) {
        val token = intent?.getStringExtra(PendingPlay.EXTRA_TOKEN) ?: return
        intent.removeExtra(PendingPlay.EXTRA_TOKEN)
        val play = PendingPlay.take(token) ?: return
        showLinkedDevicesDialog.value = false
        launchPlayer(play.mediaItemId, play.title, play.positionMs, forcePosition = true)
    }

    private fun refreshLinkedDevices() {
        lifecycleScope.launch {
            if (isTvDevice) {
                linkedPhones.value = withContext(Dispatchers.IO) { DeviceLinkStore.phones(this@MainActivity).links() }
            } else {
                linkedTvs.value = withContext(Dispatchers.IO) { DeviceLinkStore.tvs(this@MainActivity).links() }
            }
        }
    }

    /** Milestone D, phone: sends [mediaItemId] to the linked TV (a chooser when there are several, linking when none). */
    private fun playOnTv(mediaItemId: String, title: String) {
        val tvs = linkedTvs.value
        when (tvs.size) {
            0 -> openPhonePairingDialog()
            1 -> sendToTv(tvs[0], mediaItemId, title)
            else -> android.app.AlertDialog.Builder(this)
                .setTitle("Play on which TV?")
                .setItems(tvs.map { it.name }.toTypedArray()) { _, which -> sendToTv(tvs[which], mediaItemId, title) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun sendToTv(tv: DeviceLink, mediaItemId: String, title: String) {
        Toast.makeText(this, "Sending to ${tv.name}…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val request = withContext(Dispatchers.IO) {
                PlayOnTvRouting.buildRequest(MediaDaoPlayCatalog(database.mediaDao()), database.mediaSourceDao(), mediaItemId, title)
            }
            val outcome = PhonePlayOnTv(this@MainActivity).send(tv, request)
            val ok = outcome == PlayOnTvOutcome.Answered(PlayStatus.OK)
            Toast.makeText(this@MainActivity, PlayOnTvMessages.of(outcome, tv.name, title), if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            refreshLinkedDevices()
        }
    }

    private val libraryActions by lazy {
        LibraryActions(
            play = { mediaItemId, title, startPositionMs -> launchPlayer(mediaItemId, title, startPositionMs) },
            pairTv = ::openPhonePairingDialog,
            playOnTv = ::playOnTv,
            canPlayOnTv = { !isTvDevice && linkedTvs.value.isNotEmpty() },
            onHomeReady = ::reportFullyDrawnOnce
        )
    }

    /** Task P.2: what the Settings and Local folders screens do (the Account dialog's callbacks before). */
    private val settingsActions by lazy {
        SettingsActions(
            refreshLibrary = ::refreshLibrary,
            switchLibraryFolder = ::startLibrarySwitch,
            reviewDuplicates = ::openDuplicateReview,
            openLocalFolders = { navigator.open(Screen.LocalFolders) },
            addLocalFolder = ::launchLocalFolderPicker,
            removeLocalFolder = ::removeLocalFolder,
            openFoldersHelp = { navigator.open(Screen.HelpTopic(SetupGuideContent.FOLDERS)) },
            openPcs = { navigator.open(Screen.Pcs) },
            pairTv = ::openPhonePairingDialog,
            setSkipIntro = { enabled ->
                settings.skipIntroEnabled.value = enabled
                SkipIntroSetting(this).setEnabled(enabled)
            },
            setDeviceSync = ::onDeviceSyncChange,
            openLinkedDevices = {
                refreshLinkedDevices()
                showLinkedDevicesDialog.value = true
            },
            openNamingHelper = { navigator.open(Screen.NamingHelper) },
            openHelp = ::openSetupGuide,
            openAbout = { navigator.open(Screen.HelpTopic(SetupGuideContent.ABOUT)) },
            onOpen = {
                settings.skipIntroEnabled.value = SkipIntroSetting(this).isEnabled()
                updatePcRows()
            },
            signOut = ::handleSignOut,
            back = { navigator.back() }
        )
    }

    /** Task P.3: what My PCs does (the popup's callbacks before); pairing and the connections themselves are unchanged. */
    private val pcsActions by lazy {
        PcsActions(
            search = ::searchPcs,
            pair = ::pairPc,
            setVpnAddress = { hostId, address ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { hostSync.setVpnAddress(hostId, address) }
                    updatePcRows()
                    syncPcs()
                }
            },
            remove = { hostId ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { hostSync.remove(hostId) }
                    pcStatus.remove(hostId)
                    updatePcRows()
                }
            },
            checkNow = ::syncPcs,
            onOpen = {
                settings.pcs.notice.value = null
                updatePcRows()
            },
            back = { navigator.back() }
        )
    }

    /** Copies in the removed folder stop being playable; titles and watch progress are kept. */
    private fun removeLocalFolder(folderUri: Uri) {
        lifecycleScope.launch {
            settings.localFolders.value = withContext(Dispatchers.IO) {
                localFolderPreferences.removeFolderUri(folderUri)
                localIndexer.retireUnsavedFolders(localFolderPreferences.getAllFolderUris())
                localFolderPreferences.getAllFolders()
            }
        }
    }

    private val setupActions by lazy {
        SetupActions(
            signIn = ::launchGoogleSignIn,
            pair = { if (isTvDevice) startTvPairing() else openPhonePairingDialog() },
            openGuide = ::openSetupGuide,
            openHelpTopic = { id -> navigator.open(Screen.HelpTopic(id)) },
            chooseFolder = ::onFolderChosen,
            reloadFolders = {
                val creds = credentialStore.getCredentials()
                if (creds != null) {
                    if (setup.isSwitchingLibrary.value) {
                        loadFoldersForSwitch(creds.refreshToken)
                    } else {
                        refreshTokenAndLoadFolders(creds.refreshToken)
                    }
                }
            },
            signOut = ::handleSignOut,
            cancelLibrarySwitch = ::cancelLibrarySwitch,
            currentFolderId = { credentialStore.getCredentials()?.rootFolderId },
            confirmSameTitle = { sourceKey, itemId ->
                resolveDuplicate { database.mediaSourceDao().confirmSameTitle(sourceKey, itemId) }
            },
            keepSeparate = { sourceKey -> resolveDuplicate { database.mediaSourceDao().deletePendingMatch(sourceKey) } },
            back = { navigator.back() }
        )
    }

    @Composable
    private fun MainAppContent() {
        // Back closes an open dialog first; screens are popped by the NavDisplay in AppNavDisplay (Task M.3).
        when {
            showTvStorageUnsupportedDialog.value -> {
                BackHandler { showTvStorageUnsupportedDialog.value = false }
            }
            showPhonePairingDialog.value -> {
                BackHandler { showPhonePairingDialog.value = false }
            }
            showLinkedDevicesDialog.value -> {
                BackHandler { showLinkedDevicesDialog.value = false }
            }
            showTvPairingDialog.value -> {
                BackHandler {
                    tvPairingServer?.stopServer()
                    showTvPairingDialog.value = false
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0D10))) {
            AppNavDisplay(
                navigator = navigator,
                isTv = isTvDevice,
                homeState = homeViewModel.uiState,
                searchViewModel = searchViewModel,
                newDetailsViewModel = { DetailsViewModel.from(database) },
                actions = libraryActions,
                setup = setup,
                setupActions = setupActions,
                settings = settings,
                settingsActions = settingsActions,
                pcsActions = pcsActions,
                naming = namingDeps
            )

            // TV Pairing Dialog
            if (showTvPairingDialog.value) {
                TvPairingDialog(
                    pin = tvPairingPin.value,
                    statusText = tvPairingStatus.value,
                    serverAddress = tvPairingAddress.value,
                    title = if (tvPairingLinkOnly.value) LinkedDevicesTexts.TV_ADD else "Pair with Phone",
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
                                // Milestone D: link for Play on TV in the same step (this phone's ID, name and a new key).
                                val tvStore = DeviceLinkStore.tvs(this@MainActivity)
                                val offer = withContext(Dispatchers.IO) {
                                    DeviceLink(tvStore.selfId(), DeviceLinkStore.deviceName(this@MainActivity), DeviceLink.newKey())
                                }
                                val result = client.pairWithTv(host, port, pin, creds, offer)
                                isPhonePairing.value = false
                                result.fold(
                                    onSuccess = { tv ->
                                        showPhonePairingDialog.value = false
                                        if (tv != null) {
                                            withContext(Dispatchers.IO) { tvStore.save(tv) }
                                            refreshLinkedDevices()
                                        }
                                        val text = if (tv != null) "Paired with ${tv.name}. Play on TV is ready." else PlayOnTvMessages.OLD_TV
                                        Toast.makeText(this@MainActivity, text, Toast.LENGTH_LONG).show()
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

            // Milestone D: "My TVs" (phone) / "Linked phones" (TV)
            if (showLinkedDevicesDialog.value) {
                LinkedDevicesDialog(
                    title = if (isTvDevice) LinkedDevicesTexts.TV_TITLE else LinkedDevicesTexts.PHONE_TITLE,
                    explanation = if (isTvDevice) LinkedDevicesTexts.TV_EXPLAIN else LinkedDevicesTexts.PHONE_EXPLAIN,
                    emptyText = if (isTvDevice) LinkedDevicesTexts.TV_EMPTY else LinkedDevicesTexts.PHONE_EMPTY,
                    addLabel = if (isTvDevice) LinkedDevicesTexts.TV_ADD else LinkedDevicesTexts.PHONE_ADD,
                    devices = if (isTvDevice) linkedPhones.value else linkedTvs.value,
                    onAdd = {
                        showLinkedDevicesDialog.value = false
                        if (isTvDevice) startTvPairing(linkOnly = true) else openPhonePairingDialog()
                    },
                    onRemove = { id ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                (if (isTvDevice) DeviceLinkStore.phones(this@MainActivity) else DeviceLinkStore.tvs(this@MainActivity)).remove(id)
                            }
                            refreshLinkedDevices()
                        }
                    },
                    onDismissRequest = { showLinkedDevicesDialog.value = false }
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
        val signInOptions = setup.signInOptions.value
        if (!signInOptions.showGoogleSignIn) {
            // TVs sign in by pairing only (Task J.6); the notice says why.
            setup.authErrorMessage.value = signInOptions.notice ?: SignInOptions.TV_GOOGLE_SIGN_IN_UNAVAILABLE_NOTICE
            return
        }
        try {
            setup.isAuthLoading.value = true
            setup.authErrorMessage.value = null
            if (isTvDevice) {
                val intent = authManager.getTvSignInIntent(this)
                tvSignInLauncher.launch(intent)
            } else {
                val intent = authManager.createAuthRequestIntent(this, GOOGLE_CLIENT_ID)
                authResultLauncher.launch(intent)
            }
        } catch (e: Exception) {
            setup.isAuthLoading.value = false
            setup.authErrorMessage.value = "Failed to launch sign-in: ${e.message}"
        }
    }

    private fun loadFoldersAndProceed(accessToken: String) {
        setup.isFolderLoading.value = true
        setup.folderErrorMessage.value = null
        fetchAndCacheUserIdentity(accessToken)

        lifecycleScope.launch {
            // 1. Attempt zero-click auto-discovery for standard Streaming folder
            val autoDiscovered = driveGateway.autoDiscoverStreamingFolder(accessToken).getOrNull()
            if (autoDiscovered != null) {
                val existingCreds = credentialStore.getCredentials()
                if (existingCreds != null) {
                    credentialStore.saveCredentials(existingCreds.copy(rootFolderId = autoDiscovered.id))
                }
                setup.isFolderLoading.value = false
                navigator.resetTo(Screen.Home)
                triggerBackgroundSync(existingCreds?.refreshToken ?: "", autoDiscovered.id)
                return@launch
            }

            // 2. Fall back to manual folder picker if auto-discovery finds 0 or ambiguous matches
            navigator.resetTo(Screen.FolderPicker)
            val result = driveGateway.listRootFolders(accessToken)
            setup.isFolderLoading.value = false
            result.fold(
                onSuccess = { folders ->
                    setup.driveFolders.value = folders
                },
                onFailure = { err ->
                    setup.folderErrorMessage.value = "Could not list Google Drive folders: ${err.message}"
                }
            )
        }
    }

    private fun refreshTokenAndLoadFolders(refreshToken: String) {
        setup.isFolderLoading.value = true
        authManager.refreshAccessToken(this, GOOGLE_CLIENT_ID, refreshToken) { result ->
            result.fold(
                onSuccess = { token ->
                    cachedAccessToken = token
                    loadFoldersAndProceed(token)
                },
                onFailure = { err ->
                    runOnUiThread {
                        setup.isFolderLoading.value = false
                        setup.folderErrorMessage.value = "Token refresh failed: ${err.message}"
                    }
                }
            )
        }
    }

    private fun onFolderChosen(folder: DriveFolder) {
        if (setup.isSwitchingLibrary.value) {
            switchLibraryFolder(folder)
            return
        }
        val token = cachedAccessToken
        if (token.isEmpty()) {
            setup.folderErrorMessage.value = "Missing session token. Please re-authenticate."
            return
        }

        setup.isFolderValidating.value = true
        setup.folderErrorMessage.value = null

        lifecycleScope.launch {
            val result = driveGateway.validateStreamingFolder(folder.id, token)
            setup.isFolderValidating.value = false

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
                            navigator.resetTo(Screen.Home)
                            triggerBackgroundSync(existingCreds?.refreshToken ?: "", folder.id)
                        }
                        is FolderValidationResult.Invalid -> {
                            setup.folderErrorMessage.value = "Selected folder '${folder.name}' is invalid: ${validation.reason}. Ensure it contains video files or 'Films' / 'Series' subfolders."
                        }
                    }
                },
                onFailure = { err ->
                    setup.folderErrorMessage.value = "Validation failed: ${err.message}"
                }
            )
        }
    }

    private fun openSetupGuide() {
        navigator.open(Screen.SetupGuide)
    }

    private fun startLibrarySwitch() {
        val creds = credentialStore.getCredentials()
        if (creds == null || creds.refreshToken.isEmpty()) {
            handleSignOut()
            return
        }
        setup.isSwitchingLibrary.value = true
        setup.driveFolders.value = emptyList()
        setup.folderErrorMessage.value = null
        navigator.open(Screen.FolderPicker)
        loadFoldersForSwitch(creds.refreshToken)
    }

    /** Lists owned + shared folders without auto-discovery, reusing the stored session (no re-auth). */
    private fun loadFoldersForSwitch(refreshToken: String) {
        folderSwitchJob?.cancel()
        setup.isFolderValidating.value = false
        setup.isFolderLoading.value = true
        setup.folderErrorMessage.value = null
        folderSwitchJob = lifecycleScope.launch {
            val token = obtainAccessToken(refreshToken).getOrNull()
            if (token.isNullOrEmpty()) {
                setup.isFolderLoading.value = false
                setup.folderErrorMessage.value = "Couldn't refresh your Google session. Check your connection and try again."
                return@launch
            }
            cachedAccessToken = token
            val result = driveGateway.listRootFolders(token)
            setup.isFolderLoading.value = false
            result.fold(
                onSuccess = { folders -> setup.driveFolders.value = folders },
                onFailure = { err ->
                    setup.folderErrorMessage.value = LibraryFolderSwitcher.describeDriveError("your Google Drive", err)
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
        setup.isFolderValidating.value = true
        setup.folderErrorMessage.value = null
        folderSwitchJob = lifecycleScope.launch {
            val result = folderSwitcher.switchTo(folder, cachedAccessToken)
            setup.isFolderValidating.value = false
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
                    setup.folderErrorMessage.value = result.message
                }
            }
        }
    }

    /** Back or Cancel in the folder picker: the library stays as it was and Back returns to Settings (Task P.2). */
    private fun cancelLibrarySwitch() {
        folderSwitchJob?.cancel()
        folderSwitchJob = null
        clearLibrarySwitch()
        navigator.backOrResetTo(Screen.Home)
    }

    /** A new library folder is in use: start again from its Home. */
    private fun finishLibrarySwitch() {
        clearLibrarySwitch()
        navigator.resetTo(Screen.Home)
    }

    private fun clearLibrarySwitch() {
        setup.isSwitchingLibrary.value = false
        setup.isFolderLoading.value = false
        setup.isFolderValidating.value = false
        setup.folderErrorMessage.value = null
    }

    private var fullyDrawnReported = false

    /** Task M.0: Home has content for the first time; Logcat's "Fully drawn" line and `am start -W` see it. */
    private fun reportFullyDrawnOnce() {
        if (fullyDrawnReported) return
        fullyDrawnReported = true
        reportFullyDrawn()
    }

    private suspend fun obtainAccessToken(refreshToken: String): Result<String> =
        suspendCancellableCoroutine { cont ->
            authManager.refreshAccessToken(this, GOOGLE_CLIENT_ID, refreshToken) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }

    /** "Refresh library" in Settings: same sync as app start, on demand (Task H.1). */
    // ---- Task I.2: paired PCs ----

    private fun updatePcRows() {
        settings.pcs.rows.value = hostSync.hosts().map {
            val hostId = it.pairing.hostId
            PcRow(hostId, it.pairing.hostName, it.homeAddress, it.vpnAddress, pcStatus[hostId] ?: PcConnection.NotChecked)
        }
    }

    /** Re-reads every paired PC's folders; a PC that can't be reached has its copies set aside (Drive copies still play). */
    private fun syncPcs() {
        settings.pcs.isChecking.value = true
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { runCatching { hostSync.syncAll() }.getOrDefault(emptyList()) }
            val hosts = hostSync.hosts().associateBy { it.pairing.hostId }
            results.filter { it.removedByPc }.forEach {
                pcStatus.remove(it.hostId)
                settings.pcs.notice.value =
                    PcsNotice("${it.hostName} removed this device. Pair again to play its films.", DcBannerKind.Warning)
            }
            results.filterNot { it.removedByPc }.forEach { result ->
                val viaVpn = result.reachableAt != null && result.reachableAt == hosts[result.hostId]?.vpnAddress
                pcStatus[result.hostId] = when {
                    result.reachableAt == null -> PcConnection.Unreachable
                    viaVpn -> PcConnection.OverVpn(result.videosFound)
                    else -> PcConnection.AtHome(result.videosFound)
                }
            }
            updatePcRows()
        }.invokeOnCompletion { settings.pcs.isChecking.value = false }
    }

    private fun searchPcs() {
        val pcs = settings.pcs
        pcs.isSearching.value = true
        pcs.notice.value = null
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { HostDiscoveryClient.discover() }.getOrDefault(emptyList()) }
            pcs.discovered.value = found
            pcs.isSearching.value = false
            if (found.isEmpty()) {
                pcs.notice.value = PcsNotice("No PC found. Is sharing on there? You can also type its address.", DcBannerKind.Warning)
            }
        }
    }

    private fun pairPc(address: String, code: String, vpnAddress: String) {
        val pcs = settings.pcs
        pcs.isPairing.value = true
        pcs.notice.value = null
        lifecycleScope.launch {
            val deviceName = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL).joinToString(" ").trim()
            val result = withContext(Dispatchers.IO) { runCatching { hostSync.pair(address, code, deviceName, vpnAddress) } }
            pcs.isPairing.value = false
            result.onSuccess {
                pcs.notice.value = PcsNotice("Paired with ${it.pairing.hostName}. Loading its films…")
                pcs.pairedCount.value++
                syncPcs()
            }.onFailure {
                pcs.notice.value = PcsNotice((it as? HostPairingException)?.message ?: "Pairing failed. Try again.", DcBannerKind.Error)
            }
            updatePcRows()
        }
    }

    private fun refreshLibrary() {
        val credentials = credentialStore.getCredentials() ?: return
        if (settings.isRefreshingLibrary.value || credentials.rootFolderId.isEmpty()) return
        librarySyncJob?.cancel()
        settings.isRefreshingLibrary.value = true
        librarySyncJob = lifecycleScope.launch {
            val result = Perf.measure("sync.refresh") { span ->
                // Always fetch a fresh token: the cached one may have expired while the app was open.
                val token = Perf.measure("sync.token") { obtainAccessToken(credentials.refreshToken).getOrNull() }
                if (!token.isNullOrEmpty()) cachedAccessToken = token
                withContext(Dispatchers.IO) { libraryRefresher.refresh(credentials.rootFolderId, token) }
                    .also { span.put("ok", it == LibraryRefreshResult.Refreshed) }
            }
            settings.isRefreshingLibrary.value = false
            val message = when (result) {
                LibraryRefreshResult.Refreshed -> "Library refreshed"
                is LibraryRefreshResult.Failed -> result.message
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            syncPcs()
            deviceSync.syncNow(Trigger.REFRESH)
        }.also { job -> job.invokeOnCompletion { settings.isRefreshingLibrary.value = false } }
    }

    private fun triggerBackgroundSync(refreshToken: String, rootFolderId: String) {
        librarySyncJob?.cancel()
        librarySyncJob = lifecycleScope.launch(Dispatchers.IO) {
            Perf.measure("sync.startup") { span ->
                // Drive first; TMDB enrichment runs inside the crawl, or alone when no crawl completed (Task N.2).
                StartupSync.run(
                    syncDrive = syncDrive@{
                        // Suspends instead of blocking a thread, and waits long enough for a slow network (report F-06).
                        val token = Perf.measure("sync.token") {
                            StartupToken.obtain(cachedAccessToken, refresh = { obtainAccessToken(refreshToken) })
                        }
                        if (token == null) return@syncDrive false
                        cachedAccessToken = token
                        fetchAndCacheUserIdentity(token)
                        val validation = Perf.measure("sync.folderCheck") {
                            driveGateway.validateStreamingFolder(rootFolderId, token).getOrNull()
                        }
                        if (validation !is FolderValidationResult.Valid) return@syncDrive false
                        syncEngine.executeInitialCrawl(validation.filmsFolderId, validation.seriesFolderId, token, rootFolderId)
                            .onSuccess { StartupSyncGate.process.markSynced(rootFolderId) }
                            .also { span.put("ok", it.isSuccess) }
                            .isSuccess
                    },
                    enrich = { syncEngine.enrichExistingCatalog() }
                )
            }
            // Milestone Q: after the library, so the other devices' titles are known here.
            deviceSync.syncNow(Trigger.STARTUP)
        }
    }

    /**
     * Opens the TV's PIN pairing. Milestone D: every pairing also links the phone for Play on TV; with [linkOnly] (from
     * Linked phones, the TV already signed in) it only links and keeps the TV's own sign-in.
     */
    private fun startTvPairing(linkOnly: Boolean = false) {
        val onPhoneLinked = { phone: DeviceLink ->
            val phones = DeviceLinkStore.phones(this)
            phones.save(phone)
            PairingLink.answer(phones.selfId(), DeviceLinkStore.deviceName(this), PlayOnTvReceiver.port)
        }
        tvPairingLinkOnly.value = linkOnly
        val server = TvPairingServer(this, linkOnly = linkOnly, onPhoneLinked = onPhoneLinked) { creds ->
            runOnUiThread {
                credentialStore.saveCredentials(creds)
                showTvPairingDialog.value = false
                if (creds.rootFolderId.isNotEmpty()) {
                    navigator.resetTo(Screen.Home)
                    triggerBackgroundSync(creds.refreshToken, creds.rootFolderId)
                } else {
                    navigator.resetTo(Screen.FolderPicker)
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
                onSuccess = {
                    runOnUiThread {
                        if (linkOnly) showTvPairingDialog.value = false
                        refreshLinkedDevices()
                        val text = if (linkOnly) "Phone linked. You can now use Play on TV from it." else "TV Paired Successfully!"
                        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
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

    /**
     * The player plays the next or previous episode itself (F-29); it hands the ID back only when that needs a choice
     * or a message here (slow connection to the PC, or no copy available). Then play it like any other title. Its
     * Details button comes back here too: the film's page, or the series' page for an episode, on top of where the
     * player was opened from (Back then returns there).
     */
    private val playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Milestone Q: share what was just watched.
        runDeviceSync(Trigger.PLAYER_CLOSED)
        when (val exit = PlayerExit.from(result.data)) {
            is PlayerExit.PlayEpisode -> launchPlayer(exit.mediaItemId, exit.title ?: "Episode")
            is PlayerExit.OpenDetails -> lifecycleScope.launch {
                val item = withContext(Dispatchers.IO) { database.mediaDao().getMediaItemById(exit.mediaItemId) }
                PlayerExit.detailsScreen(item)?.let { navigator.open(it) }
            }
            null -> Unit
        }
    }

    /** (catalog ID, label) of the episodes before and after [mediaItemId]; null for films, first and last episodes. */
    private data class EpisodeNeighbours(val previous: Pair<String, String>?, val next: Pair<String, String>?)

    private suspend fun findEpisodeNeighbours(mediaItemId: String) = EpisodeNeighbours(
        previous = PreviousEpisode.find(database.mediaDao(), mediaItemId),
        next = NextEpisode.find(database.mediaDao(), mediaItemId)
    )

    /** Plays a catalog title from its best available copy (local first, then Drive), resuming across copies. */
    /** [forcePosition]: start at [startPosMs] even when this device has its own progress (Play on TV, Milestone D). */
    private fun launchPlayer(mediaItemId: String, title: String, startPosMs: Long = 0L, forcePosition: Boolean = false) {
        // Task M.0: Play pressed → first video frame; PlayerActivity ends it.
        val firstFrame = Perf.begin("play.firstFrame")
        lifecycleScope.launch {
            val (resolved, neighbours) = Perf.measure("play.resolve") {
                withContext(Dispatchers.IO) {
                    val copy = playbackResolver.resolve(mediaItemId, startPosMs)
                    (if (forcePosition) copy.withStartPosition(startPosMs) else copy) to findEpisodeNeighbours(mediaItemId)
                }
            }
            if (resolved !is ResolvedPlayback.Playable) firstFrame.put("result", resolved.javaClass.simpleName).end()
            when (resolved) {
                is ResolvedPlayback.Unavailable -> Toast.makeText(
                    this@MainActivity,
                    "'$title' isn't available right now — its local folder may be removed or disconnected.",
                    Toast.LENGTH_LONG
                ).show()
                is ResolvedPlayback.Playable -> startPlayer(resolved, mediaItemId, title, neighbours, firstFrame)
                is ResolvedPlayback.SlowConnection -> {
                    // Task I.2 (design §2.6): warn only when needed, and offer the Drive copy when there is one.
                    val dialog = android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Slow connection to the PC")
                        .setMessage(resolved.verdict.message(hasDriveCopy = resolved.fallback != null))
                        .setNeutralButton("Play from the PC anyway") { _, _ -> startPlayer(resolved.pc, mediaItemId, title, neighbours) }
                        .setNegativeButton("Cancel", null)
                    resolved.fallback?.let { fallback ->
                        dialog.setPositiveButton(if (fallback.source is PlaybackSource.Drive) "Play from Drive" else "Play the other copy") { _, _ ->
                            startPlayer(fallback, mediaItemId, title, neighbours)
                        }
                    }
                    dialog.show()
                }
            }
        }
    }

    private fun startPlayer(
        resolved: ResolvedPlayback.Playable,
        mediaItemId: String,
        title: String,
        neighbours: EpisodeNeighbours,
        firstFrame: PerfSpan? = null
    ) {
        val intent = Intent(this@MainActivity, PlayerActivity::class.java).apply {
            if (firstFrame != null) {
                putExtra(PlayerActivity.EXTRA_PERF_START_NANOS, firstFrame.startNanos)
                putExtra(PlayerActivity.EXTRA_PERF_COOKIE, firstFrame.cookie)
            }
            when (val source = resolved.source) {
                is PlaybackSource.Local -> putExtra(PlayerActivity.EXTRA_LOCAL_CONTENT_URI, source.contentUri)
                is PlaybackSource.Drive -> putExtra(PlayerActivity.EXTRA_DRIVE_FILE_ID, source.fileId)
                is PlaybackSource.Host -> {
                    putExtra(PlayerActivity.EXTRA_HOST_SOURCE_KEY, source.sourceKey)
                    putExtra(PlayerActivity.EXTRA_HOST_URL, source.url)
                }
            }
            putExtra(PlayerActivity.EXTRA_MEDIA_ITEM_ID, mediaItemId)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
            putExtra(PlayerActivity.EXTRA_START_POSITION_MS, resolved.resumePositionMs)
            putExtra(PlayerActivity.EXTRA_ACCESS_TOKEN, cachedAccessToken)
            neighbours.next?.let { next ->
                putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_ID, next.first)
                putExtra(PlayerActivity.EXTRA_NEXT_EPISODE_TITLE, next.second)
            }
            neighbours.previous?.let { previous ->
                putExtra(PlayerActivity.EXTRA_PREVIOUS_EPISODE_ID, previous.first)
                putExtra(PlayerActivity.EXTRA_PREVIOUS_EPISODE_TITLE, previous.second)
            }
        }
        playerLauncher.launch(intent)
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
        setup.isSwitchingLibrary.value = false
        credentialStore.clear()
        cachedAccessToken = ""
        // Milestone Q: the next sign-in is read-only; turning sync on again asks for the app folder.
        deviceSync.disable()
        homeViewModel.updateAccount(AccountUiState(isConnected = false))
        navigator.resetTo(Screen.SignIn)
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
