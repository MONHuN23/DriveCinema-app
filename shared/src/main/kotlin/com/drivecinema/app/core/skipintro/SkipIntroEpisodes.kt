package com.drivecinema.app.core.skipintro

import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.dao.MediaSourceDao
import com.drivecinema.app.core.database.entity.MediaType

/** The show and episode numbers to ask the intro services about; the file length is added once the player knows it. */
data class SkipIntroEpisode(val tmdbShowId: Int, val season: Int, val episode: Int) {
    fun request(fileLengthMs: Long) = SkipIntroRequest(tmdbShowId, season, episode, fileLengthMs)
}

/**
 * Finds the episode behind a catalog item or a playing copy. Null for films (D4), for specials (season 0), for
 * episodes without numbers and for shows TMDB hasn't matched: no lookup at all for those.
 */
object SkipIntroEpisodes {

    suspend fun forItem(mediaDao: MediaDao, itemId: String): SkipIntroEpisode? {
        val item = mediaDao.getMediaItemById(itemId) ?: return null
        if (item.type != MediaType.EPISODE) return null
        val season = item.seasonNumber?.takeIf { it >= 1 } ?: return null
        val episode = item.episodeNumber?.takeIf { it >= 1 } ?: return null
        val seriesId = item.seriesId ?: return null
        val tmdbShowId = mediaDao.getSeriesById(seriesId)?.tmdbId?.takeIf { it > 0 } ?: return null
        return SkipIntroEpisode(tmdbShowId, season, episode)
    }

    /** The phone/TV player knows only the copy's key (its `media_sources.sourceKey`, also its progress key). */
    suspend fun forSource(mediaSourceDao: MediaSourceDao, mediaDao: MediaDao, sourceKey: String): SkipIntroEpisode? {
        val itemId = mediaSourceDao.getSource(sourceKey)?.mediaItemId ?: sourceKey
        return forItem(mediaDao, itemId)
    }
}
