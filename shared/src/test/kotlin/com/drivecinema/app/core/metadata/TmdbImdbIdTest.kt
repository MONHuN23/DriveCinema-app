package com.drivecinema.app.core.metadata

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `TmdbClient.getTvImdbId` (Milestone O: the intro services know shows by IMDb ID). */
class TmdbImdbIdTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(key: String = "test_key") =
        TmdbClient(apiKey = key, baseUrl = server.url("/3").toString().removeSuffix("/"))

    @Test
    fun readsTheShowsImdbId() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":63247,"imdb_id":"tt0475784","tvdb_id":296762}"""))
        assertEquals("tt0475784", client().getTvImdbId(63247).getOrNull())
        val request = server.takeRequest()
        assertEquals("/3/tv/63247/external_ids", request.requestUrl!!.encodedPath)
        assertEquals("test_key", request.requestUrl!!.queryParameter("api_key"))
    }

    @Test
    fun aMissingOrOddImdbIdIsNull() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":1,"imdb_id":null}"""))
        server.enqueue(MockResponse().setBody("""{"id":1,"imdb_id":""}"""))
        server.enqueue(MockResponse().setBody("""{"id":1,"imdb_id":"nm0000001"}"""))
        repeat(3) {
            val result = client().getTvImdbId(1)
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull())
        }
    }

    @Test
    fun anHttpErrorIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client().getTvImdbId(1).isFailure)
    }

    @Test
    fun aBearerTokenGoesInTheHeader() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"imdb_id":"tt0098936"}"""))
        val token = "eyJ" + "a".repeat(60)
        assertEquals("tt0098936", client(token).getTvImdbId(1920).getOrNull())
        val request = server.takeRequest()
        assertEquals("Bearer $token", request.getHeader("Authorization"))
        assertNull(request.requestUrl!!.queryParameter("api_key"))
    }

    @Test
    fun withoutAKeyNothingIsSent() = runBlocking {
        val result = TmdbClient(apiKey = null).getTvImdbId(1)
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertEquals(0, server.requestCount)
    }
}
