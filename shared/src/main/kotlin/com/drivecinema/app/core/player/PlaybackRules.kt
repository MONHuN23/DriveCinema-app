package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.dao.MediaSourceDao
import com.drivecinema.app.core.database.entity.SourceKind

/** Watch-progress rules shared by every DriveCinema player (Android, TV, desktop). */
object PlaybackProgressRules {
    const val COMPLETION_THRESHOLD_RATIO = 0.90f
    /** Ending song + preview of a ~24 min episode; see [calculateCompletion]. */
    const val COMPLETION_REMAINING_MS = 3 * 60_000L
    /** The remaining-time rule never marks a title before this point (keeps short clips honest). */
    const val COMPLETION_FLOOR_RATIO = 0.85f
    const val MINIMUM_RESUME_OFFSET_MS = 10_000L

    /**
     * Watched = 90 % played, or at most 3 minutes left once 85 % is played (Milestone J.1). Credits are usually
     * skipped: a 24-min episode counts from 21:00, a 2-h film still only from 1:48.
     */
    fun calculateCompletion(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0 || positionMs <= 0) return false
        val ratio = positionMs.toFloat() / durationMs.toFloat()
        if (ratio >= COMPLETION_THRESHOLD_RATIO) return true
        return ratio >= COMPLETION_FLOOR_RATIO && durationMs - positionMs <= COMPLETION_REMAINING_MS
    }

    /** Finished titles and the first few seconds start from the beginning. */
    fun resolveResumeOffset(isCompleted: Boolean, savedPositionMs: Long): Long {
        if (isCompleted) return 0L
        if (savedPositionMs < MINIMUM_RESUME_OFFSET_MS) return 0L
        return savedPositionMs
    }
}

/** Google Drive media URLs (streamed with the `Authorization` header, never with a token in the URL). */
object DriveStreamUrls {
    fun media(fileId: String): String =
        "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true"
}

/** Catalog IDs of titles found in local folders (Drive titles use their Drive file ID). */
object LocalIds {
    const val PREFIX = "local:"
}

/** The copy to play and where to start; platform players turn it into their own media source. */
data class SelectedCopy(val kind: SourceKind, val sourceKey: String, val resumePositionMs: Long)

/**
 * Picks the best available copy of a title (local first, then Drive) and the resume position from the most
 * recently watched copy (progress model A). Null when every copy is currently unavailable.
 */
class PlaybackSelector(private val dao: MediaSourceDao) {

    suspend fun select(mediaItemId: String, requestedStartMs: Long = 0L): SelectedCopy? {
        val latest = dao.getLatestProgressForItem(mediaItemId)
        val resume = when {
            latest == null -> requestedStartMs
            latest.isCompleted -> 0L
            else -> latest.positionMs
        }

        val sources = dao.getSourcesForItem(mediaItemId)
        if (sources.isEmpty()) {
            // Pre-C.4 rows without source records are Drive files keyed by their Drive ID.
            return if (mediaItemId.startsWith(LocalIds.PREFIX)) null
            else SelectedCopy(SourceKind.DRIVE, mediaItemId, resume)
        }
        val best = BestSourceSelector.choose(sources) ?: return null
        return SelectedCopy(best.kind, best.sourceKey, resume)
    }
}
