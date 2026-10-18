package com.drivecinema.desktop.auth

import com.drivecinema.app.core.player.TokenProvider

/**
 * Access tokens for Drive calls and streaming. Refreshes shortly before expiry, or on demand when Drive
 * answers 401 ([forceRefresh], used by the shared `TokenRefreshInterceptor`).
 */
class DesktopTokenProvider(
    private val tokenClient: OAuthTokenClient,
    refreshToken: String,
    accessToken: String,
    expiresAtMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called after every refresh (new access token, and the refresh token if Google rotated it). */
    @Volatile var onTokensChanged: (refreshToken: String, accessToken: String, expiresAtMillis: Long) -> Unit = { _, _, _ -> }
) : TokenProvider {

    @Volatile var refreshToken: String = refreshToken
        private set
    @Volatile private var accessToken: String = accessToken
    @Volatile private var expiresAtMillis: Long = expiresAtMillis

    override fun getAccessToken(): String {
        if (accessToken.isNotEmpty() && clock() < expiresAtMillis - EXPIRY_MARGIN_MS) return accessToken
        return forceRefresh()
    }

    /** Throws [OAuthException] (check [OAuthException.requiresSignIn]) or an IOException when offline. */
    @Synchronized
    override fun forceRefresh(): String {
        val tokens = tokenClient.refresh(refreshToken)
        accessToken = tokens.accessToken
        expiresAtMillis = clock() + tokens.expiresInSeconds * 1000L
        tokens.refreshToken?.let { refreshToken = it }
        onTokensChanged(refreshToken, accessToken, expiresAtMillis)
        return accessToken
    }

    companion object {
        /** Refresh a minute early so a request never starts with an about-to-expire token. */
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
