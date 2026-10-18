package com.drivecinema.desktop.player

import com.drivecinema.app.core.player.TokenProvider
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DriveStreamProxyTest {

    private lateinit var drive: MockWebServer
    private lateinit var proxy: DriveStreamProxy
    private val http = OkHttpClient()
    private val file = ByteArray(1000) { (it % 251).toByte() }
    private val refreshes = AtomicInteger()

    /** Starts with an expired token; Drive rejects it until it is refreshed. */
    private val tokens = object : TokenProvider {
        @Volatile var token = "expired"
        override fun getAccessToken() = token
        override fun forceRefresh(): String { refreshes.incrementAndGet(); token = "fresh"; return token }
    }

    @Before
    fun setUp() {
        drive = MockWebServer()
        drive.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Authorization") != "Bearer fresh") return MockResponse().setResponseCode(401)
                val range = request.getHeader("Range")
                    ?: return MockResponse().setBody(okio.Buffer().write(file)).setHeader("Content-Type", "video/x-matroska")
                val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Type", "video/x-matroska")
                    .setHeader("Content-Range", "bytes $start-$end/${file.size}")
                    .setHeader("Accept-Ranges", "bytes")
                    .setBody(okio.Buffer().write(file.copyOfRange(start, end + 1)))
            }
        }
        drive.start()
        proxy = DriveStreamProxy(tokens, mediaUrl = { id -> drive.url("/drive/v3/files/$id?alt=media").toString() })
    }

    @After
    fun tearDown() {
        proxy.close()
        drive.shutdown()
    }

    @Test
    fun forwardsRangeRequestsWithTheTokenAndRefreshesOnce() {
        val response = http.newCall(
            Request.Builder().url(proxy.urlFor("file-1")).header("Range", "bytes=100-199").build()
        ).execute()

        response.use {
            assertEquals(206, it.code)
            assertEquals("bytes 100-199/1000", it.header("Content-Range"))
            assertEquals("bytes", it.header("Accept-Ranges"))
            assertTrue(file.copyOfRange(100, 200).contentEquals(it.body!!.bytes()))
        }
        assertEquals(1, refreshes.get())
        val first = drive.takeRequest()
        assertEquals("/drive/v3/files/file-1?alt=media", first.path)
        assertEquals("bytes=100-199", first.getHeader("Range"))
    }

    @Test
    fun fullDownloadWithoutRange() {
        tokens.token = "fresh"
        http.newCall(Request.Builder().url(proxy.urlFor("file-1")).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals(1000L, it.body!!.contentLength())
            assertTrue(file.contentEquals(it.body!!.bytes()))
        }
    }

    @Test
    fun requestsWithoutTheSecretAreRefused() {
        val goodUrl = proxy.urlFor("file-1")
        val wrong = goodUrl.replaceAfter("127.0.0.1:", "").let { prefix ->
            prefix + goodUrl.substringAfter("127.0.0.1:").substringBefore('/') + "/guess/drive/file-1"
        }
        http.newCall(Request.Builder().url(wrong).build()).execute().use { assertEquals(404, it.code) }
        assertEquals(0, drive.requestCount)
    }

    @Test
    fun streamingNeverSharesConnectionsWithLibraryCalls() {
        // Regression: sharing one HTTP/2 connection let an unread video stream stall the library sync (timeouts)
        // and VLC's own second range request (slow start).
        val libraryClient = OkHttpClient()
        DriveStreamProxy(tokens, libraryClient).use { separate ->
            assertEquals(listOf(Protocol.HTTP_1_1), separate.client.protocols)
            assertNotSame(libraryClient.connectionPool, separate.client.connectionPool)
            assertFalse(separate.client.followRedirects)
        }
    }

    @Test
    fun urlNeverContainsTheToken() {
        tokens.token = "fresh"
        assertFalse(proxy.urlFor("file-1").contains("fresh"))
        assertTrue(proxy.urlFor("file-1").startsWith("http://127.0.0.1:"))
    }
}
