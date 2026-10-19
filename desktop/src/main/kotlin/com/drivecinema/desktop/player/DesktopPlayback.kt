package com.drivecinema.desktop.player

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.player.NextEpisode
import com.drivecinema.app.core.player.PlaybackSelector
import com.drivecinema.desktop.ui.PlaybackRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the player screen opens for a catalog title, and the key its progress is saved under. */
data class ResolvedRequest(val request: PlaybackRequest, val progressKey: String?)

/** Turns "play this title" into a stream URL with the same copy/resume rules as Android. */
class DesktopPlayback(
    val database: DriveCinemaDatabase,
    private val vlcAvailable: () -> Boolean = { VlcAvailability.prepare() },
    // resolve() is called from Compose (the UI thread); the libVLC search blocks, and on Windows without VLC it is
    // slow enough to freeze the window (F.11), so it never runs on the caller's thread.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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

    suspend fun resolve(itemId: String, fromStart: Boolean, proxy: DriveStreamProxy?): ResolvedRequest {
        if (!withContext(ioDispatcher) { vlcAvailable() }) return ResolvedRequest(PlaybackRequest.VlcMissing, null)
        val copy = selector.select(itemId) ?: return unavailable("This title is not available right now. Refresh the library and try again.")
        return when (copy.kind) {
            SourceKind.DRIVE -> {
                proxy ?: return unavailable("Sign in to play titles from Google Drive.")
                ResolvedRequest(
                    PlaybackRequest.Ready(proxy.urlFor(copy.sourceKey), startMs = if (fromStart) 0L else copy.resumePositionMs),
                    progressKey = copy.sourceKey
                )
            }
            SourceKind.LOCAL -> {
                val exists = runCatching { java.nio.file.Files.isRegularFile(java.nio.file.Paths.get(java.net.URI(copy.sourceKey))) }
                    .getOrDefault(false)
                if (!exists) return unavailable("The local copy can't be found. Is the drive with your local folder plugged in?")
                // VLC plays file: URIs directly; the URI is also the progress key (like content:// on Android).
                ResolvedRequest(
                    PlaybackRequest.Ready(copy.sourceKey, startMs = if (fromStart) 0L else copy.resumePositionMs),
                    progressKey = copy.sourceKey
                )
            }
        }
    }

    private fun unavailable(message: String) = ResolvedRequest(PlaybackRequest.Unavailable(message), null)
}
