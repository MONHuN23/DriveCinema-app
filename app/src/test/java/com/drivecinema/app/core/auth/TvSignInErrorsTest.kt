package com.drivecinema.app.core.auth

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task J.6: TV "Sign in with Google" failures must say why, not just "10: ". */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TvSignInErrorsTest {

    private fun apiError(code: Int) = ApiException(Status(code))

    @Test
    fun developerErrorPointsToTheUnregisteredSigningKey() {
        val message = TvSignInErrors.describe(apiError(10))
        assertTrue(message, message.contains("error 10"))
        assertTrue(message, message.contains("SHA-1"))
        assertTrue(message, message.contains("Pair with Phone"))
    }

    @Test
    fun cancelledSignInIsNotReportedAsAFailure() {
        assertEquals("Sign-in was cancelled.", TvSignInErrors.describe(apiError(12501)))
    }

    @Test
    fun networkAndGenericSignInFailuresNameTheirCode() {
        assertTrue(TvSignInErrors.describe(apiError(7)).contains("error 7"))
        assertTrue(TvSignInErrors.describe(apiError(12500)).contains("error 12500"))
    }

    @Test
    fun unknownCodesStillShowTheCode() {
        val message = TvSignInErrors.describe(apiError(8))
        assertTrue(message, message.contains("error 8"))
    }

    @Test
    fun nonApiErrorsKeepTheirMessage() {
        assertEquals("Sign in failed: boom", TvSignInErrors.describe(IllegalStateException("boom")))
    }
}
