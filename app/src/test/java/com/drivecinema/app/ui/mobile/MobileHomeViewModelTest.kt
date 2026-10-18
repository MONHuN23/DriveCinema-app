package com.drivecinema.app.ui.mobile

import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileHomeViewModelTest {
    @Test
    fun testContinueWatchingOnlyPresentWhenUnfinishedMediaExists() {
        val emptyList = emptyList<ContinueWatchingItem>()
        val state = MobileHomeUiState(continueWatching = emptyList)
        assertFalse(state.hasContinueWatching)
    }

    @Test
    fun testContinueWatchingIsTrueWhenItemsExist() {
        val items = listOf(
            ContinueWatchingItem(
                id = "m1",
                title = "Inception",
                type = MediaType.MOVIE,
                posterPath = null,
                backdropPath = null,
                positionMs = 30000L,
                durationMs = 120000L,
                lastWatchedAt = 1000L
            )
        )
        val state = MobileHomeUiState(continueWatching = items)
        assertTrue(state.hasContinueWatching)
    }

    private val fakeDao = object : com.drivecinema.app.core.database.dao.MediaDao {
        override suspend fun insertMediaItems(items: List<com.drivecinema.app.core.database.entity.MediaItemEntity>) {}
        override suspend fun insertMediaItem(item: com.drivecinema.app.core.database.entity.MediaItemEntity) {}
        override suspend fun insertSeries(series: List<com.drivecinema.app.core.database.entity.SeriesEntity>) {}
        override suspend fun insertSingleSeries(series: com.drivecinema.app.core.database.entity.SeriesEntity) {}
        override suspend fun getSeriesById(id: String): com.drivecinema.app.core.database.entity.SeriesEntity? = null
        override fun getSeriesByIdFlow(id: String): kotlinx.coroutines.flow.Flow<com.drivecinema.app.core.database.entity.SeriesEntity?> = kotlinx.coroutines.flow.flowOf(null)
        override suspend fun getMediaItemById(id: String): com.drivecinema.app.core.database.entity.MediaItemEntity? = null
        override suspend fun getMediaItemsByIds(ids: List<String>): List<com.drivecinema.app.core.database.entity.MediaItemEntity> = emptyList()
        override suspend fun getSeriesByIds(ids: List<String>): List<com.drivecinema.app.core.database.entity.SeriesEntity> = emptyList()
        override fun getAllMoviesFlow(): kotlinx.coroutines.flow.Flow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getAllMovies(): List<com.drivecinema.app.core.database.entity.MediaItemEntity> = emptyList()
        override suspend fun getUnenrichedMovies(): List<com.drivecinema.app.core.database.entity.MediaItemEntity> = emptyList()
        override fun getAllSeriesFlow(): kotlinx.coroutines.flow.Flow<List<com.drivecinema.app.core.database.entity.SeriesEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getAllSeries(): List<com.drivecinema.app.core.database.entity.SeriesEntity> = emptyList()
        override fun getEpisodesForSeriesFlow(seriesId: String): kotlinx.coroutines.flow.Flow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getEpisodesForSeries(seriesId: String): List<com.drivecinema.app.core.database.entity.MediaItemEntity> = emptyList()
        override suspend fun getItemsInFolder(folderId: String): List<com.drivecinema.app.core.database.entity.MediaItemEntity> = emptyList()
        override suspend fun markDeleted(id: String) {}
        override suspend fun markFolderItemsDeleted(folderId: String) {}
        override suspend fun markSourcesUnavailable(kind: com.drivecinema.app.core.database.entity.SourceKind) {}
        override suspend fun insertSourcesIfAbsent(sources: List<com.drivecinema.app.core.database.entity.MediaSourceEntity>) {}
        override suspend fun markSourcesAvailable(sourceKeys: List<String>, libraryRootId: String?, seenAt: Long) {}
        override suspend fun refreshVisibilityFromSources() {}
        override fun getContinueWatchingFlow(): kotlinx.coroutines.flow.Flow<List<ContinueWatchingItem>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun searchMoviesFlow(query: String): kotlinx.coroutines.flow.Flow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun searchSeriesFlow(query: String): kotlinx.coroutines.flow.Flow<List<com.drivecinema.app.core.database.entity.SeriesEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getUnenrichedSeries(): List<com.drivecinema.app.core.database.entity.SeriesEntity> = emptyList()
    }

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()

    @org.junit.Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    @org.junit.After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun testInitialAccountStateExposedInViewModel() {
        val account = com.drivecinema.app.ui.mobile.home.AccountUiState(
            displayName = "Alex Cinema",
            email = "alex@cinema.org",
            photoLink = "https://lh3.googleusercontent.com/avatar.jpg",
            isConnected = true
        )
        val viewModel = com.drivecinema.app.ui.mobile.home.MobileHomeViewModel(fakeDao, initialAccount = account)
        val state = viewModel.uiState.value
        org.junit.Assert.assertEquals("Alex Cinema", state.account.displayName)
        org.junit.Assert.assertEquals("alex@cinema.org", state.account.email)
        org.junit.Assert.assertEquals("https://lh3.googleusercontent.com/avatar.jpg", state.account.photoLink)
        assertTrue(state.account.isConnected)
    }

    @Test
    fun testUpdateAccountUpdatesUiState() = kotlinx.coroutines.test.runTest(testDispatcher) {
        val viewModel = com.drivecinema.app.ui.mobile.home.MobileHomeViewModel(fakeDao)
        val job = backgroundScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            viewModel.uiState.collect()
        }
        testScheduler.advanceUntilIdle()
        org.junit.Assert.assertEquals(null, viewModel.uiState.value.account.email)

        val updated = com.drivecinema.app.ui.mobile.home.AccountUiState(
            displayName = "Updated User",
            email = "updated@cinema.org",
            photoLink = null,
            isConnected = true
        )
        viewModel.updateAccount(updated)
        testScheduler.advanceUntilIdle()

        org.junit.Assert.assertEquals("Updated User", viewModel.uiState.value.account.displayName)
        org.junit.Assert.assertEquals("updated@cinema.org", viewModel.uiState.value.account.email)
        assertTrue(viewModel.uiState.value.account.isConnected)
        job.cancel()
    }
}
