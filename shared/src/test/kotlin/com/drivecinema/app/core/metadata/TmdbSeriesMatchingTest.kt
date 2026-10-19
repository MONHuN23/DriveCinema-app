package com.drivecinema.app.core.metadata

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** "Ted (2024)" was matched to "Ted Lasso": series search ignored the folder year and took the first result. */
class TmdbSeriesMatchingTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TmdbClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = TmdbClient(apiKey = "test_key", baseUrl = server.url("/3").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun show(id: Int, name: String, firstAir: String, original: String = name) =
        """{"id": $id, "name": "$name", "original_name": "$original", "first_air_date": "$firstAir"}"""

    private fun results(vararg shows: String) = MockResponse().setBody("""{"results": [${shows.joinToString(",")}]}""")

    // --- Matcher rules ---

    private fun c(name: String, year: Int?) = TmdbSeriesMatcher.Candidate(name, name, year)

    @Test
    fun exactNameBeatsAMorePopularPartialMatch() {
        val candidates = listOf(c("Ted Lasso", 2020), c("ted", 2024))
        assertEquals(1, TmdbSeriesMatcher.pick(candidates, "Ted", year = null))
    }

    @Test
    fun exactNameAndYearBeatExactNameAlone() {
        val candidates = listOf(c("Doctor Who", 1963), c("Doctor Who", 2005), c("Doctor Who", 2023))
        assertEquals(1, TmdbSeriesMatcher.pick(candidates, "Doctor Who", year = 2005))
    }

    @Test
    fun yearDecidesWhenNoNameIsExact() {
        val candidates = listOf(c("The Office (US)", 2005), c("The Office (UK)", 2001))
        assertEquals(1, TmdbSeriesMatcher.pick(candidates, "The Office", year = 2001))
    }

    @Test
    fun fallsBackToTmdbOrderAndHandlesNoResults() {
        assertEquals(0, TmdbSeriesMatcher.pick(listOf(c("Breaking Bad", 2008), c("Breaking Bad Fans", 2010)), "Breaking", null))
        assertNull(TmdbSeriesMatcher.pick(emptyList(), "Anything", 2020))
    }

    @Test
    fun namesCompareWithoutCaseOrPunctuation() {
        assertEquals(TmdbSeriesMatcher.normalize("Grey's Anatomy"), TmdbSeriesMatcher.normalize("greys anatomy"))
        assertEquals(TmdbSeriesMatcher.normalize("Law & Order"), TmdbSeriesMatcher.normalize("Law and Order"))
    }

    // --- Client: the year reaches TMDB, with a fallback ---

    @Test
    fun folderYearIsSentAndTheRightShowIsChosen() = runBlocking {
        server.enqueue(results(show(1, "Ted Lasso", "2020-08-14"), show(2, "ted", "2024-01-11")))

        val meta = client.searchSeries("Ted", year = 2024).getOrNull()!!

        assertEquals(2, meta.tmdbId)
        assertEquals(2024, meta.year)
        assertEquals("2024", server.takeRequest().requestUrl!!.queryParameter("first_air_date_year"))
    }

    @Test
    fun wrongFolderYearFallsBackToASearchWithoutYear() = runBlocking {
        server.enqueue(results())
        server.enqueue(results(show(1396, "Breaking Bad", "2008-01-20")))

        val meta = client.searchSeries("Breaking Bad", year = 2009).getOrNull()!!

        assertEquals(1396, meta.tmdbId)
        assertEquals("2009", server.takeRequest().requestUrl!!.queryParameter("first_air_date_year"))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("first_air_date_year"))
    }

    @Test
    fun withoutYearOnlyOneRequestIsMade() = runBlocking {
        server.enqueue(results(show(1396, "Breaking Bad", "2008-01-20")))

        client.searchSeries("Breaking Bad").getOrNull()!!

        assertNull(server.takeRequest().requestUrl!!.queryParameter("first_air_date_year"))
        assertEquals(1, server.requestCount)
    }
}
