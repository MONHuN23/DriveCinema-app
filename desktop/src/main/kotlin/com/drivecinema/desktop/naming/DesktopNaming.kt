package com.drivecinema.desktop.naming

import com.drivecinema.app.core.metadata.SeriesFolderListing
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.player.LocalIds
import com.drivecinema.app.core.player.TokenProvider
import com.drivecinema.app.core.sync.FolderQueueCrawler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Task R.3: what the desktop's naming helper and Naming check use: TMDB, and a series folder read read-only with
 * the signed-in session's tokens (the `drive.readonly` part of the grant is enough).
 */
class DesktopNaming(
    val tmdb: TmdbClient,
    val tmdbAvailable: Boolean,
    private val crawler: FolderQueueCrawler
) {
    /** Drive series folder [seriesFolderId]: its name and its videos (three listing requests at most). */
    suspend fun readSeriesFolder(tokens: TokenProvider, seriesFolderId: String): SeriesFolderListing =
        withContext(Dispatchers.IO) { crawler.readSeriesFolder(seriesFolderId, tokens.getAccessToken()) }

    companion object {
        /** A series from a computer folder (a `local:` id) has no Drive folder to check. */
        fun isDriveSeries(seriesId: String): Boolean = !seriesId.startsWith(LocalIds.PREFIX)
    }
}
