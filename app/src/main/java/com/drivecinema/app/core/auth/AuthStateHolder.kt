package com.drivecinema.app.core.auth

import net.openid.appauth.AuthState
import net.openid.appauth.TokenResponse

class AuthStateHolder(
    var authState: AuthState = AuthState()
) {
    val accessToken: String?
        get() = authState.accessToken

    val refreshToken: String?
        get() = authState.refreshToken

    val isAuthorized: Boolean
        get() = authState.isAuthorized

    fun update(tokenResponse: TokenResponse) {
        authState.update(tokenResponse, null)
    }

    fun clear() {
        authState = AuthState()
    }
}
