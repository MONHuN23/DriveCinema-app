package com.drivecinema.app.core.metadata

import com.drivecinema.app.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class TmdbMetadata(
    val tmdbId: Int,
    val title: String,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val year: Int?,
    val genres: List<String> = emptyList(),
    val voteAverage: Double? = null,
    val voteCount: Int? = null
)

/** One series search result for the naming helper (Task R.1): the show, its original name, and whether it's animated. */
data class TmdbSeriesResult(val metadata: TmdbMetadata, val originalName: String, val animated: Boolean)

data class TmdbEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String? = null,
    val stillPath: String? = null,
    val voteAverage: Double? = null,
    val runtimeSeconds: Int? = null,
    /** "2022-10-11"; lets [TmdbEpisodeMapping] split a long season into its broadcast parts. */
    val airDate: String? = null
)

/** One entry of a show's season list (`/tv/{id}`); an arc folder can be matched to it by [name]. */
data class TmdbSeason(
    val seasonNumber: Int,
    val name: String,
    val posterPath: String? = null,
    val overview: String? = null,
    val episodeCount: Int = 0,
    /** "2022-10-11", the season's first air date; the naming helper's arc folder year (Task R.1). */
    val airDate: String? = null
)

object TmdbGenreLookup {
    private val GENRES = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Science Fiction",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
        10759 to "Action & Adventure",
        10762 to "Kids",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics"
    )

    fun resolveGenres(genreIds: List<Int>): List<String> {
        return genreIds.mapNotNull { GENRES[it] }
    }
}

class TmdbClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val apiKey: String? = null,
    private val baseUrl: String = "https://api.themoviedb.org/3"
) : TmdbEpisodeSource {

    companion object {
        private const val TAG = "TmdbClient"
        private val IMDB_ID = Regex("tt\\d{5,10}")
        private val TVDB_ORDER = Regex("tvdb", RegexOption.IGNORE_CASE)
    }

    private val requests = java.util.concurrent.atomic.AtomicInteger()

    /** Task M.0: HTTP requests sent so far; enrichment logs how much this grew. */
    val requestCount: Int get() = requests.get()

    fun hasApiKey(): Boolean = !apiKey.isNullOrEmpty()

    private fun isBearerToken(key: String): Boolean {
        return key.length > 50 || key.startsWith("eyJ")
    }

    suspend fun searchMovie(title: String, year: Int?): Result<TmdbMetadata?> = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey?.ifEmpty { null }
        if (effectiveKey == null) {
            AppLog.w(TAG, "searchMovie skipped: TMDB API key is not configured")
            return@withContext Result.success(null)
        }
        return@withContext try {
            val first = fetchMovieResults(title, year, effectiveKey).firstOrNull()
            Result.success(first?.let { parseMovie(it, title, year) })
        } catch (e: Exception) {
            AppLog.e(TAG, "searchMovie failed for '$title'", e)
            Result.failure(e)
        }
    }

    /**
     * Task R.1 (naming helper): TMDB's `/search/movie` results in TMDB's order, for the user to pick from. The sync's
     * [searchMovie] takes the first of the same list.
     */
    suspend fun searchMovieCandidates(query: String, year: Int? = null): Result<List<TmdbMetadata>> =
        withContext(Dispatchers.IO) {
            val effectiveKey = apiKey?.ifEmpty { null } ?: return@withContext Result.success(emptyList())
            try {
                Result.success(fetchMovieResults(query, year, effectiveKey).map { parseMovie(it, query, year) })
            } catch (e: Exception) {
                AppLog.e(TAG, "searchMovieCandidates failed for '$query'", e)
                Result.failure(e)
            }
        }

    /** One `/search/movie` request; a [year] after 1900 becomes `year`. Throws on an HTTP error. */
    private fun fetchMovieResults(title: String, year: Int?, key: String): List<JSONObject> {
        val isBearer = isBearerToken(key)
        val urlBuilder = "$baseUrl/search/movie".toHttpUrl().newBuilder()
            .addQueryParameter("query", title)
        if (!isBearer) {
            urlBuilder.addQueryParameter("api_key", key)
        }
        if (year != null && year > 1900) {
            urlBuilder.addQueryParameter("year", year.toString())
        }
        val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
        if (isBearer) {
            requestBuilder.addHeader("Authorization", "Bearer $key")
        }
        requests.incrementAndGet()
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            AppLog.d(TAG, "searchMovie: endpoint=/search/movie, query='$title', status=${response.code}")
            if (!response.isSuccessful) {
                throw IOException("TMDB search movie failed: ${response.code}")
            }
            val results = JSONObject(response.body?.string() ?: "").optJSONArray("results") ?: return emptyList()
            return (0 until results.length()).map { results.getJSONObject(it) }
        }
    }

    private fun parseMovie(obj: JSONObject, fallbackTitle: String, fallbackYear: Int?): TmdbMetadata =
        TmdbMetadata(
            tmdbId = obj.getInt("id"),
            title = obj.optString("title", fallbackTitle),
            overview = obj.optString("overview", "").ifEmpty { null },
            posterPath = obj.optString("poster_path", "").ifEmpty { null },
            backdropPath = obj.optString("backdrop_path", "").ifEmpty { null },
            year = obj.optString("release_date", "").take(4).toIntOrNull() ?: fallbackYear,
            genres = TmdbGenreLookup.resolveGenres(genreIds(obj)),
            voteAverage = obj.optDouble("vote_average", Double.NaN).let { if (it.isNaN() || it == 0.0) null else it },
            voteCount = obj.optInt("vote_count", 0).let { if (it == 0) null else it }
        )

    private fun genreIds(obj: JSONObject): List<Int> {
        val ids = obj.optJSONArray("genre_ids") ?: return emptyList()
        return (0 until ids.length()).map { ids.getInt(it) }
    }

    /**
     * Searches TMDB for a series. [year] (from the folder name, e.g. "Ted (2024)") narrows the search to shows
     * that first aired that year; when nothing matches, the search runs again without it. From the results,
     * [TmdbSeriesMatcher] prefers an exact name and year over TMDB's popularity order, so "Ted (2024)" finds
     * the 2024 show "ted" rather than the more popular "Ted Lasso". [animated] prefers animated shows (the
     * folder's "(Anime)" tag).
     */
    suspend fun searchSeries(
        title: String,
        year: Int? = null,
        animated: Boolean = false
    ): Result<TmdbMetadata?> = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey?.ifEmpty { null }
        if (effectiveKey == null) {
            AppLog.w(TAG, "searchSeries skipped: TMDB API key is not configured")
            return@withContext Result.success(null)
        }
        val validYear = year?.takeIf { it > 1900 }

        return@withContext try {
            var results = fetchSeriesResults(title, validYear, effectiveKey)
            // F-48: only bare entries that year (TMDB's empty 2022 "Bleach: Thousand-Year Blood War") is like none:
            // the show the folder means (Bleach, 2004) is found without the year.
            if (validYear != null && results.all(::isBare)) {
                results = (results + fetchSeriesResults(title, null, effectiveKey)).distinctBy { it.optInt("id") }
            }
            val candidates = results.map { obj ->
                TmdbSeriesMatcher.Candidate(
                    name = obj.optString("name", ""),
                    originalName = obj.optString("original_name", ""),
                    year = obj.optString("first_air_date", "").take(4).toIntOrNull(),
                    animated = isAnimated(obj),
                    bare = isBare(obj)
                )
            }
            val index = TmdbSeriesMatcher.pick(candidates, title, validYear, animated)
                ?: return@withContext Result.success(null)
            Result.success(parseSeries(results[index], title))
        } catch (e: Exception) {
            AppLog.e(TAG, "searchSeries failed for '$title'", e)
            Result.failure(e)
        }
    }

    /**
     * Task R.1 (naming helper): TMDB's `/search/tv` results in TMDB's order, for the user to pick from; with a [year],
     * searched again without it when nothing first aired that year, as [searchSeries] does.
     */
    suspend fun searchSeriesCandidates(query: String, year: Int? = null): Result<List<TmdbSeriesResult>> =
        withContext(Dispatchers.IO) {
            val effectiveKey = apiKey?.ifEmpty { null } ?: return@withContext Result.success(emptyList())
            val validYear = year?.takeIf { it > 1900 }
            try {
                var results = fetchSeriesResults(query, validYear, effectiveKey)
                if (results.isEmpty() && validYear != null) results = fetchSeriesResults(query, null, effectiveKey)
                Result.success(results.map { obj ->
                    TmdbSeriesResult(parseSeries(obj, query), obj.optString("original_name", ""), isAnimated(obj))
                })
            } catch (e: Exception) {
                AppLog.e(TAG, "searchSeriesCandidates failed for '$query'", e)
                Result.failure(e)
            }
        }

    private fun isAnimated(obj: JSONObject): Boolean = TmdbSeriesMatcher.ANIMATION_GENRE in genreIds(obj)

    /** A search result with no poster and no genres (F-48, [TmdbSeriesMatcher.Candidate.bare]). */
    private fun isBare(obj: JSONObject): Boolean = obj.isNull("poster_path") && genreIds(obj).isEmpty()

    /** One `/search/tv` request; [year] becomes `first_air_date_year`. Throws on an HTTP error. */
    private fun fetchSeriesResults(title: String, year: Int?, key: String): List<JSONObject> {
        val isBearer = isBearerToken(key)
        val urlBuilder = "$baseUrl/search/tv".toHttpUrl().newBuilder()
            .addQueryParameter("query", title)
        if (!isBearer) {
            urlBuilder.addQueryParameter("api_key", key)
        }
        if (year != null) {
            urlBuilder.addQueryParameter("first_air_date_year", year.toString())
        }
        val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
        if (isBearer) {
            requestBuilder.addHeader("Authorization", "Bearer $key")
        }
        requests.incrementAndGet()
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            AppLog.d(TAG, "searchSeries: endpoint=/search/tv, query='$title', year=$year, status=${response.code}")
            if (!response.isSuccessful) {
                throw IOException("TMDB search tv failed: ${response.code}")
            }
            val results = JSONObject(response.body?.string() ?: "").optJSONArray("results") ?: return emptyList()
            return (0 until results.length()).map { results.getJSONObject(it) }
        }
    }

    private fun parseSeries(obj: JSONObject, fallbackTitle: String): TmdbMetadata {
        val genreIds = mutableListOf<Int>()
        val genreArray = obj.optJSONArray("genre_ids")
        if (genreArray != null) {
            for (i in 0 until genreArray.length()) {
                genreIds.add(genreArray.getInt(i))
            }
        }
        return TmdbMetadata(
            tmdbId = obj.getInt("id"),
            title = obj.optString("name", fallbackTitle),
            overview = obj.optString("overview", "").ifEmpty { null },
            posterPath = obj.optString("poster_path", "").ifEmpty { null },
            backdropPath = obj.optString("backdrop_path", "").ifEmpty { null },
            year = obj.optString("first_air_date", "").take(4).toIntOrNull(),
            genres = TmdbGenreLookup.resolveGenres(genreIds),
            voteAverage = obj.optDouble("vote_average", Double.NaN).let { if (it.isNaN() || it == 0.0) null else it },
            voteCount = obj.optInt("vote_count", 0).let { if (it == 0) null else it }
        )
    }

    /**
     * The show's IMDb ID ("tt…") from `/tv/{id}/external_ids`, for the Skip Intro services (Milestone O), which
     * know shows by IMDb ID. Success with null: TMDB has none (or no API key is configured).
     */
    suspend fun getTvImdbId(tvId: Int): Result<String?> = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey?.ifEmpty { null }
        if (effectiveKey == null) {
            AppLog.w(TAG, "getTvImdbId skipped: TMDB API key is not configured")
            return@withContext Result.success(null)
        }

        val isBearer = isBearerToken(effectiveKey)
        val urlBuilder = "$baseUrl/tv/$tvId/external_ids".toHttpUrl().newBuilder()
        if (!isBearer) {
            urlBuilder.addQueryParameter("api_key", effectiveKey)
        }
        val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
        if (isBearer) {
            requestBuilder.addHeader("Authorization", "Bearer $effectiveKey")
        }

        return@withContext try {
            requests.incrementAndGet()
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                AppLog.d(TAG, "getTvImdbId: endpoint=/tv/$tvId/external_ids, status=${response.code}")
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("TMDB external ids failed: ${response.code}"))
                }
                val imdbId = JSONObject(response.body?.string() ?: "").optString("imdb_id", "")
                Result.success(imdbId.takeIf { IMDB_ID.matches(it) })
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "getTvImdbId failed for tvId=$tvId", e)
            Result.failure(e)
        }
    }

    suspend fun getSeasonEpisodes(tvId: Int, seasonNumber: Int): Result<List<TmdbEpisode>> = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey?.ifEmpty { null }
        if (effectiveKey == null) {
            AppLog.w(TAG, "getSeasonEpisodes skipped: TMDB API key is not configured")
            return@withContext Result.success(emptyList())
        }

        val isBearer = isBearerToken(effectiveKey)
        val urlBuilder = "$baseUrl/tv/$tvId/season/$seasonNumber".toHttpUrl().newBuilder()

        if (!isBearer) {
            urlBuilder.addQueryParameter("api_key", effectiveKey)
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .get()

        if (isBearer) {
            requestBuilder.addHeader("Authorization", "Bearer $effectiveKey")
        }

        val request = requestBuilder.build()

        return@withContext try {
            requests.incrementAndGet()
            httpClient.newCall(request).execute().use { response ->
                AppLog.d(TAG, "getSeasonEpisodes: endpoint=/tv/$tvId/season/$seasonNumber, status=${response.code}")
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("TMDB get season failed: ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val epArray = json.optJSONArray("episodes") ?: return@withContext Result.success(emptyList())

                val episodes = (0 until epArray.length()).map { i ->
                    parseEpisode(epArray.getJSONObject(i), seasonNumber, i + 1)
                }

                Result.success(episodes)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "getSeasonEpisodes failed for tvId=$tvId season=$seasonNumber", e)
            Result.failure(e)
        }
    }

    private fun parseEpisode(epObj: JSONObject, fallbackSeason: Int, fallbackNumber: Int): TmdbEpisode {
        val eNum = epObj.optInt("episode_number", fallbackNumber)
        return TmdbEpisode(
            seasonNumber = epObj.optInt("season_number", fallbackSeason),
            episodeNumber = eNum,
            name = epObj.optString("name", "Episode $eNum"),
            overview = epObj.optString("overview", "").ifEmpty { null },
            stillPath = epObj.optString("still_path", "").ifEmpty { null },
            voteAverage = epObj.optDouble("vote_average", Double.NaN).let { if (it.isNaN() || it == 0.0) null else it },
            runtimeSeconds = epObj.optInt("runtime", 0).takeIf { it > 0 }?.times(60),
            airDate = epObj.optString("air_date", "").ifEmpty { null }
        )
    }

    /** One GET of [path] with the API key; throws on an HTTP error. */
    private fun getJson(path: String, key: String): JSONObject {
        val isBearer = isBearerToken(key)
        val urlBuilder = "$baseUrl$path".toHttpUrl().newBuilder()
        if (!isBearer) {
            urlBuilder.addQueryParameter("api_key", key)
        }
        val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
        if (isBearer) {
            requestBuilder.addHeader("Authorization", "Bearer $key")
        }
        requests.incrementAndGet()
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            AppLog.d(TAG, "endpoint=$path, status=${response.code}")
            if (!response.isSuccessful) {
                throw IOException("TMDB $path failed: ${response.code}")
            }
            return JSONObject(response.body?.string() ?: "")
        }
    }

    override suspend fun seasons(tvId: Int) = getSeasons(tvId)
    override suspend fun episodes(tvId: Int, season: Int) = getSeasonEpisodes(tvId, season)
    override suspend fun tvdbOrder(tvId: Int) = getTvdbOrder(tvId)

    /** The show's season list from `/tv/{id}`, specials (season 0) included. */
    suspend fun getSeasons(tvId: Int): Result<List<TmdbSeason>> = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey?.ifEmpty { null } ?: return@withContext Result.success(emptyList())
        return@withContext try {
            val seasons = getJson("/tv/$tvId", effectiveKey).optJSONArray("seasons")
                ?: return@withContext Result.success(emptyList())
            Result.success((0 until seasons.length()).map { i ->
                val obj = seasons.getJSONObject(i)
                TmdbSeason(
                    seasonNumber = obj.optInt("season_number", -1),
                    name = obj.optString("name", ""),
                    posterPath = obj.optString("poster_path", "").ifEmpty { null },
                    overview = obj.optString("overview", "").ifEmpty { null },
                    episodeCount = obj.optInt("episode_count", 0),
                    airDate = obj.optString("air_date", "").ifEmpty { null }
                )
            })
        } catch (e: Exception) {
            AppLog.e(TAG, "getSeasons failed for tvId=$tvId", e)
            Result.failure(e)
        }
    }

    /**
     * The show's episodes in TVDB's season order, from a TMDB episode group named "TVDB …", keyed by TVDB season
     * number and listed in episode order. TMDB files some long anime as one big season ("Bleach" season 1 has 366
     * episodes) while folders often follow TVDB ("Bleach/Season 17"). Empty when the show has no such group.
     */
    suspend fun getTvdbOrder(tvId: Int): Result<Map<Int, List<TmdbEpisode>>> = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey?.ifEmpty { null } ?: return@withContext Result.success(emptyMap())
        return@withContext try {
            val groups = getJson("/tv/$tvId/episode_groups", effectiveKey).optJSONArray("results")
            val groupId = groups?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
                ?.firstOrNull { TVDB_ORDER.containsMatchIn(it.optString("name", "")) }
                ?.optString("id", "")?.ifEmpty { null }
                ?: return@withContext Result.success(emptyMap())
            val seasons = getJson("/tv/episode_group/$groupId", effectiveKey).optJSONArray("groups")
                ?: return@withContext Result.success(emptyMap())
            Result.success((0 until seasons.length()).associate { i ->
                val season = seasons.getJSONObject(i)
                val eps = season.optJSONArray("episodes")
                val ordered = (0 until (eps?.length() ?: 0)).map { eps!!.getJSONObject(it) }
                    .sortedBy { it.optInt("order", 0) }
                    .mapIndexed { index, obj -> parseEpisode(obj, fallbackSeason = 0, fallbackNumber = index + 1) }
                season.optInt("order", -1) to ordered
            })
        } catch (e: Exception) {
            AppLog.e(TAG, "getTvdbOrder failed for tvId=$tvId", e)
            Result.failure(e)
        }
    }
}
