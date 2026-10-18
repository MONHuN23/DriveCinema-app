package com.drivecinema.app.core.auth

import com.drivecinema.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone J.5: the phone/TV sign-in client comes from local.properties, with the developer's as fallback. */
class GoogleClientConfigTest {

    @Test
    fun clientIdIsAlwaysAGoogleOAuthClientId() {
        val id = MainActivity.GOOGLE_CLIENT_ID
        assertTrue("Expected a Google OAuth client ID, got '$id'", Regex("""^\d+-[a-z0-9]+\.apps\.googleusercontent\.com$""").matches(id))
    }
}
