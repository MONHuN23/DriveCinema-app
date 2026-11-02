package com.drivecinema.app.core.skipintro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.Instant
import java.util.concurrent.TimeUnit

/** One service request's outcome. Anything but [Answer] means "no button", and nothing is cached for it. */
sealed interface ServiceResult<out T> {
    data class Answer<T>(val value: T) : ServiceResult<T>

    /** HTTP 429: no further requests to that service before [retryAtMs] (epoch ms). */
    data class RateLimited(val retryAtMs: Long) : ServiceResult<Nothing>

    /** Network error, timeout, non-2xx status, an oversized or malformed body. */
    data object Failed : ServiceResult<Nothing>
}

/**
 * The HTTP client for the intro services only. Deliberately a plain client: never a Drive client and never
 * `TokenRefreshInterceptor`, so no Google token can reach these hosts (the F-02 rule). Short timeouts, because the
 * button is only useful near the start of an episode; redirects are not followed.
 */
object SkipIntroHttp {
    /** Larger answers are rejected unread: a real one is well under 2 KB. */
    const val MAX_BODY_BYTES = 64L * 1024
    private const val DEFAULT_RETRY_MS = 60_000L

    fun newClient(userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    internal suspend fun <T> get(
        client: OkHttpClient,
        request: Request,
        now: () -> Long,
        parse: (String) -> T?
    ): ServiceResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> ServiceResult.RateLimited(retryAt(response, now()))
                    !response.isSuccessful -> ServiceResult.Failed
                    else -> {
                        val body = readCapped(response) ?: return@use ServiceResult.Failed
                        parse(body)?.let { ServiceResult.Answer(it) } ?: ServiceResult.Failed
                    }
                }
            }
        } catch (e: Exception) {
            ServiceResult.Failed
        }
    }

    private fun readCapped(response: Response): String? {
        val source = response.body?.source() ?: return null
        if (source.request(MAX_BODY_BYTES + 1)) return null
        return source.buffer.readUtf8()
    }

    /**
     * IntroDB sends `reset_at` in the 429 body (format not documented: an ISO time or epoch seconds/ms are
     * accepted); otherwise a `Retry-After` header in seconds; otherwise one minute.
     */
    private fun retryAt(response: Response, nowMs: Long): Long {
        val fromBody = try {
            val body = response.body?.source()?.let { if (it.request(MAX_BODY_BYTES + 1)) null else it.buffer.readUtf8() }
            body?.let { org.json.JSONObject(it).opt("reset_at") }?.let(::parseInstantMs)
        } catch (e: Exception) {
            null
        }
        val fromHeader = response.header("Retry-After")?.trim()?.toLongOrNull()?.let { nowMs + it * 1000 }
        val retryAt = fromBody ?: fromHeader ?: (nowMs + DEFAULT_RETRY_MS)
        // Never longer than a day, whatever the service says.
        return retryAt.coerceIn(nowMs, nowMs + 24 * 60 * 60 * 1000L)
    }

    private fun parseInstantMs(value: Any): Long? = when (value) {
        is Number -> value.toLong().let { if (it < 100_000_000_000L) it * 1000 else it }
        is String -> value.trim().toLongOrNull()?.let { parseInstantMs(it) }
            ?: try { Instant.parse(value.trim()).toEpochMilli() } catch (e: Exception) { null }
        else -> null
    }
}

/** SkipDB (`GET /api/segments`): sends the show's IMDb ID, season, episode and the file length in whole seconds. */
class SkipDbClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.skipdb.tv",
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun intro(imdbId: String, season: Int, episode: Int, lengthSeconds: Long): ServiceResult<SkipDbAnswer> {
        val url = "$baseUrl/api/segments".toHttpUrl().newBuilder()
            .addQueryParameter("imdb_id", imdbId)
            .addQueryParameter("season", season.toString())
            .addQueryParameter("episode", episode.toString())
            .addQueryParameter("type", "intro")
            .addQueryParameter("duration", lengthSeconds.toString())
            .build()
        return SkipIntroHttp.get(httpClient, Request.Builder().url(url).get().build(), now, SkipIntroParsers::parseSkipDb)
    }
}

/** IntroDB (`GET /segments`): sends the show's IMDb ID, season and episode (no file length). */
class IntroDbClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.introdb.app",
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun intro(imdbId: String, season: Int, episode: Int): ServiceResult<IntroDbAnswer> {
        val url = "$baseUrl/segments".toHttpUrl().newBuilder()
            .addQueryParameter("imdb_id", imdbId)
            .addQueryParameter("season", season.toString())
            .addQueryParameter("episode", episode.toString())
            .build()
        return SkipIntroHttp.get(httpClient, Request.Builder().url(url).get().build(), now, SkipIntroParsers::parseIntroDb)
    }
}
