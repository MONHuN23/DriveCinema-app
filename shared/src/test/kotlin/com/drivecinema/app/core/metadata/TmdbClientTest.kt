package com.drivecinema.app.core.metadata

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbClientTest {

    @Test
    fun testSearchMovieSuccess() = runBlocking {
        val server = MockWebServer()
        server.start()

        val json = """
            {
                "results": [
                    {
                        "id": 27205,
                        "title": "Inception",
                        "overview": "Cobb steals information from subconscious mind.",
                        "poster_path": "/qmDpIHrmpJINaRKAfWQfftjCdyi.jpg",
                        "backdrop_path": "/s3TBrRGB1iav7gFOCNx3H31MoES.jpg",
                        "release_date": "2010-07-15"
                    }
                ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val client = TmdbClient(
            apiKey = "test_key",
            baseUrl = server.url("/3").toString().removeSuffix("/")
        )

        val result = client.searchMovie("Inception", 2010)
        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals(27205, meta!!.tmdbId)
        assertEquals("Inception", meta.title)
        assertEquals(2010, meta.year)
        assertEquals("/qmDpIHrmpJINaRKAfWQfftjCdyi.jpg", meta.posterPath)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("api_key=test_key"))
        assertTrue(recorded.path!!.contains("query=Inception"))
        assertTrue(recorded.path!!.contains("year=2010"))

        server.shutdown()
    }

    @Test
    fun testSearchWithoutApiKeyReturnsNullGracefully() = runBlocking {
        val client = TmdbClient(apiKey = null)
        val result = client.searchMovie("Inception", 2010)
        assertTrue(result.isSuccess)
        assertNull("Missing API key must gracefully return null without error", result.getOrNull())
    }

    @Test
    fun testEmptyResultsReturnsNullGracefully() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"results": []}"""))

        val client = TmdbClient(
            apiKey = "test_key",
            baseUrl = server.url("/3").toString().removeSuffix("/")
        )

        val result = client.searchMovie("Unknown Film 999", null)
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())

        server.shutdown()
    }

    @Test
    fun testSearchMovieRichMetadata() = runBlocking {
        val server = MockWebServer()
        server.start()

        val json = """
            {
                "results": [
                    {
                        "id": 693134,
                        "title": "Dune: Part Two",
                        "overview": "Follow the mythic journey of Paul Atreides...",
                        "poster_path": "/8b8R8l88Qje9dn9OE8PY05Nx0zx.jpg",
                        "backdrop_path": "/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
                        "release_date": "2024-02-27",
                        "genre_ids": [878, 12],
                        "vote_average": 8.165,
                        "vote_count": 5200
                    }
                ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val client = TmdbClient(
            apiKey = "test_key",
            baseUrl = server.url("/3").toString().removeSuffix("/")
        )

        val result = client.searchMovie("Dune Part Two", 2024)
        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals(693134, meta!!.tmdbId)
        assertEquals("Dune: Part Two", meta.title)
        assertEquals(listOf("Science Fiction", "Adventure"), meta.genres)
        assertEquals(8.165, meta.voteAverage ?: 0.0, 0.001)
        assertEquals(5200, meta.voteCount)

        server.shutdown()
    }

    @Test
    fun testSearchSeriesRichMetadata() = runBlocking {
        val server = MockWebServer()
        server.start()

        val json = """
            {
                "results": [
                    {
                        "id": 1396,
                        "name": "Breaking Bad",
                        "overview": "Walter White, a New Mexico chemistry teacher...",
                        "poster_path": "/ztkUQFLlC19CCMYHW9o1zWhJRNq.jpg",
                        "backdrop_path": "/tsRy63Mu5cu8etL1X7ZLyf7UP1M.jpg",
                        "first_air_date": "2008-01-20",
                        "genre_ids": [18, 80],
                        "vote_average": 8.9,
                        "vote_count": 14000
                    }
                ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val client = TmdbClient(
            apiKey = "test_key",
            baseUrl = server.url("/3").toString().removeSuffix("/")
        )

        val result = client.searchSeries("Breaking Bad")
        assertTrue(result.isSuccess)
        val meta = result.getOrNull()
        assertNotNull(meta)
        assertEquals(1396, meta!!.tmdbId)
        assertEquals("Breaking Bad", meta.title)
        assertEquals(2008, meta.year)
        assertEquals(listOf("Drama", "Crime"), meta.genres)
        assertEquals(8.9, meta.voteAverage ?: 0.0, 0.001)
        assertEquals(14000, meta.voteCount)

        server.shutdown()
    }

    @Test
    fun testGetSeasonEpisodesSuccess() = runBlocking {
        val server = MockWebServer()
        server.start()

        val json = """
            {
                "season_number": 1,
                "episodes": [
                    {
                        "id": 62085,
                        "season_number": 1,
                        "episode_number": 1,
                        "name": "Pilot",
                        "overview": "Walter White is diagnosed with stage III terminal cancer...",
                        "still_path": "/ydlY3iPfeOAvFn8gyqxjCUgvoxv.jpg",
                        "vote_average": 8.3,
                        "runtime": 58
                    },
                    {
                        "id": 62086,
                        "season_number": 1,
                        "episode_number": 2,
                        "name": "Cat's in the Bag...",
                        "overview": "Walt and Jesse attempt to tie up loose ends...",
                        "still_path": "/e5O5WJzT1o7Lp35b0Yp1J8Xo2aA.jpg",
                        "vote_average": 8.2,
                        "runtime": 48
                    }
                ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val client = TmdbClient(
            apiKey = "test_key",
            baseUrl = server.url("/3").toString().removeSuffix("/")
        )

        val result = client.getSeasonEpisodes(tvId = 1396, seasonNumber = 1)
        assertTrue(result.isSuccess)
        val episodes = result.getOrNull()
        assertNotNull(episodes)
        assertEquals(2, episodes!!.size)

        val ep1 = episodes[0]
        assertEquals(1, ep1.seasonNumber)
        assertEquals(1, ep1.episodeNumber)
        assertEquals("Pilot", ep1.name)
        assertEquals("/ydlY3iPfeOAvFn8gyqxjCUgvoxv.jpg", ep1.stillPath)
        assertEquals(58 * 60, ep1.runtimeSeconds)
        assertEquals(8.3, ep1.voteAverage ?: 0.0, 0.001)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("/tv/1396/season/1"))
        assertTrue(recorded.path!!.contains("api_key=test_key"))

        server.shutdown()
    }

    @Test
    fun testHasApiKey() {
        assertTrue(TmdbClient(apiKey = "key123").hasApiKey())
        org.junit.Assert.assertFalse(TmdbClient(apiKey = null).hasApiKey())
        org.junit.Assert.assertFalse(TmdbClient(apiKey = "").hasApiKey())
    }

    @Test
    fun testBearerTokenHeaderUsedWhenTokenIsLongOrJwt() = runBlocking {
        val server = MockWebServer()
        server.start()

        val json = """{"results": [{"id": 100, "title": "Test Movie"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val client = TmdbClient(
            apiKey = jwtToken,
            baseUrl = server.url("/3").toString().removeSuffix("/")
        )

        val result = client.searchMovie("Test Movie", null)
        assertTrue(result.isSuccess)

        val recorded = server.takeRequest()
        assertEquals("Bearer $jwtToken", recorded.getHeader("Authorization"))
        org.junit.Assert.assertFalse("Should not include api_key in query when Bearer is used", recorded.path!!.contains("api_key="))

        server.shutdown()
    }
}
