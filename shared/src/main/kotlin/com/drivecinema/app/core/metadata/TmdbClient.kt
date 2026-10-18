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

data class TmdbEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String? = null,
    val stillPath: String? = null,
    val voteAverage: Double? = null,
    val runtimeSeconds: Int? = null
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
) {

    companion object {
        private const val TAG = "TmdbClient"
    }

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

        val isBearer = isBearerToken(effectiveKey)
        val urlBuilder = "$baseUrl/search/movie".toHttpUrl().newBuilder()
            .addQueryParameter("query", title)

        if (!isBearer) {
            urlBuilder.addQueryParameter("api_key", effectiveKey)
        }
        if (year != null && year > 1900) {
            urlBuilder.addQueryParameter("year", year.toString())
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .get()

        if (isBearer) {
            requestBuilder.addHeader("Authorization", "Bearer $effectiveKey")
        }

        val request = requestBuilder.build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                AppLog.d(TAG, "searchMovie: endpoint=/search/movie, query='$title', status=${response.code}")
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("TMDB search movie failed: ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results == null || results.length() == 0) {
                    return@withContext Result.success(null)
                }

                val first = results.getJSONObject(0)
                val id = first.getInt("id")
                val movieTitle = first.optString("title", title)
                val overview = first.optString("overview", "").ifEmpty { null }
                val poster = first.optString("poster_path", "").ifEmpty { null }
                val backdrop = first.optString("backdrop_path", "").ifEmpty { null }
                val releaseDate = first.optString("release_date", "")
                val releaseYear = releaseDate.take(4).toIntOrNull() ?: year

                val genreIds = mutableListOf<Int>()
                val genreArray = first.optJSONArray("genre_ids")
                if (genreArray != null) {
                    for (i in 0 until genreArray.length()) {
                        genreIds.add(genreArray.getInt(i))
                    }
                }
                val genres = TmdbGenreLookup.resolveGenres(genreIds)

                val voteAvg = first.optDouble("vote_average", Double.NaN).let {
                    if (it.isNaN() || it == 0.0) null else it
                }
                val voteCount = first.optInt("vote_count", 0).let {
                    if (it == 0) null else it
                }

                Result.success(
                    TmdbMetadata(
                        tmdbId = id,
                        title = movieTitle,
                        overview = overview,
                        posterPath = poster,
                        backdropPath = backdrop,
                        year = releaseYear,
                        genres = genres,
                        voteAverage = voteAvg,
                        voteCount = voteCount
                    )
                )
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "searchMovie failed for '$title'", e)
            Result.failure(e)
        }
    }

    suspend fun searchSeries(title: String): Result<TmdbMetadata?> = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey?.ifEmpty { null }
        if (effectiveKey == null) {
            AppLog.w(TAG, "searchSeries skipped: TMDB API key is not configured")
            return@withContext Result.success(null)
        }

        val isBearer = isBearerToken(effectiveKey)
        val urlBuilder = "$baseUrl/search/tv".toHttpUrl().newBuilder()
            .addQueryParameter("query", title)

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
            httpClient.newCall(request).execute().use { response ->
                AppLog.d(TAG, "searchSeries: endpoint=/search/tv, query='$title', status=${response.code}")
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("TMDB search tv failed: ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results == null || results.length() == 0) {
                    return@withContext Result.success(null)
                }

                val first = results.getJSONObject(0)
                val id = first.getInt("id")
                val showTitle = first.optString("name", title)
                val overview = first.optString("overview", "").ifEmpty { null }
                val poster = first.optString("poster_path", "").ifEmpty { null }
                val backdrop = first.optString("backdrop_path", "").ifEmpty { null }
                val firstAirDate = first.optString("first_air_date", "")
                val year = firstAirDate.take(4).toIntOrNull()

                val genreIds = mutableListOf<Int>()
                val genreArray = first.optJSONArray("genre_ids")
                if (genreArray != null) {
                    for (i in 0 until genreArray.length()) {
                        genreIds.add(genreArray.getInt(i))
                    }
                }
                val genres = TmdbGenreLookup.resolveGenres(genreIds)

                val voteAvg = first.optDouble("vote_average", Double.NaN).let {
                    if (it.isNaN() || it == 0.0) null else it
                }
                val voteCount = first.optInt("vote_count", 0).let {
                    if (it == 0) null else it
                }

                Result.success(
                    TmdbMetadata(
                        tmdbId = id,
                        title = showTitle,
                        overview = overview,
                        posterPath = poster,
                        backdropPath = backdrop,
                        year = year,
                        genres = genres,
                        voteAverage = voteAvg,
                        voteCount = voteCount
                    )
                )
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "searchSeries failed for '$title'", e)
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
            httpClient.newCall(request).execute().use { response ->
                AppLog.d(TAG, "getSeasonEpisodes: endpoint=/tv/$tvId/season/$seasonNumber, status=${response.code}")
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("TMDB get season failed: ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val epArray = json.optJSONArray("episodes") ?: return@withContext Result.success(emptyList())

                val episodes = mutableListOf<TmdbEpisode>()
                for (i in 0 until epArray.length()) {
                    val epObj = epArray.getJSONObject(i)
                    val sNum = epObj.optInt("season_number", seasonNumber)
                    val eNum = epObj.optInt("episode_number", i + 1)
                    val name = epObj.optString("name", "Episode $eNum")
                    val overview = epObj.optString("overview", "").ifEmpty { null }
                    val still = epObj.optString("still_path", "").ifEmpty { null }
                    val voteAvg = epObj.optDouble("vote_average", Double.NaN).let {
                        if (it.isNaN() || it == 0.0) null else it
                    }
                    val runtimeMin = epObj.optInt("runtime", 0).let {
                        if (it > 0) it else null
                    }
                    val runtimeSec = runtimeMin?.times(60)

                    episodes.add(
                        TmdbEpisode(
                            seasonNumber = sNum,
                            episodeNumber = eNum,
                            name = name,
                            overview = overview,
                            stillPath = still,
                            voteAverage = voteAvg,
                            runtimeSeconds = runtimeSec
                        )
                    )
                }

                Result.success(episodes)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "getSeasonEpisodes failed for tvId=$tvId season=$seasonNumber", e)
            Result.failure(e)
        }
    }
}
