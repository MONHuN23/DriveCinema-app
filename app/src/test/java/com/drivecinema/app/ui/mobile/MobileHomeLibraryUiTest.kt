package com.drivecinema.app.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.mobile.grid.FullLibraryGridScreen
import com.drivecinema.app.ui.mobile.home.ContinueWatchingCard
import com.drivecinema.app.ui.mobile.home.MobileHomeScreen
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState
import com.drivecinema.app.ui.mobile.navigation.MobileBottomNav
import com.drivecinema.app.ui.mobile.navigation.MobileTab
import com.drivecinema.app.ui.player.WatchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The phone Home top bar, Continue Watching card, bottom bar and Library tab after the UI polish. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobileHomeLibraryUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setPhoneContent(content: @Composable () -> Unit) {
        composeTestRule.setContent { DriveCinemaTheme(isTv = false) { content() } }
    }

    private val item = ContinueWatchingItem(
        id = "e1", title = "Jujutsu Kaisen", type = MediaType.EPISODE, posterPath = null, backdropPath = null,
        positionMs = 60_000, durationMs = 1_400_000, lastWatchedAt = 1, seriesId = "jjk", episodeCode = "S1E1"
    )

    @Test
    fun topBarCastButtonPairsATvAndShowsTheDotOnlyWhenOneIsLinked() {
        var pairClicks = 0
        var linked by mutableStateOf(false)
        setPhoneContent {
            MobileHomeScreen(
                state = MobileHomeUiState(),
                onMovieClick = {}, onSeriesClick = {}, onContinueWatchingClick = {},
                onPairTvClick = { pairClicks++ },
                onAccountClick = {},
                tvLinked = linked
            )
        }
        composeTestRule.onNodeWithTag("home_tv_linked_dot").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_pair_tv").performClick()
        assertEquals(1, pairClicks)
        composeTestRule.onNodeWithTag("home_account").assertExists()

        linked = true
        composeTestRule.onNodeWithTag("home_tv_linked_dot").assertExists()
    }

    @Test
    fun playOnTvIsBesideTheTitleAndDoesNotAlsoPlayHere() {
        var plays = 0
        var sent = 0
        setPhoneContent { ContinueWatchingCard(item = item, onClick = { plays++ }, onPlayOnTvClick = { sent++ }) }
        composeTestRule.onNodeWithTag("continue_play_on_tv").performClick()
        assertEquals(1, sent)
        assertEquals(0, plays)
        composeTestRule.onNodeWithText("Jujutsu Kaisen").performClick()
        assertEquals(1, plays)
    }

    @Test
    fun noPlayOnTvButtonWithoutALinkedTv() {
        setPhoneContent { ContinueWatchingCard(item = item, onClick = {}, onPlayOnTvClick = null) }
        composeTestRule.onNodeWithTag("continue_play_on_tv").assertDoesNotExist()
    }

    @Test
    fun bottomBarMarksTheSelectedTab() {
        setPhoneContent {
            var tab by remember { mutableStateOf(MobileTab.HOME) }
            MobileBottomNav(selectedTab = tab, onTabSelected = { tab = it })
        }
        composeTestRule.onNodeWithTag("mobile_tab_home").assertIsSelected()
        composeTestRule.onNodeWithTag("mobile_tab_library").performClick()
        composeTestRule.onNodeWithTag("mobile_tab_library").assertIsSelected()
        composeTestRule.onNodeWithTag("mobile_tab_home").assertIsNotSelected()
    }

    @Test
    fun libraryChipsFilterByWatchStateAndTheSegmentsSwitchToSeries() {
        val movies = listOf(
            MediaItemEntity(id = "m1", type = MediaType.MOVIE, title = "Dune Part One", parentFolderId = "f"),
            MediaItemEntity(id = "m2", type = MediaType.MOVIE, title = "Dune Part Two", parentFolderId = "f")
        )
        val series = listOf(SeriesEntity(id = "s1", title = "Family Guy"))
        setPhoneContent {
            FullLibraryGridScreen(
                movies = movies, series = series, onMovieClick = {}, onSeriesClick = {},
                filmStates = mapOf("m2" to WatchState.IN_PROGRESS)
            )
        }
        composeTestRule.onNodeWithText("Dune Part One").assertExists()
        composeTestRule.onNodeWithTag("library_filter_in_progress").performClick()
        composeTestRule.onNodeWithText("Dune Part One").assertDoesNotExist()
        composeTestRule.onNodeWithText("Dune Part Two").assertExists()

        composeTestRule.onNodeWithTag("library_kind_series").performClick()
        composeTestRule.onNodeWithTag("library_kind_series").assertIsSelected()
        composeTestRule.onNodeWithText("No series in progress").assertExists()

        composeTestRule.onNodeWithTag("library_filter_all").performClick()
        composeTestRule.onNodeWithText("Family Guy").assertExists()
    }
}
