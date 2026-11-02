package com.drivecinema.app.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.metadata.MediaFilenameParser
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * F-43: "Series/One Piece" matched the 2023 live-action show, and "One Piece (Anime)" (read as "One Piece Anime")
 * matched nothing. The "Anime" tag now searches "One Piece" among animated shows, and the fansub and scene files'
 * running numbers find their episodes.
 */
class AnimeSeriesEnrichmentTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private val tmdb = MockWebServer()
    private val queries = CopyOnWriteArrayList<String>()

    private val routes = mapOf(
        "/search/tv" to """{"results": [
            {"id": 111110, "name": "ONE PIECE", "original_name": "ONE PIECE", "first_air_date": "2023-08-31",
             "genre_ids": [10759, 10765], "poster_path": "/live.jpg"},
            {"id": 37854, "name": "One Piece", "original_name": "ONE PIECE", "first_air_date": "1999-10-20",
             "genre_ids": [10759, 35, 16], "poster_path": "/anime.jpg"}]}""",
        "/tv/37854" to """{"seasons": [{"season_number": 0, "name": "Specials", "episode_count": 39},
            {"season_number": 1, "name": "East Blue", "episode_count": 61},
            {"season_number": 2, "name": "Egghead", "episode_count": 3}]}""",
        "/tv/37854/season/1" to """{"episodes": [{"episode_number": 1, "name": "I'm Luffy!"}]}""",
        "/tv/37854/season/2" to """{"episodes": [{"episode_number": 62, "name": "Episode 62"},
            {"episode_number": 63, "name": "Episode 63"}, {"episode_number": 64, "name": "Episode 64"}]}"""
    )

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-anime").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "anime.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        tmdb.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                request.requestUrl!!.queryParameter("query")?.let { queries += it }
                return routes[request.requestUrl!!.encodedPath.removePrefix("/3")]?.let { MockResponse().setBody(it) }
                    ?: MockResponse().setResponseCode(404)
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

    /** The folder's series row and one episode per file, numbered as the crawler parses the file names. */
    private fun addOnePiece(folderName: String, vararg files: String) = runBlocking {
        val title = MediaFilenameParser.parseMovie(folderName).cleanTitle
        db.mediaDao().insertSingleSeries(SeriesEntity(id = "op", title = title))
        files.forEachIndexed { i, file ->
            val parsed = MediaFilenameParser.parseEpisode(file)
            db.mediaDao().insertMediaItem(
                MediaItemEntity(
                    id = "op-$i", type = MediaType.EPISODE, title = file, parentFolderId = "op",
                    seasonNumber = parsed.season, episodeNumber = parsed.episode, seriesId = "op"
                )
            )
        }
    }

    @Test
    fun animeTaggedFolderGetsTheAnimeAndItsEpisodes() = runBlocking {
        addOnePiece(
            "One Piece (Anime)",
            "[SubsPlease] One Piece - 63 (1080p) [08CA0446].mkv",
            "one.piece.s24e64.1080p.web.h264-senpai.mkv"
        )

        engine().enrichExistingCatalog()

        val series = db.mediaDao().getSeriesById("op")!!
        assertEquals(37854, series.tmdbId)
        assertEquals("/anime.jpg", series.posterPath)
        assertEquals("One Piece", queries.first())
        val titles = db.mediaDao().getEpisodesForSeries("op").associate { it.episodeNumber to it.title }
        assertEquals("Episode 63", titles[63])
        assertEquals("Episode 64", titles[64])
    }

    @Test
    fun untaggedFolderStillGetsTheMostPopularExactName() = runBlocking {
        addOnePiece("One Piece", "one.piece.s01e01.mkv")

        engine().enrichExistingCatalog()

        assertEquals(111110, db.mediaDao().getSeriesById("op")!!.tmdbId)
    }
}
