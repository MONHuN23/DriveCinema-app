package com.drivecinema.app.core.player

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TokenRefreshInterceptorTest {

    @Test
    fun testInterceptorRefreshesTokenOn401AndRetries() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        server.enqueue(MockResponse().setResponseCode(206).setBody("video bytes"))
        server.start()

        var refreshed = false
        val tokenProvider = object : TokenProvider {
            override fun getAccessToken(): String = if (!refreshed) "expired_token" else "new_token"
            override fun forceRefresh(): String {
                refreshed = true
                return "new_token"
            }
        }

        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(TokenRefreshInterceptor(tokenProvider))
            .build()

        val response = client.newCall(Request.Builder().url(server.url("/stream")).build()).execute()
        assertEquals(206, response.code)

        val firstReq = server.takeRequest()
        assertEquals("Bearer expired_token", firstReq.getHeader("Authorization"))

        val secondReq = server.takeRequest()
        assertEquals("Bearer new_token", secondReq.getHeader("Authorization"))

        server.shutdown()
    }

    @Test
    fun testInterceptorFollowsCrossDomainRedirectAndPreservesAuthorization() {
        val server1 = MockWebServer()
        val server2 = MockWebServer()
        server1.start()
        server2.start()

        server1.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server2.url("/download/content").toString())
        )
        server2.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setBody("video stream chunk")
        )

        val tokenProvider = object : TokenProvider {
            override fun getAccessToken(): String = "auth_valid_token"
            override fun forceRefresh(): String = "auth_valid_token"
        }

        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(TokenRefreshInterceptor(tokenProvider))
            .build()

        val response = client.newCall(Request.Builder().url(server1.url("/api/files/123?alt=media")).build()).execute()
        assertEquals(206, response.code)

        val req1 = server1.takeRequest()
        assertEquals("Bearer auth_valid_token", req1.getHeader("Authorization"))

        val req2 = server2.takeRequest()
        assertEquals("Bearer auth_valid_token", req2.getHeader("Authorization"))
        assertEquals("/download/content", req2.path)

        server1.shutdown()
        server2.shutdown()
    }

    @Test
    fun testInterceptorPreservesRangeHeaderAcrossRedirect() {
        val server1 = MockWebServer()
        val server2 = MockWebServer()
        server1.start()
        server2.start()

        server1.enqueue(
            MockResponse()
                .setResponseCode(307)
                .setHeader("Location", server2.url("/stream/media.mkv").toString())
        )
        server2.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 5000-10000/100000")
                .setBody("partial video content")
        )

        val tokenProvider = object : TokenProvider {
            override fun getAccessToken(): String = "range_token"
            override fun forceRefresh(): String = "range_token"
        }

        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(TokenRefreshInterceptor(tokenProvider))
            .build()

        val request = Request.Builder()
            .url(server1.url("/files/xyz?alt=media"))
            .header("Range", "bytes=5000-10000")
            .build()

        val response = client.newCall(request).execute()
        assertEquals(206, response.code)

        val req1 = server1.takeRequest()
        assertEquals("bytes=5000-10000", req1.getHeader("Range"))

        val req2 = server2.takeRequest()
        assertEquals("bytes=5000-10000", req2.getHeader("Range"))
        assertEquals("Bearer range_token", req2.getHeader("Authorization"))

        server1.shutdown()
        server2.shutdown()
    }

    @Test
    fun testInterceptorRefreshesImmediatelyIfInitialTokenIsEmpty() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        server.start()

        var refreshed = false
        val tokenProvider = object : TokenProvider {
            override fun getAccessToken(): String = if (refreshed) "proactive_token" else ""
            override fun forceRefresh(): String {
                refreshed = true
                return "proactive_token"
            }
        }

        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(TokenRefreshInterceptor(tokenProvider))
            .build()

        val response = client.newCall(Request.Builder().url(server.url("/stream")).build()).execute()
        assertEquals(200, response.code)

        val req = server.takeRequest()
        assertEquals("Bearer proactive_token", req.getHeader("Authorization"))

        server.shutdown()
    }
}
