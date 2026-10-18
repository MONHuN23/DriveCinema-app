package com.drivecinema.desktop.auth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class OAuthTokens(
    val accessToken: String,
    val expiresInSeconds: Long,
    /** Google only returns it on the first exchange (and when it rotates it); null on most refreshes. */
    val refreshToken: String?,
    val scope: String?
) {
    // Never print tokens (logs, crash reports).
    override fun toString(): String = "OAuthTokens(expiresIn=$expiresInSeconds, hasRefreshToken=${refreshToken != null}, scope=$scope)"
}

/** Google returned an OAuth error, e.g. `invalid_grant` when the refresh token was revoked. */
class OAuthException(val error: String, val description: String?, val httpStatus: Int) :
    IOException("OAuth error $error (HTTP $httpStatus)${description?.let { ": $it" } ?: ""}") {
    /** The refresh token no longer works: the user has to sign in again. */
    val requiresSignIn: Boolean get() = error == "invalid_grant"
}

/**
 * Token endpoint calls for the desktop *installed app* client. Google requires the client secret of a
 * "Desktop app" client even with PKCE; it is not confidential for installed apps.
 */
class OAuthTokenClient(
    private val config: DesktopOAuthConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val tokenEndpoint: String = GoogleOAuth.TOKEN_ENDPOINT
) {

    fun exchangeCode(code: String, redirectUri: String, pkce: Pkce): OAuthTokens = post(
        FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", pkce.verifier)
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .build()
    )

    fun refresh(refreshToken: String): OAuthTokens = post(
        FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .build()
    )

    private fun post(body: FormBody): OAuthTokens {
        val request = Request.Builder().url(tokenEndpoint).post(body).build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful || json == null || !json.has("access_token")) {
                throw OAuthException(
                    error = json?.optString("error")?.takeIf { it.isNotEmpty() } ?: "http_${response.code}",
                    description = json?.optString("error_description")?.takeIf { it.isNotEmpty() },
                    httpStatus = response.code
                )
            }
            return OAuthTokens(
                accessToken = json.getString("access_token"),
                expiresInSeconds = json.optLong("expires_in", 3600L),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() },
                scope = json.optString("scope").takeIf { it.isNotEmpty() }
            )
        }
    }
}
