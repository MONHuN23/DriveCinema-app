package com.drivecinema.app.core.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest

class GoogleAuthManager {

    companion object {
        const val TV_ACCOUNT_PREFIX = "tv_account:"
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        /** Milestone Q: the hidden app folder, asked only when "Sync with my devices" is turned on. */
        const val DRIVE_APPDATA_SCOPE = com.drivecinema.app.core.devicesync.AppDataClient.SCOPE
        val SIGN_IN_SCOPES = listOf(DRIVE_READONLY_SCOPE)
        val DEVICE_SYNC_SCOPES = listOf(DRIVE_READONLY_SCOPE, DRIVE_APPDATA_SCOPE)
    }

    val serviceConfig: AuthorizationServiceConfiguration = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    /**
     * The phone's sign-in. Milestone Q: with [scopes] beyond read-only (turning device sync on), the grant is added to
     * the existing one (`include_granted_scopes`) and [loginHint] preselects the signed-in account.
     */
    fun buildAuthorizationRequest(
        clientId: String,
        scopes: List<String> = SIGN_IN_SCOPES,
        loginHint: String? = null
    ): AuthorizationRequest {
        val redirectUri = Uri.parse("com.drivecinema.app:/oauth2redirect")
        val extraParams = buildMap {
            put("access_type", "offline")
            if (scopes != SIGN_IN_SCOPES) put("include_granted_scopes", "true")
        }

        return AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
            .setScopes(scopes)
            .setPrompt("consent")
            .setAdditionalParameters(extraParams)
            .apply { if (!loginHint.isNullOrEmpty()) setLoginHint(loginHint) }
            .build()
    }

    fun createAuthRequestIntent(
        context: Context,
        clientId: String,
        scopes: List<String> = SIGN_IN_SCOPES,
        loginHint: String? = null
    ): Intent {
        val authService = AuthorizationService(context)
        val request = buildAuthorizationRequest(clientId, scopes, loginHint)
        return try {
            authService.getAuthorizationRequestIntent(request)
        } finally {
            authService.dispose()
        }
    }

    fun handleAuthResponse(
        context: Context,
        intent: Intent,
        onComplete: (Result<Pair<String, String>>) -> Unit
    ) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            onComplete(Result.failure(exception))
            return
        }

        if (response != null) {
            val authService = AuthorizationService(context)
            authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
                authService.dispose()
                if (tokenResponse != null) {
                    val accessToken = tokenResponse.accessToken ?: ""
                    val refreshToken = tokenResponse.refreshToken ?: ""
                    if (accessToken.isNotEmpty() && refreshToken.isNotEmpty()) {
                        onComplete(Result.success(Pair(accessToken, refreshToken)))
                    } else {
                        onComplete(Result.failure(IllegalStateException("Missing tokens in token response")))
                    }
                } else {
                    onComplete(Result.failure(tokenEx ?: IllegalStateException("Unknown token exchange error")))
                }
            }
        } else {
            onComplete(Result.failure(IllegalStateException("No authorization response found in intent")))
        }
    }

    // --- Android TV Google Play Services Native Sign-In ---

    fun buildTvSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_READONLY_SCOPE))
            .build()
    }

    fun getTvSignInIntent(context: Context): Intent {
        val client = GoogleSignIn.getClient(context, buildTvSignInOptions())
        return client.signInIntent
    }

    fun handleTvSignInResult(
        context: Context,
        data: Intent?,
        onComplete: (Result<Pair<String, String>>) -> Unit
    ) {
        if (data == null) {
            onComplete(Result.failure(IllegalStateException("No data returned from TV sign-in")))
            return
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email ?: ""
            if (email.isEmpty()) {
                onComplete(Result.failure(IllegalStateException("Google account has no email address")))
                return
            }
            val targetAccount = account.account ?: Account(email, "com.google")
            fetchTvAccessToken(context, targetAccount, onComplete)
        } catch (e: Exception) {
            onComplete(Result.failure(e))
        }
    }

    fun fetchTvAccessToken(
        context: Context,
        account: Account,
        onComplete: (Result<Pair<String, String>>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scopeStr = "oauth2:$DRIVE_READONLY_SCOPE"
                val token = GoogleAuthUtil.getToken(context, account, scopeStr)
                if (!token.isNullOrEmpty()) {
                    onComplete(Result.success(Pair(token, "$TV_ACCOUNT_PREFIX${account.name}")))
                } else {
                    onComplete(Result.failure(IllegalStateException("Received empty access token for TV")))
                }
            } catch (recoverable: UserRecoverableAuthException) {
                // Return recoverable exception so UI can launch the consent intent
                onComplete(Result.failure(recoverable))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            }
        }
    }

    fun invalidateTvToken(context: Context, token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (_: Exception) {}
    }

    fun refreshAccessToken(
        context: Context,
        clientId: String,
        refreshToken: String,
        onComplete: (Result<String>) -> Unit
    ) {
        if (refreshToken.startsWith(TV_ACCOUNT_PREFIX)) {
            val email = refreshToken.removePrefix(TV_ACCOUNT_PREFIX)
            val account = Account(email, "com.google")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val scopeStr = "oauth2:$DRIVE_READONLY_SCOPE"
                    val token = GoogleAuthUtil.getToken(context, account, scopeStr)
                    if (!token.isNullOrEmpty()) {
                        onComplete(Result.success(token))
                    } else {
                        onComplete(Result.failure(IllegalStateException("Failed to refresh TV access token")))
                    }
                } catch (e: Exception) {
                    onComplete(Result.failure(e))
                }
            }
            return
        }

        // Standard AppAuth refresh flow (Phone)
        val authService = AuthorizationService(context)
        val tokenRequest = TokenRequest.Builder(serviceConfig, clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()

        authService.performTokenRequest(tokenRequest) { tokenResponse, tokenEx ->
            authService.dispose()
            if (tokenResponse != null && tokenResponse.accessToken != null) {
                onComplete(Result.success(tokenResponse.accessToken!!))
            } else {
                onComplete(Result.failure(tokenEx ?: IllegalStateException("Token refresh failed")))
            }
        }
    }
}
