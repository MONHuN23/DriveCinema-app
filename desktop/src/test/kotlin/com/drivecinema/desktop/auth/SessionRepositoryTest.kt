package com.drivecinema.desktop.auth

import com.drivecinema.app.core.model.DriveUser
import com.drivecinema.desktop.security.CredentialStore
import com.drivecinema.desktop.security.InMemoryCredentialStore
import com.drivecinema.desktop.security.StoredCredentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {

    private lateinit var google: MockWebServer
    private lateinit var tokenClient: OAuthTokenClient
    private val stored = StoredCredentials("1//r", "Ana Example", "ana@example.com")

    @Before
    fun setUp() {
        google = MockWebServer()
        google.start()
        tokenClient = OAuthTokenClient(DesktopOAuthConfig("id", "secret"), tokenEndpoint = google.url("/token").toString())
    }

    @After
    fun tearDown() {
        google.shutdown()
    }

    private fun ok(refresh: String? = null) = MockResponse().setBody(
        """{"access_token":"ya29.x","expires_in":3600${refresh?.let { ",\"refresh_token\":\"$it\"" } ?: ""}}"""
    )

    @Test
    fun restoresAccountWithoutNetwork() {
        val session = SessionRepository(InMemoryCredentialStore(stored), tokenClient).restore()!!
        assertEquals("Ana Example", session.user.displayName)
        assertEquals("1//r", session.tokenProvider.refreshToken)
        assertEquals(0, google.requestCount)
    }

    @Test
    fun nothingStoredRestoresNothing() {
        assertNull(SessionRepository(InMemoryCredentialStore(), tokenClient).restore())
    }

    @Test
    fun checkTellsRevokedFromOffline() {
        val repository = SessionRepository(InMemoryCredentialStore(stored), tokenClient)
        val session = repository.restore()!!

        google.enqueue(ok())
        assertEquals(SessionCheck.VALID, repository.check(session))

        google.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertEquals(SessionCheck.REVOKED, repository.check(session))

        google.enqueue(MockResponse().setResponseCode(503))
        assertEquals(SessionCheck.OFFLINE, repository.check(session))
    }

    @Test
    fun rotatedRefreshTokenIsStored() {
        val store = InMemoryCredentialStore(stored)
        val repository = SessionRepository(store, tokenClient)
        val session = repository.restore()!!

        google.enqueue(ok(refresh = "1//rotated"))
        session.tokenProvider.forceRefresh()

        assertEquals("1//rotated", store.load()!!.refreshToken)
    }

    @Test
    fun brokenStoreNeverCrashesTheApp() {
        val broken = object : CredentialStore {
            override val description = "broken"
            override val isSystemKeyring = true
            override fun load(): StoredCredentials? = throw IllegalStateException("locked")
            override fun save(credentials: StoredCredentials) = throw IllegalStateException("locked")
            override fun clear() = throw IllegalStateException("locked")
        }
        val repository = SessionRepository(broken, tokenClient)
        assertNull(repository.restore())
        val session = DesktopSession(DriveUser("A", "a@x"), DesktopTokenProvider(tokenClient, "1//r", "", 0L))
        assertFalse(repository.save(session))
        repository.clear()
    }

    @Test
    fun controllerRestoresSignsOutAndHandlesRevocation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryCredentialStore(stored)
        val repository = SessionRepository(store, tokenClient)

        // Valid: stays signed in.
        google.enqueue(ok())
        val controller = SignInController(this, true, signIn = { error("not used") }, sessions = repository, ioDispatcher = dispatcher)
        controller.restoreSession()
        assertTrue(controller.state.value is SignInUiState.SignedIn)
        advanceUntilIdle()
        assertTrue(controller.state.value is SignInUiState.SignedIn)

        // Sign out clears the stored sign-in.
        controller.signOut()
        assertNull(store.load())
        assertEquals(SignInUiState.Idle(), controller.state.value)

        // Revoked: back to sign-in with a message, stored sign-in removed.
        store.save(stored)
        google.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        val second = SignInController(this, true, signIn = { error("not used") }, sessions = repository, ioDispatcher = dispatcher)
        second.restoreSession()
        advanceUntilIdle()
        assertEquals(SignInUiState.Idle(SignInController.SESSION_EXPIRED), second.state.value)
        assertNull(store.load())
    }

    @Test
    fun successfulSignInIsStored() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = InMemoryCredentialStore()
        val session = DesktopSession(DriveUser("Ana Example", "ana@example.com"), DesktopTokenProvider(tokenClient, "1//new", "ya29", Long.MAX_VALUE))
        val controller = SignInController(
            this, true, signIn = { session }, openBrowser = { true },
            sessions = SessionRepository(store, tokenClient), ioDispatcher = dispatcher
        )
        controller.startSignIn()
        advanceUntilIdle()
        assertEquals(StoredCredentials("1//new", "Ana Example", "ana@example.com"), store.load())
    }
}
