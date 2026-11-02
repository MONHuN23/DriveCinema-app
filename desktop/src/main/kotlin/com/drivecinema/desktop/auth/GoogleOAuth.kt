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
    const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
    /** Same scope as the Android app. */
    const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    /** Task I.1 (user decision 2026-10-21): full Drive access, desktop only, asked when import is turned on. */
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

    /** The normal sign-in: read-only, as on every platform. */
    val SIGN_IN_SCOPES = listOf(DRIVE_READONLY_SCOPE)
    /** Turning import on: read-only plus write, added to the existing grant (incremental authorization). */
    val IMPORT_SCOPES = listOf(DRIVE_READONLY_SCOPE, DRIVE_SCOPE)

    /** Milestone Q: the hidden app folder for device sync, asked when "Sync with my devices" is turned on. */
    const val DRIVE_APPDATA_SCOPE = com.drivecinema.app.core.devicesync.AppDataClient.SCOPE

    /** Turning device sync on: read-only plus the app folder, and write access again when import has it. */
    fun deviceSyncScopes(withWriteAccess: Boolean): List<String> =
        listOf(DRIVE_READONLY_SCOPE) + (if (withWriteAccess) listOf(DRIVE_SCOPE) else emptyList()) + DRIVE_APPDATA_SCOPE

    /** True when the space-separated scope list Google returned with a token includes write access. */
    fun hasDriveWriteAccess(grantedScope: String?): Boolean = grantedScope?.split(' ')?.contains(DRIVE_SCOPE) == true

    /** Milestone Q: false only when the granted scopes are known and lack the app folder (null = not known yet). */
    fun mayHaveAppDataAccess(grantedScope: String?): Boolean = grantedScope == null || grantedScope.split(' ').contains(DRIVE_APPDATA_SCOPE)

    fun authorizationUrl(
        clientId: String,
        redirectUri: String,
        pkce: Pkce,
        state: String,
        scopes: List<String> = SIGN_IN_SCOPES,
        /** Preselects the signed-in account, so write access is asked for the same one. */
        loginHint: String? = null
    ): String =
        AUTHORIZATION_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", scopes.joinToString(" "))
            .addQueryParameter("code_challenge", pkce.challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            // A refresh token every time, like the Android app (access_type=offline + prompt=consent).
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .apply {
                if (scopes != SIGN_IN_SCOPES) addQueryParameter("include_granted_scopes", "true")
                loginHint?.let { addQueryParameter("login_hint", it) }
            }
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
