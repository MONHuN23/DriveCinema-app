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
    suspend fun signIn(onAuthorizationUrl: (String) -> Unit): DesktopSession {
        if (!config.isConfigured) throw SignInException("The desktop Google sign-in is not configured (see DESKTOP.md).")
        val pkce = Pkce.generate()
        val state = Pkce.randomToken(16)
        startReceiver().use { receiver ->
            onAuthorizationUrl(GoogleOAuth.authorizationUrl(config.clientId, receiver.redirectUri, pkce, state))

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
                clock = clock
            )
            val user = driveGateway.getAboutUser(tokens.accessToken).getOrElse { e ->
                throw SignInException("Signed in, but the Drive account could not be read: ${e.message}", e)
            }
            return DesktopSession(user, tokenProvider)
        }
    }
}
