package com.drivecinema.desktop.importer

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.ImportQueueEntity
import com.drivecinema.app.core.database.entity.ImportState
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.desktop.auth.DesktopTokenProvider
import com.drivecinema.desktop.auth.OAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Where the import folders and the pause switch are kept (desktop: settings.json). */
interface ImportSettings {
    fun importFolder(kind: ImportFolderKind): String?
    fun setImportFolder(kind: ImportFolderKind, path: String?)
    fun importPaused(): Boolean
    fun setImportPaused(paused: Boolean)
}

/** What the import screen shows besides the queue itself. */
data class ImportStatus(
    val moviesFolder: String? = null,
    val seriesFolder: String? = null,
    /** Null until known (the first token refresh after a restart tells). */
    val hasWriteAccess: Boolean? = null,
    val libraryName: String? = null,
    val pausedByUser: Boolean = false,
    /** Set when the queue stopped by itself (Drive full, daily upload limit, sign-in needed). */
    val stoppedReason: String? = null,
    val isRunning: Boolean = false
)

/**
 * Task I.1: watches the import folders, queues finished videos in `import_queue` and uploads them one at a time
 * into the matching Drive folders. The queue survives restarts (an interrupted upload resumes from its stored
 * session). Local files are only read: never moved, renamed or deleted.
 *
 * Videos already in a folder when it is chosen are listed as [ImportState.SKIPPED], not uploaded, until the user
 * asks ("Upload" or "Upload existing"); only files that arrive later go up by themselves.
 */
