package com.drivecinema.app.core.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tasks R.2/R.3: the naming check reads one series folder: its name (`files.get`, the catalog keeps TMDB's title),
 * then its videos directly in it and in its season folders, in three requests at most, read-only.
 */
class SeriesFolderReadTest {

    private val server = MockWebServer()
    private val tree = FakeDriveTree().apply {
        folder("series", "show", "One Piece (Anime) (1999)")
        folder("show", "s1", "Season 01")
        folder("show", "s22", "Season 22")
        video("s1", "e1", "One Piece S01E01.mkv")
        file("s1", "nfo", "notes.txt", "text/plain")
        video("s22", "e1086", "One Piece S22E1086.mkv")
        video("show", "loose", "One Piece - 1100.mkv")
    }
    private val names = mapOf("show" to "One Piece (Anime) (1999)")
    private val methods = mutableListOf<String>()

    @After
    fun tearDown() = server.shutdown()

    private fun crawler(): FolderQueueCrawler {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                methods += request.method!!
                val id = request.requestUrl!!.encodedPath.substringAfter("/files/", "")
                if (id.isNotEmpty()) {
                    assertEquals("name", request.requestUrl!!.queryParameter("fields"))
                    return names[id]?.let { MockResponse().setBody(JSONObject().put("name", it).toString()) }
                        ?: MockResponse().setResponseCode(404)
                }
                return tree.dispatcher.dispatch(request)
            }
        }
        server.start()
        return FolderQueueCrawler(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
    }

    @Test
    fun readsTheFolderNameAndItsVideosInThreeGetRequests() = runBlocking {
        val listing = crawler().readSeriesFolder("show", "token")

        assertEquals("One Piece (Anime) (1999)", listing.seriesFolderName)
        assertEquals("show", listing.seriesFolderId)
        assertEquals(
            setOf("Season 01/One Piece S01E01.mkv", "Season 22/One Piece S22E1086.mkv", "/One Piece - 1100.mkv"),
            listing.files.map { "${it.seasonFolderName.orEmpty()}/${it.name}" }.toSet()
        )
        assertEquals("s22", listing.files.single { it.fileId == "e1086" }.folderId)
        assertEquals(setOf("Season 01", "Season 22"), listing.subfolderNames.toSet())
        assertEquals(listOf("GET", "GET", "GET"), methods)
    }

    @Test
    fun aFolderDriveCantFindIsAnError() {
        val crawler = crawler()
        val failed = runCatching { runBlocking { crawler.readSeriesFolder("gone", "token") } }.exceptionOrNull()
        assertTrue(failed is IOException)
    }
}
