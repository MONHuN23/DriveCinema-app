package com.drivecinema.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.dao.PlaybackProgressDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val voteAverage: Double? = null
)

data class MovieDetailsState(
    val movie: MediaItemEntity? = null,
    val progress: PlaybackProgressEntity? = null,
    val isLoading: Boolean = false
)

data class SeriesDetailsState(
    val series: SeriesEntity? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val nextEpisode: EpisodeItem? = null,
    val isLoading: Boolean = false
)

class DetailsViewModel(
    private val mediaDao: MediaDao,
    private val progressDao: PlaybackProgressDao,
    /** Progress for a title; the app passes the value aggregated across its Drive and local copies. */
    private val latestProgress: suspend (String) -> PlaybackProgressEntity? = { progressDao.getProgress(it) }
) : ViewModel() {

    companion object {
        fun findNextTargetEpisode(episodes: List<EpisodeItem>): EpisodeItem? {
            val available = episodes.filter { it.isAvailable }
            if (available.isEmpty()) return null
            return available.firstOrNull { !it.isCompleted } ?: available.first()
        }
    }

    private val _movieState = MutableStateFlow(MovieDetailsState())
    val movieState: StateFlow<MovieDetailsState> = _movieState.asStateFlow()

    private val _seriesState = MutableStateFlow(SeriesDetailsState())
    val seriesState: StateFlow<SeriesDetailsState> = _seriesState.asStateFlow()

    fun loadMovie(movieId: String) {
        viewModelScope.launch {
            _movieState.value = _movieState.value.copy(isLoading = true)
            val movie = mediaDao.getMediaItemById(movieId)
            val progress = latestProgress(movieId)
            _movieState.value = MovieDetailsState(
                movie = movie,
                progress = progress,
                isLoading = false
            )
        }
    }

    fun loadSeries(seriesId: String) {
        viewModelScope.launch {
            _seriesState.value = _seriesState.value.copy(isLoading = true)
            val series = mediaDao.getSeriesById(seriesId) ?: mediaDao.getAllSeriesFlow().first().find { it.id == seriesId }
            val rawEpisodes = mediaDao.getEpisodesForSeriesFlow(seriesId).first()

            val episodeItems = rawEpisodes.map { ep ->
                val prog = latestProgress(ep.id)
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
                    voteAverage = ep.voteAverage
                )
            }

            val seasons = episodeItems.map { it.season }.distinct().sorted()
            val initialSeason = seasons.firstOrNull() ?: 1
            val nextEp = findNextTargetEpisode(episodeItems)

            _seriesState.value = SeriesDetailsState(
                series = series,
                seasons = seasons,
                selectedSeason = initialSeason,
                episodes = episodeItems,
                nextEpisode = nextEp,
                isLoading = false
            )
        }
    }

    fun selectSeason(season: Int) {
        _seriesState.value = _seriesState.value.copy(selectedSeason = season)
    }
}
