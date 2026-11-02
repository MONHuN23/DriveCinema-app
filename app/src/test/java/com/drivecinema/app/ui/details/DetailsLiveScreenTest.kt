package com.drivecinema.app.ui.details

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.navigation.AppNavigator
import com.drivecinema.app.ui.navigation.NavTestLibrary
import com.drivecinema.app.ui.navigation.Screen
import com.drivecinema.app.ui.search.SearchViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/** Task M.2: the details screens on a re-emission (F-37) and when they close (F-35). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h1600dp")
class DetailsLiveScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val series = SeriesEntity(id = "fg", title = "Family Guy")

    private fun episodes(completedUpTo: Int) = (1..3).map {
        EpisodeItem(id = "e$it", title = "Episode $it", season = 1, episode = it, isCompleted = it <= completedUpTo)
    }

    private fun state(completedUpTo: Int): SeriesDetailsState {
        val episodes = episodes(completedUpTo)
        return SeriesDetailsState(
            series = series, seasons = listOf(1), selectedSeason = 1,
            episodes = episodes, nextEpisode = DetailsViewModel.findNextTargetEpisode(episodes)
        )
    }

    /** F-37: progress or copies re-emit while the user is on an episode; the focus must not jump back to Play. */
    @Test
    fun aReEmissionKeepsFocusOnTheFocusedEpisode() {
        val state = mutableStateOf(state(completedUpTo = 0))
        composeTestRule.setContent {
            SeriesDetailsScreen(series = series, state = state.value, onSeasonSelect = {}, onEpisodeClick = {}, onBackClick = {})
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasText("Play S1E1", substring = true)).assertIsFocused()

        composeTestRule.onNodeWithText("E3. Episode 3").requestFocus().assertIsFocused()

        state.value = state(completedUpTo = 1)    // new EpisodeItems and a new nextEpisode (S1E2)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("E3. Episode 3").assertIsFocused()
        composeTestRule.onNode(hasText("Play S1E2", substring = true)).assertIsNotFocused()
    }

    /**
     * F-35: popping the details page clears its ViewModel, so its database collectors stop. Since Task M.3 the
     * ViewModel belongs to the page's back stack entry (Navigation 3's ViewModel-store decorator).
     */
    @Test
    fun poppingTheDetailsEntryStopsItsCollectors() {
        val progress = MutableStateFlow(emptyList<PlaybackProgressEntity>())
        val sources = MutableStateFlow(emptyList<com.drivecinema.app.core.database.entity.MediaSourceEntity>())
        val navigator = AppNavigator(listOf(Screen.Home, Screen.SeriesDetails(series.id)))
        composeTestRule.setContent {
            NavTestLibrary.Screens(
                navigator = navigator,
                isTv = true,
                searchViewModel = SearchViewModel(NavTestLibrary.mediaDao()),
                newDetailsViewModel = {
                    DetailsViewModel(fakeMediaDao(), progressForSeries = { progress }, sourcesForSeries = { sources })
                }
            )
        }
        composeTestRule.waitForIdle()
        assertEquals(1, progress.subscriptionCount.value)
        assertEquals(1, sources.subscriptionCount.value)

        composeTestRule.runOnIdle { navigator.back() }
        composeTestRule.waitForIdle()

        assertEquals(0, progress.subscriptionCount.value)
        assertEquals(0, sources.subscriptionCount.value)
    }

    private fun fakeMediaDao(): MediaDao {
        val unused = Proxy.newProxyInstance(MediaDao::class.java.classLoader, arrayOf(MediaDao::class.java)) { _, method, _ ->
            error("not used: MediaDao.${method.name}")
        } as MediaDao
        return object : MediaDao by unused {
            override suspend fun getSeriesById(id: String): SeriesEntity = series
            override suspend fun getEpisodesForSeries(seriesId: String): List<MediaItemEntity> = emptyList()
        }
    }
}
