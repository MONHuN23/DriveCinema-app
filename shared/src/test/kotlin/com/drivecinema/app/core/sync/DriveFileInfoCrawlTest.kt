package com.drivecinema.app.core.sync

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
import org.junit.Test

/**
 * Task L.3: the listing request also asks for `size` and `videoMediaMetadata` (no extra Drive calls), and files Drive
 * hasn't processed simply have no resolution or length.
 */
class DriveFileInfoCrawlTest {

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun crawlKeepsSizeResolutionAndLengthPerFile() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val files = listOf(
                    // int64 fields arrive as strings, as the Drive API sends them.
                    JSONObject().put("id", "dune").put("name", "Dune Part Two (2024).mkv").put("mimeType", "video/x-matroska")
                        .put("size", "9019431321")
                        .put("videoMediaMetadata", JSONObject().put("width", 3840).put("height", 1600).put("durationMillis", "9960000")),
                    JSONObject().put("id", "fresh").put("name", "Fresh Upload.mp4").put("mimeType", "video/mp4").put("size", "1048576"),
                    JSONObject().put("id", "notes").put("name", "notes.txt").put("mimeType", "text/plain").put("size", "10")
                )
                return MockResponse().setBody(JSONObject().put("files", JSONArray(files)).toString())
            }
        }
        server.start()

        val library = FolderQueueCrawler(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
            .crawl(filmsFolderId = "films", accessToken = "t")

        val fields = server.takeRequest().requestUrl!!.queryParameter("fields").orEmpty()
        assertTrue(fields, "size" in fields && "videoMediaMetadata(width, height, durationMillis)" in fields)
        assertEquals(
            DriveFileInfo(sizeBytes = 9_019_431_321, widthPx = 3840, heightPx = 1600, durationMs = 9_960_000, container = "MKV"),
            library.fileInfo["dune"]
        )
        assertEquals(DriveFileInfo(sizeBytes = 1_048_576, container = "MP4"), library.fileInfo["fresh"])
        assertEquals("only videos are kept", setOf("dune", "fresh"), library.fileInfo.keys)
    }
}
