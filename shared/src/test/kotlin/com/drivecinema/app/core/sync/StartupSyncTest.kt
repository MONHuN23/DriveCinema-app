package com.drivecinema.app.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Task N.2 (F-40): the startup sync used to search TMDB before the Drive token was even fetched. Now Drive comes
 * first (the crawl enriches what it wrote), and TMDB runs alone only when no crawl completed.
 */
class StartupSyncTest {

    private val events = mutableListOf<String>()

    @Test
    fun driveComesFirstAndACompletedCrawlNeedsNoSeparateEnrichment() = runTest {
        StartupSync.run(
            syncDrive = { events += "token"; events += "crawl+enrich"; true },
            enrich = { events += "enrich" }
        )
        assertEquals(listOf("token", "crawl+enrich"), events)
    }

    @Test
    fun withoutACrawlTheCatalogIsStillEnrichedAfterwards() = runTest {
        // No token (offline), or the folder is no longer a valid library, or the crawl failed.
        StartupSync.run(syncDrive = { events += "token"; false }, enrich = { events += "enrich" })
        assertEquals(listOf("token", "enrich"), events)
    }

    @Test
    fun aDriveErrorStillEnrichesAndIsPassedOn() = runTest {
        val error = runCatching {
            StartupSync.run(syncDrive = { events += "token"; throw IOException("offline") }, enrich = { events += "enrich" })
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(listOf("token", "enrich"), events)
    }

    @Test
    fun aCancelledSyncEnrichesNothing() = runTest {
        // Superseded by a folder switch or a refresh: that one enriches the new catalog.
        val error = runCatching {
            StartupSync.run(syncDrive = { throw CancellationException("superseded") }, enrich = { events += "enrich" })
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(emptyList<String>(), events)
    }
}
