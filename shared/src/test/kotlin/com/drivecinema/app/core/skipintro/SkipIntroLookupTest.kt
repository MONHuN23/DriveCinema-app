package com.drivecinema.app.core.skipintro

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SkipIntroLookupTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private var skipDbBody: MockResponse = MockResponse().setBody("""{"segments":{"intro":null}}""")
    private var introDbBody: MockResponse = MockResponse().setBody("""{"intro":null,"no_intro":false}""")
    private val skipDbCalls = AtomicInteger()
    private val introDbCalls = AtomicInteger()
    private val tmdbCalls = AtomicInteger()
    private var imdbResult: Result<String?> = Result.success("tt14824792")
    private var clock = 1_800_000_000_000L
    private val cacheFile get() = File(folder.root, "skip-intro-cache.json")

    // Ted S1E2 from the O.0 spike: unknown in SkipDB, timed on the same cut in IntroDB.
    private val ted = SkipIntroRequest(tmdbShowId = 201834, season = 1, episode = 2, fileLengthMs = 2_361_942)
    private val tedIntroDb = """{"intro":{"start_ms":0,"end_ms":27000,"submission_count":1},
        "outro":{"start_ms":2291000,"end_ms":2362000},"no_intro":false}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl!!.encodedPath) {
                "/api/segments" -> skipDbBody.also { skipDbCalls.incrementAndGet() }
                "/segments" -> introDbBody.also { introDbCalls.incrementAndGet() }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun lookup(withIntroDb: Boolean = true): SkipIntroLookup {
        val http = SkipIntroHttp.newClient("DriveCinema/test")
        val base = server.url("/").toString().removeSuffix("/")
        return SkipIntroLookup(
            imdbIdOf = { tmdbCalls.incrementAndGet(); imdbResult },
            skipDb = SkipDbClient(http, base, now = { clock }),
            introDb = if (withIntroDb) IntroDbClient(http, base, now = { clock }) else null,
            cache = SkipIntroCache(cacheFile, now = { clock })
        )
    }

    @Test
    fun skipDbFirstThenIntroDbWithTheSameCut() = runBlocking {
        introDbBody = MockResponse().setBody(tedIntroDb)
        assertEquals(IntroTimes(0, 27_000), lookup().find(ted))
        assertEquals(1, skipDbCalls.get())
        assertEquals(1, introDbCalls.get())
    }

    @Test
    fun aUsableSkipDbAnswerNeverAsksIntroDb() = runBlocking {
        skipDbBody = MockResponse().setBody("""{"segments":{"intro":{"start_ms":1000,"end_ms":28000,"match":"exact","confidence":0.9}}}""")
        assertEquals(IntroTimes(1_000, 28_000), lookup().find(ted))
        assertEquals(0, introDbCalls.get())
    }

    @Test
    fun withoutIntroDbOnlySkipDbIsAsked() = runBlocking {
        introDbBody = MockResponse().setBody(tedIntroDb)
        assertNull(lookup(withIntroDb = false).find(ted))
        assertEquals(0, introDbCalls.get())
    }

    @Test
    fun theAnswerIsCachedAcrossInstances() = runBlocking {
        introDbBody = MockResponse().setBody(tedIntroDb)
        lookup().find(ted)
        assertEquals(IntroTimes(0, 27_000), lookup().find(ted))
        assertEquals(1, skipDbCalls.get())
        assertEquals(1, tmdbCalls.get())
    }

    @Test
    fun anotherLengthIsAnotherEntry() = runBlocking {
        introDbBody = MockResponse().setBody(tedIntroDb)
        val lookup = lookup()
        lookup.find(ted)
        assertNull("a different cut: IntroDB's outro doesn't match", lookup.find(ted.copy(fileLengthMs = 2_400_000)))
        assertEquals(2, skipDbCalls.get())
        assertEquals(1, tmdbCalls.get())
    }

    @Test
    fun twoCallsAtOnceShareOneLookup() = runBlocking {
        skipDbBody = MockResponse().setBody("""{"segments":{"intro":null}}""").setBodyDelay(300, TimeUnit.MILLISECONDS)
        introDbBody = MockResponse().setBody(tedIntroDb)
        val lookup = lookup()
        val results = listOf(async { lookup.find(ted) }, async { lookup.find(ted) }).awaitAll()
        assertEquals(listOf(IntroTimes(0, 27_000), IntroTimes(0, 27_000)), results)
        assertEquals(1, tmdbCalls.get())
        assertEquals(1, skipDbCalls.get())
        assertEquals(1, introDbCalls.get())
    }

    @Test
    fun aSkipDbErrorIsNotCachedAndIsRetried() = runBlocking {
        skipDbBody = MockResponse().setResponseCode(503)
        introDbBody = MockResponse().setBody(tedIntroDb)
        assertEquals("IntroDB still answers", IntroTimes(0, 27_000), lookup().find(ted))
        skipDbBody = MockResponse().setBody("""{"segments":{"intro":null}}""")
        lookup().find(ted)
        assertEquals(2, skipDbCalls.get())
    }

    @Test
    fun a429PausesThatServiceUntilItsTime() = runBlocking {
        skipDbBody = MockResponse().setResponseCode(429).setHeader("Retry-After", "60")
        introDbBody = MockResponse().setResponseCode(503)
        val lookup = lookup()
        assertNull(lookup.find(ted))
        assertNull(lookup.find(ted))
        assertEquals("paused: no second request", 1, skipDbCalls.get())

        clock += 60_000
        skipDbBody = MockResponse().setBody("""{"segments":{"intro":null}}""")
        lookup.find(ted)
        assertEquals(2, skipDbCalls.get())
    }

    @Test
    fun aShowWithoutAnImdbIdSendsNothingToTheIntroServices() = runBlocking {
        imdbResult = Result.success(null)
        assertNull(lookup().find(ted))
        assertNull(lookup().find(ted))
        assertEquals("'none' is cached", 1, tmdbCalls.get())
        assertEquals(0, skipDbCalls.get())
    }

    @Test
    fun aTmdbErrorIsNotCached() = runBlocking {
        imdbResult = Result.failure(java.io.IOException("offline"))
        assertNull(lookup().find(ted))
        imdbResult = Result.success("tt14824792")
        lookup().find(ted)
        assertEquals(2, tmdbCalls.get())
        assertEquals(1, skipDbCalls.get())
    }

    @Test
    fun specialsAndUnknownLengthsAreNeverLookedUp() = runBlocking {
        val lookup = lookup()
        assertNull(lookup.find(ted.copy(season = 0)))
        assertNull(lookup.find(ted.copy(episode = 0)))
        assertNull(lookup.find(ted.copy(fileLengthMs = 0)))
        assertNull(lookup.find(ted.copy(tmdbShowId = 0)))
        assertEquals(0, tmdbCalls.get())
        assertEquals(0, skipDbCalls.get())
    }
}
