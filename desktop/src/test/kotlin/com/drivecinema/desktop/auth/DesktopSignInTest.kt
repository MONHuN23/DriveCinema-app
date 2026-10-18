package com.drivecinema.desktop.auth

import com.drivecinema.app.core.network.GoogleDriveGateway
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import kotlin.concurrent.thread

class DesktopSignInTest {

    private lateinit var google: MockWebServer
    private val http = OkHttpClient()
    private val config = DesktopOAuthConfig("client-123", "secret-456")

    @Before
    fun setUp() {
        google = MockWebServer()
        google.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/token" -> MockResponse().setBody("""{"access_token":"ya29.a","expires_in":3600,"refresh_token":"1//r"}""")
                "/drive/v3/about?fields=user%28displayName%2CemailAddress%2CphotoLink%29" ->
                    MockResponse().setBody("""{"user":{"displayName":"Ana Example","emailAddress":"ana@example.com"}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        google.start()
    }

    @After
    fun tearDown() {
        google.shutdown()
    }

    private fun signIn() = DesktopSignIn(
        config = config,
        tokenClient = OAuthTokenClient(config, tokenEndpoint = google.url("/token").toString()),
        driveGateway = GoogleDriveGateway(baseUrl = google.url("/drive/v3").toString().trimEnd('/'))
    )

    /** Plays the browser: "the user approves" and Google redirects back with the code and the same state. */
    private fun approvingBrowser(queryFor: (state: String) -> String): (String) -> Unit = { authUrl ->
        val url = authUrl.toHttpUrl()
        val redirectUri = url.queryParameter("redirect_uri")!!
        val state = url.queryParameter("state")!!
        thread { http.newCall(Request.Builder().url("$redirectUri/?${queryFor(state)}").build()).execute().close() }
    }

    @Test
    fun completeSignInReturnsAccountAndTokens() = runBlocking {
        val session = signIn().signIn(approvingBrowser { state -> "state=$state&code=auth-code" })

        assertEquals("Ana Example", session.user.displayName)
        assertEquals("ana@example.com", session.user.emailAddress)
        assertEquals("ya29.a", session.tokenProvider.getAccessToken())
        assertEquals("1//r", session.tokenProvider.refreshToken)
        val tokenRequest = google.takeRequest().body.readUtf8()
        assertTrue(tokenRequest.contains("code=auth-code"))
        assertTrue(tokenRequest.contains("code_verifier="))
    }

    @Test
    fun cancelledInBrowserGivesAClearMessage() = runBlocking {
        try {
            signIn().signIn(approvingBrowser { state -> "state=$state&error=access_denied" })
            fail("Expected SignInException")
        } catch (e: SignInException) {
            assertEquals("Sign-in was cancelled.", e.message)
        }
    }

    @Test
    fun forgedRedirectIsRejected() = runBlocking {
        try {
            signIn().signIn(approvingBrowser { _ -> "state=not-ours&code=evil" })
            fail("Expected SignInException")
        } catch (e: SignInException) {
            assertTrue(e.message!!.contains("did not match"))
        }
        assertEquals("No token request for a forged redirect", 0, google.requestCount)
    }

    @Test
    fun missingConfigurationFailsBeforeOpeningTheBrowser() = runBlocking {
        var opened = false
        try {
            DesktopSignIn(DesktopOAuthConfig("", "")).signIn { opened = true }
            fail("Expected SignInException")
        } catch (e: SignInException) {
            assertTrue(e.message!!.contains("not configured"))
        }
        assertEquals(false, opened)
    }
}
