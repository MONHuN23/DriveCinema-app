package com.drivecinema.app.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.logging.LogSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * N.2 device check (2026-10-27): one cold start on the phone ended with `sync.crawl … aborted=true` and `ok=false`,
 * and nothing in the log said why, because the engine turned the error into a `Result` silently. A failed crawl now
 * logs one warning with the reason, never the Drive answer itself.
 */
class CrawlFailureLogTest {

    private val server = MockWebServer()
    private val originalSink = AppLog.sink
    private val warnings = mutableListOf<String>()
    @Volatile private var answer: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(503) }
    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answer(request)
        }
        server.start()
        AppLog.sink = LogSink { priority, _, message, _ ->
            if (priority == AppLog.Priority.WARN) synchronized(warnings) { warnings += message }
        }
        dir = Files.createTempDirectory("drivecinema-crawlfail").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "c.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    }

    @After
    fun tearDown() {
        AppLog.sink = originalSink
        server.shutdown()
        db.close()
        dir.deleteRecursively()
    }

    private fun crawl(): Result<Unit> = runBlocking {
        val base = server.url("/drive/v3").toString().removeSuffix("/")
        DriveLibrarySyncEngine(FolderQueueCrawler(baseUrl = base), db.mediaDao(), db.syncMetadataDao(), baseUrl = base)
            .executeInitialCrawl(filmsFolderId = "films", accessToken = "secret-token", rootFolderId = "root")
    }

    @Test
    fun aFailedCrawlLogsWhy() {
        assertTrue(crawl().isFailure)
        val line = synchronized(warnings) { warnings.single() }
        assertTrue(line, line.startsWith("Library crawl failed, catalog left unchanged: IOException"))
        assertTrue(line, line.contains("code 503"))
        assertFalse("never the access token", line.contains("secret-token"))
    }

    @Test
    fun anUnreadableDriveAnswerIsLoggedWithoutItsContent() {
        answer = { MockResponse().setBody("not json, mentions My Holiday Video.mkv") }
        assertTrue(crawl().isFailure)
        val line = synchronized(warnings) { warnings.single() }
        assertEquals("Library crawl failed, catalog left unchanged: JSONException (unreadable answer from Drive)", line)
    }

    @Test
    fun aSuccessfulCrawlLogsNoWarning() {
        answer = { request ->
            if (request.path.orEmpty().contains("startPageToken")) MockResponse().setBody("""{"startPageToken":"1"}""")
            else MockResponse().setBody("""{"files":[]}""")
        }
        assertTrue(crawl().isSuccess)
        assertEquals(emptyList<String>(), synchronized(warnings) { warnings.toList() })
    }
}
