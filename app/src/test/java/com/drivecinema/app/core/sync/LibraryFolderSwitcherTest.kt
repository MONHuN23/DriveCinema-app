package com.drivecinema.app.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.security.AuthCredentialStore
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LibraryFolderSwitcherTest {

    private lateinit var context: Context
    private lateinit var db: DriveCinemaDatabase
    private lateinit var server: MockWebServer
    private lateinit var credentialStore: AuthCredentialStore
    private lateinit var syncEngine: DriveLibrarySyncEngine
    private lateinit var switcher: LibraryFolderSwitcher

    // Folders whose listing blocks until released, to simulate slow crawls.
    private val gatedFolders = mutableMapOf<String, Pair<CountDownLatch, CountDownLatch>>()

    private val personal = DriveFolder(id = "rootA", name = "My Movies")
    private val shared = DriveFolder(id = "rootB", name = "Streaming")

    private val folderChildren = mapOf(
        // Personal library: Films/Alpha
        "rootA" to listOf(folder("filmsA", "Films")),
        "filmsA" to listOf(video("a1", "Alpha (2001).mkv")),
        // Shared "Streaming" library: Series/Show B/Season 1/S01E01 + a direct video
        "rootB" to listOf(folder("seriesB", "Series"), video("b1", "Bravo (2002).mp4")),
        "seriesB" to listOf(folder("showB", "Show B")),
        "showB" to listOf(folder("seasonB1", "Season 1")),
        "seasonB1" to listOf(video("bep1", "Show.B.S01E01.mkv")),
        // Valid structure but no videos yet
        "rootE" to listOf(folder("filmsE", "Films")),
        "filmsE" to emptyList(),
        // Not a library
        "rootI" to listOf("""{"id":"doc1","name":"notes.txt","mimeType":"text/plain"}"""),
        // Valid root whose Films crawl fails mid-way
        "rootF" to listOf(folder("filmsF", "Films")),
        // Slow library used for cancellation tests
        "rootG" to listOf(folder("filmsG", "Films")),
        "filmsG" to listOf(video("g1", "Gamma (2003).mkv"))
    )
    private val httpErrors = mapOf("rootX" to 403, "rootGone" to 404, "filmsF" to 500)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
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
                val q = url.queryParameter("q") ?: return MockResponse().setResponseCode(400)
                val folderId = Regex("""'([^']+)' in parents""").find(q)?.groupValues?.get(1)
                    ?: return MockResponse().setResponseCode(400)
                gatedFolders[folderId]?.let { (requested, release) ->
                    requested.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
                httpErrors[folderId]?.let { code ->
                    return MockResponse().setResponseCode(code).setBody("""{"error":{"code":$code}}""")
                }
                val files = folderChildren[folderId] ?: emptyList()
                return MockResponse().setResponseCode(200).setBody("""{"files":[${files.joinToString(",")}]}""")
            }
        }
        server.start()
        val baseUrl = server.url("/drive/v3").toString()

        credentialStore = AuthCredentialStore(context)
        credentialStore.clear()
        credentialStore.saveCredentials(
            AuthCredentials(
                refreshToken = "refresh_abc",
                rootFolderId = personal.id,
                userDisplayName = "Jane",
                userEmail = "jane@example.com"
            )
        )

        syncEngine = DriveLibrarySyncEngine(
            crawler = FolderQueueCrawler(baseUrl = baseUrl),
            mediaDao = db.mediaDao(),
            syncMetadataDao = db.syncMetadataDao(),
            baseUrl = baseUrl
        )
        switcher = LibraryFolderSwitcher(GoogleDriveGateway(baseUrl = baseUrl), syncEngine, credentialStore)
    }

    @After
    fun tearDown() {
        gatedFolders.values.forEach { it.second.countDown() }
        server.shutdown()
        db.close()
        credentialStore.clear()
    }

    /** Existing user state: personal library indexed at startup, with watch history on Alpha. */
    private suspend fun seedPersonalLibraryWithHistory() {
        syncEngine.executeInitialCrawl("filmsA", null, ACCESS_TOKEN, personal.id).getOrThrow()
        db.playbackProgressDao().saveProgress(
            PlaybackProgressEntity(mediaItemId = "a1", positionMs = 120_000L, durationMs = 6_000_000L)
        )
    }

    private suspend fun visibleMovieIds() = db.mediaDao().getAllMoviesFlow().first().map { it.id }
    private suspend fun visibleSeriesIds() = db.mediaDao().getAllSeriesFlow().first().map { it.id }

    @Test
    fun switchPersistsFolderAndShowsOnlyTheNewLibrary() = runBlocking {
        seedPersonalLibraryWithHistory()
        assertEquals(listOf("a1"), visibleMovieIds())

        val result = switcher.switchTo(shared, ACCESS_TOKEN)

        assertEquals(FolderSwitchResult.Switched(folderId = "rootB", itemCount = 2), result)
        assertEquals(listOf("b1"), visibleMovieIds())
        assertEquals(listOf("showB"), visibleSeriesIds())
        assertEquals(listOf("bep1"), db.mediaDao().getEpisodesForSeries("showB").map { it.id })
        assertEquals("rootB", AuthCredentialStore(context).getCredentials()?.rootFolderId)
        assertEquals("tok_1", db.syncMetadataDao().getMetadata(DriveLibrarySyncEngine.SYNC_TOKEN_KEY)?.syncToken)
    }

    @Test
    fun switchKeepsTheExistingSessionWithoutReauthentication() = runBlocking {
        seedPersonalLibraryWithHistory()

        switcher.switchTo(shared, ACCESS_TOKEN)

        val creds = credentialStore.getCredentials()
        assertNotNull(creds)
        assertEquals("refresh_abc", creds!!.refreshToken)
        assertEquals("Jane", creds.userDisplayName)
        assertEquals("jane@example.com", creds.userEmail)
        // Every Drive request reused the existing access token; no token endpoint was hit.
        repeat(server.requestCount) {
            val request = server.takeRequest(1, TimeUnit.SECONDS) ?: return@repeat
            assertEquals("Bearer $ACCESS_TOKEN", request.getHeader("Authorization"))
            assertFalse(request.path.orEmpty().contains("token?"))
        }
    }

    @Test
    fun switchPreservesWatchHistoryAndSoftDeletesPreviousLibrary() = runBlocking {
        seedPersonalLibraryWithHistory()

        switcher.switchTo(shared, ACCESS_TOKEN)

        val progress = db.playbackProgressDao().getProgress("a1")
        assertNotNull("Watch history must survive a folder switch", progress)
        assertEquals(120_000L, progress!!.positionMs)
        val oldMovie = db.mediaDao().getMediaItemById("a1")
        assertNotNull("Previous library rows are soft-deleted, never removed", oldMovie)
        assertTrue(oldMovie!!.isDeleted)
        assertTrue(db.mediaDao().getContinueWatchingFlow().first().isEmpty())
    }

    @Test
    fun switchingBackRestoresPreviousLibraryAndContinueWatching() = runBlocking {
        seedPersonalLibraryWithHistory()

        switcher.switchTo(shared, ACCESS_TOKEN)
        val result = switcher.switchTo(personal, ACCESS_TOKEN)

        assertEquals(FolderSwitchResult.Switched(folderId = "rootA", itemCount = 1), result)
        assertEquals(listOf("a1"), visibleMovieIds())
        assertTrue("Series of the other library must be hidden", visibleSeriesIds().isEmpty())
        val continueWatching = db.mediaDao().getContinueWatchingFlow().first()
        assertEquals(listOf("a1"), continueWatching.map { it.id })
        assertEquals(120_000L, continueWatching.single().positionMs)
        assertEquals("rootA", credentialStore.getCredentials()?.rootFolderId)
    }

    @Test
    fun permissionDeniedFolderIsRejectedAndNothingChanges() = runBlocking {
        seedPersonalLibraryWithHistory()

        val result = switcher.switchTo(DriveFolder(id = "rootX", name = "Private"), ACCESS_TOKEN)

        assertTrue(result is FolderSwitchResult.Rejected)
        assertTrue((result as FolderSwitchResult.Rejected).message.contains("permission"))
        assertEquals("rootA", credentialStore.getCredentials()?.rootFolderId)
        assertEquals(listOf("a1"), visibleMovieIds())
    }

    @Test
    fun missingFolderIsRejectedAndNothingChanges() = runBlocking {
        seedPersonalLibraryWithHistory()

        val result = switcher.switchTo(DriveFolder(id = "rootGone", name = "Old"), ACCESS_TOKEN)

        assertTrue((result as FolderSwitchResult.Rejected).message.contains("could not be found"))
        assertEquals("rootA", credentialStore.getCredentials()?.rootFolderId)
        assertEquals(listOf("a1"), visibleMovieIds())
    }

    @Test
    fun failedCrawlDoesNotHideTheCurrentLibrary() = runBlocking {
        seedPersonalLibraryWithHistory()

        val result = switcher.switchTo(DriveFolder(id = "rootF", name = "Flaky"), ACCESS_TOKEN)

        assertTrue(result is FolderSwitchResult.Rejected)
        assertEquals("rootA", credentialStore.getCredentials()?.rootFolderId)
        assertEquals(listOf("a1"), visibleMovieIds())
        assertFalse(db.mediaDao().getMediaItemById("a1")!!.isDeleted)
    }

    @Test
    fun folderWithoutLibraryStructureIsRejected() = runBlocking {
        seedPersonalLibraryWithHistory()

        val result = switcher.switchTo(DriveFolder(id = "rootI", name = "Documents"), ACCESS_TOKEN)

        assertTrue((result as FolderSwitchResult.Rejected).message.contains("can't be used as a library"))
        assertEquals("rootA", credentialStore.getCredentials()?.rootFolderId)
        assertEquals(listOf("a1"), visibleMovieIds())
    }

    @Test
    fun emptyLibraryFolderSwitchesWithZeroItems() = runBlocking {
        seedPersonalLibraryWithHistory()

        val result = switcher.switchTo(DriveFolder(id = "rootE", name = "Empty"), ACCESS_TOKEN)

        assertEquals(FolderSwitchResult.Switched(folderId = "rootE", itemCount = 0), result)
        assertTrue(visibleMovieIds().isEmpty())
        assertNotNull(db.playbackProgressDao().getProgress("a1"))
    }

    @Test
    fun signedOutSessionIsRejected() = runBlocking {
        credentialStore.clear()

        val result = switcher.switchTo(shared, ACCESS_TOKEN)

        assertTrue(result is FolderSwitchResult.Rejected)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun cancelledStartupCrawlCannotOverwriteANewerSwitch() = runBlocking {
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        gatedFolders["filmsA"] = requested to release

        // Startup sync of the personal library is still crawling when the user switches.
        val startupSync = launch(Dispatchers.IO) {
            syncEngine.executeInitialCrawl("filmsA", null, ACCESS_TOKEN, personal.id)
        }
        assertTrue(requested.await(5, TimeUnit.SECONDS))
        startupSync.cancel()

        val result = switcher.switchTo(shared, ACCESS_TOKEN)
        release.countDown()
        startupSync.join()

        assertTrue(result is FolderSwitchResult.Switched)
        assertEquals(listOf("b1"), visibleMovieIds())
        assertEquals(null, db.mediaDao().getMediaItemById("a1"))
    }

    @Test
    fun rapidSwitchingKeepsOnlyTheLatestSelection() = runBlocking {
        seedPersonalLibraryWithHistory()
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        gatedFolders["filmsG"] = requested to release

        val firstSwitch = async(Dispatchers.IO) {
            switcher.switchTo(DriveFolder(id = "rootG", name = "Slow"), ACCESS_TOKEN)
        }
        assertTrue(requested.await(5, TimeUnit.SECONDS))
        firstSwitch.cancel()

        val secondResult = switcher.switchTo(shared, ACCESS_TOKEN)
        release.countDown()
        firstSwitch.join()

        assertTrue(secondResult is FolderSwitchResult.Switched)
        assertEquals("rootB", credentialStore.getCredentials()?.rootFolderId)
        assertEquals(listOf("b1"), visibleMovieIds())
        assertEquals(null, db.mediaDao().getMediaItemById("g1"))
        assertNotNull(db.playbackProgressDao().getProgress("a1"))
    }

    @Test
    fun describeDriveErrorMapsHttpFailuresToFriendlyMessages() {
        fun describe(message: String) =
            LibraryFolderSwitcher.describeDriveError("Streaming", IOException(message))

        assertTrue(describe("Google Drive API request failed (403): {}").contains("permission"))
        assertTrue(describe("Google Drive API request failed (404): {}").contains("could not be found"))
        assertTrue(describe("Failed to query folder x: code 401").contains("session expired"))
        assertTrue(describe("Failed to get startPageToken: 503").contains("(503)"))
        assertTrue(describe("timeout").contains("connection"))
    }

    private companion object {
        const val ACCESS_TOKEN = "access_xyz"

        fun folder(id: String, name: String) =
            """{"id":"$id","name":"$name","mimeType":"application/vnd.google-apps.folder"}"""

        fun video(id: String, name: String) =
            """{"id":"$id","name":"$name","mimeType":"video/x-matroska"}"""
    }
}
