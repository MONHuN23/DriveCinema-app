package com.drivecinema.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val movies: List<MediaItemEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isEmptyQuery: Boolean = true
) {
    val totalCount: Int get() = movies.size + series.size
    val isNoResults: Boolean get() = !isEmptyQuery && !isLoading && totalCount == 0
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val mediaDao: MediaDao,
    private val debounceMs: Long = 300L
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)

    private val _searchResults = _query
        .debounce { q -> if (q.isEmpty()) 0L else debounceMs }
        .distinctUntilChanged()
        .flatMapLatest { debouncedQuery ->
            val trimmed = debouncedQuery.trim()
            val isQueryEmpty = trimmed.isEmpty()
            val moviesFlow = if (isQueryEmpty) {
                mediaDao.getAllMoviesFlow()
            } else {
                mediaDao.searchMoviesFlow(trimmed)
            }
            val seriesFlow = if (isQueryEmpty) {
                mediaDao.getAllSeriesFlow()
            } else {
                mediaDao.searchSeriesFlow(trimmed)
            }

            combine(moviesFlow, seriesFlow) { movies, series ->
                _isLoading.value = false
                Pair(movies, series)
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        _query,
        _isLoading,
        _searchResults
    ) { currentQuery, isLoading, (movies, series) ->
        SearchUiState(
            query = currentQuery,
            movies = movies,
            series = series,
            isLoading = isLoading,
            isEmptyQuery = currentQuery.isBlank()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SearchUiState(isLoading = false, isEmptyQuery = true)
    )

    fun onQueryChanged(newQuery: String) {
        if (_query.value != newQuery) {
            _isLoading.value = newQuery.isNotBlank()
            _query.value = newQuery
        }
    }

    fun clearQuery() {
        _isLoading.value = false
        _query.value = ""
    }
}
