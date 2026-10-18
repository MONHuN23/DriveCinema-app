package com.drivecinema.desktop.library

import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.model.FolderPickerSections
import com.drivecinema.app.core.network.FolderValidationResult
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.sync.DriveLibrarySyncEngine
import com.drivecinema.app.core.sync.FolderSwitchResult
import com.drivecinema.app.core.sync.LibraryFolderSwitcher
import com.drivecinema.app.core.sync.LibrarySelectionStore
import com.drivecinema.desktop.auth.DesktopSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryState {
    /** Not signed in. */
    data object Idle : LibraryState

    /** Looking for the user's `Streaming` folder right after sign-in. */
    data object Discovering : LibraryState

    data class ChoosingFolder(
        val sections: FolderPickerSections = FolderPickerSections(emptyList(), emptyList()),
        val isLoading: Boolean = true,
        /** A folder was picked and is being checked / indexed. */
        val switchingTo: String? = null,
        val errorMessage: String? = null,
        /** True when a library already exists (switching): the picker can be closed again. */
        val canCancel: Boolean = false
    ) : LibraryState

    data class Ready(
        val folderName: String,
        val isSyncing: Boolean = false,
        /** Shown above the library, e.g. "Offline — showing the library from the last sync". */
        val message: String? = null
    ) : LibraryState
}

/**
 * The desktop library: finds or lets the user pick the Drive library folder, and keeps the shared database
 * in sync with it using the same engine as Android (atomic swap, TMDB enrichment, watch history untouched).
 */
class LibraryService(
    private val scope: CoroutineScope,
    private val gateway: GoogleDriveGateway,
    private val syncEngine: DriveLibrarySyncEngine,
    private val settings: DesktopSettings,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Idle)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var session: DesktopSession? = null
    private var job: Job? = null
    /** The library to return to when the user closes the folder picker. */
    private var current: LibraryFolderSetting? = null

    private val selectionStore = object : LibrarySelectionStore {
        override fun isSignedIn(): Boolean = session != null
        override fun saveLibraryFolder(folder: DriveFolder) {
            val email = session?.user?.emailAddress ?: return
            val setting = LibraryFolderSetting(folder.id, folder.name)
            settings.setLibraryFolder(email, setting)
            current = setting
        }
    }
    private val switcher = LibraryFolderSwitcher(gateway, syncEngine, selectionStore)

    /** Called after sign-in or session restore. */
    fun start(newSession: DesktopSession) {
        stop()
        session = newSession
        val saved = settings.libraryFolder(newSession.user.emailAddress)
        if (saved != null) {
            current = saved
            sync(saved)
        } else {
            discover()
        }
    }

    /** Called on sign-out: stops syncing; the database (catalog + watch history) is kept. */
    fun stop() {
        job?.cancel()
        job = null
        session = null
        current = null
        _state.value = LibraryState.Idle
    }

    /** "Change library folder" from Home. */
    fun openFolderPicker() {
        if (session == null) return
        loadFolders(canCancel = current != null)
    }

    fun closeFolderPicker() {
        val library = current ?: return
        val choosing = _state.value as? LibraryState.ChoosingFolder ?: return
        if (choosing.switchingTo != null) return
        job?.cancel()
        _state.value = LibraryState.Ready(library.folderName)
    }

    /** Checks the folder, indexes it and makes it the library (nothing changes if anything fails). */
    fun chooseFolder(folder: DriveFolder) {
        val choosing = _state.value as? LibraryState.ChoosingFolder ?: return
        if (choosing.switchingTo != null) return
        _state.value = choosing.copy(switchingTo = folder.name, errorMessage = null)
        launch {
            when (val result = switcher.switchTo(folder, token())) {
                is FolderSwitchResult.Switched -> {
                    _state.value = LibraryState.Ready(folder.name, isSyncing = true)
                    enrichQuietly()
                    _state.value = LibraryState.Ready(folder.name)
                }
                is FolderSwitchResult.Rejected -> {
                    _state.value = choosing.copy(switchingTo = null, errorMessage = result.message)
                }
            }
        }
    }

    /** Re-syncs the current library (Refresh button). */
    fun refresh() {
        current?.let { sync(it) }
    }

    private fun discover() {
        _state.value = LibraryState.Discovering
        launch {
            val found = gateway.autoDiscoverStreamingFolder(token()).getOrNull()
            if (found != null) {
                _state.value = LibraryState.Ready(found.name, isSyncing = true)
                when (val result = switcher.switchTo(found, token())) {
                    is FolderSwitchResult.Switched -> {
                        enrichQuietly()
                        _state.value = LibraryState.Ready(found.name)
                    }
                    is FolderSwitchResult.Rejected -> loadFolders(canCancel = false, error = result.message)
                }
            } else {
                loadFolders(canCancel = false)
            }
        }
    }

    private fun loadFolders(canCancel: Boolean, error: String? = null) {
        _state.value = LibraryState.ChoosingFolder(isLoading = true, errorMessage = error, canCancel = canCancel)
        launch {
            gateway.listRootFolders(token()).fold(
                onSuccess = { folders ->
                    _state.value = LibraryState.ChoosingFolder(
                        sections = FolderPickerSections.from(folders),
                        isLoading = false,
                        errorMessage = error,
                        canCancel = canCancel
                    )
                },
                onFailure = { e ->
                    _state.value = LibraryState.ChoosingFolder(
                        isLoading = false,
                        errorMessage = LibraryFolderSwitcher.describeDriveError("your Drive", e),
                        canCancel = canCancel
                    )
                }
            )
        }
    }

    /** Startup sync, like Android: enrich what is there, then re-crawl the library folder. */
    private fun sync(library: LibraryFolderSetting) {
        _state.value = LibraryState.Ready(library.folderName, isSyncing = true)
        launch {
            enrichQuietly()
            val token = token()
            val validation = gateway.validateStreamingFolder(library.folderId, token).getOrElse { e ->
                _state.value = LibraryState.Ready(library.folderName, message = offlineMessage(e))
                return@launch
            }
            when (validation) {
                is FolderValidationResult.Valid -> {
                    val result = withContext(ioDispatcher) {
                        syncEngine.executeInitialCrawl(validation.filmsFolderId, validation.seriesFolderId, token, library.folderId)
                    }
                    _state.value = LibraryState.Ready(
                        library.folderName,
                        message = result.exceptionOrNull()?.let { offlineMessage(it) }
                    )
                }
                is FolderValidationResult.Invalid -> loadFolders(
                    canCancel = false,
                    error = "'${library.folderName}' can no longer be used as a library: ${validation.reason}. Choose a folder."
                )
            }
        }
    }

    private suspend fun enrichQuietly() {
        withContext(ioDispatcher) { syncEngine.enrichExistingCatalog() }
    }

    private suspend fun token(): String {
        val provider = session?.tokenProvider ?: throw CancellationException("Signed out")
        return withContext(ioDispatcher) { provider.getAccessToken() }
    }

    private fun offlineMessage(e: Throwable): String {
        AppLog.w(TAG, "Library sync failed: ${e.message}")
        return "Couldn't reach Google Drive — showing the library from the last sync."
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Library operation failed: ${e.message}")
                val library = current
                _state.value = if (library != null) LibraryState.Ready(library.folderName, message = offlineMessage(e))
                else LibraryState.ChoosingFolder(isLoading = false, errorMessage = "Couldn't reach Google Drive. Check your connection and try again.")
            }
        }
    }

    private companion object {
        const val TAG = "LibraryService"
    }
}
