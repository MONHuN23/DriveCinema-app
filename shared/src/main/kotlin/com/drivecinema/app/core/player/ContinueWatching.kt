package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.dao.WatchHistoryRow
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.player.WatchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Continue Watching with one card per series (Milestone J.2), shared by phone, TV and desktop.
 *
 * - Films: one card while unfinished, as before.
 * - Series: one card for the most recently watched episode. Unfinished → resume it. Watched → "Up next" with
 *   the following episode (resuming it if it was started before); no following episode → no card.
 */
object ContinueWatching {
    const val LIMIT = 20

    @OptIn(ExperimentalCoroutinesApi::class)
    fun flow(db: DriveCinemaDatabase): Flow<List<ContinueWatchingItem>> =
        db.mediaSourceDao().getWatchHistoryFlow().mapLatest { history ->
            val dao = db.mediaDao()
            build(
                history,
                episodesOf = { seriesId -> dao.getEpisodesForSeries(seriesId) },
                seriesOf = { seriesId -> dao.getSeriesById(seriesId) }
            )
        }

    /** [history] is newest first (see `MediaSourceDao.getWatchHistoryFlow`). */
    suspend fun build(
        history: List<WatchHistoryRow>,
        episodesOf: suspend (seriesId: String) -> List<MediaItemEntity>,
        seriesOf: suspend (seriesId: String) -> SeriesEntity?
    ): List<ContinueWatchingItem> {
        val byId = history.associateBy { it.id }
        val seenSeries = mutableSetOf<String>()
        val result = mutableListOf<ContinueWatchingItem>()
        for (row in history) {
            if (result.size >= LIMIT) break
            val seriesId = row.seriesId
            if (row.type != MediaType.EPISODE || seriesId == null) {
                if (!row.isCompleted) result += row.toFilmItem()
                continue
            }
            if (!seenSeries.add(seriesId)) continue
            val series = seriesOf(seriesId)
            if (!row.isCompleted) {
                result += row.toEpisodeItem(series)
                continue
            }
            val next = NextEpisode.after(episodesOf(seriesId), row.id) ?: continue
            val nextProgress = byId[next.id]?.takeIf { !it.isCompleted }
            result += ContinueWatchingItem(
                id = next.id,
                title = series?.title ?: next.title,
                type = MediaType.EPISODE,
                posterPath = series?.posterPath ?: next.posterPath,
                backdropPath = next.backdropPath ?: series?.backdropPath,
                positionMs = nextProgress?.positionMs ?: 0L,
                durationMs = nextProgress?.durationMs ?: 0L,
                // Ordered by when the finished episode was watched, so the series keeps its place.
                lastWatchedAt = row.lastWatchedAt,
                seriesId = seriesId,
                episodeCode = episodeCode(next.seasonNumber, next.episodeNumber),
                isUpNext = nextProgress == null
            )
        }
        return result
    }

    /** Second line of a card: "S1E4 · 12 min left", "Up next · S1E5", or "45 min left" for a film. */
    fun subtitle(item: ContinueWatchingItem): String? {
        val code = item.episodeCode
        if (item.isUpNext) return listOfNotNull("Up next", code).joinToString(" · ")
        val left = WatchState.label(item.positionMs, item.durationMs, isCompleted = false)
        return listOfNotNull(code, left).joinToString(" · ").ifEmpty { null }
    }

    /** Title for the player: "Series - S1E4" for episodes, the title itself for films. */
    fun playerTitle(item: ContinueWatchingItem): String =
        item.episodeCode?.let { "${item.title} - $it" } ?: item.title

    private fun episodeCode(season: Int?, episode: Int?): String = "S${season ?: 0}E${episode ?: 0}"

    private fun WatchHistoryRow.toFilmItem() = ContinueWatchingItem(
        id = id, title = title, type = type, posterPath = posterPath, backdropPath = backdropPath,
        positionMs = positionMs, durationMs = durationMs, lastWatchedAt = lastWatchedAt
    )

    private fun WatchHistoryRow.toEpisodeItem(series: SeriesEntity?) =
        ContinueWatchingItem(
            id = id,
            title = series?.title ?: title,
            type = MediaType.EPISODE,
            posterPath = series?.posterPath ?: posterPath,
            backdropPath = backdropPath ?: series?.backdropPath,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedAt = lastWatchedAt,
            seriesId = seriesId,
            episodeCode = episodeCode(seasonNumber, episodeNumber)
        )
}
