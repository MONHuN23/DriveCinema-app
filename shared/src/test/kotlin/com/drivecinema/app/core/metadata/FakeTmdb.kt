package com.drivecinema.app.core.metadata

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Task R.1: a small TMDB on MockWebServer for the naming helper's tests: `/search/tv`, `/search/movie` (a show matches
 * when every word of the query is in its name or one of its other names; results in popularity order; the year
 * filters), `/tv/{id}` (season list), `/tv/{id}/season/{n}` and no episode groups. Shaped like TMDB's real shows.
 */
class FakeTmdb : AutoCloseable {

    data class Season(val number: Int, val name: String, val episodes: List<TmdbEpisode>) {
        val airDate: String? get() = episodes.firstOrNull()?.airDate
    }

    data class Show(
        val id: Int,
        val name: String,
        val firstAired: String,
        val animated: Boolean,
        val seasons: List<Season>,
        val otherNames: List<String> = emptyList(),
        /** F-48: an entry someone has only started: no genres (no show here has a poster). */
        val bare: Boolean = false
    )

    data class Film(val id: Int, val title: String, val released: String)

    val server = MockWebServer()
    /** Most popular first, as TMDB sorts search results. */
    val shows = mutableListOf<Show>()
    val films = mutableListOf<Film>()
    /** Every request's path (no query). */
    val paths = CopyOnWriteArrayList<String>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val path = url.encodedPath.removePrefix("/3")
                paths += path
                val body = answer(path, url.queryParameter("query"), url.queryParameter("first_air_date_year") ?: url.queryParameter("year"))
                return body?.let { MockResponse().setBody(it.toString()) } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    val client: TmdbClient by lazy { TmdbClient(apiKey = "test_key", baseUrl = server.url("/3").toString().removeSuffix("/")) }

    override fun close() = server.shutdown()

    private fun words(text: String) =
        text.lowercase().replace("&", " and ").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun found(query: String, names: List<String>) = names.any { words(it).containsAll(words(query)) }

    private fun answer(path: String, query: String?, year: String?): JSONObject? {
        val tv = Regex("""/tv/(\d+)""").matchEntire(path)
        val season = Regex("""/tv/(\d+)/season/(\d+)""").matchEntire(path)
        return when {
            path == "/search/tv" -> JSONObject().put("results", JSONArray(
                shows.filter { found(query!!, listOf(it.name) + it.otherNames) && (year == null || it.firstAired.startsWith(year)) }
                    .map { show ->
                        JSONObject().put("id", show.id).put("name", show.name).put("original_name", show.name)
                            .put("first_air_date", show.firstAired)
                            .put("genre_ids", JSONArray(when {
                                show.bare -> emptyList()
                                show.animated -> listOf(16, 10759)
                                else -> listOf(18)
                            }))
                    }
            ))
            path == "/search/movie" -> JSONObject().put("results", JSONArray(
                films.filter { found(query!!, listOf(it.title)) && (year == null || it.released.startsWith(year)) }
                    .map { JSONObject().put("id", it.id).put("title", it.title).put("release_date", it.released) }
            ))
            tv != null -> show(tv.groupValues[1])?.let { show ->
                JSONObject().put("name", show.name).put("seasons", JSONArray(show.seasons.map {
                    JSONObject().put("season_number", it.number).put("name", it.name)
                        .put("episode_count", it.episodes.size).put("air_date", it.airDate ?: "")
                }))
            }
            season != null -> show(season.groupValues[1])?.seasons?.firstOrNull { it.number == season.groupValues[2].toInt() }
                ?.let { s ->
                    JSONObject().put("episodes", JSONArray(s.episodes.map {
                        JSONObject().put("season_number", it.seasonNumber).put("episode_number", it.episodeNumber)
                            .put("name", it.name).put("air_date", it.airDate ?: "")
                    }))
                }
            path.endsWith("/episode_groups") -> JSONObject().put("results", JSONArray())
            else -> null
        }
    }

    private fun show(id: String) = shows.firstOrNull { it.id.toString() == id }

    /**
     * What the sync makes of files in the series folder [seriesFolder]: the crawler reads the folder and each
     * (season folder, file) name, [SeriesFolderSearch] finds the show, and [SeriesEpisodeMatcher] matches all files
     * at once, from TMDB directly (no cache). Null when the folder finds no show.
     */
    suspend fun syncReads(seriesFolder: String, files: List<Pair<String?, String>>): SyncRead? {
        val folder = MediaFilenameParser.parseMovie(seriesFolder)
        val found = SeriesFolderSearch.find(client, folder.cleanTitle, folder.year).getOrThrow() ?: return null
        val keys = files.associateWith { (seasonFolder, file) -> SeriesLayout.episodeKey(seasonFolder, file).pair }
        val match = SeriesEpisodeMatcher(client).match(found.show.tmdbId, found.show.title, found.searchTitle, keys.values.distinct())
        return SyncRead(found.show.tmdbId, keys.mapValues { match.episodes[it.value] })
    }

