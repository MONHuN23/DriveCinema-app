package com.drivecinema.desktop.auth

import com.drivecinema.app.core.model.DriveUser
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Task I.1 (scope decision A): write access is asked on the desktop only when import is turned on, for the same
 * account, and only kept when Google really granted it.
 */
class DriveWriteAccessTest {

    private lateinit var google: MockWebServer
    private val http = OkHttpClient()
    private val config = DesktopOAuthConfig("client-123", "secret-456")
    private var grantedScope = "${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_SCOPE}"
    private var accountEmail = "ana@example.com"
    private val revoked = CopyOnWriteArrayList<String>()
    private var lastAuthUrl: String? = null

    @Before
    fun setUp() {
        google = MockWebServer()
        google.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/token" -> MockResponse().setBody(
                    """{"access_token":"ya29.w","expires_in":3600,"refresh_token":"1//write","scope":"$grantedScope"}"""
                )
                "/revoke" -> {
                    revoked += request.body.readUtf8().substringAfter("token=")
                    MockResponse()
                }
                "/drive/v3/about" -> MockResponse().setBody("""{"user":{"displayName":"Ana","emailAddress":"$accountEmail"}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        google.start()
    }

    @After
    fun tearDown() {
        google.shutdown()
    }

    private fun tokenClient() = OAuthTokenClient(
        config,
        tokenEndpoint = google.url("/token").toString(),
        revokeEndpoint = google.url("/revoke").toString()
    )

    private fun signIn() = DesktopSignIn(
        config = config,
        tokenClient = tokenClient(),
        driveGateway = GoogleDriveGateway(baseUrl = google.url("/drive/v3").toString().trimEnd('/'))
    )

    private val current = DesktopSession(
        DriveUser("Ana", "ana@example.com", null),
        DesktopTokenProvider(OAuthTokenClient(config), "1//readonly", "ya29.r", Long.MAX_VALUE, grantedScope = GoogleOAuth.DRIVE_READONLY_SCOPE)
    )

    private val approvingBrowser: (String) -> Unit = { authUrl ->
        lastAuthUrl = authUrl
        val url = authUrl.toHttpUrl()
        val redirectUri = url.queryParameter("redirect_uri")!!
        val state = url.queryParameter("state")!!
        thread { http.newCall(Request.Builder().url("$redirectUri/?state=$state&code=c").build()).execute().close() }
    }

    @Test
    fun normalSignInStaysReadOnly() {
        val url = GoogleOAuth.authorizationUrl("c", "http://127.0.0.1:1", Pkce.generate(), "s").toHttpUrl()
        assertEquals(GoogleOAuth.DRIVE_READONLY_SCOPE, url.queryParameter("scope"))
        assertNull(url.queryParameter("include_granted_scopes"))
        assertFalse(current.tokenProvider.hasDriveWriteAccess)
    }

    @Test
    fun turningImportOnAsksForWriteAccessForTheSameAccount() = runBlocking {
        val session = signIn().grantDriveWriteAccess(current, approvingBrowser)

        val url = lastAuthUrl!!.toHttpUrl()
        assertEquals("${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_SCOPE}", url.queryParameter("scope"))
        assertEquals("true", url.queryParameter("include_granted_scopes"))
        assertEquals("ana@example.com", url.queryParameter("login_hint"))
        assertTrue(session.tokenProvider.hasDriveWriteAccess)
        assertEquals("1//write", session.tokenProvider.refreshToken)
        assertTrue("nothing revoked when it worked", revoked.isEmpty())
    }

    @Test
    fun anotherAccountIsRefusedAndItsGrantRevoked() = runBlocking {
        accountEmail = "someone.else@example.com"
        try {
            signIn().grantDriveWriteAccess(current, approvingBrowser)
            fail("Expected SignInException")
        } catch (e: SignInException) {
            assertTrue(e.message!!.contains("ana@example.com"))
        }
        assertEquals(listOf("1%2F%2Fwrite"), revoked)
    }

    @Test
    fun untickedDrivePermissionIsRefusedAndRevoked() = runBlocking {
        grantedScope = GoogleOAuth.DRIVE_READONLY_SCOPE
        try {
            signIn().grantDriveWriteAccess(current, approvingBrowser)
            fail("Expected SignInException")
        } catch (e: SignInException) {
            assertTrue(e.message!!.contains("did not grant write access"))
        }
        assertEquals(1, revoked.size)
    }

    @Test
    fun restoredSessionLearnsItsScopeFromTheFirstRefresh() {
        val provider = DesktopTokenProvider(tokenClient(), "1//write", accessToken = "", expiresAtMillis = 0L)
        assertFalse("unknown until refreshed", provider.hasDriveWriteAccess)
        provider.getAccessToken()
        assertTrue(provider.hasDriveWriteAccess)
    }

    @Test
    fun revokeTreatsAnUnknownTokenAsRevokedButReportsOtherErrors() {
        google.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.body.readUtf8().contains("gone")) MockResponse().setResponseCode(400).setBody("""{"error":"invalid_token"}""")
                else MockResponse().setResponseCode(503).setBody("""{"error":"backend_error"}""")
        }
        tokenClient().revoke("gone")
        try {
            tokenClient().revoke("live")
            fail("Expected OAuthException")
        } catch (e: OAuthException) {
            assertEquals("backend_error", e.error)
        }
    }
}
