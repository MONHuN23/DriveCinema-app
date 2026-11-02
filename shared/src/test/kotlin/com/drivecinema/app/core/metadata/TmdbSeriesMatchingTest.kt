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

    /** A filled-in show: TMDB lists a poster for it (F-48: one without a poster or genres is bare). */
    private fun show(id: Int, name: String, firstAir: String, original: String = name) =
        """{"id": $id, "name": "$name", "original_name": "$original", "first_air_date": "$firstAir", "poster_path": "/$id.jpg"}"""

    private fun bareShow(id: Int, name: String, firstAir: String) =
        """{"id": $id, "name": "$name", "original_name": "$name", "first_air_date": "$firstAir", "poster_path": null, "genre_ids": []}"""

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

    // --- F-43: "One Piece (Anime)" ---

    @Test
    fun animeTagIsReadFromTheCleanedFolderName() {
        assertEquals("One Piece", TmdbSeriesMatcher.withoutAnimeTag("One Piece Anime"))
        assertEquals("One Piece", TmdbSeriesMatcher.withoutAnimeTag("One Piece anime"))
        assertNull(TmdbSeriesMatcher.withoutAnimeTag("One Piece"))
        assertNull("the word alone is a title", TmdbSeriesMatcher.withoutAnimeTag("Anime"))
    }

    @Test
    fun animatedCandidatesWinWhenAsked() {
        val candidates = listOf(
            TmdbSeriesMatcher.Candidate("ONE PIECE", "ONE PIECE", 2023),
            TmdbSeriesMatcher.Candidate("One Piece", "ワンピース", 1999, animated = true),
            TmdbSeriesMatcher.Candidate("LEGO ONE PIECE", "LEGO ONE PIECE", 2026, animated = true)
        )
        assertEquals("live action without the tag, as before", 0, TmdbSeriesMatcher.pick(candidates, "One Piece", null))
        assertEquals(1, TmdbSeriesMatcher.pick(candidates, "One Piece", null, animated = true))
        assertEquals("no animated result: all of them", 0,
            TmdbSeriesMatcher.pick(candidates.take(1), "One Piece", null, animated = true))
    }

    @Test
    fun animationGenreReachesTheMatcher() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results": [
            {"id": 111110, "name": "ONE PIECE", "original_name": "ONE PIECE", "first_air_date": "2023-08-31", "genre_ids": [10759, 10765]},
            {"id": 37854, "name": "One Piece", "original_name": "ONE PIECE", "first_air_date": "1999-10-20", "genre_ids": [10759, 35, 16]}
        ]}"""))

        assertEquals(37854, client.searchSeries("One Piece", animated = true).getOrNull()!!.tmdbId)
    }

    // --- F-48: a bare TMDB entry (no poster, no genres) ---

    private fun bare(name: String, year: Int?) = TmdbSeriesMatcher.Candidate(name, name, year, bare = true)

    @Test
    fun theArcsParentShowBeatsABareEntryWithTheExactNameAndYear() {
        val candidates = listOf(bare("Bleach: Thousand-Year Blood War", 2022), c("Bleach", 2004))
        assertEquals(1, TmdbSeriesMatcher.pick(candidates, "bleach: Thousand Year Blood War", 2022))
    }

    @Test
    fun aBareNewShowStillBeatsAnotherShowOfTheSameYear() {
        val candidates = listOf(bare("Kitchen Wars", 2026), c("Desert Wars", 2026))
        assertEquals(0, TmdbSeriesMatcher.pick(candidates, "Kitchen Wars", 2026))
    }

    @Test
    fun aFilledShowWithTheExactNameBeatsABareOne() {
        val candidates = listOf(bare("Dune: Prophecy", 2024), c("Dune: Prophecy", 2024))
        assertEquals(1, TmdbSeriesMatcher.pick(candidates, "Dune Prophecy", 2024))
    }

    @Test
    fun onlyBareEntriesFollowTheOldRules() {
        val candidates = listOf(bare("Ted Lasso", 2020), bare("ted", 2024))
        assertEquals(1, TmdbSeriesMatcher.pick(candidates, "Ted", null))
        assertEquals("a parent name alone doesn't count among bare entries", 0,
            TmdbSeriesMatcher.pick(listOf(bare("Other", 2001), bare("Bleach", 2004)), "Bleach Thousand-Year Blood War", null))
    }

    @Test
    fun aShortNameIsNotAParentShow() {
        val candidates = listOf(bare("It Crowd Returns", 2025), c("It", 1990))
        assertEquals("'It' doesn't start 'It Crowd Returns' as a show", 0,
            TmdbSeriesMatcher.pick(candidates, "It Crowd Returns", 2025))
    }

    @Test
    fun aYearWithOnlyBareEntriesIsSearchedAgainWithoutTheYear() = runBlocking {
        server.enqueue(results(bareShow(340620, "Bleach: Thousand-Year Blood War", "2022-10-10")))
        server.enqueue(results(
            bareShow(340620, "Bleach: Thousand-Year Blood War", "2022-10-10"), show(30984, "Bleach", "2004-10-05")
        ))

        val meta = client.searchSeries("bleach: Thousand Year Blood War", year = 2022).getOrNull()!!

        assertEquals(30984, meta.tmdbId)
        assertEquals("2022", server.takeRequest().requestUrl!!.queryParameter("first_air_date_year"))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("first_air_date_year"))
    }

    @Test
    fun aFilledResultForTheYearNeedsNoSecondSearch() = runBlocking {
        server.enqueue(results(show(2, "ted", "2024-01-11")))

        client.searchSeries("Ted", year = 2024).getOrNull()!!

        assertEquals(1, server.requestCount)
    }
}
