package com.drivecinema.desktop.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopbackReceiverTest {

    private val http = OkHttpClient()

    @Test
    fun listensOnLoopbackAndReturnsTheRedirectQuery() = runBlocking {
        LoopbackReceiver.start().use { receiver ->
            assertTrue(receiver.redirectUri.startsWith("http://127.0.0.1:"))
            val result = async { receiver.awaitRedirectQuery(timeoutMillis = 5_000) }

            // The browser asks for the favicon first: ignored, still waiting.
            http.newCall(Request.Builder().url("${receiver.redirectUri}/favicon.ico").build()).execute().use {
                assertEquals(404, it.code)
            }
            http.newCall(Request.Builder().url("${receiver.redirectUri}/?state=s1&code=abc").build()).execute().use {
                assertEquals(200, it.code)
                assertTrue(it.body!!.string().contains("You can close this tab"))
            }

            assertEquals("state=s1&code=abc", result.await())
        }
    }

    @Test(expected = kotlinx.coroutines.TimeoutCancellationException::class)
    fun timesOutWhenTheBrowserNeverComesBack() = runBlocking<Unit> {
        LoopbackReceiver.start().use { it.awaitRedirectQuery(timeoutMillis = 200) }
    }
}
