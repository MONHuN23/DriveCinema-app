package com.drivecinema.app.core.sync

import kotlin.coroutines.cancellation.CancellationException

/**
 * Order of the startup library sync on phone/TV and desktop (Task N.2, F-40). Drive comes first: the token, the folder
 * check and the crawl, which enriches what it wrote with TMDB ([DriveLibrarySyncEngine.executeInitialCrawl]).
 * TMDB enrichment used to run before the token was even fetched; now it runs on its own only when no crawl
 * completed (no token, offline, folder no longer valid, crawl failed), so titles from an earlier sync still get
 * their posters. A cancelled sync (superseded by a folder switch or a refresh) enriches nothing.
 */
object StartupSync {
    /** @param syncDrive true when the crawl completed (and so already enriched the catalog). */
    suspend fun run(syncDrive: suspend () -> Boolean, enrich: suspend () -> Unit) {
        val crawled = try {
            syncDrive()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            enrich()
            throw e
        }
        if (!crawled) enrich()
    }
}
