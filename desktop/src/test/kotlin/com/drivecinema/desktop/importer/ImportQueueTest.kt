package com.drivecinema.desktop.importer

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.ImportQueueEntity
import com.drivecinema.app.core.database.entity.ImportState
import com.drivecinema.desktop.auth.DesktopOAuthConfig
import com.drivecinema.desktop.auth.DesktopTokenProvider
import com.drivecinema.desktop.auth.GoogleOAuth
import com.drivecinema.desktop.auth.OAuthTokenClient
import com.drivecinema.desktop.library.DesktopDatabase
import com.drivecinema.desktop.library.DesktopSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Task I.1, queue: detect → queue → upload once into the right folder, survive restarts, stop on quota, never touch local files. */
class ImportQueueTest {

    private lateinit var drive: FakeDrive
    private lateinit var dir: File
    private lateinit var movies: Path
    private lateinit var series: Path
    private lateinit var db: DriveCinemaDatabase
    private lateinit var settings: DesktopSettings
    private lateinit var scope: CoroutineScope
    private var now = 1_000_000L
    private val chunk = ResumableUploader.CHUNK_GRANULARITY

    @Before
    fun setUp() {
        drive = FakeDrive()
        drive.folder("Streaming", id = "lib")
        drive.folder("Films", "lib", id = "films")
        drive.folder("Series", "lib", id = "series")
        dir = Files.createTempDirectory("drivecinema-importq").toFile()
        movies = Files.createDirectories(dir.toPath().resolve("Movies"))
        series = Files.createDirectories(dir.toPath().resolve("TV"))
        db = DesktopDatabase.open(File(dir, "db").apply { mkdirs() })
        settings = DesktopSettings(File(dir, "settings.json"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        drive.close()
        dir.deleteRecursively()
    }

    private fun tokens(scope: String = "${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_SCOPE}") =
        DesktopTokenProvider(OAuthTokenClient(DesktopOAuthConfig("c", "s")), "1//r", "ya29.test", Long.MAX_VALUE, grantedScope = scope)

    private fun queue(client: OkHttpClient = OkHttpClient(), maxAttempts: Int = 6) = ImportQueue(
        scope, db, settings,
        driveFor = { drive.client(client) },
        uploaderFor = { ResumableUploader(it, chunkSize = chunk, maxAttempts = maxAttempts, sleep = {}) },
        clock = { now },
        quietPeriodMs = 10_000,
        runLoop = false
    )

    private fun attached(queue: ImportQueue, provider: DesktopTokenProvider = tokens()): ImportQueue = runBlocking {
        queue.attach(provider, "lib", "Streaming")
        withTimeout(5_000) { while (queue.status.value.hasWriteAccess == null) delay(10) }
        queue
    }

    private fun write(folder: Path, relative: String, bytes: ByteArray = Random(relative.hashCode()).nextBytes(chunk + 500)): Path {
        val file = folder.resolve(relative.replace('/', File.separatorChar))
        Files.createDirectories(file.parent)
        Files.write(file, bytes)
        return file
    }

    /** Two detection passes, the quiet period apart: the file counts as finished. */
    private suspend fun detect(queue: ImportQueue) {
        queue.scanOnce()
        now += 20_000
        queue.scanOnce()
    }

    private suspend fun entries() = db.importQueueDao().observeAll().first()

    private suspend fun entry(name: String): ImportQueueEntity = entries().single { it.fileName == name }

    @Test
    fun videosAlreadyInTheFolderAreSkippedAndNewOnesUploadOnce() = runBlocking {
        write(movies, "Old Film (1999).mkv")
        val queue = attached(queue())
        assertNull(queue.setFolder(ImportFolderKind.MOVIES, movies))
        assertEquals(ImportState.SKIPPED, entry("Old Film (1999).mkv").state)

        val dune = write(movies, "Dune (2021).mkv")
        val original = Files.readAllBytes(dune)
        detect(queue)
        detect(queue) // duplicate events: still one entry
        assertEquals(ImportState.QUEUED, entry("Dune (2021).mkv").state)
        assertEquals(2, entries().size)

        assertTrue(queue.uploadNext())
        assertEquals(ImportState.DONE, entry("Dune (2021).mkv").state)
        assertEquals(listOf("Dune (2021).mkv"), drive.childrenOf("films").map { it.name })
        assertArrayEquals(original, drive.childrenOf("films").single().bytes)
        assertArrayEquals("the local file is unchanged", original, Files.readAllBytes(dune))
        assertTrue("nothing else queued", !queue.uploadNext())

        queue.uploadAllSkipped().join()
        assertTrue(queue.uploadNext())
        assertEquals(2, drive.childrenOf("films").size)
    }

    @Test
    fun episodesGoIntoCreatedSeasonFoldersAndUnclearFilesWaitForReview() = runBlocking {
        val queue = attached(queue())
        queue.setFolder(ImportFolderKind.SERIES, series)
        write(series, "Ted (2024)/Season 1/Ted.S01E01.mkv")
        write(series, "Ted (2024)/Extras/Making of.mkv")
        detect(queue)
        while (queue.uploadNext()) Unit

        val show = drive.childrenOf("series").single()
        assertEquals("Ted (2024)", show.name)
        val season = drive.childrenOf(show.id).single()
        assertEquals("Season 01", season.name)
        assertEquals(listOf("Ted.S01E01.mkv"), drive.childrenOf(season.id).map { it.name })
        val review = entry("Making of.mkv")
        assertEquals(ImportState.NEEDS_REVIEW, review.state)
        assertNotNull(review.message)
    }

    @Test
    fun anUploadInterruptedByAnAppRestartResumesWithoutASecondCopy() = runBlocking {
        val calls = AtomicInteger()
        val droppingSecondChunk = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (chain.request().method == "PUT" && calls.incrementAndGet() == 2) throw IOException("connection lost")
            chain.proceed(chain.request())
        }).build()
        val first = attached(queue(droppingSecondChunk, maxAttempts = 1))
        first.setFolder(ImportFolderKind.MOVIES, movies)
        val bytes = Random(3).nextBytes(chunk * 2 + 10)
        write(movies, "Heat (1995).mkv", bytes)
        detect(first)
        first.uploadNext()
        val stopped = entry("Heat (1995).mkv")
        assertNotNull("session kept for resuming", stopped.sessionUri)
        // As if the app had been killed during the upload.
        db.importQueueDao().setState(stopped.importKey, ImportState.UPLOADING, null, now)
        first.detach()

        val second = attached(queue())
        assertTrue(second.uploadNext())
        assertEquals(ImportState.DONE, entry("Heat (1995).mkv").state)
        assertArrayEquals(bytes, drive.childrenOf("films").single().bytes)
        assertEquals(1, drive.calls.count { it == "POST /upload/drive/v3/files" })
    }

