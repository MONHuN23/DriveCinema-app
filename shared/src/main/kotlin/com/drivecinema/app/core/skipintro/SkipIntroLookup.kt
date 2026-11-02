package com.drivecinema.app.core.skipintro

import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.metadata.TmdbClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One episode copy to look up: [fileLengthMs] is the length of the file being played. */
data class SkipIntroRequest(val tmdbShowId: Int, val season: Int, val episode: Int, val fileLengthMs: Long)

/**
 * Finds an episode's intro (Milestone O, design: `docs/design/o0-skip-intro.md`). Order: cache, then the show's
 * IMDb ID (TMDB), SkipDB, and IntroDB only when SkipDB has no usable answer. Every failure gives null, which means
 * "no button"; playback never waits for this. Callers check the user's "Skip Intro" setting before calling, so
 * nothing is sent when it is off.
 */
class SkipIntroLookup(
    private val imdbIdOf: suspend (tmdbShowId: Int) -> Result<String?>,
    private val skipDb: SkipDbClient,
    private val introDb: IntroDbClient?,
    private val cache: SkipIntroCache
) {
    companion object {
        private const val TAG = "SkipIntro"

        /** The production wiring: one plain client for both services, the cache at [cacheFile]. */
        fun create(cacheFile: File, tmdbClient: TmdbClient, userAgent: String): SkipIntroLookup {
            val http = SkipIntroHttp.newClient(userAgent)
            return SkipIntroLookup(
                imdbIdOf = tmdbClient::getTvImdbId,
                skipDb = SkipDbClient(http),
                introDb = IntroDbClient(http),
                cache = SkipIntroCache(cacheFile)
            )
        }
    }

    private val inFlight = HashMap<String, CompletableDeferred<IntroTimes?>>()

    /** The intro to offer for this copy, or null. Two calls for the same copy at once share one lookup. */
    suspend fun find(request: SkipIntroRequest): IntroTimes? = withContext(Dispatchers.IO) {
        if (request.tmdbShowId <= 0 || request.season < 1 || request.episode < 1 || request.fileLengthMs <= 0) {
            return@withContext null
        }
        val lengthSeconds = (request.fileLengthMs + 500) / 1000
        val flightKey = "${request.tmdbShowId}:${request.season}:${request.episode}:$lengthSeconds"

        val mine = CompletableDeferred<IntroTimes?>()
        val running = synchronized(inFlight) { inFlight[flightKey] ?: run { inFlight[flightKey] = mine; null } }
        if (running != null) return@withContext running.await()
        try {
            lookUp(request, lengthSeconds).also { mine.complete(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "skip-intro: error")
            null
        } finally {
            mine.complete(null)
            synchronized(inFlight) { inFlight.remove(flightKey) }
        }
    }

    private suspend fun imdbId(tmdbShowId: Int): String? {
        cache.imdbId(tmdbShowId)?.let { return it.imdbId }
        val result = imdbIdOf(tmdbShowId)
        if (result.isFailure) return null
        val imdbId = result.getOrNull()
        cache.putImdbId(tmdbShowId, imdbId)
        return imdbId
    }

    private suspend fun lookUp(request: SkipIntroRequest, lengthSeconds: Long): IntroTimes? {
        val imdbId = imdbId(request.tmdbShowId) ?: return null
        val key = SkipIntroCache.episodeKey(imdbId, request.season, request.episode, lengthSeconds)
        cache.outcome(key)?.let { return (it as? SkipIntroOutcome.Found)?.times }

        val skipAnswer = ask(IntroSource.SKIPDB) { skipDb.intro(imdbId, request.season, request.episode, lengthSeconds) }
        val introAnswer = if (introDb != null && SkipIntroRules.needsIntroDb(skipAnswer, request.fileLengthMs)) {
            ask(IntroSource.INTRODB) { introDb.intro(imdbId, request.season, request.episode) }
        } else {
            null
        }
        val outcome = SkipIntroRules.decide(skipAnswer, introAnswer, request.fileLengthMs)
        // Stored only when SkipDB answered, so a SkipDB outage is retried on the next play.
        if (skipAnswer != null) cache.putOutcome(key, outcome)
        AppLog.d(TAG, "skip-intro: ${describe(outcome)}")
        return (outcome as? SkipIntroOutcome.Found)?.times
    }

    /** A service's answer, or null when it failed, is paused after a 429, or just answered 429. */
    private suspend fun <T> ask(source: IntroSource, call: suspend () -> ServiceResult<T>): T? {
        if (cache.isPaused(source)) return null
        return when (val result = call()) {
            is ServiceResult.Answer -> result.value
            is ServiceResult.RateLimited -> {
                cache.pause(source, result.retryAtMs)
                null
            }
            ServiceResult.Failed -> null
        }
    }

    private fun describe(outcome: SkipIntroOutcome) = when (outcome) {
        is SkipIntroOutcome.Found -> "found (${outcome.source.name.lowercase()})"
        SkipIntroOutcome.ConfirmedNone -> "none"
        SkipIntroOutcome.Unknown -> "unknown"
    }
}
