package com.drivecinema.desktop.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SignInUiState {
    /** No desktop OAuth client configured in local.properties: sign-in cannot work. */
    data object NotConfigured : SignInUiState
    data class Idle(val errorMessage: String? = null) : SignInUiState
    /** Browser opened; [authorizationUrl] is shown so the user can open it by hand if needed. */
    data class WaitingForBrowser(val authorizationUrl: String?, val browserOpened: Boolean) : SignInUiState
    data class SignedIn(val session: DesktopSession) : SignInUiState
}

/** Task I.1: asking Google for Drive write access from the import screen. */
sealed interface WriteAccessState {
    data class Idle(val errorMessage: String? = null) : WriteAccessState
    data class WaitingForBrowser(val authorizationUrl: String?, val browserOpened: Boolean) : WriteAccessState
}

/**
 * Drives the sign-in screen: restore the stored session at startup, start, cancel, sign out.
 * With [sessions] the sign-in survives restarts; without it (tests) it lives in memory only.
 */
class SignInController(
    private val scope: CoroutineScope,
    isConfigured: Boolean,
    private val signIn: suspend (onAuthorizationUrl: (String) -> Unit) -> DesktopSession,
    private val openBrowser: (String) -> Boolean = BrowserLauncher::open,
    private val sessions: SessionRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Task I.1: [DesktopSignIn.grantDriveWriteAccess]; null where import isn't offered. */
    private val grantWriteAccess: (suspend (DesktopSession, (String) -> Unit) -> DesktopSession)? = null,
    /** Milestone Q: [DesktopSignIn.grantDeviceSync]; null where device sync isn't offered. */
    private val grantDeviceSync: (suspend (DesktopSession, (String) -> Unit) -> DesktopSession)? = null,
    /** Revokes a refresh token with Google ([OAuthTokenClient.revoke]). */
    private val revoke: (String) -> Unit = {}
) {
    private val _state = MutableStateFlow<SignInUiState>(if (isConfigured) SignInUiState.Idle() else SignInUiState.NotConfigured)
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private var job: Job? = null

    private val _writeAccess = MutableStateFlow<WriteAccessState>(WriteAccessState.Idle())
    val writeAccess: StateFlow<WriteAccessState> = _writeAccess.asStateFlow()

    /**
     * Shows the stored account at once (works offline), then checks it with Google in the background:
     * a revoked sign-in (password changed, access removed) returns to the sign-in screen.
     */
    fun restoreSession() {
        val repository = sessions ?: return
        if (_state.value is SignInUiState.NotConfigured) return
        val session = repository.restore() ?: return
        _state.value = SignInUiState.SignedIn(session)
        scope.launch {
            val check = withContext(ioDispatcher) { repository.check(session) }
            if (check == SessionCheck.REVOKED && (_state.value as? SignInUiState.SignedIn)?.session === session) {
                repository.clear()
                _state.value = SignInUiState.Idle(SESSION_EXPIRED)
            }
        }
    }

    fun startSignIn() {
        if (_state.value is SignInUiState.NotConfigured || job?.isActive == true) return
        _state.value = SignInUiState.WaitingForBrowser(authorizationUrl = null, browserOpened = false)
        job = scope.launch {
            try {
                val session = signIn { url ->
                    val opened = openBrowser(url)
                    _state.value = SignInUiState.WaitingForBrowser(url, opened)
                }
                sessions?.let { repository -> withContext(ioDispatcher) { repository.save(session) } }
                _state.value = SignInUiState.SignedIn(session)
            } catch (e: CancellationException) {
                _state.value = SignInUiState.Idle()
                throw e
            } catch (e: SignInException) {
                _state.value = SignInUiState.Idle(e.message)
            } catch (e: Exception) {
                _state.value = SignInUiState.Idle("Sign-in failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value is SignInUiState.WaitingForBrowser) _state.value = SignInUiState.Idle()
    }

    /**
     * Task I.1: adds Drive write access to the current sign-in. The session object stays the same (its token provider
     * adopts the new grant and stores the new refresh token), so the library and the open screen are not restarted.
     * [onGranted] runs after success, e.g. to let the import queue start.
     */
    fun requestWriteAccess(onGranted: () -> Unit = {}) {
        val grant = grantWriteAccess ?: return
        val session = (_state.value as? SignInUiState.SignedIn)?.session ?: return
        if (_writeAccess.value is WriteAccessState.WaitingForBrowser) return
        _writeAccess.value = WriteAccessState.WaitingForBrowser(null, browserOpened = false)
        scope.launch {
            try {
                val granted = grant(session) { url -> _writeAccess.value = WriteAccessState.WaitingForBrowser(url, openBrowser(url)) }
                session.tokenProvider.adoptGrant(granted.tokenProvider)
                _writeAccess.value = WriteAccessState.Idle()
                onGranted()
            } catch (e: CancellationException) {
                _writeAccess.value = WriteAccessState.Idle()
                throw e
            } catch (e: SignInException) {
                _writeAccess.value = WriteAccessState.Idle(e.message)
            } catch (e: Exception) {
                _writeAccess.value = WriteAccessState.Idle("Couldn't get write access: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Milestone Q: adds the app folder scope to the current sign-in, like [requestWriteAccess] (same session object, the
     * new refresh token is stored). [onUrl] gets the consent page address, and whether a browser opened. Returns null when
     * granted, or the message to show.
     */
    suspend fun requestDeviceSyncAccess(onUrl: (url: String, browserOpened: Boolean) -> Unit = { _, _ -> }): String? {
        val grant = grantDeviceSync ?: return "Device sync isn't available in this build."
        val session = (_state.value as? SignInUiState.SignedIn)?.session ?: return "Sign in first."
        return try {
            val granted = grant(session) { url -> onUrl(url, openBrowser(url)) }
            session.tokenProvider.adoptGrant(granted.tokenProvider)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SignInException) {
            e.message ?: "Sync stays off."
        } catch (e: Exception) {
            "Couldn't turn sync on: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * "Remove write access": Google can only revoke the whole sign-in, so this revokes it and signs out; the user signs
     * in again (read-only). The library database and watch history are kept.
     */
    fun removeWriteAccessAndSignOut() {
        val session = (_state.value as? SignInUiState.SignedIn)?.session ?: return
        scope.launch {
            withContext(ioDispatcher) { runCatching { revoke(session.tokenProvider.refreshToken) } }
            signOut()
        }
    }

    /** Forgets the session and the stored sign-in; the library database and watch history are kept. */
    fun signOut() {
        cancel()
        sessions?.clear()
        _state.value = SignInUiState.Idle()
    }

    companion object {
        const val SESSION_EXPIRED = "Your Google sign-in has expired or was removed. Please sign in again."
    }
}
