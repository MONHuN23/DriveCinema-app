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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow<SignInUiState>(if (isConfigured) SignInUiState.Idle() else SignInUiState.NotConfigured)
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private var job: Job? = null

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
