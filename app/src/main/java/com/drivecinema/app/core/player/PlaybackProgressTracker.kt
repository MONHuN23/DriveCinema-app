package com.drivecinema.app.core.player

import androidx.media3.common.Player
import com.drivecinema.app.core.database.dao.PlaybackProgressDao
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class PlaybackProgressTracker(
    private val progressDao: PlaybackProgressDao,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher)
) {

    companion object {
        // The rules themselves are shared with the desktop player (PlaybackProgressRules in :shared).
        const val COMPLETION_THRESHOLD_RATIO = PlaybackProgressRules.COMPLETION_THRESHOLD_RATIO
        const val MINIMUM_RESUME_OFFSET_MS = PlaybackProgressRules.MINIMUM_RESUME_OFFSET_MS
        const val PROGRESS_POLL_INTERVAL_MS = 5000L

        fun calculateCompletion(positionMs: Long, durationMs: Long): Boolean =
            PlaybackProgressRules.calculateCompletion(positionMs, durationMs)

        fun resolveResumeOffset(isCompleted: Boolean, savedPositionMs: Long): Long =
            PlaybackProgressRules.resolveResumeOffset(isCompleted, savedPositionMs)
    }

    private var pollingJob: Job? = null
    private var activeListener: Player.Listener? = null
    private var activePlayer: Player? = null

    /**
     * Attaches progress tracking to the ExoPlayer instance.
     * Listens for playback state changes (completed / paused) and periodically polls every 5s.
     * All player queries occur on [mainDispatcher], while persistence runs on [ioDispatcher].
     */
    fun attach(player: Player, mediaItemId: String) {
        detachCurrent()

        activePlayer = player

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    flush(player, mediaItemId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    flush(player, mediaItemId)
                }
            }
        }

        player.addListener(listener)
        activeListener = listener

        // Polling job runs on mainDispatcher so that player queries are always on the application thread
        pollingJob = scope.launch(mainDispatcher) {
            while (isActive) {
                delay(PROGRESS_POLL_INTERVAL_MS)
                flush(player, mediaItemId)
            }
        }
    }

    /**
     * Detaches listener and stops polling. Flushes the final playback progress before detaching.
     */
    fun detach(player: Player, mediaItemId: String) {
        detachCurrent()
        flush(player, mediaItemId)
    }

    private fun detachCurrent() {
        pollingJob?.cancel()
        pollingJob = null
        val listener = activeListener
        val player = activePlayer
        if (player != null && listener != null) {
            try {
                player.removeListener(listener)
            } catch (_: Exception) {}
        }
        activeListener = null
        activePlayer = null
    }

    /**
     * Captures player position & duration on the application thread ([mainDispatcher]),
     * then separately saves the progress entity to Room SQLite on the background thread ([ioDispatcher]).
     */
    fun flush(player: Player, mediaItemId: String) {
        scope.launch(mainDispatcher) {
            val captured = captureSnapshot(player) ?: return@launch
            val snapshot = if (mediaItemId == markedWatchedId) captured.copy(isCompleted = true) else captured
            saveSnapshot(mediaItemId, snapshot)
        }
    }

    /** Set by [markWatched]; later flushes of that title (e.g. onStop while finishing) keep it watched. */
    @Volatile
    private var markedWatchedId: String? = null

    /** The user moved on with Next Episode: this title counts as watched wherever it was stopped (Milestone J.1). */
    fun markWatched(player: Player, mediaItemId: String) {
        markedWatchedId = mediaItemId
        flush(player, mediaItemId)
    }

    /**
     * Queries player properties strictly on the application thread.
     * Returns null if media duration or position are non-positive.
     */
    fun captureSnapshot(player: Player): PlaybackSnapshot? {
        val (pos, dur) = try {
            player.currentPosition.coerceAtLeast(0L) to player.duration.coerceAtLeast(0L)
        } catch (_: Exception) {
            return null
        }
        if (dur <= 0 && pos <= 0) return null

        val isCompleted = calculateCompletion(pos, dur)
        return PlaybackSnapshot(
            positionMs = pos,
            durationMs = dur,
            isCompleted = isCompleted
        )
    }

    /**
     * Persists the captured snapshot to the Room SQLite database strictly on [ioDispatcher].
     */
    suspend fun saveSnapshot(mediaItemId: String, snapshot: PlaybackSnapshot) = withContext(ioDispatcher) {
        progressDao.saveProgress(
            PlaybackProgressEntity(
                mediaItemId = mediaItemId,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                isCompleted = snapshot.isCompleted,
                lastWatchedAt = snapshot.timestamp
            )
        )
    }

    /**
     * Reads saved progress from the database on [ioDispatcher] and resolves the resume position.
     */
    suspend fun getResumePosition(mediaItemId: String): Long = withContext(ioDispatcher) {
        val progress = progressDao.getProgress(mediaItemId) ?: return@withContext 0L
        resolveResumeOffset(progress.isCompleted, progress.positionMs)
    }
}
