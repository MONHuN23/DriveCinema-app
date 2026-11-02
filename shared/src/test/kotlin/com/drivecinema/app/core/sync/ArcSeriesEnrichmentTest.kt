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

/**
 * The enrichment pass on the user's two Bleach layouts: "Bleach: Thousand-Year Blood War (2022)/Season 01" got
 * Bleach's first episode ("The Day I Became a Shinigami"), and "Bleach/Season 17" got no episode titles.
 */
class ArcSeriesEnrichmentTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private val tmdb = MockWebServer()

    private val routes = mapOf(
        "/search/tv" to """{"results": [{"id": 30984, "name": "Bleach", "original_name": "BLEACH",
            "first_air_date": "2004-10-05", "poster_path": "/bleach.jpg", "overview": "Ichigo..."}]}""",
        "/tv/30984" to """{"seasons": [
            {"season_number": 0, "name": "Specials"},
            {"season_number": 1, "name": "Bleach", "poster_path": "/s1.jpg"},
            {"season_number": 2, "name": "Thousand-Year Blood War", "poster_path": "/tybw.jpg", "overview": "The final arc."}]}""",
        "/tv/30984/season/1" to """{"episodes": [{"episode_number": 1, "name": "The Day I Became a Shinigami"}]}""",
        "/tv/30984/season/2" to """{"episodes": [
            {"episode_number": 1, "name": "The Blood Warfare", "air_date": "2022-10-11"},
            {"episode_number": 2, "name": "Foundation Stones", "air_date": "2022-10-18"}]}""",
        "/tv/30984/episode_groups" to """{"results": [{"id": "g1", "name": "TVDB Order"}]}""",
        "/tv/episode_group/g1" to """{"groups": [{"order": 17, "episodes": [
            {"season_number": 2, "episode_number": 1, "order": 0, "name": "The Blood Warfare"}]}]}"""
    )

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-arc").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "arc.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        tmdb.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.requestUrl!!.encodedPath.removePrefix("/3")]?.let { MockResponse().setBody(it) }
                    ?: MockResponse().setResponseCode(404)
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

    private fun addSeries(id: String, title: String, year: Int?, season: Int) = runBlocking {
        db.mediaDao().insertSingleSeries(SeriesEntity(id = id, title = title, year = year))
        db.mediaDao().insertMediaItem(
            MediaItemEntity(
                id = "$id-e1", type = MediaType.EPISODE, title = "s${season}e01", parentFolderId = "$id-season",
                seasonNumber = season, episodeNumber = 1, seriesId = id
            )
        )
    }

    @Test
    fun arcFolderGetsTheArcsFirstEpisodeAndPoster() = runBlocking {
        addSeries("tybw", "Bleach: Thousand-Year Blood War", 2022, season = 1)

        engine().enrichExistingCatalog()

        assertEquals("The Blood Warfare", db.mediaDao().getEpisodesForSeries("tybw").single().title)
        val series = db.mediaDao().getSeriesById("tybw")!!
        assertEquals(30984, series.tmdbId)
        assertEquals("/tybw.jpg", series.posterPath)
        assertEquals("The final arc.", series.overview)
        assertEquals(2022, series.year)
    }

    @Test
    fun tvdbSeasonFolderGetsItsEpisodeTitles() = runBlocking {
        addSeries("bleach", "Bleach", null, season = 17)

        engine().enrichExistingCatalog()

        assertEquals("The Blood Warfare", db.mediaDao().getEpisodesForSeries("bleach").single().title)
        assertEquals("/bleach.jpg", db.mediaDao().getSeriesById("bleach")!!.posterPath)
    }
}
