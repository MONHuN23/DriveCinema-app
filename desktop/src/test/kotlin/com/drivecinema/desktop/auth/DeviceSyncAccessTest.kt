package com.drivecinema.desktop.auth

import com.drivecinema.app.core.model.DriveUser
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.desktop.library.DesktopSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Q.3: turning device sync on asks for the Drive app folder, for the same account, keeping import's write access. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSyncAccessTest {

    private lateinit var google: MockWebServer
    private val http = OkHttpClient()
    private val config = DesktopOAuthConfig("client-123", "secret-456")
    private var grantedScope = "${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_APPDATA_SCOPE}"
    private var accountEmail = "ana@example.com"
    private val revoked = CopyOnWriteArrayList<String>()
    private var lastAuthUrl: String? = null

    @Before
    fun setUp() {
        google = MockWebServer()
        google.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/token" -> MockResponse().setBody(
                    """{"access_token":"ya29.s","expires_in":3600,"refresh_token":"1//sync","scope":"$grantedScope"}"""
                )
                "/revoke" -> { revoked += request.body.readUtf8(); MockResponse() }
                "/drive/v3/about" -> MockResponse().setBody("""{"user":{"displayName":"Ana","emailAddress":"$accountEmail"}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        google.start()
    }

    @After
    fun tearDown() = google.shutdown()

    private fun signIn() = DesktopSignIn(
        config = config,
        tokenClient = OAuthTokenClient(config, tokenEndpoint = google.url("/token").toString(), revokeEndpoint = google.url("/revoke").toString()),
        driveGateway = GoogleDriveGateway(baseUrl = google.url("/drive/v3").toString().trimEnd('/'))
    )

    private fun session(scope: String) = DesktopSession(
        DriveUser("Ana", "ana@example.com", null),
        DesktopTokenProvider(OAuthTokenClient(config), "1//old", "ya29.r", Long.MAX_VALUE, grantedScope = scope)
    )

    private val approvingBrowser: (String) -> Unit = { authUrl ->
        lastAuthUrl = authUrl
        val url = authUrl.toHttpUrl()
        thread {
            http.newCall(Request.Builder().url("${url.queryParameter("redirect_uri")}/?state=${url.queryParameter("state")}&code=c").build())
                .execute().close()
        }
    }

    @Test
    fun theRequestAddsTheAppFolderForTheSignedInAccount() = runBlocking {
        val granted = signIn().grantDeviceSync(session(GoogleOAuth.DRIVE_READONLY_SCOPE), approvingBrowser)
        val url = lastAuthUrl!!.toHttpUrl()
        assertEquals("${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_APPDATA_SCOPE}", url.queryParameter("scope"))
        assertEquals("true", url.queryParameter("include_granted_scopes"))
        assertEquals("ana@example.com", url.queryParameter("login_hint"))
        assertEquals("1//sync", granted.tokenProvider.refreshToken)
        assertTrue(revoked.isEmpty())
    }

    @Test
    fun importsWriteAccessIsAskedForAgainSoItIsKept() = runBlocking {
        grantedScope = "${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_SCOPE} ${GoogleOAuth.DRIVE_APPDATA_SCOPE}"
        val granted = signIn().grantDeviceSync(session("${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_SCOPE}"), approvingBrowser)
        assertEquals(GoogleOAuth.deviceSyncScopes(withWriteAccess = true).joinToString(" "), lastAuthUrl!!.toHttpUrl().queryParameter("scope"))
        assertTrue(granted.tokenProvider.hasDriveWriteAccess)
    }

    @Test
    fun anotherAccountOrAnUntickedBoxIsRefusedAndRevoked() = runBlocking {
        accountEmail = "someone.else@example.com"
        try {
            signIn().grantDeviceSync(session(GoogleOAuth.DRIVE_READONLY_SCOPE), approvingBrowser); fail()
        } catch (e: SignInException) {
            assertTrue(e.message!!.contains("ana@example.com"))
        }
        accountEmail = "ana@example.com"
        grantedScope = GoogleOAuth.DRIVE_READONLY_SCOPE
        try {
            signIn().grantDeviceSync(session(GoogleOAuth.DRIVE_READONLY_SCOPE), approvingBrowser); fail()
        } catch (e: SignInException) {
            assertTrue(e.message!!.contains("app folder"))
        }
        assertEquals(2, revoked.size)
    }

    @Test
    fun theScopeIsOnlyRuledOutWhenGoogleSaidWhatItGranted() {
        assertTrue("not known yet after a restart", GoogleOAuth.mayHaveAppDataAccess(null))
        assertFalse(GoogleOAuth.mayHaveAppDataAccess(GoogleOAuth.DRIVE_READONLY_SCOPE))
        assertTrue(GoogleOAuth.mayHaveAppDataAccess("${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_APPDATA_SCOPE}"))
    }

    @Test
    fun theControllerKeepsTheSessionAndAdoptsTheGrant() = runTest {
        val current = session(GoogleOAuth.DRIVE_READONLY_SCOPE)
        var stored: String? = null
        current.tokenProvider.onTokensChanged = { refresh, _, _ -> stored = refresh }
        val grantedTokens = DesktopTokenProvider(OAuthTokenClient(config), "1//sync", "ya29.s", Long.MAX_VALUE, grantedScope = grantedScope)
        val controller = SignInController(
            this, isConfigured = true, signIn = { current }, openBrowser = { false },
            grantDeviceSync = { s, onUrl -> onUrl("https://accounts.google.com/s"); DesktopSession(s.user, grantedTokens) }
        )
        controller.startSignIn()
        advanceUntilIdle()
        var shownUrl: Pair<String, Boolean>? = null
        assertNull(controller.requestDeviceSyncAccess { url, opened -> shownUrl = url to opened })
        assertEquals("https://accounts.google.com/s" to false, shownUrl)
        assertEquals(SignInUiState.SignedIn(current), controller.state.value)
        assertEquals("1//sync", stored)

        val refusing = SignInController(this, true, signIn = { current }, grantDeviceSync = { _, _ -> throw SignInException("Sync stays off.") })
        assertEquals("not signed in yet", "Sign in first.", refusing.requestDeviceSyncAccess())
        refusing.startSignIn()
        advanceUntilIdle()
        assertEquals("Sync stays off.", refusing.requestDeviceSyncAccess())
    }

    @Test
    fun theSwitchIsOffByDefaultAndTheDeviceIdIsKept() {
        val dir = Files.createTempDirectory("drivecinema-settings").toFile()
        try {
            val settings = DesktopSettings(java.io.File(dir, "settings.json"))
            assertFalse(settings.deviceSyncEnabled())
            settings.setDeviceSyncEnabled(true)
            val id = settings.deviceSyncId()
            val reopened = DesktopSettings(java.io.File(dir, "settings.json"))
            assertTrue(reopened.deviceSyncEnabled())
            assertEquals(id, reopened.deviceSyncId())
            assertTrue("other settings untouched", reopened.skipIntroEnabled())
        } finally {
            dir.deleteRecursively()
        }
    }
}
