package com.drivecinema.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regression: every startup sync used to replace catalog rows and blank TMDB posters until re-enrichment. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KeepEnrichmentOnSyncTest {

    private lateinit var db: DriveCinemaDatabase
    private lateinit var server: MockWebServer
    private lateinit var syncEngine: DriveLibrarySyncEngine

    private val folderChildren = mutableMapOf(
        "rootB" to listOf(folder("seriesB", "Series"), video("b1", "Bravo (2002).mp4"), video("d1", "Delta.mp4")),
        "seriesB" to listOf(folder("showB", "Show B")),
        "showB" to listOf(folder("seasonB1", "Season 1")),
        "seasonB1" to listOf(video("bep1", "Show.B.S01E01.mkv"))
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.encodedPath.endsWith("/changes/startPageToken")) {
                    return MockResponse().setResponseCode(200).setBody("""{"startPageToken":"tok_1"}""")
                }
                val folderId = Regex("""'([^']+)' in parents""").find(url.queryParameter("q").orEmpty())
                    ?.groupValues?.get(1) ?: return MockResponse().setResponseCode(400)
                val files = folderChildren[folderId] ?: emptyList()
                return MockResponse().setResponseCode(200).setBody("""{"files":[${files.joinToString(",")}]}""")
            }
        }
        server.start()
        val baseUrl = server.url("/drive/v3").toString()
        syncEngine = DriveLibrarySyncEngine(
            crawler = FolderQueueCrawler(baseUrl = baseUrl),
            mediaDao = db.mediaDao(),
            syncMetadataDao = db.syncMetadataDao(),
            baseUrl = baseUrl
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private suspend fun sync() = assertTrue(
        syncEngine.executeInitialCrawl(filmsFolderId = null, seriesFolderId = "seriesB", accessToken = "access", rootFolderId = "rootB").isSuccess
    )

    /** Writes what DriveLibrarySyncEngine.enrichExistingCatalog stores after a TMDB match. */
    private suspend fun simulateEnrichment() {
        val dao = db.mediaDao()
        dao.insertMediaItem(dao.getMediaItemById("b1")!!.copy(tmdbId = 101, posterPath = "/bravo.jpg", backdropPath = "/bravo_bg.jpg", overview = "Bravo plot", genres = "Drama", voteAverage = 7.5, voteCount = 900))
        dao.insertMediaItem(dao.getMediaItemById("d1")!!.copy(tmdbId = 104, year = 1999, posterPath = "/delta.jpg"))
        dao.insertSingleSeries(dao.getSeriesById("showB")!!.copy(tmdbId = 202, posterPath = "/showb.jpg", backdropPath = "/showb_bg.jpg", overview = "Show plot"))
        dao.insertMediaItem(dao.getMediaItemById("bep1")!!.copy(title = "Pilot", overview = "Ep plot", backdropPath = "/still.jpg", runtimeSeconds = 2_700))
    }

    @Test
    fun secondSyncKeepsEnrichmentOfUnchangedItems() = runBlocking {
        sync()
        simulateEnrichment()
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity(mediaItemId = "b1", positionMs = 30_000L, durationMs = 6_000_000L))

        sync()

        val dao = db.mediaDao()
        val bravo = dao.getMediaItemById("b1")!!
        assertEquals(101, bravo.tmdbId)
        assertEquals("/bravo.jpg", bravo.posterPath)
        assertEquals("/bravo_bg.jpg", bravo.backdropPath)
        assertEquals("Bravo plot", bravo.overview)
        assertEquals("Drama", bravo.genres)
        assertEquals(7.5, bravo.voteAverage!!, 0.0)
        assertEquals(900, bravo.voteCount)
        // A TMDB-filled year on a filename without one is not mistaken for a rename.
        val delta = dao.getMediaItemById("d1")!!
        assertEquals(104, delta.tmdbId)
        assertEquals(1999, delta.year)
        assertEquals("/delta.jpg", delta.posterPath)
        val show = dao.getSeriesById("showB")!!
        assertEquals(202, show.tmdbId)
        assertEquals("/showb.jpg", show.posterPath)
        val episode = dao.getMediaItemById("bep1")!!
        assertEquals("Pilot", episode.title)
        assertEquals("/still.jpg", episode.backdropPath)
        assertEquals(2_700, episode.runtimeSeconds)
        assertTrue(dao.getUnenrichedMovies().isEmpty())
        assertTrue(dao.getUnenrichedSeries().isEmpty())
        assertEquals(30_000L, db.playbackProgressDao().getProgress("b1")!!.positionMs)
    }

    @Test
    fun renamedMovieLosesOldMatchAndIsRematched() = runBlocking {
        sync()
        simulateEnrichment()

        folderChildren["rootB"] = listOf(folder("seriesB", "Series"), video("b1", "Charlie (2005).mp4"), video("d1", "Delta.mp4"))
        sync()

        val dao = db.mediaDao()
        val renamed = dao.getMediaItemById("b1")!!
        assertEquals("Charlie", renamed.title)
        assertEquals(2005, renamed.year)
        assertNull(renamed.tmdbId)
        assertNull(renamed.posterPath)
        assertEquals(listOf("b1"), dao.getUnenrichedMovies().map { it.id })
        assertEquals("/delta.jpg", dao.getMediaItemById("d1")!!.posterPath)
    }

    @Test
    fun newEpisodeRematchesSeriesButKeepsItsArtwork() = runBlocking {
        sync()
        simulateEnrichment()

        folderChildren["seasonB1"] = listOf(video("bep1", "Show.B.S01E01.mkv"), video("bep2", "Show.B.S01E02.mkv"))
        sync()

        val dao = db.mediaDao()
        val show = dao.getSeriesById("showB")!!
        assertNull(show.tmdbId)
        assertEquals("/showb.jpg", show.posterPath)
        assertEquals(listOf("showB"), dao.getUnenrichedSeries().map { it.id })
        assertEquals("Pilot", dao.getMediaItemById("bep1")!!.title)
    }

    @Test
    fun renamedSeriesFolderIsRematched() = runBlocking {
        sync()
        simulateEnrichment()

        folderChildren["seriesB"] = listOf(folder("showB", "Show Bee"))
        sync()

        val show = db.mediaDao().getSeriesById("showB")!!
        assertEquals("Show Bee", show.title)
        assertNull(show.tmdbId)
        assertNull(show.posterPath)
    }

    private companion object {
        fun folder(id: String, name: String) =
            """{"id":"$id","name":"$name","mimeType":"application/vnd.google-apps.folder"}"""

        fun video(id: String, name: String) =
            """{"id":"$id","name":"$name","mimeType":"video/x-matroska"}"""
    }
}
