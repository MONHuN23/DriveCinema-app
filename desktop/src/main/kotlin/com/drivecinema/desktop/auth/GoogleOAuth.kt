package com.drivecinema.desktop.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLDecoder

/** Result of Google redirecting the browser back to the app's loopback address. */
sealed interface AuthRedirect {
    data class Code(val code: String) : AuthRedirect
    /** The user cancelled or Google refused (e.g. `access_denied`). */
    data class Denied(val error: String, val description: String?) : AuthRedirect
    /** `state` did not match: the request did not come from this sign-in attempt. */
    data object StateMismatch : AuthRedirect
    data class Invalid(val reason: String) : AuthRedirect
}

/** Google OAuth 2.0 for installed desktop apps: browser + loopback redirect + PKCE (RFC 8252). */
object GoogleOAuth {
    const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    /** Same scope as the Android app. */
    const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"

    fun authorizationUrl(clientId: String, redirectUri: String, pkce: Pkce, state: String): String =
        AUTHORIZATION_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", DRIVE_READONLY_SCOPE)
            .addQueryParameter("code_challenge", pkce.challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            // A refresh token every time, like the Android app (access_type=offline + prompt=consent).
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .build()
            .toString()

    /** Parses the query string of the redirect request (`code=...&state=...` or `error=...`). */
    fun parseRedirect(rawQuery: String?, expectedState: String): AuthRedirect {
        val params = parseQuery(rawQuery)
        val error = params["error"]
        val state = params["state"]
        if (state != expectedState) {
            return if (error != null && state == null) AuthRedirect.Denied(error, params["error_description"])
            else AuthRedirect.StateMismatch
        }
        if (error != null) return AuthRedirect.Denied(error, params["error_description"])
        val code = params["code"]
        return if (code.isNullOrBlank()) AuthRedirect.Invalid("No authorization code in the redirect")
        else AuthRedirect.Code(code)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            URLDecoder.decode(key, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }
    }
}
