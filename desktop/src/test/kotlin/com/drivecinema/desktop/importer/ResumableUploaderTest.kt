package com.drivecinema.desktop.importer

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Task I.1, upload: resumable upload in chunks, resume after a restart, retries with backoff, exactly one copy on
 * Drive, and only list/create/upload requests (no update, delete or move).
 */
class ResumableUploaderTest {

    private lateinit var drive: FakeDrive
    private lateinit var dir: Path
    private lateinit var films: String
    private val sleeps = mutableListOf<Long>()
    private val chunk = ResumableUploader.CHUNK_GRANULARITY
    private val content = Random(7).nextBytes(chunk * 2 + 1234)

    @Before
    fun setUp() {
        drive = FakeDrive()
        films = drive.folder("Films", id = "films")
        dir = Files.createTempDirectory("drivecinema-upload")
    }

    @After
    fun tearDown() {
        drive.close()
        dir.toFile().deleteRecursively()
    }

    private fun localFile(bytes: ByteArray = content): UploadRequest {
        val file = dir.resolve("Dune (2021).mkv")
        Files.write(file, bytes)
        return UploadRequest(file, "Dune (2021).mkv", films, bytes.size.toLong(), Files.getLastModifiedTime(file).toMillis())
    }

    private fun uploader(client: DriveWriteClient = drive.client(), maxAttempts: Int = 6) =
        ResumableUploader(client, chunkSize = chunk, maxAttempts = maxAttempts, sleep = { sleeps += it }, random = Random(1))

    private fun uploaded(): List<FakeDrive.Item> = drive.childrenOf(films).filter { it.bytes != null }

    @Test
    fun uploadsInChunksAndForgetsTheSessionWhenDone() = runBlocking {
        val sessions = mutableListOf<String?>()
        val progress = mutableListOf<Long>()
        val outcome = uploader().upload(localFile(), onSession = { sessions += it }, onProgress = { progress += it })

        assertTrue(outcome is UploadOutcome.Uploaded)
        assertArrayEquals(content, uploaded().single().bytes)
        assertEquals(listOf(drive.sessionUrl(1), null), sessions)
        assertEquals(listOf(0L, chunk.toLong(), 2L * chunk, content.size.toLong()), progress)
        assertEquals(3, drive.calls.count { it == "PUT /upload/session/s1" })
    }

    @Test
    fun onlyListCreateAndUploadRequestsAreEverSent() = runBlocking {
        uploader().upload(localFile())
        drive.folder("Series")
        DriveFolderPathResolver(drive.client(), "root").resolve(listOf("Series", "Show", "Season 01"))
        val allowed = setOf("GET /drive/v3/files", "POST /drive/v3/files", "POST /upload/drive/v3/files")
        val unexpected = drive.calls.filterNot { it in allowed || it.startsWith("PUT /upload/session/") }
        assertTrue("unexpected Drive calls: $unexpected", unexpected.isEmpty())
        assertTrue(drive.authorizations.all { it == "Bearer ya29.test" })
    }

