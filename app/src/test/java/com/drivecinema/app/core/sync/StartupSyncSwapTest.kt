package com.drivecinema.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task B.3: the startup sync makes exactly the configured library visible, without ever losing data. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StartupSyncSwapTest {

    private lateinit var db: DriveCinemaDatabase
    private lateinit var server: MockWebServer
    private lateinit var syncEngine: DriveLibrarySyncEngine

    // Mutable so a test can simulate Drive changing between launches.
    private val folderChildren = mutableMapOf(
        "rootB" to listOf(folder("seriesB", "Series"), video("b1", "Bravo (2002).mp4")),
        "seriesB" to listOf(folder("showB", "Show B")),
        "showB" to listOf(folder("seasonB1", "Season 1")),
        "seasonB1" to listOf(video("bep1", "Show.B.S01E01.mkv"))
    )
    private val httpErrors = mutableMapOf<String, Int>()

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
                httpErrors[folderId]?.let { return MockResponse().setResponseCode(it) }
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

    private suspend fun startupSyncOfB() =
        syncEngine.executeInitialCrawl(filmsFolderId = null, seriesFolderId = "seriesB", accessToken = "access", rootFolderId = "rootB")

    private suspend fun visibleMovieIds() = db.mediaDao().getAllMoviesFlow().first().map { it.id }
    private suspend fun visibleSeriesIds() = db.mediaDao().getAllSeriesFlow().first().map { it.id }

    /** State an interrupted switch could leave behind: library A and B both visible, with history on A. */
    private suspend fun seedMixedCatalog() {
        val dao = db.mediaDao()
        dao.insertMediaItems(
            listOf(
                MediaItemEntity(id = "a1", type = MediaType.MOVIE, title = "Alpha", parentFolderId = "filmsA"),
                MediaItemEntity(id = "aep1", type = MediaType.EPISODE, title = "Pilot", parentFolderId = "seasonA1", seriesId = "showA", seasonNumber = 1, episodeNumber = 1),
                MediaItemEntity(id = "b1", type = MediaType.MOVIE, title = "Bravo", parentFolderId = "rootB")
            )
        )
        dao.insertSeries(listOf(SeriesEntity(id = "showA", title = "Show A")))
        db.playbackProgressDao().saveProgress(
            PlaybackProgressEntity(mediaItemId = "a1", positionMs = 90_000L, durationMs = 5_000_000L)
        )
    }

    @Test
    fun startupSyncRecoversMixedLibraryAndKeepsHistory() = runBlocking {
        seedMixedCatalog()
        assertEquals(listOf("a1", "b1"), visibleMovieIds())

        val result = startupSyncOfB()

        assertTrue(result.isSuccess)
        assertEquals(listOf("b1"), visibleMovieIds())
        assertEquals(listOf("showB"), visibleSeriesIds())
        assertEquals(listOf("bep1"), db.mediaDao().getEpisodesForSeries("showB").map { it.id })
        // Other library is soft-deleted, not removed, and its watch history is untouched.
        assertTrue(db.mediaDao().getMediaItemById("a1")!!.isDeleted)
        assertTrue(db.mediaDao().getMediaItemById("aep1")!!.isDeleted)
        assertNotNull(db.mediaDao().getSeriesById("showA"))
        assertEquals(90_000L, db.playbackProgressDao().getProgress("a1")!!.positionMs)
    }

    @Test
    fun failedStartupCrawlLeavesCatalogUntouched() = runBlocking {
        seedMixedCatalog()
        httpErrors["seasonB1"] = 500

        val result = startupSyncOfB()

        assertTrue(result.isFailure)
        assertEquals(listOf("a1", "b1"), visibleMovieIds())
        assertFalse(db.mediaDao().getMediaItemById("a1")!!.isDeleted)
        assertNotNull(db.playbackProgressDao().getProgress("a1"))
    }

    @Test
    fun emptyStartupCrawlNeverHidesExistingCatalog() = runBlocking {
        seedMixedCatalog()
        folderChildren["rootB"] = listOf(folder("seriesB", "Series"))
        folderChildren["seriesB"] = emptyList()

        val result = startupSyncOfB()

        assertTrue(result.isSuccess)
        assertEquals(listOf("a1", "b1"), visibleMovieIds())
    }

    @Test
    fun fileRemovedFromDriveIsHiddenAndComesBackWhenRestored() = runBlocking {
        assertTrue(startupSyncOfB().isSuccess)
        db.playbackProgressDao().saveProgress(
            PlaybackProgressEntity(mediaItemId = "b1", positionMs = 30_000L, durationMs = 6_000_000L)
        )

        // Next launch: b1 was moved out of the library folder.
        folderChildren["rootB"] = listOf(folder("seriesB", "Series"))
        assertTrue(startupSyncOfB().isSuccess)
        assertTrue(visibleMovieIds().isEmpty())
        assertTrue(db.mediaDao().getContinueWatchingFlow().first().isEmpty())

        // Following launch: b1 is back, with its resume position intact.
        folderChildren["rootB"] = listOf(folder("seriesB", "Series"), video("b1", "Bravo (2002).mp4"))
        assertTrue(startupSyncOfB().isSuccess)
        assertEquals(listOf("b1"), visibleMovieIds())
        assertEquals(30_000L, db.mediaDao().getContinueWatchingFlow().first().single().positionMs)
    }

    private companion object {
        fun folder(id: String, name: String) =
            """{"id":"$id","name":"$name","mimeType":"application/vnd.google-apps.folder"}"""

        fun video(id: String, name: String) =
            """{"id":"$id","name":"$name","mimeType":"video/x-matroska"}"""
    }
}
