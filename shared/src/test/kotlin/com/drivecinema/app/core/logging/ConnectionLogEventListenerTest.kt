package com.drivecinema.app.core.logging

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Task M.0 / F-12: one log line per new connection, none for a reused pooled connection. */
class ConnectionLogEventListenerTest {

    private val originalSink = AppLog.sink
    private val lines = mutableListOf<String>()
    private val server = MockWebServer()

    @Before
    fun setUp() {
        AppLog.sink = LogSink { _, tag, message, _ -> synchronized(lines) { lines += "$tag $message" } }
        server.start()
    }

    @After
    fun tearDown() {
        AppLog.sink = originalSink
        server.shutdown()
    }

    private fun get(client: OkHttpClient) {
        server.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(server.url("/files")).build()).execute().use { it.body?.string() }
    }

    @Test
    fun logsANewConnectionOnceAndNotWhenItIsReused() {
        val client = OkHttpClient.Builder().eventListenerFactory(ConnectionLogEventListener.factory("drive")).build()
        val before = Perf.counter(ConnectionLogEventListener.COUNTER).get()

        get(client)
        get(client)

        val connects = synchronized(lines) { lines.filter { it.startsWith("Perf net.connect ") } }
        assertEquals(connects.toString(), 1, connects.size)
        assertTrue(connects.single(), connects.single().contains(" client=drive host=${server.hostName} protocol=http/1.1"))
        assertEquals(before + 1, Perf.counter(ConnectionLogEventListener.COUNTER).get())
    }

    @Test
    fun separateClientsEachOpenTheirOwnConnection() {
        // Today each component builds its own OkHttpClient (F-12), so each pays its own handshake.
        get(OkHttpClient.Builder().eventListenerFactory(ConnectionLogEventListener.factory("drive-crawl")).build())
        get(OkHttpClient.Builder().eventListenerFactory(ConnectionLogEventListener.factory("tmdb")).build())

        val clients = synchronized(lines) { lines.filter { it.startsWith("Perf net.connect ") } }
            .map { it.substringAfter("client=").substringBefore(' ') }
        assertEquals(listOf("drive-crawl", "tmdb"), clients)
    }
}
