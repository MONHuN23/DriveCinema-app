package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity

/** Finds the episode before the one being played, for the player's "Previous episode" button. */
object PreviousEpisode {

    /** The episode before [currentId] in season/episode order (across seasons), or null for the first one or a film. */
    fun before(episodes: List<MediaItemEntity>, currentId: String): MediaItemEntity? {
        val ordered = NextEpisode.ordered(episodes)
        val index = ordered.indexOfFirst { it.id == currentId }
        return if (index <= 0) null else ordered[index - 1]
    }

    /** (catalog ID, label) of the episode before [mediaItemId], or null for films and first episodes. */
    suspend fun find(dao: MediaDao, mediaItemId: String): Pair<String, String>? {
        val seriesId = dao.getMediaItemById(mediaItemId)?.seriesId ?: return null
        val previous = before(dao.getEpisodesForSeries(seriesId), mediaItemId) ?: return null
        val seriesTitle = dao.getSeriesById(seriesId)?.title.orEmpty()
        return previous.id to NextEpisode.label(seriesTitle, previous)
    }
}
