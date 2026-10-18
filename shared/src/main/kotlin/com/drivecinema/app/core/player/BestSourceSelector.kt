package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.SourceKind

/** Picks which copy of a title to play: an available local file first (offline, instant start), else Drive. */
object BestSourceSelector {
    fun choose(sources: List<MediaSourceEntity>): MediaSourceEntity? =
        sources
            .filter { it.isAvailable }
            .sortedWith(
                compareBy<MediaSourceEntity> { if (it.kind == SourceKind.LOCAL) 0 else 1 }
                    .thenByDescending { it.lastSeenAt }
            )
            .firstOrNull()
}
