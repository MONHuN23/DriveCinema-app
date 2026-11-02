package com.drivecinema.app.ui.tv.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.drivecinema.app.core.database.dao.ContinueWatchingItem
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI task ui/tv-browse-naming: TV Home's tabs, spotlight and rows. The first card starts focused and the spotlight
 * names it; the spotlight follows the focus once it rests; Up from the rows reaches the tabs; the tabs open the grids
 * and search.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvHomeScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private val calls = mutableListOf<String>()

    private val westworld = SeriesEntity(id = "ww", title = "Westworld", year = 2016, overview = "A park of hosts.", genres = "Drama, Sci-Fi, Western")
    private val episode = ContinueWatchingItem(
        id = "ww-1-2", title = "Westworld", type = MediaType.EPISODE, posterPath = null, backdropPath = null,
        positionMs = 15 * 60_000L, durationMs = 60 * 60_000L, lastWatchedAt = 0L, seriesId = "ww", episodeCode = "S1E2"
    )
    private val ted = MediaItemEntity(
        id = "ted", type = MediaType.MOVIE, title = "Ted", year = 2012, parentFolderId = "films",
        overview = "A teddy bear comes to life.", genres = "Comedy, Fantasy"
    )
    private val dune = MediaItemEntity(id = "dune", type = MediaType.MOVIE, title = "Dune", year = 2021, parentFolderId = "films")
    private val home = MobileHomeUiState(continueWatching = listOf(episode), movies = listOf(ted, dune), series = listOf(westworld))

    private fun show(state: MobileHomeUiState = home) {
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                TvHomeScreen(
                    state = state,
                    onMovieClick = { calls += "film ${it.id}" },
                    onSeriesClick = { calls += "series ${it.id}" },
                    onContinueWatchingClick = { calls += "play ${it.id}" },
                    onFilmsHeaderClick = { calls += "films" },
                    onSeriesHeaderClick = { calls += "series grid" },
                    onSearchClick = { calls += "search" },
                    onAccountClick = { calls += "account" }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun move(direction: FocusDirection) {
        rule.runOnUiThread { focusManager.moveFocus(direction) }
        rule.waitForIdle()
    }

    private fun rest() {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    // ---- The spotlight's text ----

    @Test
    fun continueWatchingNamesTheEpisodeItsProgressAndTheShowsOverview() {
        val item = tvSpotlightItem(home, TvHomeKeys.continueWatching(episode.id))!!
        assertEquals("Continue watching", item.eyebrow)
        assertEquals("Westworld", item.title)
        assertEquals("S1E2 · 45 min left", item.meta)
        assertEquals(0.25f, item.progress!!, 0.001f)
        assertEquals("A park of hosts.", item.overview)
    }

    @Test
    fun aFilmShowsItsYearAndTwoGenres() {
        val item = tvSpotlightItem(home, TvHomeKeys.film("ted"))!!
        assertEquals(TvSpotlightItem("Film", "Ted", "2012 · Comedy · Fantasy", null, "A teddy bear comes to life."), item)
        assertEquals("2016 · Drama · Sci-Fi", tvSpotlightItem(home, TvHomeKeys.series("ww"))!!.meta)
    }

    @Test
    fun withoutAFocusedCardTheFirstCardIsShown() {
        assertEquals("Westworld", tvSpotlightItem(home, null)!!.title)
        assertEquals("Ted", tvSpotlightItem(home.copy(continueWatching = emptyList()), "film:gone")!!.title)
        assertNull(tvSpotlightItem(MobileHomeUiState(), null))
    }

    // ---- The screen ----

    @Test
    fun theFirstCardStartsFocusedAndTheSpotlightNamesIt() {
        show()
        rule.onNodeWithTag("tv_home_card_cw:ww-1-2").assertIsFocused()
        rule.onNodeWithTag("tv_spotlight_title").assertTextEquals("Westworld")
    }

    @Test
    fun theSpotlightFollowsTheFocusOnceItRests() {
        show()
        move(FocusDirection.Down)
        rule.onNodeWithTag("tv_home_card_film:ted").assertIsFocused()
        move(FocusDirection.Right)
        rule.onNodeWithTag("tv_home_card_film:dune").assertIsFocused()
        rest()
        rule.onNodeWithTag("tv_spotlight_title").assertTextEquals("Dune")
    }

    @Test
    fun upFromTheFirstRowReachesTheTabs() {
        show()
        move(FocusDirection.Up)
        rule.onNode(isFocused()).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    @Test
    fun theTabsOpenTheGridsAndSearch() {
        show()
        rule.onNodeWithTag("tv_tab_films").performClick()
        rule.onNodeWithTag("tv_tab_series").performClick()
        rule.onNodeWithTag("tv_tab_search").performClick()
        assertEquals(listOf("films", "series grid", "search"), calls)
    }

    @Test
    fun okOnACardOpensIt() {
        show()
        rule.onNodeWithTag("tv_home_card_cw:ww-1-2").performClick()
        // The Films row only peeks in under Continue Watching (a tap at its centre would miss the screen), and the
        // Series row is further down, not composed yet.
        rule.onNodeWithTag("tv_home_card_film:ted").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("play ww-1-2", "film ted"), calls)
    }

    @Test
    fun emptyRowsAreLeftOutAndAnEmptyHomeFocusesTheHomeTab() {
        show(MobileHomeUiState())
        rule.onNodeWithTag("tv_tab_home").assertIsFocused()
        rule.onNodeWithTag("tv_home_row_0").assertDoesNotExist()
    }
}