    @Test
    fun aFullDriveStopsTheQueueAndKeepsTheFileQueued() = runBlocking {
        val queue = attached(queue())
        queue.setFolder(ImportFolderKind.MOVIES, movies)
        write(movies, "Dune (2021).mkv")
        detect(queue)
        drive.failures += "PUT /upload/session" to MockResponse().setResponseCode(403)
            .setBody("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"message":"full"}}""")
        queue.uploadNext()

        assertEquals(ImportState.QUEUED, entry("Dune (2021).mkv").state)
        assertTrue(queue.status.value.stoppedReason!!.contains("full"))
        assertTrue(!queue.status.value.isRunning)
    }

    @Test
    fun withoutWriteAccessNothingRuns() = runBlocking {
        val queue = attached(queue(), tokens(scope = GoogleOAuth.DRIVE_READONLY_SCOPE))
        queue.setFolder(ImportFolderKind.MOVIES, movies)
        assertEquals(false, queue.status.value.hasWriteAccess)
        assertTrue(!queue.status.value.isRunning)
    }

    @Test
    fun cancelledAndFinishedEntriesCanBeCleared() = runBlocking {
        val queue = attached(queue())
        queue.setFolder(ImportFolderKind.MOVIES, movies)
        write(movies, "A (2001).mkv")
        write(movies, "B (2002).mkv")
        detect(queue)
        queue.cancel(entry("A (2001).mkv").importKey).join()
        while (queue.uploadNext()) Unit
        assertEquals(listOf("B (2002).mkv"), drive.childrenOf("films").map { it.name })

        queue.clearFinished().join()
        assertTrue(entries().isEmpty())
        assertTrue("local files stay", Files.exists(movies.resolve("A (2001).mkv")))
    }

    @Test
    fun cancelStopsARunningUploadAndRetryUploadsItOnce() = runBlocking {
        // Each chunk takes a moment, like a real upload, so Cancel arrives in the middle.
        val chunksSent = AtomicInteger()
        val slow = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (chain.request().method == "PUT") { Thread.sleep(150); chunksSent.incrementAndGet() }
            chain.proceed(chain.request())
        }).build()
        val queue = attached(queue(slow))
        queue.setFolder(ImportFolderKind.MOVIES, movies)
        val bytes = Random(4).nextBytes(chunk * 8 + 10)
        write(movies, "Long Film (2001).mkv", bytes)
        detect(queue)
        val key = entry("Long Film (2001).mkv").importKey

        val running = launch(Dispatchers.IO) { queue.uploadNext() }
        withTimeout(10_000) { while (chunksSent.get() < 2) delay(10) }
        queue.cancel(key).join()
        withTimeout(10_000) { running.join() }

        assertEquals(ImportState.CANCELLED, entry("Long Film (2001).mkv").state)
        assertTrue("the upload stopped instead of running to the end (${chunksSent.get()} of 9 chunks)", chunksSent.get() < 9)
        assertTrue("nothing finished on Drive", drive.childrenOf("films").none { it.bytes != null })

        queue.retry(key).join()
        assertEquals(ImportState.QUEUED, entry("Long Film (2001).mkv").state)
        assertTrue(queue.uploadNext())
        assertEquals(ImportState.DONE, entry("Long Film (2001).mkv").state)
        assertArrayEquals(bytes, drive.childrenOf("films").single { it.bytes != null }.bytes)
    }

    @Test
    fun foldersMustBeSeparateAndNeedALibrary() = runBlocking {
        val unattached = queue()
        assertEquals("Sign in and choose your Drive library first.", unattached.setFolder(ImportFolderKind.MOVIES, movies))
        val queue = attached(queue())
        assertNull(queue.setFolder(ImportFolderKind.MOVIES, movies))
        assertTrue(queue.setFolder(ImportFolderKind.SERIES, movies.resolve("sub").also { Files.createDirectories(it) })!!.contains("separate"))
        assertEquals(movies.toString(), settings.importFolder(ImportFolderKind.MOVIES))
    }
}
