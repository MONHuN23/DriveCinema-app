package com.drivecinema.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.metadata.MediaInfoFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodeItem(
    val id: String?,
    val title: String = "",
    val season: Int,
    val episode: Int,
    val overview: String? = null,
    val stillPath: String? = null,
    val isAvailable: Boolean = !id.isNullOrEmpty(),
    val isCompleted: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val voteAverage: Double? = null,
    /** Task L.3: "48 min · 1080p" from the best-known copy; null when nothing is known. */
    val info: String? = null
)

data class MovieDetailsState(
    val movie: MediaItemEntity? = null,
    val progress: PlaybackProgressEntity? = null,
    val isLoading: Boolean = false,
    /** Task L.3: one line per available copy, in play order. */
    val infoLines: List<String> = emptyList()
)

data class SeriesDetailsState(
    val series: SeriesEntity? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val nextEpisode: EpisodeItem? = null,
    val isLoading: Boolean = false
)

/**
 * Film and series details (phone and TV). Progress and copies are live (F-36), so the watched marks and the Play
 * label follow the player; everything runs in [viewModelScope], cancelled when the details page is popped off the
 * back stack (F-35; the ViewModel is entry-scoped since Task M.3).
 */
class DetailsViewModel(
    private val mediaDao: MediaDao,
    /** A title's progress, live, aggregated across its Drive and local copies. */
    private val progressForItem: (String) -> Flow<PlaybackProgressEntity?> = { flowOf(null) },
    /** Every episode's progress in one query, live, keyed by the catalog episode ID (F-36: not one per episode). */
    private val progressForSeries: (String) -> Flow<List<PlaybackProgressEntity>> = { flowOf(emptyList()) },
    /** Task L.3: a title's available copies, live, so details saved by the player show up on return. */
    private val sourcesForItem: (String) -> Flow<List<MediaSourceEntity>> = { flowOf(emptyList()) },
    /** Task L.3: every available copy of a series' episodes, live. */
    private val sourcesForSeries: (String) -> Flow<List<MediaSourceEntity>> = { flowOf(emptyList()) }
) : ViewModel() {

    companion object {
        /** The app's wiring: progress aggregated across each title's Drive and local copies, all live. */
        fun from(database: DriveCinemaDatabase): DetailsViewModel {
            val sources = database.mediaSourceDao()
            return DetailsViewModel(
                database.mediaDao(),
                progressForItem = sources::getLatestProgressForItemFlow,
                progressForSeries = sources::getSeriesProgressFlow,
                sourcesForItem = sources::getAvailableSourcesForItemFlow,
                sourcesForSeries = sources::getAvailableSourcesForSeriesFlow
            )
        }

        /** The details screen's lines: one per copy, in play order; copies with nothing known are left out. */
        fun infoLines(copies: List<MediaSourceEntity>): List<String> =
            MediaInfoFormat.sortCopies(copies).mapNotNull { MediaInfoFormat.infoLine(it) }

        /** Episode id → its row's short line, from the copy with the most known details. */
        fun episodeInfo(copies: List<MediaSourceEntity>): Map<String, String> =
            copies.groupBy { it.mediaItemId }
                .mapNotNull { (id, list) -> MediaInfoFormat.bestKnown(list)?.let(MediaInfoFormat::shortLine)?.let { id to it } }
                .toMap()

        fun findNextTargetEpisode(episodes: List<EpisodeItem>): EpisodeItem? {
            val available = episodes.filter { it.isAvailable }
            if (available.isEmpty()) return null
            return available.firstOrNull { !it.isCompleted } ?: available.first()
        }

        /** The episode rows: catalog episodes with their progress (by catalog ID) and info line. */
        fun episodeItems(
            episodes: List<MediaItemEntity>,
            progress: Map<String, PlaybackProgressEntity>,
            info: Map<String, String>
        ): List<EpisodeItem> = episodes.map { ep ->
            val prog = progress[ep.id]
            EpisodeItem(
                id = ep.id,
                title = ep.title,
                season = ep.seasonNumber ?: 1,
                episode = ep.episodeNumber ?: 1,
                overview = ep.overview,
                stillPath = ep.backdropPath ?: ep.posterPath,
                isAvailable = true,
                isCompleted = prog?.isCompleted ?: false,
                positionMs = prog?.positionMs ?: 0L,
                durationMs = prog?.durationMs ?: 0L,
                voteAverage = ep.voteAverage,
                info = info[ep.id]
            )
        }
    }

    private val _movieState = MutableStateFlow(MovieDetailsState())
    val movieState: StateFlow<MovieDetailsState> = _movieState.asStateFlow()

    private val _seriesState = MutableStateFlow(SeriesDetailsState())
    val seriesState: StateFlow<SeriesDetailsState> = _seriesState.asStateFlow()

    private var loadJob: Job? = null

    fun loadMovie(movieId: String) {
        loadJob?.cancel()
        _movieState.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            val movie = mediaDao.getMediaItemById(movieId)
            combine(progressForItem(movieId), sourcesForItem(movieId)) { progress, copies ->
                MovieDetailsState(movie = movie, progress = progress, isLoading = false, infoLines = infoLines(copies))
            }.collect { _movieState.value = it }
        }
    }

    fun loadSeries(seriesId: String) {
        loadJob?.cancel()
        _seriesState.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            val series = mediaDao.getSeriesById(seriesId) ?: mediaDao.getAllSeriesFlow().first().find { it.id == seriesId }
            val rawEpisodes = mediaDao.getEpisodesForSeries(seriesId)
            val seasons = rawEpisodes.map { it.seasonNumber ?: 1 }.distinct().sorted()
            var firstEmission = true

            combine(progressForSeries(seriesId), sourcesForSeries(seriesId)) { progress, copies ->
                episodeItems(rawEpisodes, progress.associateBy { it.mediaItemId }, episodeInfo(copies))
            }.collect { episodes ->
                // Later emissions (player returned, sync wrote) keep the season the user picked.
                val season = if (firstEmission) seasons.firstOrNull() ?: 1 else _seriesState.value.selectedSeason
                firstEmission = false
                _seriesState.value = SeriesDetailsState(
                    series = series,
                    seasons = seasons,
                    selectedSeason = season,
                    episodes = episodes,
                    nextEpisode = findNextTargetEpisode(episodes),
                    isLoading = false
                )
            }
        }
    }

    fun selectSeason(season: Int) {
        _seriesState.update { it.copy(selectedSeason = season) }
    }
}
