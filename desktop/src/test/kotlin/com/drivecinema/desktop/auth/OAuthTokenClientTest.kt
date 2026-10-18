package com.drivecinema.desktop.auth

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class OAuthTokenClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OAuthTokenClient
    private val config = DesktopOAuthConfig("client-123", "secret-456")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OAuthTokenClient(config, tokenEndpoint = server.url("/token").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun form(body: String): Map<String, String> = body.split('&').associate {
        URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }

    @Test
    fun exchangesCodeWithVerifierAndParsesTokens() {
        server.enqueue(
            MockResponse().setBody(
                """{"access_token":"ya29.a","expires_in":3599,"refresh_token":"1//r","scope":"https://www.googleapis.com/auth/drive.readonly","token_type":"Bearer"}"""
            )
        )
        val pkce = Pkce.fromVerifier("verifier-abc")

        val tokens = client.exchangeCode("auth-code", "http://127.0.0.1:5555", pkce)

        assertEquals("ya29.a", tokens.accessToken)
        assertEquals(3599L, tokens.expiresInSeconds)
        assertEquals("1//r", tokens.refreshToken)
        val sent = form(server.takeRequest().body.readUtf8())
        assertEquals("authorization_code", sent["grant_type"])
        assertEquals("auth-code", sent["code"])
        assertEquals("http://127.0.0.1:5555", sent["redirect_uri"])
        assertEquals("verifier-abc", sent["code_verifier"])
        assertEquals("client-123", sent["client_id"])
        assertEquals("secret-456", sent["client_secret"])
    }

    @Test
    fun refreshWithoutRotationKeepsRefreshTokenNull() {
        server.enqueue(MockResponse().setBody("""{"access_token":"ya29.b","expires_in":3599}"""))

        val tokens = client.refresh("1//r")

        assertEquals("ya29.b", tokens.accessToken)
        assertNull(tokens.refreshToken)
        val sent = form(server.takeRequest().body.readUtf8())
        assertEquals("refresh_token", sent["grant_type"])
        assertEquals("1//r", sent["refresh_token"])
    }

    @Test
    fun revokedRefreshTokenRequiresSignIn() {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"invalid_grant","error_description":"Token has been expired or revoked."}""")
        )
        try {
            client.refresh("1//revoked")
            fail("Expected OAuthException")
        } catch (e: OAuthException) {
            assertEquals("invalid_grant", e.error)
            assertTrue(e.requiresSignIn)
            assertEquals(400, e.httpStatus)
        }
    }

    @Test
    fun nonJsonServerErrorIsReported() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        try {
            client.refresh("1//r")
            fail("Expected OAuthException")
        } catch (e: OAuthException) {
            assertEquals("http_503", e.error)
            assertFalse(e.requiresSignIn)
        }
    }

    @Test
    fun tokensAndSecretNeverAppearInToString() {
        val tokens = OAuthTokens("ya29.secret", 3600, "1//secret", null)
        assertFalse(tokens.toString().contains("secret"))
        assertFalse(config.toString().contains("secret-456"))
    }
}
