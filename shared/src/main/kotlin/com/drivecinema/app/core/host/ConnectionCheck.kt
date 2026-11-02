package com.drivecinema.app.core.host

import com.drivecinema.app.core.database.entity.MediaType
import java.util.Locale

/** What a film needs and what the connection to the PC gives, in Mbit/s (Task I.2, design §2.6). */
data class ConnectionVerdict(val neededMbps: Double, val measuredMbps: Double) {
    /** Below the film's average bitrate plus a margin for its peaks, playback would keep pausing to buffer. */
    val tooSlow: Boolean get() = measuredMbps < neededMbps * ConnectionCheck.MARGIN

    /** "This film needs about 9 Mbit/s, but the connection to the PC gives about 4. …" */
    fun message(hasDriveCopy: Boolean): String = String.format(
        Locale.ENGLISH,
        "This film needs about %s Mbit/s, but the connection to the PC gives about %s, so it may keep pausing. %s",
        round(neededMbps), round(measuredMbps),
        if (hasDriveCopy) "Play the Drive copy instead, or use Ethernet or stronger Wi-Fi."
        else "Use Ethernet or stronger Wi-Fi (away from home: the PC's internet upload is the limit)."
    )

    private fun round(value: Double) = if (value >= 10) "%.0f".format(Locale.ENGLISH, value) else "%.1f".format(Locale.ENGLISH, value)
}

object ConnectionCheck {
    /** Read when Play is pressed; about 0.2 s on home Wi-Fi, longer only on a connection that is slow anyway. */
    const val SAMPLE_BYTES = 2 * 1024 * 1024
    const val MARGIN = 1.3
    /** Used only when neither TMDB nor an earlier viewing told the length. */
    private const val TYPICAL_FILM_MS = 110 * 60_000L
    private const val TYPICAL_EPISODE_MS = 42 * 60_000L

    /** Average bitrate of a file: its size over its length. */
    fun neededMbps(sizeBytes: Long, durationMs: Long): Double =
        if (durationMs <= 0) 0.0 else sizeBytes * 8.0 / (durationMs / 1000.0) / 1_000_000.0

    fun measuredMbps(bytes: Long, elapsedNanos: Long): Double =
        if (elapsedNanos <= 0) Double.MAX_VALUE else bytes * 8.0 / (elapsedNanos / 1_000_000_000.0) / 1_000_000.0

    /** Best known length: an earlier viewing's duration, else TMDB's runtime, else a typical length for the type. */
    fun durationMs(type: MediaType?, watchedDurationMs: Long?, runtimeSeconds: Int?): Long = when {
        watchedDurationMs != null && watchedDurationMs > 0 -> watchedDurationMs
        runtimeSeconds != null && runtimeSeconds > 0 -> runtimeSeconds * 1000L
        type == MediaType.EPISODE -> TYPICAL_EPISODE_MS
        else -> TYPICAL_FILM_MS
    }
}
