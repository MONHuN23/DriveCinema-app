package com.drivecinema.desktop.browse

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.ui.player.PlaybackTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class HomeContent(
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val movies: List<MediaItemEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList()
) {
    val isEmpty: Boolean get() = continueWatching.isEmpty() && movies.isEmpty() && series.isEmpty()
}

data class SearchResults(
    val query: String = "",
    val movies: List<MediaItemEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList()
) {
    val isEmpty: Boolean get() = movies.isEmpty() && series.isEmpty()
}

data class MovieDetails(val movie: MediaItemEntity, val progress: PlaybackProgressEntity?) {
    /** "Resume from 1:02:03" when there is an unfinished position worth resuming. */
    val resumePositionMs: Long?
        get() = progress?.takeIf { !it.isCompleted && it.positionMs > 0 }?.positionMs
}

data class SeriesDetails(
    val series: SeriesEntity,
    val seasons: Map<Int, List<MediaItemEntity>>,
    /** Latest progress per episode ID, across Drive and local copies. */
    val progress: Map<String, PlaybackProgressEntity> = emptyMap()
)

/** Where a Continue Watching card leads. */
sealed interface BrowseTarget {
    data class Movie(val id: String, val title: String) : BrowseTarget
    data class Series(val id: String, val title: String) : BrowseTarget
}

/** Read models over the shared database for the desktop screens (same queries as Android). */
class BrowseModels(private val db: DriveCinemaDatabase) {

    fun home(): Flow<HomeContent> = combine(
        ContinueWatching.flow(db),
        db.mediaDao().getAllMoviesFlow(),
        db.mediaDao().getAllSeriesFlow()
    ) { continueWatching, movies, series -> HomeContent(continueWatching, movies, series) }

    /** Debounced like the Android search (300 ms); blank queries show nothing. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun search(query: MutableStateFlow<String>): Flow<SearchResults> = query
        .debounce(SEARCH_DEBOUNCE_MS)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isEmpty()) flowOf(SearchResults())
            else combine(db.mediaDao().searchMoviesFlow(q), db.mediaDao().searchSeriesFlow(q)) { movies, series ->
                SearchResults(q, movies, series)
            }
        }

    /** Null when the film no longer exists. Progress is aggregated over all copies (Drive and local). */
    fun movie(id: String): Flow<MovieDetails?> = combine(
        flow { emit(db.mediaDao().getMediaItemById(id)) },
        db.mediaSourceDao().getLatestProgressForItemFlow(id)
    ) { movie, progress -> movie?.let { MovieDetails(it, progress) } }

    fun series(id: String): Flow<SeriesDetails?> = combine(
        db.mediaDao().getSeriesByIdFlow(id),
        db.mediaDao().getEpisodesForSeriesFlow(id),
        db.mediaSourceDao().getSeriesProgressFlow(id)
    ) { series, episodes, progress ->
        series?.let {
            SeriesDetails(it, episodes.groupBy { ep -> ep.seasonNumber ?: 1 }.toSortedMap(), progress.associateBy { p -> p.mediaItemId })
        }
    }

    suspend fun target(item: ContinueWatchingItem): BrowseTarget? = when (item.type) {
        MediaType.MOVIE -> BrowseTarget.Movie(item.id, item.title)
        MediaType.EPISODE -> db.mediaDao().getMediaItemById(item.id)?.seriesId
            ?.let { db.mediaDao().getSeriesById(it) }
            ?.let { BrowseTarget.Series(it.id, it.title) }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L

        fun progressFraction(positionMs: Long, durationMs: Long): Float =
            PlaybackTimeUtils.calculateProgress(positionMs, durationMs)

        fun formatRuntime(seconds: Int?): String? {
            val minutes = (seconds ?: return null) / 60
            if (minutes <= 0) return null
            val h = minutes / 60
            val m = minutes % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
    }
}
