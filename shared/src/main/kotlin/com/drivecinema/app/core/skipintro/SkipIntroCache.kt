package com.drivecinema.app.core.skipintro

import com.drivecinema.app.core.logging.AppLog
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A cached IMDb ID lookup: [imdbId] null means TMDB has none for the show. */
data class CachedImdbId(val imdbId: String?)

/**
 * The Skip Intro answers, in one small JSON file (no database table, so no Room migration). Lifetimes from the
 * design (§6): intro found 14 days, unknown 2 days, confirmed none 30 days, IMDb IDs 30 days ("none" 2 days).
 * Errors are never stored. A corrupt or unreadable file starts the cache empty; writes go to a temp file that
 * replaces the old one atomically (the F-09/F-23 pattern).
 */
class SkipIntroCache(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES
) {
    companion object {
        private const val TAG = "SkipIntroCache"
        const val MAX_ENTRIES = 2_000
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val FOUND_TTL_MS = 14 * DAY_MS
        const val UNKNOWN_TTL_MS = 2 * DAY_MS
        const val NONE_TTL_MS = 30 * DAY_MS
        const val IMDB_TTL_MS = 30 * DAY_MS

        private const val EPISODES = "episodes"
        private const val SHOWS = "shows"
        private const val PAUSES = "pauses"
        private const val AT = "at"
        private const val KIND = "kind"

        /** The cache key of one copy: the same episode with another length is another entry. */
        fun episodeKey(imdbId: String, season: Int, episode: Int, lengthSeconds: Long) =
            "$imdbId:$season:$episode:$lengthSeconds"
    }

    private var root: JSONObject? = null

    @Synchronized
    fun outcome(key: String): SkipIntroOutcome? {
        val entry = section(EPISODES).optJSONObject(key) ?: return null
        val at = entry.optLong(AT, 0L)
        return when (entry.optString(KIND)) {
            "found" -> {
                val times = IntroTimes(entry.optLong("start", -1L), entry.optLong("end", -1L))
                val source = if (entry.optString("source") == "introdb") IntroSource.INTRODB else IntroSource.SKIPDB
                SkipIntroOutcome.Found(times, source).takeIf { fresh(at, FOUND_TTL_MS) && times.startMs >= 0 }
            }
            "none" -> SkipIntroOutcome.ConfirmedNone.takeIf { fresh(at, NONE_TTL_MS) }
            "unknown" -> SkipIntroOutcome.Unknown.takeIf { fresh(at, UNKNOWN_TTL_MS) }
            else -> null
        }
    }

    @Synchronized
    fun putOutcome(key: String, outcome: SkipIntroOutcome) {
        val entry = JSONObject().put(AT, now())
        when (outcome) {
            is SkipIntroOutcome.Found -> entry.put(KIND, "found")
                .put("start", outcome.times.startMs)
                .put("end", outcome.times.endMs)
                .put("source", if (outcome.source == IntroSource.INTRODB) "introdb" else "skipdb")
            SkipIntroOutcome.ConfirmedNone -> entry.put(KIND, "none")
            SkipIntroOutcome.Unknown -> entry.put(KIND, "unknown")
        }
        section(EPISODES).put(key, entry)
        save()
    }

    @Synchronized
    fun imdbId(tmdbId: Int): CachedImdbId? {
        val entry = section(SHOWS).optJSONObject(tmdbId.toString()) ?: return null
        val imdbId = entry.optString("imdb", "").ifEmpty { null }
        val ttl = if (imdbId != null) IMDB_TTL_MS else UNKNOWN_TTL_MS
        return CachedImdbId(imdbId).takeIf { fresh(entry.optLong(AT, 0L), ttl) }
    }

    @Synchronized
    fun putImdbId(tmdbId: Int, imdbId: String?) {
        section(SHOWS).put(tmdbId.toString(), JSONObject().put(AT, now()).put("imdb", imdbId ?: ""))
        save()
    }

    /** After a 429: no requests to [source] before this time. */
    @Synchronized
    fun isPaused(source: IntroSource): Boolean = section(PAUSES).optLong(source.name, 0L) > now()

    @Synchronized
    fun pause(source: IntroSource, untilMs: Long) {
        section(PAUSES).put(source.name, untilMs)
        save()
    }

    private fun fresh(at: Long, ttlMs: Long): Boolean {
        val age = now() - at
        return age in 0 until ttlMs
    }

    private fun section(name: String): JSONObject {
        val data = root ?: load().also { root = it }
        return data.optJSONObject(name) ?: JSONObject().also { data.put(name, it) }
    }

    private fun load(): JSONObject = try {
        if (file.isFile) JSONObject(file.readText()) else JSONObject()
    } catch (e: Exception) {
        AppLog.w(TAG, "Skip Intro cache unreadable; starting empty")
        JSONObject()
    }

    private fun save() {
        val data = root ?: return
        trim(data.optJSONObject(EPISODES))
        trim(data.optJSONObject(SHOWS))
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(data.toString())
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            // The cache only saves requests; the answer just found is still used for this playback.
            AppLog.w(TAG, "Skip Intro cache not saved", e)
        }
    }

    /** Drops the oldest entries beyond [maxEntries]. */
    private fun trim(entries: JSONObject?) {
        if (entries == null || entries.length() <= maxEntries) return
        val keys = entries.keys().asSequence().toList()
        keys.sortedBy { entries.optJSONObject(it)?.optLong(AT, 0L) ?: 0L }
            .take(keys.size - maxEntries)
            .forEach { entries.remove(it) }
    }
}
