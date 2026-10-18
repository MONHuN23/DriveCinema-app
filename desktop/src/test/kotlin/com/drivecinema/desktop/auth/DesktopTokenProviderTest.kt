package com.drivecinema.desktop.auth

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DesktopTokenProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenClient: OAuthTokenClient
    private var now = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokenClient = OAuthTokenClient(DesktopOAuthConfig("id", "secret"), tokenEndpoint = server.url("/token").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(expiresAt: Long, onChanged: (String, String, Long) -> Unit = { _, _, _ -> }) =
        DesktopTokenProvider(tokenClient, "1//r", "ya29.old", expiresAt, clock = { now }, onTokensChanged = onChanged)

    @Test
    fun validTokenIsReusedWithoutNetwork() {
        val provider = provider(expiresAt = now + 30 * 60_000L)
        assertEquals("ya29.old", provider.getAccessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun tokenCloseToExpiryIsRefreshed() {
        server.enqueue(MockResponse().setBody("""{"access_token":"ya29.new","expires_in":3600}"""))
        var stored: Triple<String, String, Long>? = null
        val provider = provider(expiresAt = now + 30_000L) { r, a, e -> stored = Triple(r, a, e) }

        assertEquals("ya29.new", provider.getAccessToken())
        assertEquals(Triple("1//r", "ya29.new", now + 3_600_000L), stored)
        // Now valid again: no second request.
        assertEquals("ya29.new", provider.getAccessToken())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rotatedRefreshTokenIsKept() {
        server.enqueue(MockResponse().setBody("""{"access_token":"ya29.new","expires_in":3600,"refresh_token":"1//rotated"}"""))
        val provider = provider(expiresAt = 0L)

        provider.forceRefresh()

        assertEquals("1//rotated", provider.refreshToken)
    }
}
