package com.drivecinema.app.ui.player

import com.drivecinema.app.core.database.dao.MediaSourceDao
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.host.ConnectionVerdict
import com.drivecinema.app.core.player.PlaybackSelector

sealed interface ResolvedPlayback {
    data class Playable(val source: PlaybackSource, val resumePositionMs: Long) : ResolvedPlayback
    /** Every copy of the title is currently unavailable (e.g. local folder removed, Drive file gone). */
    data object Unavailable : ResolvedPlayback

    /**
     * Task I.2: the best copy is on a PC, but the connection to it is too slow for this film (design §2.6). The user
     * picks: [pc] anyway, or [fallback] (the next copy, usually Drive) when there is one.
     */
    data class SlowConnection(val pc: Playable, val fallback: Playable?, val verdict: ConnectionVerdict) : ResolvedPlayback
}

/**
 * Report F-29: Next Episode plays a [ResolvedPlayback.Playable] next episode in the same player (no trip through the
 * home screen); anything that needs a dialog or a message (slow connection to the PC, nothing available) is handed
 * back to MainActivity, which handles it as before. Null = hand back.
 */
fun ResolvedPlayback.playableInPlace(): ResolvedPlayback.Playable? = this as? ResolvedPlayback.Playable

/**
 * Milestone D: Play on TV starts at the phone's position, not at this device's own saved progress (which
 * [PlaybackSelector] prefers): every copy, the slow-PC choice's included, starts at [positionMs].
 */
fun ResolvedPlayback.withStartPosition(positionMs: Long): ResolvedPlayback = when (this) {
    is ResolvedPlayback.Playable -> copy(resumePositionMs = positionMs)
    is ResolvedPlayback.SlowConnection -> copy(pc = pc.copy(resumePositionMs = positionMs), fallback = fallback?.copy(resumePositionMs = positionMs))
    ResolvedPlayback.Unavailable -> this
}

/**
 * Turns a catalog title into something the player can open: the best available copy (local, then a paired PC, then
 * Drive) and the resume position from the most recently watched copy (progress model A).
 */
class PlaybackResolver(
    private val dao: MediaSourceDao,
    /** Task I.2: the HTTPS URL of a PC copy, or null when that PC can't be reached right now (blocking: call on IO). */
    private val hostUrl: (sourceKey: String) -> String? = { null },
    /** Task I.2: measures the connection to the PC for this title; null when it can't tell. */
    private val checkHost: suspend (mediaItemId: String, sourceKey: String) -> ConnectionVerdict? = { _, _ -> null }
) {

    // The selection rules live in :shared (PlaybackSelector) so the desktop player follows the same ones.
    private val selector = PlaybackSelector(dao)

    suspend fun resolve(mediaItemId: String, requestedStartMs: Long = 0L): ResolvedPlayback {
        val playable = mutableListOf<ResolvedPlayback.Playable>()
        // Best copy first; a PC that turned out to be off is skipped for the next copy (usually Drive).
        for (copy in selector.candidates(mediaItemId, requestedStartMs)) {
            val source = when (copy.kind) {
                SourceKind.LOCAL -> PlaybackSource.Local(copy.sourceKey)
                SourceKind.DRIVE -> PlaybackSource.Drive(copy.sourceKey)
                SourceKind.HOST -> hostUrl(copy.sourceKey)?.let { PlaybackSource.Host(copy.sourceKey, it) } ?: continue
            }
            playable += ResolvedPlayback.Playable(source, copy.resumePositionMs)
            if (playable.size == 2 || source !is PlaybackSource.Host) break
        }
        val best = playable.firstOrNull() ?: return ResolvedPlayback.Unavailable
        val pc = best.source as? PlaybackSource.Host ?: return best
        val verdict = checkHost(mediaItemId, pc.sourceKey)?.takeIf { it.tooSlow } ?: return best
        return ResolvedPlayback.SlowConnection(best, playable.getOrNull(1), verdict)
    }
}