    data class SyncRead(val tvId: Int, val episodes: Map<Pair<String?, String>, TmdbEpisode?>)

    companion object {
        private fun weekly(season: Int, numbers: IntRange, start: String, name: (Int) -> String = { "Episode $it" }) =
            numbers.mapIndexed { i, n ->
                TmdbEpisode(season, n, name(n), airDate = LocalDate.parse(start).plusWeeks(i.toLong()).toString())
            }

        val breakingBad = Show(
            1396, "Breaking Bad", "2008-01-20", animated = false, seasons = listOf(
                Season(0, "Specials", weekly(0, 1..2, "2009-02-17")),
                Season(1, "Season 1", weekly(1, 1..7, "2008-01-20") { if (it == 1) "Pilot" else "BB1 $it" }),
                Season(2, "Season 2", weekly(2, 1..13, "2009-03-08") { "BB2 $it" })
            )
        )

        val tedLasso = Show(97546, "Ted Lasso", "2020-08-14", animated = false,
            seasons = listOf(Season(1, "Season 1", weekly(1, 1..10, "2020-08-14"))))

        val ted = Show(201834, "ted", "2024-01-11", animated = false,
            seasons = listOf(Season(1, "Season 1", weekly(1, 1..7, "2024-01-11") { "ted $it" })))

        val onePieceLiveAction = Show(111110, "ONE PIECE", "2023-08-31", animated = false,
            seasons = listOf(Season(1, "Season 1", weekly(1, 1..8, "2023-08-31"))))

        /**
         * One Piece numbers its episodes through the seasons: 21 seasons up to 1085, season 22 is 1086–1155 and
         * season 23 1156–1180, so "S22E1086" is TMDB's own name. Weekly from 1999.
         */
        val onePiece: Show = run {
            val sizes = List(20) { 52 } + 45 + 70 + 25
            var next = 1
            var date = LocalDate.parse("1999-10-20")
            val seasons = sizes.mapIndexed { i, size ->
                val numbers = next until next + size
                val season = Season(i + 1, "Arc ${i + 1}", weekly(i + 1, numbers, date.toString()) { "OP $it" })
                next += size
                date = date.plusWeeks(size.toLong())
                season
            }
            Show(37854, "One Piece", "1999-10-20", animated = true,
                seasons = listOf(Season(0, "Specials", weekly(0, 1..3, "2000-01-01"))) + seasons)
        }

        /** "Thousand-Year Blood War" is Bleach's season 2: four broadcast parts of 13, 13, 14 and 10, months apart. */
        val bleach: Show = run {
            val starts = listOf("2022-10-11", "2023-07-08", "2024-10-05", "2026-07-25")
            val sizes = listOf(13, 13, 14, 10)
            var number = 0
            val tybw = starts.zip(sizes).flatMap { (start, size) ->
                (0 until size).map { week ->
                    number++
                    TmdbEpisode(2, number, "TYBW $number", airDate = LocalDate.parse(start).plusWeeks(week.toLong()).toString())
                }
            }
            Show(30984, "Bleach", "2004-10-05", animated = true, otherNames = listOf("Bleach: Thousand-Year Blood War"),
                seasons = listOf(
                    Season(0, "Specials", weekly(0, 1..2, "2006-12-16")),
                    Season(1, "Bleach", weekly(1, 1..366, "2004-10-05") { "Bleach $it" }),
                    Season(2, "Thousand-Year Blood War", tybw)
                ))
        }

        /** F-48: the empty entry TMDB gained in November 2026, one episode, beside Bleach's season 2. */
        val bareThousandYearBloodWar = Show(340620, "Bleach: Thousand-Year Blood War", "2022-10-10", animated = false,
            bare = true, seasons = listOf(Season(1, "Season 1", weekly(1, 1..1, "2022-10-10") { "S1.E1 · The Blood Warfare" })))

        fun library() = FakeTmdb().apply {
            shows += listOf(breakingBad, tedLasso, ted, onePieceLiveAction, onePiece, bleach)
            films += listOf(
                Film(214756, "Ted 2", "2015-06-25"), Film(72105, "Ted", "2012-06-29"),
                Film(335984, "Blade Runner 2049", "2017-10-04"), Film(78, "Blade Runner", "1982-06-25"),
                Film(1, "Alpha", "2018-08-17"), Film(2, "Alpha", "2018-05-04")
            )
        }
    }
}
