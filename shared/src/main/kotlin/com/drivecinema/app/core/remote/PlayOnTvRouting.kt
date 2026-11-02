package com.drivecinema.app.core.remote

import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.dao.MediaSourceDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.player.LocalIds
import com.drivecinema.app.core.player.PlaybackProgressRules

/** The catalog reads Play on TV needs, so the routing is tested with a fake. */
interface PlayCatalog {
    suspend fun item(id: String): MediaItemEntity?
    suspend fun series(id: String): SeriesEntity?
    suspend fun movies(): List<MediaItemEntity>
    suspend fun allSeries(): List<SeriesEntity>
    suspend fun episodes(seriesId: String): List<MediaItemEntity>
}

class MediaDaoPlayCatalog(private val dao: MediaDao) : PlayCatalog {
    override suspend fun item(id: String) = dao.getMediaItemById(id)
    override suspend fun series(id: String) = dao.getSeriesById(id)
    override suspend fun movies() = dao.getAllMovies()
    override suspend fun allSeries() = dao.getAllSeries()
    override suspend fun episodes(seriesId: String) = dao.getEpisodesForSeries(seriesId)
}

/** What the TV does with a valid request: the status to answer and, for [PlayStatus.OK], the TV's catalog ID to play. */
data class PlayDecision(val status: PlayStatus, val mediaItemId: String? = null)

object PlayOnTvRouting {
    /**
     * The TV's own catalog ID for [request] (design §5): the phone's ID when the TV has that title (Drive titles have the
     * same ID on both), else a film by TMDB ID or an episode by its series' TMDB ID + season + episode. `local:` IDs
     * are per device, so they are only ever matched through TMDB. Null when the TV doesn't have the title.
     */
    suspend fun find(catalog: PlayCatalog, request: PlayRequest): String? {
        if (!request.mediaItemId.startsWith(LocalIds.PREFIX)) {
            catalog.item(request.mediaItemId)?.takeIf { !it.isDeleted && it.isEpisode() == request.isEpisode }?.let { return it.id }
        }
        val tmdbId = request.tmdbId ?: return null
        if (!request.isEpisode) return catalog.movies().firstOrNull { it.tmdbId == tmdbId && !it.isDeleted }?.id
        val season = request.season ?: return null
        val episode = request.episode ?: return null
        return catalog.allSeries().filter { it.tmdbId == tmdbId }.firstNotNullOfOrNull { series ->
            catalog.episodes(series.id).firstOrNull { it.seasonNumber == season && it.episodeNumber == episode && !it.isDeleted }?.id
        }
    }

    suspend fun decide(signedIn: Boolean, catalog: PlayCatalog, request: PlayRequest): PlayDecision {
        if (!signedIn) return PlayDecision(PlayStatus.NOT_SIGNED_IN)
        val id = find(catalog, request) ?: return PlayDecision(PlayStatus.NOT_IN_LIBRARY)
        return PlayDecision(PlayStatus.OK, id)
    }

    /**
     * The phone's request for [mediaItemId]: identity from its catalog and the position where the phone left off (its
     * latest progress across copies, with the usual resume rules: finished or under 10 s starts at 0).
     */
    suspend fun buildRequest(catalog: PlayCatalog, sources: MediaSourceDao, mediaItemId: String, title: String): PlayRequest {
        val item = catalog.item(mediaItemId)
        val isEpisode = item?.isEpisode() ?: false
        val tmdbId = if (isEpisode) item?.seriesId?.let { catalog.series(it) }?.tmdbId else item?.tmdbId
        val progress = sources.getLatestProgressForItem(mediaItemId)
        val position = progress?.let { PlaybackProgressRules.resolveResumeOffset(it.isCompleted, it.positionMs) } ?: 0L
        return PlayRequest(
            mediaItemId = mediaItemId,
            isEpisode = isEpisode,
            tmdbId = tmdbId,
            season = item?.seasonNumber.takeIf { isEpisode },
            episode = item?.episodeNumber.takeIf { isEpisode },
            title = title.take(PlayOnTvProtocol.MAX_TITLE),
            positionMs = position.coerceIn(0, PlayOnTvProtocol.MAX_POSITION_MS)
        )
    }

    private fun MediaItemEntity.isEpisode() = type == MediaType.EPISODE
}

/** How a Play on TV attempt ended on the phone (design §7). */
sealed interface PlayOnTvOutcome {
    data class Answered(val status: PlayStatus) : PlayOnTvOutcome
    data object NoWifi : PlayOnTvOutcome
    data object NotFound : PlayOnTvOutcome
    data object UnknownPhone : PlayOnTvOutcome
    data object Failed : PlayOnTvOutcome
}

object PlayOnTvMessages {
    fun of(outcome: PlayOnTvOutcome, tvName: String, title: String): String = when (outcome) {
        is PlayOnTvOutcome.Answered -> when (outcome.status) {
            PlayStatus.OK -> "Playing on $tvName"
            PlayStatus.NOT_IN_LIBRARY -> "'$title' isn't in the TV's library. Refresh the library on the TV, or it is only on this phone."
            PlayStatus.NOT_SIGNED_IN -> "DriveCinema on $tvName isn't signed in."
            PlayStatus.BAD_REQUEST -> "$tvName couldn't read the request. Update DriveCinema on both devices."
        }
        PlayOnTvOutcome.NoWifi -> "Connect the phone to the same Wi-Fi as the TV."
        PlayOnTvOutcome.NotFound -> "Can't find $tvName. Open DriveCinema on the TV and check both are on the same Wi-Fi."
        PlayOnTvOutcome.UnknownPhone -> "$tvName doesn't recognise this phone any more. Link it again: on the TV, Account → Link a phone."
        PlayOnTvOutcome.Failed -> "Something went wrong talking to $tvName. Try again."
    }

    const val OLD_TV = "Paired. To use Play on TV, update DriveCinema on the TV and link again (TV: Account → Link a phone)."
}
