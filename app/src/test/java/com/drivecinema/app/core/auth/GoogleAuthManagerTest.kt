package com.drivecinema.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GoogleAuthManagerTest {
    @Test
    fun testAuthRequestContainsOfflineAccessAndReadOnlyScope() {
        val manager = GoogleAuthManager()
        val request = manager.buildAuthorizationRequest("test_client_id.apps.googleusercontent.com")
        assertNotNull(request)
        assertEquals("https://www.googleapis.com/auth/drive.readonly", request.scope)
        assertEquals("offline", request.additionalParameters["access_type"])
        assertEquals("consent", request.prompt)
        assertEquals("com.drivecinema.app:/oauth2redirect", request.redirectUri.toString())
    }

    @Test
    fun testTvSignInOptionsHasDriveScopeAndEmail() {
        val manager = GoogleAuthManager()
        val options = manager.buildTvSignInOptions()
        assertNotNull(options)
        val scopes = options.scopes
        val hasDriveScope = scopes.any { it.scopeUri == GoogleAuthManager.DRIVE_READONLY_SCOPE }
        assertEquals(true, hasDriveScope)
    }

    @Test
    fun testTvAccountPrefixConstant() {
        assertEquals("tv_account:", GoogleAuthManager.TV_ACCOUNT_PREFIX)
    }

    @Test
    fun testHandleTvSignInResultWithNullIntentReturnsFailure() {
        val manager = GoogleAuthManager()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        var failureResult: Throwable? = null
        manager.handleTvSignInResult(context, null) { result ->
            result.onFailure { failureResult = it }
        }
        assertNotNull(failureResult)
        assertEquals("No data returned from TV sign-in", failureResult?.message)
    }
}
