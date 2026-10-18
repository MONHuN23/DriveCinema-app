package com.drivecinema.desktop.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleOAuthTest {

    @Test
    fun pkceChallengeMatchesRfc7636Example() {
        // RFC 7636 Appendix B
        val pkce = Pkce.fromVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.challenge)
    }

    @Test
    fun generatedPkceIsUrlSafeAndRandom() {
        val a = Pkce.generate()
        val b = Pkce.generate()
        assertEquals(43, a.verifier.length)
        assertTrue(a.verifier.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(a.verifier, b.verifier)
    }

    @Test
    fun authorizationUrlCarriesPkceScopeAndOfflineAccess() {
        val pkce = Pkce.fromVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        val url = GoogleOAuth.authorizationUrl("client-123", "http://127.0.0.1:5555", pkce, "state-xyz").toHttpUrl()

        assertEquals("accounts.google.com", url.host)
        assertEquals("client-123", url.queryParameter("client_id"))
        assertEquals("http://127.0.0.1:5555", url.queryParameter("redirect_uri"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(GoogleOAuth.DRIVE_READONLY_SCOPE, url.queryParameter("scope"))
        assertEquals(pkce.challenge, url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("state-xyz", url.queryParameter("state"))
        assertEquals("offline", url.queryParameter("access_type"))
        assertEquals("consent", url.queryParameter("prompt"))
    }

    @Test
    fun redirectWithCodeAndMatchingState() {
        assertEquals(
            AuthRedirect.Code("4/0Ab_c d"),
            GoogleOAuth.parseRedirect("state=s1&code=4%2F0Ab_c+d&scope=x", "s1")
        )
    }

    @Test
    fun redirectWithWrongStateIsRejected() {
        assertEquals(AuthRedirect.StateMismatch, GoogleOAuth.parseRedirect("state=other&code=abc", "s1"))
        assertEquals(AuthRedirect.StateMismatch, GoogleOAuth.parseRedirect("code=abc", "s1"))
    }

    @Test
    fun redirectWithErrorIsDenied() {
        assertEquals(
            AuthRedirect.Denied("access_denied", null),
            GoogleOAuth.parseRedirect("error=access_denied&state=s1", "s1")
        )
    }

    @Test
    fun redirectWithoutCodeIsInvalid() {
        assertTrue(GoogleOAuth.parseRedirect("state=s1", "s1") is AuthRedirect.Invalid)
        assertTrue(GoogleOAuth.parseRedirect(null, "s1") is AuthRedirect.StateMismatch)
    }
}
