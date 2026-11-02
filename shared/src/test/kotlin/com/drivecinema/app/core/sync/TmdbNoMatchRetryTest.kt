package com.drivecinema.app.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
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
 * still retried. Task N.2 (F-40): the "no match" is kept in the database, so a new app start (a new engine) doesn't
 * search it again until the retry date; series the library doesn't show are not searched.
 */
class TmdbNoMatchRetryTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private val tmdb = MockWebServer()
    private val searches = AtomicInteger()
    private val searchedQueries = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile private var failWithServerError = false
    private var now = 1_800_000_000_000L

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
                request.requestUrl?.queryParameter("query")?.let { searchedQueries += it }
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
        tmdbClient = TmdbClient(apiKey = "test-key", baseUrl = tmdb.url("/3").toString().removeSuffix("/")),
        clock = { now }
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

    @Test
    fun noMatchIsRememberedAcrossAppStarts() = runBlocking {
        insertUnmatchedMovie()
        engine().enrichExistingCatalog() // first app start
        engine().enrichExistingCatalog() // next app start: a new engine, the same database
        now += DriveLibrarySyncEngine.TMDB_NO_MATCH_RETRY_MS - 1
        engine().enrichExistingCatalog() // just before the retry date
        assertEquals(1, searches.get())
    }

    @Test
    fun noMatchIsSearchedAgainAfterTheRetryDate() = runBlocking {
        insertUnmatchedMovie()
        engine().enrichExistingCatalog()
        now += DriveLibrarySyncEngine.TMDB_NO_MATCH_RETRY_MS
        engine().enrichExistingCatalog()
        assertEquals(2, searches.get())
        engine().enrichExistingCatalog()
        assertEquals("the new answer is remembered for another week", 2, searches.get())
    }

    @Test
    fun expiredNoMatchRowsAreRemoved() = runBlocking {
        insertUnmatchedMovie()
        val engine = engine()
        engine.enrichExistingCatalog()
        db.mediaDao().insertMediaItem( // renamed: the old title's row is never used again
            MediaItemEntity(id = "home-video", type = MediaType.MOVIE, title = "Holiday 2025", parentFolderId = "films")
        )
        engine.enrichExistingCatalog()
        val prefix = DriveLibrarySyncEngine.TMDB_NO_MATCH_PREFIX
        assertEquals(2, db.syncMetadataDao().getKeysWithPrefix(prefix).size)
        now += DriveLibrarySyncEngine.TMDB_NO_MATCH_RETRY_MS
        db.mediaDao().insertMediaItem(
            MediaItemEntity(id = "home-video", type = MediaType.MOVIE, title = "Holiday 2025", parentFolderId = "films", tmdbId = 7)
        )
        engine.enrichExistingCatalog()
        assertEquals(emptyList<String>(), db.syncMetadataDao().getKeysWithPrefix(prefix))
    }

    @Test
    fun theDriveSyncTokenIsNotTouched() = runBlocking {
        db.syncMetadataDao().setMetadata(
            com.drivecinema.app.core.database.entity.SyncMetadataEntity(DriveLibrarySyncEngine.SYNC_TOKEN_KEY, "page-7", 1L)
        )
        insertUnmatchedMovie()
        now += 10 * DriveLibrarySyncEngine.TMDB_NO_MATCH_RETRY_MS
        engine().enrichExistingCatalog()
        assertEquals("page-7", db.syncMetadataDao().getMetadata(DriveLibrarySyncEngine.SYNC_TOKEN_KEY)?.syncToken)
    }

    @Test
    fun seriesTheLibraryDoesNotShowAreNotSearched() = runBlocking {
        val dao = db.mediaDao()
        dao.insertSingleSeries(SeriesEntity(id = "empty", title = "Empty Show")) // no episodes left
        dao.insertSingleSeries(SeriesEntity(id = "gone", title = "Gone Show")) // only a deleted episode
        dao.insertMediaItem(episode("gone-e1", seriesId = "gone", deleted = true))
        dao.insertSingleSeries(SeriesEntity(id = "live", title = "Live Show"))
        dao.insertMediaItem(episode("live-e1", seriesId = "live", deleted = false))

        engine().enrichExistingCatalog()

        assertEquals(listOf("Live Show"), searchedQueries.distinct())
    }

    private fun episode(id: String, seriesId: String, deleted: Boolean) = MediaItemEntity(
        id = id, type = MediaType.EPISODE, title = id, parentFolderId = "season", seasonNumber = 1, episodeNumber = 1,
        seriesId = seriesId, isDeleted = deleted
    )
}
