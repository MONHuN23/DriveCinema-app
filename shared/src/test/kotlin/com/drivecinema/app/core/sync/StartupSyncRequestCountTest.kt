package com.drivecinema.app.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.network.FolderValidationResult
import com.drivecinema.app.core.network.GoogleDriveGateway
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections

/**
 * Report F-04 (characterisation, no behaviour change): the Drive requests of one Android startup sync
 * (`validateStreamingFolder` + `executeInitialCrawl`) for a small fixture library. It pins down the request
 * pattern so a later optimisation can be measured against it: one listing per folder, sequential, plus one
 * duplicate listing of the root folder (validation and crawl both list it) and one start-page-token call.
 */
class StartupSyncRequestCountTest {

    private val folder = "application/vnd.google-apps.folder"
    private fun f(id: String, name: String) = JSONObject().put("id", id).put("name", name).put("mimeType", folder)
    private fun v(id: String, name: String) = JSONObject().put("id", id).put("name", name).put("mimeType", "video/x-matroska")

    // root ─ Films ─ (2 videos, Collection ─ 1 video) ; Series ─ Show A (S1, S2), Show B (S1) ; 1 video in root
    private val tree = mapOf(
        "root" to listOf(f("films", "Films"), f("series", "Series"), v("r1", "Loose Film (2001).mkv")),
        "films" to listOf(v("m1", "Alpha (2010).mkv"), v("m2", "Beta (2012).mkv"), f("coll", "Collection")),
        "coll" to listOf(v("m3", "Gamma (2014).mkv")),
        "series" to listOf(f("showA", "Show A"), f("showB", "Show B")),
        "showA" to listOf(f("a1", "Season 1"), f("a2", "Season 2")),
        "a1" to listOf(v("a1e1", "Show A S01E01.mkv")),
        "a2" to listOf(v("a2e1", "Show A S02E01.mkv")),
        "showB" to listOf(f("b1", "Season 1")),
        "b1" to listOf(v("b1e1", "Show B S01E01.mkv"), v("b1e2", "Show B S01E02.mkv")),
    )

    private val server = MockWebServer()
    private val listed: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val otherCalls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath.endsWith("/changes/startPageToken")) {
                    otherCalls += "startPageToken"
                    return MockResponse().setBody("""{"startPageToken":"42"}""")
                }
                val q = url.queryParameter("q").orEmpty()
                val id = Regex("""^'([^']+)' in parents""").find(q)?.groupValues?.get(1)
                    ?: return MockResponse().setResponseCode(404)
                listed += id
                return MockResponse().setBody(JSONObject().put("files", JSONArray(tree[id] ?: emptyList<JSONObject>())).toString())
            }
        }
        server.start()
        dir = Files.createTempDirectory("drivecinema-f04").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "f04.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        dir.deleteRecursively()
    }

    /** What MainActivity.triggerBackgroundSync does after it has a token. */
    private fun startupSync() = runBlocking {
        val base = server.url("/drive/v3").toString().removeSuffix("/")
        val gateway = GoogleDriveGateway(baseUrl = base)
        val engine = DriveLibrarySyncEngine(FolderQueueCrawler(baseUrl = base), db.mediaDao(), db.syncMetadataDao(), baseUrl = base)
        val validation = gateway.validateStreamingFolder("root", "t").getOrThrow() as FolderValidationResult.Valid
        assertTrue(engine.executeInitialCrawl(validation.filmsFolderId, validation.seriesFolderId, "t", "root").isSuccess)
    }

    @Test
    fun oneStartupSyncListsEveryFolderOnceExceptTheRoot() {
        startupSync()
        assertEquals(10, listed.size) // 9 folders, root twice
        assertEquals(2, listed.count { it == "root" })
        assertEquals(tree.keys - "root", listed.filter { it != "root" }.toSet())
        assertEquals(listed.size - 1, listed.toSet().size)
        assertEquals(listOf("startPageToken"), otherCalls.toList())
    }

    @Test
    fun everyStartupRepeatsTheFullCrawl() {
        // MainActivity runs this on every onCreate (cold start, reopening after Back, and every configuration-change
        // re-creation such as a rotation, since MainActivity does not declare configChanges). Nothing is skipped
        // the second time: the cost is the same, even though nothing changed on Drive.
        startupSync()
        val first = listed.size
        startupSync()
        assertEquals(2 * first, listed.size)
        assertEquals("4 films + 4 episodes, no duplicates after the second sync", 8, db.mediaDao().let { runBlocking { it.getAllMovies().size + it.getEpisodesForSeries("showA").size + it.getEpisodesForSeries("showB").size } })
    }
}
