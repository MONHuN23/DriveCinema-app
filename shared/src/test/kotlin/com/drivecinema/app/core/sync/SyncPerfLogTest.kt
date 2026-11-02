package com.drivecinema.app.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.logging.LogSink
import com.drivecinema.app.core.logging.Perf
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.network.FolderValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Task M.0: the sync phases each log one `Perf` line with their request counts, and the counts match the
 * requests that were actually sent. Counts only: no titles in the lines.
 */
class SyncPerfLogTest {

    private val folder = "application/vnd.google-apps.folder"
    private fun f(id: String, name: String) = JSONObject().put("id", id).put("name", name).put("mimeType", folder)
    private fun v(id: String, name: String) = JSONObject().put("id", id).put("name", name).put("mimeType", "video/x-matroska")

    // root ─ Films (2 videos) ; Series ─ Show A ─ Season 1 (1 video)
    private val tree = mapOf(
        "root" to listOf(f("films", "Films"), f("series", "Series")),
        "films" to listOf(v("m1", "Alpha (2010).mkv"), v("m2", "Beta (2012).mkv")),
        "series" to listOf(f("showA", "Show A")),
        "showA" to listOf(f("a1", "Season 1")),
        "a1" to listOf(v("a1e1", "Show A S01E01.mkv")),
    )

    private val server = MockWebServer()
    private val driveListings = AtomicInteger()
    private val tmdbRequests = AtomicInteger()
    private val originalSink = AppLog.sink
    private val lines = mutableListOf<String>()
    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath.startsWith("/3/")) {
                    tmdbRequests.incrementAndGet()
                    return MockResponse().setBody("""{"results":[]}""")
                }
                if (url.encodedPath.endsWith("/changes/startPageToken")) return MockResponse().setBody("""{"startPageToken":"1"}""")
                val q = url.queryParameter("q").orEmpty()
                val ids = FakeDriveListing.folderIds(q).ifEmpty { return MockResponse().setResponseCode(404) }
                driveListings.incrementAndGet()
                return MockResponse().setBody(FakeDriveListing.body(ids) { tree[it] ?: emptyList() })
            }
        }
        server.start()
        AppLog.sink = LogSink { _, tag, message, _ -> if (tag == Perf.TAG) synchronized(lines) { lines += message } }
        dir = Files.createTempDirectory("drivecinema-m0").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "m0.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    }

    @After
    fun tearDown() {
        AppLog.sink = originalSink
        server.shutdown()
        db.close()
        dir.deleteRecursively()
    }

    private fun line(name: String): String = synchronized(lines) { lines.single { it.startsWith("$name ") } }

    private fun refresh(): LibraryRefreshResult = runBlocking {
        val drive = server.url("/drive/v3").toString().removeSuffix("/")
        val gateway = com.drivecinema.app.core.network.GoogleDriveGateway(baseUrl = drive)
        val engine = DriveLibrarySyncEngine(
            FolderQueueCrawler(baseUrl = drive), db.mediaDao(), db.syncMetadataDao(),
            tmdbClient = TmdbClient(apiKey = "test-key", baseUrl = server.url("/3").toString().removeSuffix("/")),
            baseUrl = drive
        )
        LibraryRefresher(
            validateFolder = { root, token -> gateway.validateStreamingFolder(root, token) },
            crawl = { films, series, token, root -> engine.executeInitialCrawl(films, series, token, root) },
        ).refresh("root", "t")
    }

    @Test
    fun everySyncPhaseLogsOneLine() {
        assertEquals(LibraryRefreshResult.Refreshed, refresh())
        for (phase in listOf("sync.folderCheck", "sync.crawl", "sync.dbWrite", "sync.tmdb")) {
            assertTrue("$phase in $lines", Regex("""^$phase \d+ ms""").containsMatchIn(line(phase)))
        }
    }

    @Test
    fun crawlLineCountsItsDriveRequestsAndWhatItFound() {
        refresh()
        val listingsByFolderCheck = 1 // the root, listed again by the crawl (F-04)
        val crawlRequests = driveListings.get() - listingsByFolderCheck
        assertEquals("one per folder level (N.1): root+Films+Series, Show A, Season 1", 3, crawlRequests)
        assertTrue(line("sync.crawl"), line("sync.crawl").endsWith(" requests=$crawlRequests movies=2 series=1 episodes=1"))
    }

    @Test
    fun tmdbLineCountsItsRequestsAndSearches() {
        refresh()
        // 2 films + 1 series without a match; "Show A" has no year, so no second search without the year.
        assertEquals(3, tmdbRequests.get())
        assertTrue(line("sync.tmdb"), line("sync.tmdb").endsWith(" requests=3 searched=3 enriched=0"))
    }

    @Test
    fun linesHoldCountsOnlyNoTitles() {
        refresh()
        val all = synchronized(lines) { lines.joinToString("\n") }
        for (title in listOf("Alpha", "Beta", "Show A", "Season 1")) assertFalse(all, title in all)
    }
}
