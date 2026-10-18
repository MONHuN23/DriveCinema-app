package com.drivecinema.app.ui.player

import java.util.Locale

object PlaybackTimeUtils {

    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun calculateRelativeSeek(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        val safeDuration = durationMs.coerceAtLeast(0L)
        return (currentMs + deltaMs).coerceIn(0L, safeDuration)
    }

    fun calculateProgress(currentMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /** Resume positions this close to (or past) the end of the copy being played start over instead. */
    const val RESUME_END_MARGIN_MS = 2_000L

    /**
     * True when a saved resume position doesn't fit the copy being played (e.g. progress from a longer Drive
     * copy applied to a shorter local file): playing would end instantly and wrongly mark the title watched.
     */
    fun shouldRestartFromBeginning(resumePositionMs: Long, durationMs: Long): Boolean {
        if (resumePositionMs <= 0L || durationMs <= 0L) return false
        return resumePositionMs >= durationMs - RESUME_END_MARGIN_MS
    }

    fun calculateSeekFromProgress(progress: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return (progress.coerceIn(0f, 1f) * durationMs).toLong()
    }
}
