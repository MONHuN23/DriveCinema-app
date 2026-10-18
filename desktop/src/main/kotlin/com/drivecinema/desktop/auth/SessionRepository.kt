package com.drivecinema.desktop.auth

import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.model.DriveUser
import com.drivecinema.desktop.security.CredentialStore
import com.drivecinema.desktop.security.StoredCredentials
import java.io.IOException

/** Result of checking a restored session with Google. */
enum class SessionCheck { VALID, OFFLINE, REVOKED }

/**
 * Keeps the desktop session across restarts in a [CredentialStore]. Only the refresh token and the
 * account name/email are stored; access tokens are fetched again after a restart.
 */
class SessionRepository(
    private val store: CredentialStore,
    private val tokenClient: OAuthTokenClient
) {
    val usesSystemKeyring: Boolean get() = store.isSystemKeyring

    /** The stored session, or null (nothing stored, or the store cannot be read). */
    fun restore(): DesktopSession? {
        val stored = try {
            store.load()
        } catch (e: Exception) {
            AppLog.w(TAG, "Could not read the stored sign-in from ${store.description}: ${e.message}")
            null
        } ?: return null
        val provider = DesktopTokenProvider(tokenClient, stored.refreshToken, accessToken = "", expiresAtMillis = 0L)
        val session = DesktopSession(DriveUser(stored.displayName, stored.email, stored.photoLink), provider)
        persistRotations(session)
        return session
    }

    /** Stores the session; returns false (and logs) if the store refused, so the user signs in next time. */
    fun save(session: DesktopSession): Boolean {
        persistRotations(session)
        return write(session, session.tokenProvider.refreshToken)
    }

    fun clear() {
        try {
            store.clear()
        } catch (e: Exception) {
            AppLog.w(TAG, "Could not clear the stored sign-in from ${store.description}: ${e.message}")
        }
    }

    /** Asks Google for a fresh access token: tells a revoked sign-in apart from simply being offline. */
    fun check(session: DesktopSession): SessionCheck = try {
        session.tokenProvider.forceRefresh()
        SessionCheck.VALID
    } catch (e: OAuthException) {
        if (e.requiresSignIn) SessionCheck.REVOKED else SessionCheck.OFFLINE
    } catch (e: IOException) {
        SessionCheck.OFFLINE
    }

    private fun persistRotations(session: DesktopSession) {
        var lastSaved = session.tokenProvider.refreshToken
        session.tokenProvider.onTokensChanged = { refreshToken, _, _ ->
            if (refreshToken != lastSaved && write(session, refreshToken)) lastSaved = refreshToken
        }
    }

    private fun write(session: DesktopSession, refreshToken: String): Boolean = try {
        store.save(
            StoredCredentials(
                refreshToken = refreshToken,
                displayName = session.user.displayName,
                email = session.user.emailAddress,
                photoLink = session.user.photoLink
            )
        )
        true
    } catch (e: Exception) {
        AppLog.w(TAG, "Could not save the sign-in to ${store.description}: ${e.message}")
        false
    }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