    @Test
    fun anUploadInterruptedByARestartResumesWhereDriveStoppedWithoutASecondCopy() = runBlocking {
        var stored: String? = null
        // First run: the connection drops during the second chunk and the app stops (as if it was closed mid-upload).
        val calls = AtomicInteger()
        val failingSecondChunk = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (chain.request().method == "PUT" && calls.incrementAndGet() == 2) throw IOException("connection lost")
            chain.proceed(chain.request())
        }).build()
        try {
            ResumableUploader(drive.client(failingSecondChunk), chunkSize = chunk, maxAttempts = 1, sleep = {}).upload(localFile(), onSession = { stored = it })
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("connection lost", e.message)
        }
        assertEquals(drive.sessionUrl(1), stored)
        assertTrue("nothing on Drive yet", uploaded().isEmpty())

        // After the restart: ask Drive how far it got, send only the rest.
        val outcome = uploader().upload(localFile(), storedSessionUri = stored, onSession = { stored = it })
        assertTrue(outcome is UploadOutcome.Uploaded)
        assertArrayEquals(content, uploaded().single().bytes)
        assertEquals(1, drive.calls.count { it == "POST /upload/drive/v3/files" })
        assertEquals("chunk 1, then a status query and chunks 2 and 3", 4, drive.calls.count { it == "PUT /upload/session/s1" })
        assertEquals(null, stored)
    }

    @Test
    fun aRestartAfterTheLastChunkFindsTheFinishedUploadInsteadOfUploadingAgain() = runBlocking {
        var stored: String? = null
        uploader().upload(localFile(), onSession = { if (it != null) stored = it })
        val outcome = uploader().upload(localFile(), storedSessionUri = stored)
        assertEquals(UploadOutcome.Uploaded(uploaded().single().id), outcome)
        assertEquals(1, uploaded().size)
    }

    @Test
    fun driveKeepingLessThanWasSentIsHandled() = runBlocking {
        drive.partialChunkLimit = 100_000
        uploader().upload(localFile())
        assertArrayEquals(content, uploaded().single().bytes)
    }

    @Test
    fun serverErrorsAndRateLimitsAreRetriedWithGrowingBackoff() = runBlocking {
        drive.failures += "PUT /upload/session" to MockResponse().setResponseCode(503)
        drive.failures += "PUT /upload/session" to MockResponse().setResponseCode(403)
            .setBody("""{"error":{"errors":[{"reason":"userRateLimitExceeded"}],"message":"slow down"}}""")
        drive.failures += "PUT /upload/session" to MockResponse().setResponseCode(429)
        uploader().upload(localFile())

        assertArrayEquals(content, uploaded().single().bytes)
        assertEquals(3, sleeps.size)
        assertTrue("exponential: $sleeps", sleeps[0] < 2_000 && sleeps[1] in 2_000..2_999 && sleeps[2] in 4_000..4_999)
    }

    @Test
    fun aFullQuotaStopsAtOnceForTheQueueToPause() = runBlocking {
        drive.failures += "PUT /upload/session" to MockResponse().setResponseCode(403)
            .setBody("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"message":"full"}}""")
        try {
            uploader().upload(localFile())
            fail("Expected DriveApiException")
        } catch (e: DriveApiException) {
            assertTrue(e.quotaExhausted)
        }
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun anExpiredSessionStartsAgainAfterTheDuplicateCheck() = runBlocking {
        val outcome = uploader().upload(localFile(), storedSessionUri = drive.server.url("/upload/session/gone").toString())
        assertTrue(outcome is UploadOutcome.Uploaded)
        assertEquals(1, uploaded().size)
        assertEquals(1, drive.calls.count { it == "POST /upload/drive/v3/files" })
    }

    @Test
    fun theSameFileAlreadyOnDriveIsNotUploadedAgain() = runBlocking {
        val existing = drive.file("Dune (2021).mkv", films, content)
        assertEquals(UploadOutcome.AlreadyOnDrive(existing), uploader().upload(localFile()))
        assertTrue(drive.calls.none { it.startsWith("POST /upload") })
    }

    @Test
    fun aDifferentFileWithTheSameNameNeedsReview() = runBlocking {
        drive.file("Dune (2021).mkv", films, ByteArray(10))
        try {
            uploader().upload(localFile())
            fail("Expected NeedsReviewException")
        } catch (expected: NeedsReviewException) {
            // expected
        }
        assertTrue(drive.calls.none { it.startsWith("POST /upload") })
    }

    @Test
    fun anUploadAddressOutsideGoogleIsRefusedBeforeAnyDataIsSent() = runBlocking {
        drive.failures += "POST /upload/drive/v3/files" to MockResponse().setHeader("Location", "https://evil.example.com/upload/s1")
        try {
            uploader().upload(localFile())
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Unexpected upload address"))
        }
        assertTrue(drive.calls.none { it.startsWith("PUT") })
    }

    @Test
    fun aFileThatChangesDuringTheUploadStopsItAndStaysUnchangedByUs() = runBlocking {
        val request = localFile()
        var chunks = 0
        try {
            uploader().upload(request, onProgress = {
                if (++chunks == 2) Files.write(request.file, ByteArray(10), StandardOpenOption.APPEND)
            })
            fail("Expected FileChangedException")
        } catch (expected: FileChangedException) {
            // expected
        }
        assertTrue(uploaded().isEmpty())
        assertEquals(content.size + 10L, Files.size(request.file))
    }

    @Test
    fun chunksMustBeMultiplesOf256KiB() {
        try {
            ResumableUploader(drive.client(), chunkSize = 1000)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
