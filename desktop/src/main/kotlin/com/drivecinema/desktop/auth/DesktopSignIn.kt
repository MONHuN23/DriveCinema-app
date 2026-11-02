package com.drivecinema.desktop.auth

import com.drivecinema.app.core.model.DriveUser
import com.drivecinema.app.core.network.GoogleDriveGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A signed-in desktop session: Drive account and tokens. */
class DesktopSession(
    val user: DriveUser,
    val tokenProvider: DesktopTokenProvider
)

/** Why sign-in failed, in words for the UI. */
class SignInException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Browser sign-in: start the loopback receiver, open Google's consent page, wait for the redirect,
 * exchange the code (PKCE) and read the account name/email from Drive.
 */
class DesktopSignIn(
    private val config: DesktopOAuthConfig,
    private val tokenClient: OAuthTokenClient = OAuthTokenClient(config),
    private val driveGateway: GoogleDriveGateway = GoogleDriveGateway(),
    private val startReceiver: () -> LoopbackReceiver = LoopbackReceiver::start,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * [onAuthorizationUrl] receives the consent page URL; it opens the browser and should also show the
     * link in the UI in case no browser starts.
     */
    suspend fun signIn(onAuthorizationUrl: (String) -> Unit): DesktopSession =
        authorize(GoogleOAuth.SIGN_IN_SCOPES, loginHint = null, onAuthorizationUrl)

    /**
     * Task I.1: adds Drive write access to the [current] sign-in (asked only when the user turns import on). The
     * result replaces the current session (its refresh token covers read and write). Refused when the user picks
     * another Google account or unticks the Drive box on Google's consent page; the extra grant is then revoked.
     */
    suspend fun grantDriveWriteAccess(current: DesktopSession, onAuthorizationUrl: (String) -> Unit): DesktopSession {
        val expected = current.user.emailAddress
        val session = authorize(GoogleOAuth.IMPORT_SCOPES, loginHint = expected, onAuthorizationUrl)
        val problem = when {
            !session.user.emailAddress.equals(expected, ignoreCase = true) ->
                "Write access has to come from $expected, the account signed in to DriveCinema, not ${session.user.emailAddress}."
            !session.tokenProvider.hasDriveWriteAccess ->
                "Google did not grant write access (the Drive permission was not ticked). Import stays off."
            else -> null
        } ?: return session
        withContext(Dispatchers.IO) { runCatching { tokenClient.revoke(session.tokenProvider.refreshToken) } }
        throw SignInException(problem)
    }

    /**
     * Milestone Q: adds the Drive app folder (device sync) to the [current] sign-in, keeping write access when it has
     * it. Same rules as [grantDriveWriteAccess]: another account or an unticked box is refused and the grant revoked.
     */
    suspend fun grantDeviceSync(current: DesktopSession, onAuthorizationUrl: (String) -> Unit): DesktopSession {
        val expected = current.user.emailAddress
        val scopes = GoogleOAuth.deviceSyncScopes(withWriteAccess = current.tokenProvider.hasDriveWriteAccess)
        val session = authorize(scopes, loginHint = expected, onAuthorizationUrl)
        val problem = when {
            !session.user.emailAddress.equals(expected, ignoreCase = true) ->
                "Sync has to be turned on with $expected, the account signed in to DriveCinema, not ${session.user.emailAddress}."
            !GoogleOAuth.mayHaveAppDataAccess(session.tokenProvider.grantedScope) ->
                "Google did not give DriveCinema its app folder (the permission was not ticked). Sync stays off."
            else -> null
        } ?: return session
        withContext(Dispatchers.IO) { runCatching { tokenClient.revoke(session.tokenProvider.refreshToken) } }
        throw SignInException(problem)
    }

    private suspend fun authorize(
        scopes: List<String>,
        loginHint: String?,
        onAuthorizationUrl: (String) -> Unit
    ): DesktopSession {
        if (!config.isConfigured) throw SignInException("The desktop Google sign-in is not configured (see DESKTOP.md).")
        val pkce = Pkce.generate()
        val state = Pkce.randomToken(16)
        startReceiver().use { receiver ->
            onAuthorizationUrl(GoogleOAuth.authorizationUrl(config.clientId, receiver.redirectUri, pkce, state, scopes, loginHint))

            val query = try {
                receiver.awaitRedirectQuery()
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw SignInException("Sign-in timed out. Please try again.", e)
            }
            val code = when (val redirect = GoogleOAuth.parseRedirect(query, state)) {
                is AuthRedirect.Code -> redirect.code
                is AuthRedirect.Denied -> throw SignInException(
                    if (redirect.error == "access_denied") "Sign-in was cancelled." else "Google refused the sign-in (${redirect.error})."
                )
                AuthRedirect.StateMismatch -> throw SignInException("The sign-in response did not match this request. Please try again.")
                is AuthRedirect.Invalid -> throw SignInException(redirect.reason)
            }

            val tokens = withContext(Dispatchers.IO) {
                try {
                    tokenClient.exchangeCode(code, receiver.redirectUri, pkce)
                } catch (e: OAuthException) {
                    throw SignInException("Google did not accept the sign-in (${e.error}).", e)
                }
            }
            val refreshToken = tokens.refreshToken
                ?: throw SignInException("Google did not return a refresh token. Please try again.")
            val tokenProvider = DesktopTokenProvider(
                tokenClient = tokenClient,
                refreshToken = refreshToken,
                accessToken = tokens.accessToken,
                expiresAtMillis = clock() + tokens.expiresInSeconds * 1000L,
                clock = clock,
                grantedScope = tokens.scope
            )
            val user = driveGateway.getAboutUser(tokens.accessToken).getOrElse { e ->
                throw SignInException("Signed in, but the Drive account could not be read: ${e.message}", e)
            }
            return DesktopSession(user, tokenProvider)
        }
    }
}
