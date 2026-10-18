package com.drivecinema.app.core.player

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Report F-02: the access token must only reach real Google hosts, and only over HTTPS. */
class TokenHostCheckTest {

    @Test
    fun realGoogleHostsAreTrusted() {
        listOf("www.googleapis.com", "googleapis.com", "doc-0s-8c-docs.googleusercontent.com", "accounts.google.com",
            "WWW.GOOGLEAPIS.COM", "localhost", "127.0.0.1")
            .forEach { assertTrue(it, TokenRefreshInterceptor.isTrustedGoogleHost(it)) }
    }

    @Test
    fun lookAlikeHostsAreNotTrusted() {
        listOf("evilgoogle.com", "notgoogleapis.com", "xgoogleusercontent.com", "googleapis.com.evil.net", "google.com.evil")
            .forEach { assertFalse(it, TokenRefreshInterceptor.isTrustedGoogleHost(it)) }
    }

    /** Sends a redirect from Drive to [target] and returns the Authorization header the redirect request carried. */
    private fun authorizationSentTo(target: String): String? {
        val seen = mutableListOf<Request>()
        // Stands in for the network: Drive answers with a redirect, every other host with 200.
        val fakeNetwork = Interceptor { chain ->
            val request = chain.request()
            seen += request
            val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).body("".toResponseBody())
            if (request.url.host == "www.googleapis.com") builder.code(302).message("Found").header("Location", target).build()
            else builder.code(200).message("OK").build()
        }
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(TokenRefreshInterceptor(object : TokenProvider {
                override fun getAccessToken() = "secret_token"
                override fun forceRefresh() = "secret_token"
            }))
            .addInterceptor(fakeNetwork)
            .build()
        client.newCall(Request.Builder().url("https://www.googleapis.com/drive/v3/files/x?alt=media").build()).execute().close()
        assertEquals("Bearer secret_token", seen.first().header("Authorization"))
        return seen.last().also { assertEquals(target.substringBefore('?'), it.url.toString().substringBefore('?')) }
            .header("Authorization")
    }

    @Test
    fun redirectToNonGoogleHostGetsNoToken() {
        assertNull(authorizationSentTo("https://cdn.example.com/file"))
    }

    @Test
    fun redirectToLookAlikeHostGetsNoToken() {
        assertNull(authorizationSentTo("https://evilgoogle.com/file"))
    }

    @Test
    fun redirectOverPlainHttpGetsNoToken() {
        assertNull(authorizationSentTo("http://doc-0s.googleusercontent.com/file"))
    }

    @Test
    fun redirectToGoogleDownloadHostOverHttpsKeepsToken() {
        assertEquals("Bearer secret_token", authorizationSentTo("https://doc-0s.googleusercontent.com/file"))
    }
}
