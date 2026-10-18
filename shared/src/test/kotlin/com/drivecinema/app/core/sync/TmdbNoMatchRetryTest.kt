package com.drivecinema.app.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.metadata.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Report F-03: a title TMDB has no match for keeps `tmdbId = NULL` and was searched again on every enrichment
 * pass (twice per app start, twice per manual refresh). It is now searched once per session; a TMDB error is
 * still retried.
 */
class TmdbNoMatchRetryTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private val tmdb = MockWebServer()
    private val searches = AtomicInteger()
    @Volatile private var failWithServerError = false

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-f03").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "f03.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        tmdb.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                searches.incrementAndGet()
                return if (failWithServerError) MockResponse().setResponseCode(500)
                else MockResponse().setResponseCode(200).setBody("""{"results":[]}""")
            }
        }
        tmdb.start()
    }

    @After
    fun tearDown() {
        tmdb.shutdown()
        db.close()
        dir.deleteRecursively()
    }

    private fun engine() = DriveLibrarySyncEngine(
        crawler = FolderQueueCrawler(),
        mediaDao = db.mediaDao(),
        syncMetadataDao = db.syncMetadataDao(),
        tmdbClient = TmdbClient(apiKey = "test-key", baseUrl = tmdb.url("/3").toString().removeSuffix("/"))
    )

    private fun insertUnmatchedMovie() = runBlocking {
        db.mediaDao().insertMediaItem(
            MediaItemEntity(id = "home-video", type = MediaType.MOVIE, title = "Family Holiday", parentFolderId = "films")
        )
    }

    @Test
    fun unmatchedTitleIsSearchedOncePerSession() = runBlocking {
        insertUnmatchedMovie()
        val engine = engine()
        engine.enrichExistingCatalog() // app start, before the crawl
        engine.enrichExistingCatalog() // inside the crawl
        engine.enrichExistingCatalog() // manual refresh
        assertEquals(1, searches.get())
    }

    @Test
    fun tmdbErrorIsRetriedOnTheNextPass() = runBlocking {
        insertUnmatchedMovie()
        val engine = engine()
        failWithServerError = true
        engine.enrichExistingCatalog()
        failWithServerError = false
        engine.enrichExistingCatalog()
        assertEquals("an error is not a 'no match', so it is retried", 2, searches.get())
    }

    @Test
    fun renamedTitleIsSearchedAgain() = runBlocking {
        insertUnmatchedMovie()
        val engine = engine()
        engine.enrichExistingCatalog()
        db.mediaDao().insertMediaItem(
            MediaItemEntity(id = "home-video", type = MediaType.MOVIE, title = "Holiday 2025", parentFolderId = "films")
        )
        engine.enrichExistingCatalog()
        assertEquals(2, searches.get())
    }
}
