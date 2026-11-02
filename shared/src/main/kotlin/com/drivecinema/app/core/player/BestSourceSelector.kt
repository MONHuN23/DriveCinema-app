package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.SourceKind

/**
 * Picks which copy of a title to play: an available local file first (offline, instant start), then a copy on a
 * paired PC (home network, Task I.2), then Drive.
 */
object BestSourceSelector {
    fun choose(sources: List<MediaSourceEntity>): MediaSourceEntity? = ranked(sources).firstOrNull()

    /** All available copies, best first: the player falls back to the next one when a PC turns out to be off. */
    fun ranked(sources: List<MediaSourceEntity>): List<MediaSourceEntity> =
        sources
            .filter { it.isAvailable }
            .sortedWith(
                compareBy<MediaSourceEntity> {
                    when (it.kind) {
                        SourceKind.LOCAL -> 0
                        SourceKind.HOST -> 1
                        SourceKind.DRIVE -> 2
                    }
                }.thenByDescending { it.lastSeenAt }
            )
}
