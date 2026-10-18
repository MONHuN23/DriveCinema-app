package com.drivecinema.app.ui.search

import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testMovies = listOf(
        MediaItemEntity(id = "m1", type = MediaType.MOVIE, title = "Spider-Man: Homecoming", parentFolderId = "f1"),
        MediaItemEntity(id = "m2", type = MediaType.MOVIE, title = "Dune: Part Two", parentFolderId = "f1")
    )
    private val testSeries = listOf(
        SeriesEntity(id = "s1", title = "Breaking Bad"),
        SeriesEntity(id = "s2", title = "Dark")
    )

    private val fakeDao = object : MediaDao {
        override suspend fun insertMediaItems(items: List<MediaItemEntity>) {}
        override suspend fun insertMediaItem(item: MediaItemEntity) {}
        override suspend fun insertSeries(series: List<SeriesEntity>) {}
        override suspend fun insertSingleSeries(series: SeriesEntity) {}
        override suspend fun getSeriesById(id: String): SeriesEntity? = testSeries.find { it.id == id }
        override fun getSeriesByIdFlow(id: String): Flow<SeriesEntity?> = flowOf(testSeries.find { it.id == id })
        override suspend fun getMediaItemById(id: String): MediaItemEntity? = null
        override suspend fun getMediaItemsByIds(ids: List<String>): List<MediaItemEntity> = emptyList()
        override suspend fun getSeriesByIds(ids: List<String>): List<SeriesEntity> = emptyList()
        override fun getAllMoviesFlow(): Flow<List<MediaItemEntity>> = flowOf(testMovies)
        override suspend fun getAllMovies(): List<MediaItemEntity> = testMovies
        override suspend fun getUnenrichedMovies(): List<MediaItemEntity> = testMovies.filter { it.tmdbId == null }
        override fun getAllSeriesFlow(): Flow<List<SeriesEntity>> = flowOf(testSeries)
        override suspend fun getAllSeries(): List<SeriesEntity> = testSeries
        override suspend fun getUnenrichedSeries(): List<SeriesEntity> = testSeries.filter { it.tmdbId == null }
        override fun getEpisodesForSeriesFlow(seriesId: String): Flow<List<MediaItemEntity>> = flowOf(emptyList())
        override suspend fun getEpisodesForSeries(seriesId: String): List<MediaItemEntity> = emptyList()
        override suspend fun getItemsInFolder(folderId: String): List<MediaItemEntity> = emptyList()
        override suspend fun markDeleted(id: String) {}
        override suspend fun markFolderItemsDeleted(folderId: String) {}
        override suspend fun markSourcesUnavailable(kind: com.drivecinema.app.core.database.entity.SourceKind) {}
        override suspend fun insertSourcesIfAbsent(sources: List<com.drivecinema.app.core.database.entity.MediaSourceEntity>) {}
        override suspend fun markSourcesAvailable(sourceKeys: List<String>, libraryRootId: String?, seenAt: Long) {}
        override suspend fun refreshVisibilityFromSources() {}
        override fun getContinueWatchingFlow(): Flow<List<ContinueWatchingItem>> = flowOf(emptyList())

        override fun searchMoviesFlow(query: String): Flow<List<MediaItemEntity>> {
            if (query.isEmpty()) return flowOf(testMovies)
            return flowOf(testMovies.filter { it.title.contains(query, ignoreCase = true) })
        }

        override fun searchSeriesFlow(query: String): Flow<List<SeriesEntity>> {
            if (query.isEmpty()) return flowOf(testSeries)
            return flowOf(testSeries.filter { it.title.contains(query, ignoreCase = true) })
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateIsEmptyQuery() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(fakeDao, debounceMs = 300L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.isEmptyQuery)
        assertFalse(state.isLoading)
        assertFalse(state.isNoResults)
        assertEquals(2, state.movies.size)
        assertEquals(2, state.series.size)
    }

    @Test
    fun testDebouncedQueryFiltersCatalog() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(fakeDao, debounceMs = 300L)
        advanceUntilIdle()

        // User enters "Dune"
        viewModel.onQueryChanged("Dune")

        // Before 300 ms debounce
        advanceTimeBy(100L)
        assertEquals("Dune", viewModel.uiState.value.query)

        // After 300 ms debounce
        advanceTimeBy(250L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Dune", state.query)
        assertFalse(state.isEmptyQuery)
        assertFalse(state.isLoading)
        assertFalse(state.isNoResults)
        assertEquals(1, state.movies.size)
        assertEquals("Dune: Part Two", state.movies[0].title)
        assertEquals(0, state.series.size)
    }

    @Test
    fun testClearQueryRestoresAllItems() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(fakeDao, debounceMs = 300L)
        advanceUntilIdle()

        viewModel.onQueryChanged("Dune")
        advanceTimeBy(350L)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.movies.size)

        // Clear query
        viewModel.clearQuery()
        advanceTimeBy(350L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.isEmptyQuery)
        assertEquals(2, state.movies.size)
        assertEquals(2, state.series.size)
    }

    @Test
    fun testNoResultsFlagWhenNoItemsMatch() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(fakeDao, debounceMs = 300L)
        advanceUntilIdle()

        viewModel.onQueryChanged("NonExistentTitleXYZ")
        advanceTimeBy(350L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("NonExistentTitleXYZ", state.query)
        assertFalse(state.isEmptyQuery)
        assertFalse(state.isLoading)
        assertTrue(state.isNoResults)
        assertEquals(0, state.movies.size)
        assertEquals(0, state.series.size)
    }

    @Test
    fun testLoadingStateDuringDebounce() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(fakeDao, debounceMs = 300L)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.onQueryChanged("Spider")
        advanceTimeBy(10L)
        assertTrue("Should indicate loading while debouncing", viewModel.uiState.value.isLoading)

        advanceTimeBy(350L)
        advanceUntilIdle()
        assertFalse("Should finish loading after debounce", viewModel.uiState.value.isLoading)
    }
}
