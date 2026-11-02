package com.drivecinema.app.ui.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.dao.SeriesEpisodeCount
import com.drivecinema.app.core.database.dao.WatchHistoryRow
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.mobile.grid.MobileLibrary
import com.drivecinema.app.ui.player.WatchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class AccountUiState(
    val displayName: String? = null,
    val email: String? = null,
    val photoLink: String? = null,
    val isConnected: Boolean = false
)

data class MobileHomeUiState(
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val movies: List<MediaItemEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val isLoading: Boolean = false,
    val account: AccountUiState = AccountUiState(),
    /** For the Library's watch-state chips: films and series with any history; missing = unwatched. */
    val filmStates: Map<String, WatchState> = emptyMap(),
    val seriesStates: Map<String, WatchState> = emptyMap()
) {
    val hasContinueWatching: Boolean
        get() = continueWatching.isNotEmpty()
}

private data class WatchStates(val films: Map<String, WatchState>, val series: Map<String, WatchState>)

class MobileHomeViewModel(
    mediaDao: MediaDao,
    initialAccount: AccountUiState = AccountUiState(),
    /** The app passes `ContinueWatching.flow` (one card per series, Milestone J.2). */
    continueWatching: Flow<List<ContinueWatchingItem>> = mediaDao.getContinueWatchingFlow(),
    /** The app passes `MediaSourceDao.getWatchHistoryFlow()`; it feeds the Library's watch-state chips. */
    watchHistory: Flow<List<WatchHistoryRow>> = flowOf(emptyList()),
    episodeCounts: Flow<List<SeriesEpisodeCount>> = mediaDao.getEpisodeCountsFlow()
) : ViewModel() {

    private val _accountState = MutableStateFlow(initialAccount)

    fun updateAccount(account: AccountUiState) {
        _accountState.value = account
    }

    private val watchStates: Flow<WatchStates> = combine(watchHistory, episodeCounts) { history, counts ->
        WatchStates(MobileLibrary.filmStates(history), MobileLibrary.seriesStates(history, counts))
    }

    val uiState: StateFlow<MobileHomeUiState> = combine(
        continueWatching,
        mediaDao.getAllMoviesFlow(),
        mediaDao.getAllSeriesFlow(),
        _accountState,
        watchStates
    ) { continueWatching, movies, series, account, states ->
        MobileHomeUiState(
            continueWatching = continueWatching,
            movies = movies,
            series = series,
            isLoading = false,
            account = account,
            filmStates = states.films,
            seriesStates = states.series
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MobileHomeUiState(isLoading = true, account = initialAccount)
    )
}
