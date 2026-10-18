package com.drivecinema.desktop.local

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.local.LocalLibraryCrawler
import com.drivecinema.app.core.local.LocalLibraryIndexer
import com.drivecinema.app.core.logging.AppLog
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** A saved local library folder as shown in the UI. */
data class LocalFolderStatus(
    val path: String,
    val isAvailable: Boolean,
    /** Videos found in the last scan, null before the first scan. */
    val videoCount: Int? = null,
    val linkedToDrive: Int? = null,
    val possibleDuplicates: Int? = null
)

data class LocalFoldersState(
    val folders: List<LocalFolderStatus> = emptyList(),
    val isScanning: Boolean = false,
    val message: String? = null
)

/**
 * Desktop local library: folders on this computer join the catalog with the same layout rules and the same safe
 * matching as on Android (shared crawler and indexer). Removing a folder or unplugging a drive only hides its
 * copies; nothing is deleted and watch history is never touched.
 */
class LocalFolders(
    private val scope: CoroutineScope,
    db: DriveCinemaDatabase,
    private val settings: LocalFolderSettings,
    /** Runs after every scan, e.g. TMDB enrichment so local titles get posters too. */
    private val afterScan: suspend () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val indexer = LocalLibraryIndexer(
        db,
        LocalLibraryCrawler(FileSystemTreeSource()),
        runInTransaction = { block -> db.useWriterConnection { it.immediateTransaction { block() } } }
    )
    private val lock = Mutex()
    private val _state = MutableStateFlow(LocalFoldersState(settings.localFolders().map { LocalFolderStatus(it, isAvailable(it)) }))
    val state: StateFlow<LocalFoldersState> = _state.asStateFlow()

    /** At startup: hide copies of missing or removed folders, then rescan the rest. */
    fun scanAll() = launch { scanSavedFolders() }

    /** Returns an error message, or null when the folder was added (it is then scanned in the background). */
    fun add(folder: Path): String? {
        val normalized = folder.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalized)) return "That is not a folder."
        if (!Files.isReadable(normalized)) return "DriveCinema can't read that folder."
        val path = normalized.toString()
        if (path in settings.localFolders()) return "That folder is already in the library."
        settings.setLocalFolders(settings.localFolders() + path)
        launch { scanSavedFolders() }
        return null
    }

    fun remove(path: String) {
        settings.setLocalFolders(settings.localFolders() - path)
        launch { scanSavedFolders() }
    }

    private suspend fun scanSavedFolders() {
        val saved = settings.localFolders()
        _state.value = LocalFoldersState(
            folders = saved.map { path -> _state.value.folders.firstOrNull { it.path == path } ?: LocalFolderStatus(path, isAvailable(path)) },
            isScanning = true
        )
        val available = saved.filter { isAvailable(it) }
        val statuses = withContext(ioDispatcher) {
            // Copies in removed or currently missing folders stop being playable (not deleted).
            indexer.retireUnsavedFolders(available.map { FileSystemTreeSource.treeUriOf(Path.of(it)) })
            saved.map { path ->
                if (path !in available) return@map LocalFolderStatus(path, isAvailable = false)
                try {
                    val result = indexer.indexFolder(FileSystemTreeSource.treeUriOf(Path.of(path)))
                    LocalFolderStatus(path, true, result.videosFound, result.linkedToExisting, result.possibleDuplicates)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Scanning $path failed: ${e.message}")
                    LocalFolderStatus(path, isAvailable = false)
                }
            }
        }
        val missing = statuses.count { !it.isAvailable }
        _state.value = LocalFoldersState(
            folders = statuses,
            message = if (missing > 0) "$missing folder(s) not found — plug in the drive or remove the folder." else null
        )
        runCatching { withContext(ioDispatcher) { afterScan() } }
            .onFailure { AppLog.w(TAG, "After-scan step failed: ${it.message}") }
    }

    private fun isAvailable(path: String): Boolean = Files.isDirectory(Path.of(path))

    @Volatile private var lastJob: Job? = null

    /** Scans run one after another; this waits for the last one started. */
    suspend fun awaitIdle() {
        lastJob?.join()
    }

    private fun launch(block: suspend () -> Unit) {
        lastJob = scope.launch { lock.withLock { block() } }
    }

    private companion object {
        const val TAG = "LocalFolders"
    }
}

/** Where the list of local folders is kept (desktop: settings.json). */
interface LocalFolderSettings {
    fun localFolders(): List<String>
    fun setLocalFolders(paths: List<String>)
}
