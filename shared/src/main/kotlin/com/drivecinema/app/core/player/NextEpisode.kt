package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.entity.MediaItemEntity

/** Finds the episode after the one being played, for the player's "Next Episode" button. */
object NextEpisode {

    /** The episode after [currentId] in season/episode order (across seasons), or null for the last one or a film. */
    fun after(episodes: List<MediaItemEntity>, currentId: String): MediaItemEntity? {
        val ordered = episodes.sortedWith(
            compareBy<MediaItemEntity>({ it.seasonNumber ?: Int.MAX_VALUE }, { it.episodeNumber ?: Int.MAX_VALUE })
        )
        val index = ordered.indexOfFirst { it.id == currentId }
        return if (index < 0) null else ordered.getOrNull(index + 1)
    }

    /** "Series - S1E2 Title", the same label the episode list uses when it starts playback. */
    fun label(seriesTitle: String, episode: MediaItemEntity): String {
        val code = "S${episode.seasonNumber ?: 0}E${episode.episodeNumber ?: 0}"
        return if (episode.title.isNotEmpty()) "$seriesTitle - $code ${episode.title}" else "$seriesTitle - $code"
    }
}