class ImportQueue(
    private val scope: CoroutineScope,
    db: DriveCinemaDatabase,
    private val settings: ImportSettings,
    /** Builds the Drive client for a signed-in session (tests use a fake Drive). */
    private val driveFor: (DesktopTokenProvider) -> DriveWriteClient = { DriveWriteClient(it) },
    private val uploaderFor: (DriveWriteClient) -> ResumableUploader = { ResumableUploader(it) },
    /** Runs once uploads finish, e.g. a library refresh so the new titles appear. */
    private val afterUploads: suspend () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
    private val quietPeriodMs: Long = 10_000,
    private val tickMs: Long = 2_000,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** false in tests: they call [scanOnce] and [uploadNext] themselves instead of the background loop. */
    private val runLoop: Boolean = true
) {
    private val dao = db.importQueueDao()
    private val _status = MutableStateFlow(
        ImportStatus(
            moviesFolder = settings.importFolder(ImportFolderKind.MOVIES),
            seriesFolder = settings.importFolder(ImportFolderKind.SERIES),
            pausedByUser = settings.importPaused()
        )
    )
    val status: StateFlow<ImportStatus> = _status.asStateFlow()
    val entries: Flow<List<ImportQueueEntity>> = dao.observeAll()

    private val lock = Mutex()
    private var tokens: DesktopTokenProvider? = null
    private var libraryRootId: String? = null
    private var drive: DriveWriteClient? = null
    private var uploader: ResumableUploader? = null
    private var resolver: DriveFolderPathResolver? = null
    private var detector: FinishedFileDetector? = null
    private var loop: Job? = null
    @Volatile private var current: Pair<String, Job>? = null
    @Volatile private var uploadedSinceRefresh = false
    private var interruptedRequeued = false

    /** Signed in with a library folder: checks write access, then runs the queue when there is something to watch. */
    fun attach(tokenProvider: DesktopTokenProvider, libraryRootId: String, libraryName: String) {
        if (tokens === tokenProvider && this.libraryRootId == libraryRootId) return
        detach()
        tokens = tokenProvider
        this.libraryRootId = libraryRootId
        drive = driveFor(tokenProvider).also { uploader = uploaderFor(it); resolver = DriveFolderPathResolver(it, libraryRootId) }
        _status.update { it.copy(libraryName = libraryName, hasWriteAccess = null, stoppedReason = null) }
        scope.launch {
            // Uploads that were running when the app stopped go back to the queue, once per app start.
            if (!interruptedRequeued) {
                interruptedRequeued = true
                dao.requeueInterrupted()
            }
            refreshAccess()
        }
    }

    fun detach() {
        loop?.cancel()
        loop = null
        detector?.close()
        detector = null
        tokens = null
        libraryRootId = null
        _status.update { it.copy(isRunning = false, hasWriteAccess = null, libraryName = null) }
    }

    /** After a restart the granted scopes are known only once a token was fetched; also called after write access was granted. */
    suspend fun refreshAccess() {
        val provider = tokens ?: return
        val hasAccess = withContext(ioDispatcher) {
            runCatching { provider.getAccessToken() }
            provider.hasDriveWriteAccess
        }
        _status.update { it.copy(hasWriteAccess = hasAccess) }
        restartLoop()
    }

    /** Chooses (or clears, with null) a folder; returns an error message or null. Videos already there are listed as skipped. */
    suspend fun setFolder(kind: ImportFolderKind, folder: Path?): String? {
        val path = folder?.toAbsolutePath()?.normalize()
        val root = libraryRootId
        if (path != null) {
            // Without the library the videos already in the folder couldn't be listed as skipped, and would all upload.
            if (root == null) return "Sign in and choose your Drive library first."
            if (!Files.isDirectory(path)) return "That is not a folder."
            val other = settings.importFolder(if (kind == ImportFolderKind.MOVIES) ImportFolderKind.SERIES else ImportFolderKind.MOVIES)
            if (other != null && (path.startsWith(Path.of(other)) || Path.of(other).startsWith(path))) {
                return "The films and series folders must be separate folders."
            }
        }
        settings.setImportFolder(kind, path?.toString())
        _status.update {
            if (kind == ImportFolderKind.MOVIES) it.copy(moviesFolder = path?.toString()) else it.copy(seriesFolder = path?.toString())
        }
        if (path != null && root != null) markExistingAsSkipped(kind, path, root)
        restartLoop()
        return null
    }

    fun setPaused(paused: Boolean) {
        settings.setImportPaused(paused)
        _status.update { it.copy(pausedByUser = paused, stoppedReason = if (paused) it.stoppedReason else null) }
        if (paused) current?.second?.cancel() else restartLoop()
    }

    /** Failed, cancelled, skipped or reviewed entries go back into the queue (needs review: resolved again first). */
    fun retry(importKey: String) = scope.launch {
        lock.withLock {
            val entry = dao.get(importKey) ?: return@withLock
            if (entry.state == ImportState.UPLOADING || entry.state == ImportState.DONE) return@withLock
            val target = if (entry.targetFolderPath == null) resolveTarget(Path.of(entry.localPath)) else null
            if (target is ImportTarget.NeedsReview) {
                dao.setState(importKey, ImportState.NEEDS_REVIEW, target.reason, clock())
            } else {
                val folderPath = (target as? ImportTarget.Upload)?.folderPath?.joinToString("/") ?: entry.targetFolderPath
                dao.update(entry.copy(state = ImportState.QUEUED, message = null, targetFolderPath = folderPath, updatedAt = clock()))
            }
        }
    }

    fun uploadAllSkipped() = scope.launch {
        lock.withLock {
            dao.withState(ImportState.SKIPPED).forEach {
                dao.setState(it.importKey, ImportState.QUEUED, null, clock())
            }
        }
    }

    /** Stops this file's upload (if running) and keeps it out of the queue; the partial upload on Drive simply expires. */
    fun cancel(importKey: String) = scope.launch {
        // Wait until the upload has really stopped, so a Retry right after starts cleanly.
        current?.takeIf { it.first == importKey }?.second?.cancelAndJoin()
        lock.withLock {
            val entry = dao.get(importKey) ?: return@withLock
            if (entry.state != ImportState.DONE) {
                dao.update(entry.copy(state = ImportState.CANCELLED, sessionUri = null, message = null, updatedAt = clock()))
            }
        }
    }

    fun clearFinished() = scope.launch { dao.clearFinished() }

    /** One detection pass: newly finished videos are queued (or listed as needing review). */
    suspend fun scanOnce() {
        val root = libraryRootId ?: return
        val found = withContext(ioDispatcher) { detector?.tick().orEmpty() }
        for (file in found) {
            val target = resolveTarget(file.path) ?: continue
            insert(file.path, file.sizeBytes, file.lastModifiedMs, target, root, ImportState.QUEUED)
        }
    }

    /** Uploads the oldest queued file; false when there was nothing to do. */
    suspend fun uploadNext(): Boolean {
        val root = libraryRootId ?: return false
        val entry = lock.withLock {
            dao.nextQueued(root)?.also { dao.setState(it.importKey, ImportState.UPLOADING, null, clock()) }
        } ?: return false
        try {
            // A child of the caller (the queue loop), so stopping the loop also stops the upload.
            coroutineScope {
                val job = launch(ioDispatcher) { upload(entry) }
                current = entry.importKey to job
                job.join()
            }
        } finally {
            current = null
            // Stopped while running (cancel, pause, loop restart): back to the queue unless cancel() already marked it.
            withContext(NonCancellable) {
                lock.withLock {
                    dao.get(entry.importKey)?.takeIf { it.state == ImportState.UPLOADING }?.let {
                        dao.setState(it.importKey, ImportState.QUEUED, null, clock())
                    }
                }
            }
        }
        return true
    }

    private suspend fun upload(entry: ImportQueueEntity) {
        val key = entry.importKey
        try {
            val folderPath = entry.targetFolderPath?.split('/') ?: throw NeedsReviewException("No Drive folder for this file.")
            val parentId = resolver!!.resolve(folderPath)
            val request = UploadRequest(Path.of(entry.localPath), entry.fileName, parentId, entry.sizeBytes, entry.lastModifiedMs)
            // The session is stored before any data goes out, so a restart resumes instead of uploading a second copy.
            val outcome = uploader!!.upload(
                request,
                storedSessionUri = entry.sessionUri,
                onSession = { uri -> runBlocking { dao.setSession(key, uri, clock()) } },
                onProgress = { bytes -> runBlocking { dao.setProgress(key, bytes) } }
            )
            val (fileId, note) = when (outcome) {
                is UploadOutcome.Uploaded -> outcome.fileId to null
                is UploadOutcome.AlreadyOnDrive -> outcome.fileId to "Already on Drive"
            }
            lock.withLock {
                dao.get(key)?.let {
                    dao.update(it.copy(state = ImportState.DONE, driveFileId = fileId, message = note, sessionUri = null, bytesUploaded = it.sizeBytes, updatedAt = clock()))
                }
            }
            uploadedSinceRefresh = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: NeedsReviewException) {
            dao.setState(key, ImportState.NEEDS_REVIEW, e.message, clock())
        } catch (e: DriveApiException) {
            if (e.quotaExhausted) {
                dao.setState(key, ImportState.QUEUED, null, clock())
                stop(if (e.reason == "storageQuotaExceeded") "Your Google Drive is full. Free some space, then resume." else "Google's daily upload limit is reached. Resume tomorrow.")
            } else {
                dao.setState(key, ImportState.FAILED, e.message, clock())
            }
        } catch (e: OAuthException) {
            dao.setState(key, ImportState.QUEUED, null, clock())
            stop(if (e.requiresSignIn) "Your Google sign-in has expired. Sign in again to continue." else "Couldn't reach Google. Uploads continue when you resume.")
        } catch (e: IOException) {
            AppLog.w(TAG, "Upload of ${entry.fileName} failed: ${e.message}")
            dao.setState(key, ImportState.FAILED, e.message ?: "Upload failed", clock())
        }
    }

    private fun stop(reason: String) {
        _status.update { it.copy(stoppedReason = reason) }
        loop?.cancel()
        loop = null
        _status.update { it.copy(isRunning = false) }
    }

    /** Resume after the queue stopped by itself (Drive full, daily limit, offline). */
    fun resumeAfterStop() {
        _status.update { it.copy(stoppedReason = null) }
        restartLoop()
    }

    private fun restartLoop() {
        loop?.cancel()
        detector?.close()
        val roots = listOfNotNull(_status.value.moviesFolder, _status.value.seriesFolder).map { Path.of(it) }
        val ready = libraryRootId != null && _status.value.hasWriteAccess == true && roots.isNotEmpty() &&
            !_status.value.pausedByUser && _status.value.stoppedReason == null
        detector = if (ready) FinishedFileDetector(roots, StabilityTracker(quietPeriodMs, clock)) else null
        _status.update { it.copy(isRunning = ready) }
        if (!ready || !runLoop) return
        loop = scope.launch {
            while (isActive) {
                runCatching { scanOnce() }.onFailure { if (it is CancellationException) throw it; AppLog.w(TAG, "Scan failed: ${it.message}") }
                while (isActive && uploadNext()) Unit
                if (uploadedSinceRefresh) {
                    uploadedSinceRefresh = false
                    runCatching { afterUploads() }.onFailure { AppLog.w(TAG, "Refresh after uploads failed: ${it.message}") }
                }
                delay(tickMs)
            }
        }
    }

    private suspend fun markExistingAsSkipped(kind: ImportFolderKind, folder: Path, root: String) {
        val existing = withContext(ioDispatcher) { FinishedFileDetector(listOf(folder), StabilityTracker(0, clock), watchService = null).use { it.fullScan() } }
        for (path in existing) {
            val size = runCatching { Files.size(path) }.getOrNull() ?: continue
            val modified = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrNull() ?: continue
            insert(path, size, modified, ImportTargetResolver.resolve(kind, folder, path), root, ImportState.SKIPPED)
        }
    }

    private fun resolveTarget(file: Path): ImportTarget? {
        for (kind in ImportFolderKind.entries) {
            val folder = settings.importFolder(kind)?.let { Path.of(it) } ?: continue
            if (file.startsWith(folder)) return ImportTargetResolver.resolve(kind, folder, file)
        }
        return null
    }

    private suspend fun insert(path: Path, size: Long, modified: Long, target: ImportTarget, root: String, state: ImportState) {
        val now = clock()
        dao.insert(
            ImportQueueEntity(
                importKey = ImportQueueEntity.keyOf(path.toString(), size, modified),
                localPath = path.toString(),
                fileName = path.fileName.toString(),
                sizeBytes = size,
                lastModifiedMs = modified,
                targetFolderPath = (target as? ImportTarget.Upload)?.folderPath?.joinToString("/"),
                libraryRootId = root,
                state = if (target is ImportTarget.NeedsReview) ImportState.NEEDS_REVIEW else state,
                message = (target as? ImportTarget.NeedsReview)?.reason,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private companion object {
        const val TAG = "ImportQueue"
    }
}
