package com.drivecinema.desktop.player

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.host.HostLibrarySync
import com.drivecinema.app.core.player.NextEpisode
import com.drivecinema.app.core.player.PlaybackSelector
import com.drivecinema.desktop.ui.PlaybackRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Task I.2: what the user chose after the slow-connection warning. */
enum class SlowChoice { ASK, PLAY_FROM_PC, PLAY_FALLBACK }

/** What the player screen opens for a catalog title, and the key its progress is saved under. */
data class ResolvedRequest(val request: PlaybackRequest, val progressKey: String?)

/** Turns "play this title" into a stream URL with the same copy/resume rules as Android. */
class DesktopPlayback(
    val database: DriveCinemaDatabase,
    private val vlcAvailable: () -> Boolean = { VlcAvailability.prepare() },
    // resolve() is called from Compose (the UI thread); the libVLC search blocks, and on Windows without VLC it is
    // slow enough to freeze the window (F.11), so it never runs on the caller's thread.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Task I.2: other PCs this computer streams from; null where that isn't set up (tests). */
    private val hosts: HostLibrarySync? = null
) {
    private val selector = PlaybackSelector(database.mediaSourceDao())

    /** (catalog ID, player title) of the episode after [itemId], or null for films and last episodes. */
    suspend fun nextEpisode(itemId: String): Pair<String, String>? {
        val dao = database.mediaDao()
        val seriesId = dao.getMediaItemById(itemId)?.seriesId ?: return null
        val next = NextEpisode.after(dao.getEpisodesForSeries(seriesId), itemId) ?: return null
        val seriesTitle = dao.getSeriesById(seriesId)?.title.orEmpty()
        return next.id to NextEpisode.label(seriesTitle, next)
    }

    suspend fun resolve(
        itemId: String,
        fromStart: Boolean,
        proxy: DriveStreamProxy?,
        hostProxy: HostStreamProxy? = null,
        slowChoice: SlowChoice = SlowChoice.ASK
    ): ResolvedRequest {
        if (!withContext(ioDispatcher) { vlcAvailable() }) return ResolvedRequest(PlaybackRequest.VlcMissing, null)
        val candidates = selector.candidates(itemId)
        if (candidates.isEmpty()) return unavailable("This title is not available right now. Refresh the library and try again.")
        // Best copy first (local, another PC, Drive); a copy that can't play right now falls through to the next.
        val playable = mutableListOf<Pair<ResolvedRequest, SourceKind>>()
        var firstProblem: String? = null
        for (copy in candidates) {
            val startMs = if (fromStart) 0L else copy.resumePositionMs
            val url: String = when (copy.kind) {
                SourceKind.DRIVE -> proxy?.urlFor(copy.sourceKey).also { if (it == null) firstProblem = firstProblem ?: "Sign in to play titles from Google Drive." }
                SourceKind.LOCAL -> {
                    val exists = runCatching { java.nio.file.Files.isRegularFile(java.nio.file.Paths.get(java.net.URI(copy.sourceKey))) }
                        .getOrDefault(false)
                    // VLC plays file: URIs directly; the URI is also the progress key (like content:// on Android).
                    copy.sourceKey.takeIf { exists }
                        .also { if (it == null) firstProblem = firstProblem ?: "The local copy can't be found. Is the drive with your local folder plugged in?" }
                }
                // Task I.2: another PC's copy, through the loopback proxy (VLC can't pin the PC's certificate).
                SourceKind.HOST -> {
                    val reachable = hostProxy != null && hosts != null && withContext(ioDispatcher) { hosts.stream(copy.sourceKey, recheck = true) } != null
                    hostProxy?.urlFor(copy.sourceKey).takeIf { reachable }
                        .also { if (it == null) firstProblem = firstProblem ?: "The PC with this title can't be reached. Is it on, with sharing turned on?" }
                }
            } ?: continue
            playable += ResolvedRequest(PlaybackRequest.Ready(url, startMs), progressKey = copy.sourceKey) to copy.kind
            if (playable.size == 2 || copy.kind != SourceKind.HOST) break
        }
        val (best, kind) = playable.firstOrNull() ?: return unavailable(firstProblem ?: "This title is not available right now.")
        val fallback = playable.getOrNull(1)?.first
        if (kind != SourceKind.HOST || hosts == null) return best
        return when (slowChoice) {
            SlowChoice.PLAY_FROM_PC -> best
            SlowChoice.PLAY_FALLBACK -> fallback ?: best
            SlowChoice.ASK -> {
                // Task I.2 (design §2.6): warn only when needed, and offer the Drive copy when there is one.
                val verdict = hosts.checkConnection(itemId, best.progressKey!!)?.takeIf { it.tooSlow } ?: return best
                ResolvedRequest(PlaybackRequest.SlowConnection(verdict.message(hasDriveCopy = fallback != null), hasFallback = fallback != null), null)
            }
        }
    }

    private fun unavailable(message: String) = ResolvedRequest(PlaybackRequest.Unavailable(message), null)
}
