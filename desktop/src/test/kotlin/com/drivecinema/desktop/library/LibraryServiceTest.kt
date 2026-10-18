package com.drivecinema.desktop.library

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.model.DriveUser
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.sync.DriveLibrarySyncEngine
import com.drivecinema.app.core.sync.FolderQueueCrawler
import com.drivecinema.desktop.auth.DesktopOAuthConfig
import com.drivecinema.desktop.auth.DesktopSession
import com.drivecinema.desktop.auth.DesktopTokenProvider
import com.drivecinema.desktop.auth.OAuthTokenClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LibraryServiceTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private lateinit var drive: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var settings: DesktopSettings

    /** Folder id -> children (JSON objects); "Streaming" search results; failing folders. */
    private val children = mutableMapOf(
        "stream" to listOf(folder("films", "Films")),
        "films" to listOf(video("m1", "Alpha (2001).mkv"), video("m2", "Bravo (2002).mp4")),
        "other" to listOf(folder("films2", "Films")),
        "films2" to listOf(video("m3", "Charlie (2003).mkv")),
        "notLibrary" to listOf("""{"id":"doc","name":"notes.txt","mimeType":"text/plain"}""")
    )
    private var streamingSearch = listOf(folder("stream", "Streaming"))
    private val rootFolders = listOf(folder("other", "My Movies"), folder("notLibrary", "Documents"))
    private val failing = mutableSetOf<String>()

    private val user = DriveUser("Ana Example", "ana@example.com")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-lib").toFile()
        db = DesktopDatabase.open(dir)
        settings = DesktopSettings(File(dir, "settings.json"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        drive = MockWebServer()
        drive.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                if (url.encodedPath.endsWith("/changes/startPageToken")) return ok("""{"startPageToken":"tok"}""")
                val q = url.queryParameter("q") ?: return MockResponse().setResponseCode(400)
                return when {
                    "name = 'Streaming'" in q -> files(streamingSearch)
                    "'root' in parents" in q -> files(rootFolders)
                    else -> {
                        val id = Regex("""'([^']+)' in parents""").find(q)?.groupValues?.get(1)
                        if (id in failing) MockResponse().setResponseCode(500) else files(children[id] ?: emptyList())
                    }
                }
            }
        }
        drive.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        drive.shutdown()
        db.close()
        dir.deleteRecursively()
    }

    private fun ok(body: String) = MockResponse().setBody(body)
    private fun files(list: List<String>) = ok("""{"files":[${list.joinToString(",")}]}""")
    private fun folder(id: String, name: String) =
        """{"id":"$id","name":"$name","mimeType":"application/vnd.google-apps.folder"}"""
    private fun video(id: String, name: String) = """{"id":"$id","name":"$name","mimeType":"video/x-matroska"}"""

    private fun service(): LibraryService {
        val baseUrl = drive.url("/drive/v3").toString().trimEnd('/')
        val engine = DriveLibrarySyncEngine(
            crawler = FolderQueueCrawler(baseUrl = baseUrl),
            mediaDao = db.mediaDao(),
            syncMetadataDao = db.syncMetadataDao(),
            baseUrl = baseUrl
        )
        return LibraryService(scope, GoogleDriveGateway(baseUrl = baseUrl), engine, settings)
    }

    private fun session() = DesktopSession(
        user,
        DesktopTokenProvider(OAuthTokenClient(DesktopOAuthConfig("id", "secret")), "1//r", "ya29.test", Long.MAX_VALUE)
    )

    private suspend fun LibraryService.await(predicate: (LibraryState) -> Boolean): LibraryState =
        withTimeout(10_000) { state.first(predicate) }

    private fun readyAndIdle(s: LibraryState) = s is LibraryState.Ready && !s.isSyncing

    @Test
    fun findsTheStreamingFolderAndIndexesIt() = runBlocking {
        val service = service()
        service.start(session())

        val ready = service.await(::readyAndIdle) as LibraryState.Ready
        assertEquals("Streaming", ready.folderName)
        assertEquals(listOf("Alpha", "Bravo"), db.mediaDao().getAllMovies().map { it.title })
        assertEquals(LibraryFolderSetting("stream", "Streaming"), settings.libraryFolder("ana@example.com"))
    }

    @Test
    fun withoutAStreamingFolderTheUserPicksOne() = runBlocking {
        streamingSearch = emptyList()
        val service = service()
        service.start(session())

        val choosing = service.await { it is LibraryState.ChoosingFolder && !it.isLoading } as LibraryState.ChoosingFolder
        assertEquals(listOf("Documents", "My Movies"), choosing.sections.mine.map { it.name })
        assertEquals(false, choosing.canCancel)

        service.chooseFolder(DriveFolder("other", "My Movies"))
        service.await(::readyAndIdle)
        assertEquals(listOf("Charlie"), db.mediaDao().getAllMovies().map { it.title })
        assertEquals("other", settings.libraryFolder("ana@example.com")!!.folderId)
    }

    @Test
    fun aFolderThatIsNotALibraryIsRejectedAndNothingChanges() = runBlocking {
        streamingSearch = emptyList()
        val service = service()
        service.start(session())
        service.await { it is LibraryState.ChoosingFolder && !it.isLoading }

        service.chooseFolder(DriveFolder("notLibrary", "Documents"))
        val rejected = service.await { it is LibraryState.ChoosingFolder && it.switchingTo == null && it.errorMessage != null }
        assertTrue((rejected as LibraryState.ChoosingFolder).errorMessage!!.contains("can't be used as a library"))
        assertNull(settings.libraryFolder("ana@example.com"))
        assertTrue(db.mediaDao().getAllMovies().isEmpty())
    }

    @Test
    fun savedFolderIsSyncedAtStartupAndSwitchingCanBeCancelled() = runBlocking {
        settings.setLibraryFolder("ana@example.com", LibraryFolderSetting("other", "My Movies"))
        val service = service()
        service.start(session())
        service.await(::readyAndIdle)
        assertEquals(listOf("Charlie"), db.mediaDao().getAllMovies().map { it.title })

        service.openFolderPicker()
        val picker = service.await { it is LibraryState.ChoosingFolder && !it.isLoading } as LibraryState.ChoosingFolder
        assertTrue(picker.canCancel)
        service.closeFolderPicker()
        assertEquals(LibraryState.Ready("My Movies"), service.state.value)
    }

    @Test
    fun offlineStartupKeepsTheLibraryAndWatchHistory() = runBlocking {
        val service = service()
        service.start(session())
        service.await(::readyAndIdle)
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("m1", 60_000, 7_200_000))

        failing += "stream"
        val restarted = service()
        restarted.start(session())
        val ready = restarted.await { readyAndIdle(it) && (it as LibraryState.Ready).message != null } as LibraryState.Ready
        assertTrue(ready.message!!.contains("Couldn't reach Google Drive"))
        assertEquals(listOf("Alpha", "Bravo"), db.mediaDao().getAllMovies().map { it.title })
        assertNotNull(db.playbackProgressDao().getProgress("m1"))
    }

    @Test
    fun signOutStopsButKeepsTheCatalog() = runBlocking {
        val service = service()
        service.start(session())
        service.await(::readyAndIdle)

        service.stop()
        assertEquals(LibraryState.Idle, service.state.value)
        assertEquals(2, db.mediaDao().getAllMovies().size)
    }

    @Test
    fun settingsAreKeptPerAccount() {
        settings.setLibraryFolder("Ana@Example.com", LibraryFolderSetting("a", "A"))
        settings.setLibraryFolder("bob@example.com", LibraryFolderSetting("b", "B"))
        assertEquals("a", DesktopSettings(File(dir, "settings.json")).libraryFolder("ana@example.com")!!.folderId)
        assertEquals("b", settings.libraryFolder("bob@example.com")!!.folderId)
        assertNull(settings.libraryFolder("carol@example.com"))
    }
}
