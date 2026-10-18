package com.drivecinema.desktop.player

import com.drivecinema.app.core.database.dao.PlaybackProgressDao
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.player.PlaybackProgressRules

/**
 * Saves watch progress like the Android player: keyed by the played copy (Drive file ID), same "watched" rule (PlaybackProgressRules),
 * written every few seconds while playing and on pause / finish / close.
 */
class ProgressRecorder(
    private val dao: PlaybackProgressDao,
    private val progressKey: String,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Volatile private var lastSavedPositionMs = -1L
    @Volatile private var lastDurationMs = 0L
    @Volatile private var markedWatched = false

    /** Returns the saved entity, or null when there is nothing meaningful to save yet. */
    suspend fun record(positionMs: Long, durationMs: Long): PlaybackProgressEntity? {
        if (positionMs <= 0 && durationMs <= 0) return null
        if (positionMs == lastSavedPositionMs) return null
        val entity = PlaybackProgressEntity(
            mediaItemId = progressKey,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
            isCompleted = markedWatched || PlaybackProgressRules.calculateCompletion(positionMs, durationMs),
            lastWatchedAt = clock()
        )
        dao.saveProgress(entity)
        lastSavedPositionMs = positionMs
        lastDurationMs = durationMs
        return entity
    }

    /**
     * The user moved on with Next Episode: watched wherever it was stopped (Milestone J.1). Saves at once with the
     * last known position; the save when the player closes keeps it watched.
     */
    suspend fun markWatched(): PlaybackProgressEntity? {
        markedWatched = true
        val position = lastSavedPositionMs.coerceAtLeast(0)
        lastSavedPositionMs = -1L
        return record(position, lastDurationMs)
    }

    /** Playback reached the end: always stored as watched. */
    suspend fun recordFinished(durationMs: Long): PlaybackProgressEntity? {
        if (durationMs <= 0) return null
        return record(durationMs, durationMs)
    }

    companion object {
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
