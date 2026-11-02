package com.drivecinema.app.core.skipintro

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SkipIntroClientsTest {

    private lateinit var server: MockWebServer
    private val now = 1_800_000_000_000L
    private val http = SkipIntroHttp.newClient("DriveCinema/test")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun skipDb() = SkipDbClient(http, server.url("/").toString().removeSuffix("/"), now = { now })
    private fun introDb() = IntroDbClient(http, server.url("/").toString().removeSuffix("/"), now = { now })

    @Test
    fun skipDbSendsTheLengthInSecondsAndOnlyTheIntroType() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"segments":{"intro":null}}"""))
        val result = skipDb().intro("tt0475784", 1, 2, 3515)
        assertEquals(ServiceResult.Answer(SkipDbAnswer.Unknown), result)

        val request = server.takeRequest()
        assertEquals("/api/segments", request.requestUrl!!.encodedPath)
        assertEquals("tt0475784", request.requestUrl!!.queryParameter("imdb_id"))
        assertEquals("1", request.requestUrl!!.queryParameter("season"))
        assertEquals("2", request.requestUrl!!.queryParameter("episode"))
        assertEquals("intro", request.requestUrl!!.queryParameter("type"))
        assertEquals("3515", request.requestUrl!!.queryParameter("duration"))
        assertEquals("DriveCinema/test", request.getHeader("User-Agent"))
        assertNull("never a token", request.getHeader("Authorization"))
    }

    @Test
    fun introDbSendsNoLength() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"intro":null,"no_intro":true}"""))
        assertEquals(ServiceResult.Answer(IntroDbAnswer.ConfirmedNone), introDb().intro("tt6741278", 1, 1))

        val request = server.takeRequest()
        assertEquals("/segments", request.requestUrl!!.encodedPath)
        assertEquals(setOf("imdb_id", "season", "episode"), request.requestUrl!!.queryParameterNames)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun rateLimitUsesResetAtFromTheBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"slow down","limit":60,"reset_at":"2027-01-15T08:00:30Z"}"""))
        val result = introDb().intro("tt0182576", 24, 1)
        assertEquals(ServiceResult.RateLimited(java.time.Instant.parse("2027-01-15T08:00:30Z").toEpochMilli()), result)
    }

    @Test
    fun rateLimitFallsBackToRetryAfterThenOneMinute() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        assertEquals(ServiceResult.RateLimited(now + 120_000), skipDb().intro("tt1", 1, 1, 100))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        assertEquals(ServiceResult.RateLimited(now + 60_000), skipDb().intro("tt1", 1, 1, 100))
    }

    @Test
    fun rateLimitIsNeverLongerThanADay() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"reset_at":${(now + 7L * 24 * 3600 * 1000) / 1000}}"""))
        assertEquals(ServiceResult.RateLimited(now + 24L * 3600 * 1000), introDb().intro("tt1", 1, 1))
    }

    @Test
    fun serverErrorsAndMalformedBodiesFail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(ServiceResult.Failed, skipDb().intro("tt1", 1, 1, 100))
        server.enqueue(MockResponse().setBody("<html>maintenance</html>"))
        assertEquals(ServiceResult.Failed, skipDb().intro("tt1", 1, 1, 100))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))
        assertEquals(ServiceResult.Failed, introDb().intro("tt1", 1, 1))
    }

    @Test
    fun oversizedBodiesAreRejected() = runBlocking {
        val padding = "x".repeat((SkipIntroHttp.MAX_BODY_BYTES + 10).toInt())
        server.enqueue(MockResponse().setBody("""{"segments":{"intro":null},"pad":"$padding"}"""))
        assertEquals(ServiceResult.Failed, skipDb().intro("tt1", 1, 1, 100))
    }

    @Test
    fun redirectsAreNotFollowed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/elsewhere")))
        server.enqueue(MockResponse().setBody("""{"segments":{"intro":null}}"""))
        assertEquals(ServiceResult.Failed, skipDb().intro("tt1", 1, 1, 100))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aServerThatNeverAnswersTimesOut() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val started = System.nanoTime()
        assertEquals(ServiceResult.Failed, skipDb().intro("tt1", 1, 1, 100))
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        assertTrue("gave up after $seconds s", seconds < 6)
    }
}
