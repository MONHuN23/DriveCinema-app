package com.drivecinema.desktop.player

import com.drivecinema.app.core.player.TokenProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Collections

/** Drive redirects media requests to a signed download link; the proxy remembers it so seeks skip one hop. */
class DriveStreamProxyRedirectTest {

    private lateinit var drive: MockWebServer
    private lateinit var proxy: DriveStreamProxy
    private val http = OkHttpClient()
    private val paths = Collections.synchronizedList(mutableListOf<String>())
    private var now = 0L
    @Volatile private var downloadLinkWorks = true

    private val tokens = object : TokenProvider {
        override fun getAccessToken() = "t"
        override fun forceRefresh() = "t"
    }

    @Before
    fun setUp() {
        drive = MockWebServer()
        drive.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                paths += path.substringBefore('?')
                return when {
                    path.startsWith("/drive/v3/files/") ->
                        MockResponse().setResponseCode(302).setHeader("Location", "/download/signed-link")
                    path.startsWith("/download/") && downloadLinkWorks ->
                        MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-9/100").setBody("0123456789")
                    else -> MockResponse().setResponseCode(403)
                }
            }
        }
        drive.start()
        proxy = DriveStreamProxy(
            tokens,
            mediaUrl = { id -> drive.url("/drive/v3/files/$id?alt=media").toString() },
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        proxy.close()
        drive.shutdown()
    }

    private fun fetch(range: String = "bytes=0-9"): Int =
        http.newCall(Request.Builder().url(proxy.urlFor("file-1")).header("Range", range).build()).execute().use { it.code }

    @Test
    fun secondRequestGoesStraightToTheDownloadLink() {
        assertEquals(206, fetch())
        assertEquals(listOf("/drive/v3/files/file-1", "/download/signed-link"), paths.toList())

        paths.clear()
        assertEquals(206, fetch("bytes=50-59"))
        assertEquals(listOf("/download/signed-link"), paths.toList())
    }

    @Test
    fun expiredLinkGoesThroughDriveAgain() {
        fetch()
        now += DriveStreamProxyTimings.DIRECT_LINK_TTL_MS + 1
        paths.clear()
        assertEquals(206, fetch())
        assertEquals(listOf("/drive/v3/files/file-1", "/download/signed-link"), paths.toList())
    }

    @Test
    fun refusedLinkFallsBackToDrive() {
        fetch()
        downloadLinkWorks = false
        paths.clear()
        // The remembered link is refused, Drive is asked again (and its fresh link is refused too here).
        assertEquals(403, fetch())
        assertEquals(listOf("/download/signed-link", "/drive/v3/files/file-1", "/download/signed-link"), paths.toList())

        downloadLinkWorks = true
        paths.clear()
        assertEquals(206, fetch())
        assertEquals("No stale link is kept after a failure", "/drive/v3/files/file-1", paths.first())
    }
}
