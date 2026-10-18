package com.drivecinema.app.ui.player

import com.drivecinema.app.core.database.dao.MediaSourceDao
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.player.PlaybackSelector

sealed interface ResolvedPlayback {
    data class Playable(val source: PlaybackSource, val resumePositionMs: Long) : ResolvedPlayback
    /** Every copy of the title is currently unavailable (e.g. local folder removed, Drive file gone). */
    data object Unavailable : ResolvedPlayback
}

/**
 * Turns a catalog title into something the player can open: the best available copy (local first, then Drive)
 * and the resume position from the most recently watched copy (progress model A).
 */
class PlaybackResolver(private val dao: MediaSourceDao) {

    // The selection rules live in :shared (PlaybackSelector) so the desktop player follows the same ones.
    private val selector = PlaybackSelector(dao)

    suspend fun resolve(mediaItemId: String, requestedStartMs: Long = 0L): ResolvedPlayback {
        val copy = selector.select(mediaItemId, requestedStartMs) ?: return ResolvedPlayback.Unavailable
        val source = when (copy.kind) {
            SourceKind.LOCAL -> PlaybackSource.Local(copy.sourceKey)
            SourceKind.DRIVE -> PlaybackSource.Drive(copy.sourceKey)
        }
        return ResolvedPlayback.Playable(source, copy.resumePositionMs)
    }
}
