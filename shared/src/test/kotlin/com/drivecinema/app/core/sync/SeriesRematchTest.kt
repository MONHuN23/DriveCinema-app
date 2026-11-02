package com.drivecinema.app.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SyncMetadataEntity
import com.drivecinema.app.core.metadata.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Report F-48, from the user's screenshots (2026-11-02):
 * - TMDB gained an empty "Bleach: Thousand-Year Blood War" (2022: no poster, no genres, one episode), and the folder
 *   "bleach: Thousand Year Blood War (2022)" was matched to it: no poster, and only E01 had a title.
 * - Episodes the new match didn't cover kept the old match's titles (the desktop showed Bleach's "A Shinigami's
 *   Work" on E02).
 * - The desktop kept an older build's "no match" for "One Piece Anime" for a week after F-43 could match it.
 */
class SeriesRematchTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private val drive = MockWebServer()
    private val tmdb = MockWebServer()
    private val searches = CopyOnWriteArrayList<String>()

    private val bare = """{"id": 340620, "name": "Bleach: Thousand-Year Blood War",
        "original_name": "Bleach: Thousand-Year Blood War", "first_air_date": "2022-10-10", "poster_path": null, "genre_ids": []}"""
    private val bleach = """{"id": 30984, "name": "Bleach", "original_name": "BLEACH", "first_air_date": "2004-10-05",
        "poster_path": "/bleach.jpg", "genre_ids": [10759, 16]}"""
    private val onePiece = """{"id": 37854, "name": "One Piece", "original_name": "ONE PIECE", "first_air_date": "1999-10-20",
        "poster_path": "/op.jpg", "genre_ids": [10759, 16]}"""

    private val routes = mapOf(
        "/tv/340620" to """{"seasons": [{"season_number": 1, "name": "Season 1", "episode_count": 1}]}""",
        "/tv/340620/season/1" to """{"episodes": [{"episode_number": 1, "name": "S1.E1 ∙ The Blood Warfare"}]}""",
        "/tv/30984" to """{"seasons": [
            {"season_number": 1, "name": "Bleach", "episode_count": 366},
            {"season_number": 2, "name": "Thousand-Year Blood War", "episode_count": 3, "poster_path": "/tybw.jpg"}]}""",
        "/tv/30984/season/1" to """{"episodes": [{"episode_number": 1, "name": "The Day I Became a Shinigami"},
            {"episode_number": 2, "name": "A Shinigami's Work"}, {"episode_number": 3, "name": "The Older Brother's Wish"}]}""",
        "/tv/30984/season/2" to """{"episodes": [
            {"episode_number": 1, "name": "The Blood Warfare", "air_date": "2022-10-11"},
            {"episode_number": 2, "name": "Foundation Stones", "air_date": "2022-10-18"}]}""",
        "/tv/37854/season/1" to """{"episodes": [{"episode_number": 1, "name": "I'm Luffy!"}]}"""
    )

    private val tree = FakeDriveTree().apply {
        folder("series", "tybw", "bleach: Thousand Year Blood War (2022)")
        folder("tybw", "tybw-s1", "Season 01")
        for (n in 1..3) video("tybw-s1", "tybw-e$n", "BLEACH.Thousand-Year.Blood.War.S01E0$n.1080p-VARYG.mkv")
        folder("series", "op", "One Piece (Anime)")
        folder("op", "op-s1", "Season 01")
        video("op-s1", "op-e1", "One.Piece.S01E01.mkv")
    }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-f48").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "f48.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        drive.dispatcher = tree.dispatcher
        drive.start()
        tmdb.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val path = url.encodedPath.removePrefix("/3")
                if (path == "/search/tv") {
                    val query = url.queryParameter("query")!!
                    searches += query
                    val withYear = url.queryParameter("first_air_date_year") != null
                    val results = when {
                        query.contains("Blood War", ignoreCase = true) -> if (withYear) listOf(bare) else listOf(bare, bleach)
                        query.startsWith("One Piece") && !query.contains("Anime") -> listOf(onePiece)
                        else -> emptyList()
                    }
                    return MockResponse().setBody("""{"results": [${results.joinToString(",")}]}""")
                }
                return routes[path]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
            }
        }
        tmdb.start()
    }

    @After
    fun tearDown() {
        drive.shutdown()
        tmdb.shutdown()
        db.close()
        dir.deleteRecursively()
    }

    private fun crawl() = runBlocking {
        val base = drive.url("/drive/v3").toString().removeSuffix("/")
        DriveLibrarySyncEngine(
            crawler = FolderQueueCrawler(baseUrl = base),
            mediaDao = db.mediaDao(),
            syncMetadataDao = db.syncMetadataDao(),
            tmdbClient = TmdbClient(apiKey = "test-key", baseUrl = tmdb.url("/3").toString().removeSuffix("/")),
            baseUrl = base
        ).executeInitialCrawl("films", "series", "token", "root").getOrThrow()
    }

    private fun titles(seriesId: String) = runBlocking { db.mediaDao().getEpisodesForSeries(seriesId).map { it.title } }

    private fun episode(id: String, seriesId: String, number: Int, title: String) = MediaItemEntity(
        id = id, type = MediaType.EPISODE, title = title, parentFolderId = "$seriesId-s1",
        seasonNumber = 1, episodeNumber = number, seriesId = seriesId
    )

    /** The desktop's database before the fix: TYBW matched to the bare entry, One Piece "no match" from an old build. */
    private fun storeTheDesktopsOldState(version: String?) = runBlocking {
        val dao = db.mediaDao()
        dao.insertSingleSeries(SeriesEntity("tybw", "bleach: Thousand Year Blood War", 2022, tmdbId = 340620))
        dao.insertMediaItem(episode("tybw-e1", "tybw", 1, "S1.E1 ∙ The Blood Warfare"))
        dao.insertMediaItem(episode("tybw-e2", "tybw", 2, "A Shinigami's Work"))
        dao.insertMediaItem(episode("tybw-e3", "tybw", 3, "The Older Brother's Wish"))
        dao.insertSingleSeries(SeriesEntity("op", "One Piece Anime"))
        dao.insertMediaItem(episode("op-e1", "op", 1, "One Piece S01E01"))
        val metadata = db.syncMetadataDao()
        metadata.setMetadata(
            SyncMetadataEntity("${DriveLibrarySyncEngine.TMDB_NO_MATCH_PREFIX}series|op|One Piece Anime", null, System.currentTimeMillis())
        )
        if (version != null) metadata.setMetadata(SyncMetadataEntity(DriveLibrarySyncEngine.SERIES_MATCHING_KEY, version, 1L))
    }

    @Test
    fun arcFolderSkipsTheBareTmdbEntryForTheShowsArc() {
        crawl()

        val series = runBlocking { db.mediaDao().getSeriesById("tybw")!! }
        assertEquals(30984, series.tmdbId)
        assertEquals("/tybw.jpg", series.posterPath)
        assertEquals(
            listOf("The Blood Warfare", "Foundation Stones", "BLEACH Thousand-Year Blood War S01E03 1080p-VARYG"),
            titles("tybw")
        )
    }

    @Test
    fun anOlderMatchingVersionRedoesEverySeriesOnce() {
        storeTheDesktopsOldState(version = null)

        crawl()

        assertEquals(30984, runBlocking { db.mediaDao().getSeriesById("tybw")!!.tmdbId })
        assertEquals(
            "E03 has no TMDB episode now: its file name, not Bleach's old title",
            listOf("The Blood Warfare", "Foundation Stones", "BLEACH Thousand-Year Blood War S01E03 1080p-VARYG"),
            titles("tybw")
        )
        assertEquals("the old 'no match' is forgotten", 37854, runBlocking { db.mediaDao().getSeriesById("op")!!.tmdbId })
        assertEquals(listOf("I'm Luffy!"), titles("op"))
        assertEquals(
            DriveLibrarySyncEngine.SERIES_MATCHING_VERSION,
            runBlocking { db.syncMetadataDao().getMetadata(DriveLibrarySyncEngine.SERIES_MATCHING_KEY)?.syncToken }
        )

        searches.clear()
        crawl()
        assertEquals("the next crawl matches nothing again", emptyList<String>(), searches)
    }

    @Test
    fun theCurrentVersionKeepsStoredMatches() {
        storeTheDesktopsOldState(version = DriveLibrarySyncEngine.SERIES_MATCHING_VERSION)

        crawl()

        assertEquals(340620, runBlocking { db.mediaDao().getSeriesById("tybw")!!.tmdbId })
        assertEquals("One Piece's 'no match' still holds", null, runBlocking { db.mediaDao().getSeriesById("op")!!.tmdbId })
        assertTrue(searches.isEmpty())
    }

    @Test
    fun aRenamedSeriesDropsTheOldMatchsEpisodeTitles() {
        runBlocking {
            val dao = db.mediaDao()
            // Matched as Bleach before the folder was renamed: E03 has Bleach's third title.
            dao.insertSingleSeries(SeriesEntity("tybw", "Bleach", null, tmdbId = 30984, posterPath = "/bleach.jpg"))
            dao.insertMediaItem(episode("tybw-e3", "tybw", 3, "The Older Brother's Wish").copy(overview = "Sora attacks."))
            db.syncMetadataDao().setMetadata(
                SyncMetadataEntity(DriveLibrarySyncEngine.SERIES_MATCHING_KEY, DriveLibrarySyncEngine.SERIES_MATCHING_VERSION, 1L)
            )
        }

        crawl()

        val e3 = runBlocking { db.mediaDao().getEpisodesForSeries("tybw").single { it.episodeNumber == 3 } }
        assertEquals("BLEACH Thousand-Year Blood War S01E03 1080p-VARYG", e3.title)
        assertEquals(null, e3.overview)
    }
}
