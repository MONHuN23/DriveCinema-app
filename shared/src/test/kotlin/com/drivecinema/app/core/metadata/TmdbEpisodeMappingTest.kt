package com.drivecinema.app.core.metadata

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * "Bleach: Thousand-Year Blood War (2022)/Season 01" showed the first episodes of Bleach (2004): TMDB files the arc
 * as season 2 of "Bleach", and folder season 1 was read as TMDB season 1. "Bleach/Season 17" showed no titles: TMDB
 * has no season 17, only TVDB does.
 */
class TmdbEpisodeMappingTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TmdbClient
    private val routes = mutableMapOf<String, String>()
    private val paths = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath.removePrefix("/3")
                paths += path
                return routes[path]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        client = TmdbClient(apiKey = "test_key", baseUrl = server.url("/3").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    // --- Fixtures shaped like TMDB's Bleach (id 30984) ---

    private fun season(number: Int, name: String, poster: String? = null) =
        TmdbSeason(seasonNumber = number, name = name, posterPath = poster)

    private val bleachSeasons = listOf(
        season(0, "Specials"), season(1, "Bleach"), season(2, "Thousand-Year Blood War", "/tybw.jpg")
    )

    /** Four parts of 13, 13, 14 and 10 weekly episodes, months apart, numbered 1 to 50 like TMDB's season 2. */
    private val tybwEpisodes: List<TmdbEpisode> = run {
        val starts = listOf("2022-10-11", "2023-07-08", "2024-10-05", "2026-07-25")
        val sizes = listOf(13, 13, 14, 10)
        var number = 0
        starts.zip(sizes).flatMap { (start, size) ->
            (0 until size).map { week ->
                number++
                TmdbEpisode(2, number, "TYBW $number", airDate = LocalDate.parse(start).plusWeeks(week.toLong()).toString())
            }
        }
    }

    private fun seasonsJson(seasons: List<TmdbSeason>) = """{"seasons": [${seasons.joinToString(",") {
        """{"season_number": ${it.seasonNumber}, "name": "${it.name}", "poster_path": ${it.posterPath?.let { p -> "\"$p\"" } ?: "null"}, "overview": "About ${it.name}"}"""
    }}]}"""

    private fun episodeJson(e: TmdbEpisode, order: Int? = null) =
        """{"season_number": ${e.seasonNumber}, "episode_number": ${e.episodeNumber}, "name": "${e.name}", "air_date": "${e.airDate ?: ""}"${order?.let { ", \"order\": $it" } ?: ""}}"""

    private fun seasonJson(episodes: List<TmdbEpisode>) = """{"episodes": [${episodes.joinToString(",") { episodeJson(it) }}]}"""

    private fun bleachSeason1() = (1..366).map { TmdbEpisode(1, it, "Bleach $it") }

    // --- Pure rules ---

    @Test
    fun arcFolderFindsTheSeasonNamedInIt() {
        assertEquals(2, TmdbEpisodeMapping.arcSeason(bleachSeasons, "Bleach: Thousand-Year Blood War", "Bleach")?.seasonNumber)
        assertEquals(2, TmdbEpisodeMapping.arcSeason(bleachSeasons, "Bleach Thousand Year Blood War", "Bleach")?.seasonNumber)
    }

    @Test
    fun showFolderAndGenericOrShowNamedSeasonsAreNotArcs() {
        assertNull("the folder is the show", TmdbEpisodeMapping.arcSeason(bleachSeasons, "Bleach", "Bleach"))
        assertNull("season 1 is named like the show", TmdbEpisodeMapping.arcSeason(bleachSeasons, "Bleach Complete", "Bleach"))
        val office = listOf(season(1, "Season 1"), season(2, "Season 2"), season(0, "Specials"))
        assertNull(TmdbEpisodeMapping.arcSeason(office, "The Office Season 2 Specials", "The Office (US)"))
    }

    @Test
    fun seasonSplitsIntoItsBroadcastParts() {
        assertEquals(listOf(13, 13, 14, 10), TmdbEpisodeMapping.broadcastParts(tybwEpisodes).map { it.size })
    }

    @Test
    fun folderSeasonIsAPartAndLargeNumbersAreTheArcsOwn() {
        val parts = TmdbEpisodeMapping.broadcastParts(tybwEpisodes)
        assertEquals(1, TmdbEpisodeMapping.arcEpisode(parts, season = 1, episode = 1)?.episodeNumber)
        assertEquals(14, TmdbEpisodeMapping.arcEpisode(parts, season = 2, episode = 1)?.episodeNumber)
        assertEquals(41, TmdbEpisodeMapping.arcEpisode(parts, season = 4, episode = 1)?.episodeNumber)
        assertEquals("numbered through: S01E27", 27, TmdbEpisodeMapping.arcEpisode(parts, season = 1, episode = 27)?.episodeNumber)
        assertEquals("numbered through: S03E40", 40, TmdbEpisodeMapping.arcEpisode(parts, season = 3, episode = 40)?.episodeNumber)
        assertNull(TmdbEpisodeMapping.arcEpisode(parts, season = 1, episode = 51))
    }

    // --- Matcher against a fake TMDB ---

    @Test
    fun arcFolderSeasonOneGetsTheArcsEpisodesAndPoster() = runBlocking {
        routes["/tv/30984"] = seasonsJson(bleachSeasons)
        routes["/tv/30984/season/2"] = seasonJson(tybwEpisodes)
        routes["/tv/30984/season/1"] = seasonJson(bleachSeason1())

        val match = SeriesEpisodeMatcher(client)
            .match(30984, "Bleach", "Bleach: Thousand-Year Blood War", listOf(1 to 1, 1 to 2, 2 to 1))

        assertEquals("TYBW 1", match.episodes[1 to 1]?.name)
        assertEquals("TYBW 2", match.episodes[1 to 2]?.name)
        assertEquals("TYBW 14", match.episodes[2 to 1]?.name)
        assertEquals("/tybw.jpg", match.arc?.posterPath)
        assertTrue("Bleach's own season 1 is not fetched", "/tv/30984/season/1" !in paths)
    }

    @Test
    fun showWhoseSeasonsExistCostsNoExtraRequests() = runBlocking {
        routes["/tv/1396/season/1"] = seasonJson(listOf(TmdbEpisode(1, 1, "Pilot"), TmdbEpisode(1, 2, "Cat's in the Bag...")))

        val match = SeriesEpisodeMatcher(client).match(1396, "Breaking Bad", "Breaking Bad", listOf(1 to 1, 1 to 2))

        assertEquals("Pilot", match.episodes[1 to 1]?.name)
        assertNull(match.arc)
        assertEquals(listOf("/tv/1396/season/1"), paths.toList())
    }

    @Test
    fun seasonTmdbLacksUsesTheTvdbOrder() = runBlocking {
        routes["/tv/30984/episode_groups"] =
            """{"results": [{"id": "split", "name": "Season Split"}, {"id": "tvdb", "name": "TVDB Order"}]}"""
        val season17 = tybwEpisodes.take(3).mapIndexed { i, e -> episodeJson(e, order = i) }.reversed()
        routes["/tv/episode_group/tvdb"] = """{"groups": [
            {"order": 1, "episodes": [${episodeJson(TmdbEpisode(1, 1, "Bleach 1"), order = 0)}]},
            {"order": 17, "episodes": [${season17.joinToString(",")}]}
        ]}"""

        val match = SeriesEpisodeMatcher(client).match(30984, "Bleach", "Bleach", listOf(17 to 1, 17 to 3))

        assertEquals("TYBW 1", match.episodes[17 to 1]?.name)
        assertEquals("TYBW 3", match.episodes[17 to 3]?.name)
        assertTrue("TMDB season 17 was tried first", "/tv/30984/season/17" in paths)
    }

    @Test
    fun missingSeasonWithoutTvdbOrderLeavesTheEpisodesUnmatched() = runBlocking {
        routes["/tv/30984/episode_groups"] = """{"results": [{"id": "split", "name": "Season Split"}]}"""

        val match = SeriesEpisodeMatcher(client).match(30984, "Bleach", "Bleach", listOf(17 to 1))

        assertTrue(match.episodes.isEmpty())
        assertTrue("/tv/episode_group/split" !in paths)
    }

    // --- F-43: running episode numbers (One Piece) ---

    /** One Piece on TMDB numbers its episodes through the seasons: season 2 starts at 62. Two seasons kept short. */
    private val onePieceSeasons = listOf(
        TmdbSeason(0, "Specials", episodeCount = 39), TmdbSeason(1, "East Blue", episodeCount = 61),
        TmdbSeason(2, "Whiskey Peak", episodeCount = 16), TmdbSeason(3, "Egghead", episodeCount = 5)
    )

    private fun onePieceRoutes() {
        routes["/tv/37854"] = """{"seasons": [${onePieceSeasons.joinToString(",") {
            """{"season_number": ${it.seasonNumber}, "name": "${it.name}", "episode_count": ${it.episodeCount}}"""
        }}]}"""
        routes["/tv/37854/season/1"] = seasonJson((1..61).map { TmdbEpisode(1, it, "OP $it") })
        routes["/tv/37854/season/2"] = seasonJson((62..77).map { TmdbEpisode(2, it, "OP $it") })
        routes["/tv/37854/season/3"] = seasonJson((78..82).map { TmdbEpisode(3, it, "OP $it") })
    }

    @Test
    fun runningNumbersCountThroughTheSeasons() {
        assertEquals(1 to 0, TmdbEpisodeMapping.absoluteEpisode(onePieceSeasons, 1))
        assertEquals(2 to 0, TmdbEpisodeMapping.absoluteEpisode(onePieceSeasons, 62))
        assertEquals(3 to 4, TmdbEpisodeMapping.absoluteEpisode(onePieceSeasons, 82))
        assertNull(TmdbEpisodeMapping.absoluteEpisode(onePieceSeasons, 83))
        assertEquals("Bleach 367 = the arc's first", 2 to 0, TmdbEpisodeMapping.absoluteEpisode(bleachSeasons.map {
            it.copy(episodeCount = mapOf(1 to 366, 2 to 50)[it.seasonNumber] ?: 4)
        }, 367))
    }

    @Test
    fun fansubAndSceneRunningNumbersGetTheirEpisodes() = runBlocking {
        onePieceRoutes()

        val match = SeriesEpisodeMatcher(client).match(37854, "One Piece", "One Piece", listOf(1 to 5, 1 to 70, 9 to 81))

        assertEquals("OP 5", match.episodes[1 to 5]?.name)
        assertEquals("fansub '- 70', read as season 1", "OP 70", match.episodes[1 to 70]?.name)
        assertEquals("scene 'S09E81', a season TMDB lacks", "OP 81", match.episodes[9 to 81]?.name)
    }

    @Test
    fun episodeTmdbDoesNotListYetStaysUnmatched() = runBlocking {
        routes["/tv/1/season/1"] = seasonJson((1..13).map { TmdbEpisode(1, it, "S1 $it") })
        routes["/tv/1/season/2"] = seasonJson((1..10).map { TmdbEpisode(2, it, "S2 $it") })
        routes["/tv/1"] = """{"seasons": [{"season_number": 1, "name": "Season 1", "episode_count": 13},
            {"season_number": 2, "name": "Season 2", "episode_count": 10}]}"""

        val match = SeriesEpisodeMatcher(client).match(1, "Show", "Show", listOf(1 to 12, 2 to 11, 6 to 3))

        assertEquals("S1 12", match.episodes[1 to 12]?.name)
        assertNull("season 2 exists: never a running number", match.episodes[2 to 11])
        assertNull("season 6 isn't listed yet; 3 fits in a season", match.episodes[6 to 3])
    }

    @Test
    fun seasonOneNumberPastItsEndNeedsTmdbToNumberTheSameWay() = runBlocking {
        routes["/tv/1/season/1"] = seasonJson((1..13).map { TmdbEpisode(1, it, "S1 $it") })
        routes["/tv/1/season/2"] = seasonJson((1..13).map { TmdbEpisode(2, it, "S2 $it") })
        routes["/tv/1"] = """{"seasons": [{"season_number": 1, "name": "Season 1", "episode_count": 13},
            {"season_number": 2, "name": "Season 2", "episode_count": 13}]}"""

        val match = SeriesEpisodeMatcher(client).match(1, "Show", "Show", listOf(1 to 14))

        assertNull("S01E14 is not S02E01 of a show that numbers each season from 1", match.episodes[1 to 14])
    }
}
