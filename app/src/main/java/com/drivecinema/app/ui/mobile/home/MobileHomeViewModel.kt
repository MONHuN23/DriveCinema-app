package com.drivecinema.app.ui.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val account: AccountUiState = AccountUiState()
) {
    val hasContinueWatching: Boolean
        get() = continueWatching.isNotEmpty()
}

class MobileHomeViewModel(
    mediaDao: MediaDao,
    initialAccount: AccountUiState = AccountUiState(),
    /** The app passes `ContinueWatching.flow` (one card per series, Milestone J.2). */
    continueWatching: Flow<List<ContinueWatchingItem>> = mediaDao.getContinueWatchingFlow()
) : ViewModel() {

    private val _accountState = MutableStateFlow(initialAccount)

    fun updateAccount(account: AccountUiState) {
        _accountState.value = account
    }

    val uiState: StateFlow<MobileHomeUiState> = combine(
        continueWatching,
        mediaDao.getAllMoviesFlow(),
        mediaDao.getAllSeriesFlow(),
        _accountState
    ) { continueWatching, movies, series, account ->
        MobileHomeUiState(
            continueWatching = continueWatching,
            movies = movies,
            series = series,
            isLoading = false,
            account = account
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MobileHomeUiState(isLoading = true, account = initialAccount)
    )
}
