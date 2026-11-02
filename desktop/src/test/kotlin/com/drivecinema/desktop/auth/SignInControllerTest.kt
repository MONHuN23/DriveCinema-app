package com.drivecinema.desktop.auth

import com.drivecinema.app.core.model.DriveUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

@OptIn(ExperimentalCoroutinesApi::class)
class SignInControllerTest {

    private val session = DesktopSession(
        DriveUser("Ana Example", "ana@example.com"),
        DesktopTokenProvider(OAuthTokenClient(DesktopOAuthConfig("id", "secret")), "1//r", "ya29.a", Long.MAX_VALUE)
    )

    @Test
    fun notConfiguredCannotStart() = runTest {
        var called = false
        val controller = SignInController(this, isConfigured = false, signIn = { called = true; session })
        controller.startSignIn()
        advanceUntilIdle()
        assertEquals(SignInUiState.NotConfigured, controller.state.value)
        assertEquals(false, called)
    }

    @Test
    fun successfulSignInShowsTheLinkThenTheAccount() = runTest {
        val approve = CompletableDeferred<Unit>()
        val controller = SignInController(
            this, isConfigured = true,
            signIn = { onUrl -> onUrl("https://accounts.google.com/x"); approve.await(); session },
            openBrowser = { false }
        )

        controller.startSignIn()
        advanceUntilIdle()
        assertEquals(SignInUiState.WaitingForBrowser("https://accounts.google.com/x", browserOpened = false), controller.state.value)

        approve.complete(Unit)
        advanceUntilIdle()
        assertEquals(SignInUiState.SignedIn(session), controller.state.value)

        controller.signOut()
        assertEquals(SignInUiState.Idle(), controller.state.value)
    }

    @Test
    fun failureReturnsToIdleWithTheMessage() = runTest {
        val controller = SignInController(this, isConfigured = true, signIn = { throw SignInException("Sign-in was cancelled.") })
        controller.startSignIn()
        advanceUntilIdle()
        assertEquals(SignInUiState.Idle("Sign-in was cancelled."), controller.state.value)
    }

    @Test
    fun cancelWhileWaitingGoesBackToIdle() = runTest {
        val never = CompletableDeferred<DesktopSession>()
        val controller = SignInController(this, isConfigured = true, signIn = { never.await() }, openBrowser = { true })
        controller.startSignIn()
        advanceUntilIdle()
        assertTrue(controller.state.value is SignInUiState.WaitingForBrowser)

        controller.cancel()
        advanceUntilIdle()
        assertEquals(SignInUiState.Idle(), controller.state.value)
    }

    @Test
    fun configReadsBothValuesAndNeedsBoth() {
        val props = Properties().apply {
            setProperty("GOOGLE_DESKTOP_CLIENT_ID", " 123.apps.googleusercontent.com ")
            setProperty("GOOGLE_DESKTOP_CLIENT_SECRET", "GOCSPX-abc")
        }
        val config = DesktopOAuthConfig.fromProperties(props)
        assertEquals("123.apps.googleusercontent.com", config.clientId)
        assertTrue(config.isConfigured)
        assertEquals(false, DesktopOAuthConfig.fromProperties(Properties()).isConfigured)
    }

    @Test
    fun buildBundlesTheConfigResource() {
        // Generated from local.properties by the generateDesktopConfig task (values may be empty).
        assertTrue(DesktopOAuthConfig::class.java.getResource(DesktopOAuthConfig.RESOURCE) != null)
    }

    @Test
    fun writeAccessKeepsTheSameSessionAndAdoptsTheNewGrant() = runTest {
        val granted = DesktopTokenProvider(
            OAuthTokenClient(DesktopOAuthConfig("id", "secret")), "1//write", "ya29.w", Long.MAX_VALUE,
            grantedScope = "${GoogleOAuth.DRIVE_READONLY_SCOPE} ${GoogleOAuth.DRIVE_SCOPE}"
        )
        var stored: String? = null
        session.tokenProvider.onTokensChanged = { refresh, _, _ -> stored = refresh }
        var onGranted = false
        val controller = SignInController(
            this, isConfigured = true, signIn = { session }, openBrowser = { true },
            grantWriteAccess = { current, onUrl -> onUrl("https://accounts.google.com/w"); DesktopSession(current.user, granted) }
        )
        controller.startSignIn()
        advanceUntilIdle()

        controller.requestWriteAccess { onGranted = true }
        advanceUntilIdle()

        assertEquals(SignInUiState.SignedIn(session), controller.state.value)
        assertTrue(session.tokenProvider.hasDriveWriteAccess)
        assertEquals("1//write", stored)
        assertTrue(onGranted)
        assertEquals(WriteAccessState.Idle(), controller.writeAccess.value)
    }

    @Test
    fun refusedWriteAccessShowsWhyAndChangesNothing() = runTest {
        val controller = SignInController(
            this, isConfigured = true, signIn = { session },
            grantWriteAccess = { _, _ -> throw SignInException("Google did not grant write access") }
        )
        controller.startSignIn()
        advanceUntilIdle()
        controller.requestWriteAccess()
        advanceUntilIdle()
        assertEquals(WriteAccessState.Idle("Google did not grant write access"), controller.writeAccess.value)
        assertEquals(false, session.tokenProvider.hasDriveWriteAccess)
    }

    @Test
    fun removingWriteAccessRevokesAndSignsOut() = runTest {
        val revoked = mutableListOf<String>()
        val controller = SignInController(this, isConfigured = true, signIn = { session }, revoke = { revoked += it }, ioDispatcher = StandardTestDispatcher(testScheduler))
        controller.startSignIn()
        advanceUntilIdle()
        controller.removeWriteAccessAndSignOut()
        advanceUntilIdle()
        assertEquals(listOf("1//r"), revoked)
        assertEquals(SignInUiState.Idle(), controller.state.value)
    }
}
