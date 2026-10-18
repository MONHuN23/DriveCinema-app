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
}
