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
import org.junit.Test

/**
 * Report F-14: the season number of a season folder came from `s` + digits found anywhere in the name, so
 * "Extras 2" became season 2 and "Bonus 2019" season 2019. Only "Season n" / "Sn" as their own word count.
 */
class SeasonFolderParsingTest {

    companion object {
        /** Folder name → expected season (shared with the local-folder crawler test). */
        val CASES = linkedMapOf(
            "Season 1" to 1, "Season 02" to 2, "season 10" to 10, "S3" to 3, "Show_S4" to 4, "Season5" to 5,
            "Show.S06.1080p" to 6, "S07E01-E10" to 7, "Season 8 (2019)" to 8,
            "Extras 2" to 1, "Bonus 2019" to 1, "Specials" to 1, "Behind the Scenes 3" to 1
        )
    }

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun seasonComesOnlyFromASeasonMarker() = runBlocking {
        val folders = CASES.keys.mapIndexed { i, name -> "f$i" to name }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val id = Regex("""^'([^']+)' in parents""").find(request.requestUrl!!.queryParameter("q").orEmpty())!!.groupValues[1]
                val files = when (id) {
                    "series" -> listOf(JSONObject().put("id", "show").put("name", "Show").put("mimeType", FolderQueueCrawler.FOLDER_MIME_TYPE))
                    "show" -> folders.map { (fid, name) -> JSONObject().put("id", fid).put("name", name).put("mimeType", FolderQueueCrawler.FOLDER_MIME_TYPE) }
                    else -> listOf(JSONObject().put("id", "ep_$id").put("name", "Episode 1.mkv").put("mimeType", "video/x-matroska"))
                }
                return MockResponse().setBody(JSONObject().put("files", JSONArray(files)).toString())
            }
        }
        server.start()
        val library = FolderQueueCrawler(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
            .crawl(seriesFolderId = "series", accessToken = "t")
        val seasonByFolder = library.episodes.associate { ep -> folders.toMap().getValue(ep.parentFolderId) to ep.seasonNumber }
        assertEquals(CASES, CASES.keys.associateWith { seasonByFolder[it] })
    }
}
