package com.drivecinema.app.core.auth

import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

/**
 * Readable messages for "Sign in with Google" on the TV (Play Services `GoogleSignIn`, Task J.6).
 * The raw `ApiException` message is just "10: ", which hides why sign-in failed.
 */
object TvSignInErrors {

    const val NO_RESULT = "Google sign-in closed without a result. Try again, or use Pair with Phone."

    fun describe(error: Throwable): String {
        val api = error as? ApiException ?: return "Sign in failed: ${error.message}"
        val code = api.statusCode
        return when (code) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Google rejected this app build (error 10): the signing key's SHA-1 isn't registered in the " +
                    "Google Cloud Android client for com.drivecinema.app. Use Pair with Phone for now."
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign-in was cancelled."
            CommonStatusCodes.NETWORK_ERROR -> "Can't reach Google (error 7). Check the TV's network connection."
            GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                "Google sign-in failed (error 12500). Check that Google Play services is up to date on the TV, " +
                    "or use Pair with Phone."
            else -> "Google sign-in failed (error $code: ${CommonStatusCodes.getStatusCodeString(code)})."
        }
    }
}
