package com.drivecinema.app.ui.details

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.design.dcFocusOverflow
import com.drivecinema.app.ui.tv.home.TvAmbientBackdropDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.drivecinema.app.ui.design.assertFocusFitsVertically
import com.drivecinema.app.ui.design.DcTokens

/**
 * Task P.5 (was M.4): the TV's film and series pages at the TV's 960 × 540 dp. The user's photos (2026-10-28) showed
 * Play across the whole screen and the episodes below the fold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvDetailsLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager

    private val series = SeriesEntity(
        id = "avatar", title = "Avatar Seven Havens", year = 2026, voteAverage = 9.1,
        overview = "A long overview. ".repeat(30)
    )

    private fun episodes(watchedUpTo: Int = 3) = (1..8).map {
        EpisodeItem(
            id = "e$it", title = "Episode title $it", season = 1, episode = it, info = "22 min · 1080p",
            isCompleted = it <= watchedUpTo,
            positionMs = if (it == watchedUpTo + 1) 600_000 else 0, durationMs = if (it == watchedUpTo + 1) 1_320_000 else 0,
            overview = "What happens in episode $it."
        )
    }

    private fun seriesState(watchedUpTo: Int = 3): SeriesDetailsState {
        val episodes = episodes(watchedUpTo)
        return SeriesDetailsState(
            series = series, seasons = listOf(1, 2), selectedSeason = 1, episodes = episodes,
            nextEpisode = DetailsViewModel.findNextTargetEpisode(episodes)
        )
    }

    private fun move(direction: FocusDirection) {
        rule.runOnUiThread { focusManager.moveFocus(direction) }
        rule.waitForIdle()
    }

    private fun showSeries(
        state: androidx.compose.runtime.MutableState<SeriesDetailsState> = mutableStateOf(seriesState()),
        panel: EpisodePanelState = EpisodePanelState()
    ) {
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                TvSeriesDetailsScreen(series, state.value, onSeasonSelect = {}, onEpisodeClick = {}, panel = panel)
            }
        }
        rule.waitForIdle()
    }

    private fun playWidth() = rule.onNodeWithTag("details_play").getUnclippedBoundsInRoot().width

    @Test
    fun theSeriesPlayButtonIsSizedToItsLabelAndStartsFocused() {
        showSeries()

        val play = rule.onNodeWithTag("details_play")
        play.assertIsFocused()
        play.assertTextEquals("Resume S1E4 · 12 min left")
        assertTrue("Play is ${playWidth()}", playWidth() < 960.dp * 0.4f)
        assertTrue("Play is ${playWidth()}", playWidth() >= 200.dp)
    }

    @Test
    fun theFirstEpisodesAreOnScreenWithoutScrolling() {
        showSeries()

        for (episode in 1..4) rule.onNodeWithTag("tv_episode_e$episode").assertIsDisplayed()
        rule.onNodeWithText("2026 · 2 seasons · rated 9.1").assertExists()
    }

    @Test
    fun downGoesFromPlayToTheSeasonsThenTheEpisodes() {
        showSeries()

        move(FocusDirection.Down)
        rule.onNodeWithTag("season_tab_1").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("tv_episode_e1").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("tv_episode_e2").assertIsFocused()

        move(FocusDirection.Up)
        move(FocusDirection.Up)
        rule.onNodeWithTag("season_tab_1").assertIsFocused()
        move(FocusDirection.Up)
        rule.onNodeWithTag("details_play").assertIsFocused()
    }

    @Test
    fun aReEmissionKeepsFocusOnTheFocusedEpisode() {
        // F-37 on the TV page: new progress must not pull the focus back to Play.
        val state = mutableStateOf(seriesState(watchedUpTo = 3))
        showSeries(state)
        rule.onNodeWithTag("tv_episode_e4").requestFocus().assertIsFocused()

        state.value = seriesState(watchedUpTo = 4)
        rule.waitForIdle()

        rule.onNodeWithTag("tv_episode_e4").assertIsFocused()
        rule.onNodeWithTag("details_play").assertTextEquals("Resume S1E5 · 12 min left")
    }

    @Test
    fun thePanelShowsTheEpisodeFocusRestsOnAndGoesWhenTheListIsLeft() {
        val panel = EpisodePanelState()
        rule.mainClock.autoAdvance = false
        showSeries(panel = panel)
        rule.mainClock.advanceTimeBy(100)

        rule.onNodeWithTag("tv_episode_e2").requestFocus()
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithTag("tv_episode_e3").requestFocus()
        rule.mainClock.advanceTimeBy(80)
        assertNull("nothing while focus moves", panel.shownKey)

        rule.mainClock.advanceTimeBy(TvAmbientBackdropDefaults.RestMillis + 200)
        assertEquals("e3", panel.shownKey)
        rule.onNodeWithTag("tv_episode_panel").assertExists()
        rule.onNodeWithText("E3. Episode title 3").assertExists()
        rule.onNodeWithText("What happens in episode 3.").assertExists()

        rule.onNodeWithTag("season_tab_1").requestFocus()
        rule.mainClock.advanceTimeBy(100)
        assertNull(panel.shownKey)
        rule.onNodeWithTag("tv_episode_panel").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class) // keyDown / keyUp
    @Test
    fun aHeldKeyChangesThePanelOnlyAfterItIsReleased() {
        val panel = EpisodePanelState()
        rule.mainClock.autoAdvance = false
        showSeries(panel = panel)
        rule.mainClock.advanceTimeBy(100) // Play takes the first focus, as when the page opens
        rule.onNodeWithTag("tv_episode_e1").requestFocus()
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("e1", panel.shownKey)

        val second = rule.onNodeWithTag("tv_episode_e2")
        second.performKeyInput { keyDown(Key.DirectionDown) }
        second.requestFocus()
        rule.mainClock.advanceTimeBy(600) // the TV's wait before key repeat
        rule.onNodeWithTag("tv_episode_e3").requestFocus()
        rule.mainClock.advanceTimeBy(600)
        assertEquals("not per row while the key is down", "e1", panel.shownKey)

        rule.onNodeWithTag("tv_episode_e3").performKeyInput { keyUp(Key.DirectionDown) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(TvAmbientBackdropDefaults.RestMillis + 200)
        assertEquals("e3", panel.shownKey)
    }

    @Test
    fun theSelectedSeasonsLineSpansItsLabelCentred() {
        // P.7 (device check 2026-10-29): the line was a short stub at the bottom left of the tab.
        showSeries()

        val tab = rule.onNodeWithTag("season_tab_1").getUnclippedBoundsInRoot()
        val line = rule.onNodeWithTag("season_tab_line_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val labelWidth = tab.width - 24.dp // the tab's padding, 12 dp each side
        assertTrue("line ${line.width}, label $labelWidth", line.width >= labelWidth - 1.dp)
        val leftGap = line.left - tab.left
        val rightGap = tab.right - line.right
        assertTrue("gaps $leftGap / $rightGap", (leftGap - rightGap).value in -1f..1f)
    }

    @Test
    fun theFocusedFirstSeasonTabIsNotClippedByItsScrollingRow() {
        // P.7: the row clipped the left side of Season 1's scaled focus box.
        showSeries()
        move(FocusDirection.Down)
        rule.onNodeWithTag("season_tab_1").assertIsFocused()

        val row = rule.onNodeWithTag("season_tabs").getUnclippedBoundsInRoot()
        val tab = rule.onNodeWithTag("season_tab_1").getUnclippedBoundsInRoot()
        val room = tab.left - row.left
        assertTrue("room $room", room >= dcFocusOverflow(tab.width))
    }

    @Test
    fun focusedEpisodeRowsAreNotClippedByTheList() {
        // P.7 follow-up (TV photo, 2026-10-29): the first episode's ring was cut off at the top of the list.
        showSeries()
        repeat(8) {
            rule.onNodeWithTag("tv_episode_e${it + 1}").requestFocus()
            rule.waitForIdle()
            rule.onNodeWithTag("tv_episode_e${it + 1}")
                .assertFocusFitsVertically(rule.onNodeWithTag("tv_episode_list"), scale = DcTokens.Focus.ROW_SCALE, what = "episode ${it + 1}")
        }
    }

    @Test
    fun theFocusedEpisodePanelShowsAStartedEpisodesProgressOnce() {
        // P.7: the panel showed the bar on the still and a second one under the facts line.
        val panel = EpisodePanelState()
        rule.mainClock.autoAdvance = false
        showSeries(panel = panel)
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("tv_episode_e4").requestFocus() // started: 10 of 22 minutes
        rule.mainClock.advanceTimeBy(TvAmbientBackdropDefaults.RestMillis + 200)
        assertEquals("e4", panel.shownKey)

        val bars = rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestor(hasTestTag("tv_episode_panel")),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertEquals(1, bars.size)
    }

    @Test
    fun theFilmPageHasACompactPlayThatStartsFocused() {
        val movie = MediaItemEntity(
            id = "dune", type = MediaType.MOVIE, title = "Dune Part One", year = 2021, parentFolderId = "films",
            runtimeSeconds = 155 * 60, voteAverage = 7.8, overview = "Paul Atreides. ".repeat(40)
        )
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                TvMovieDetailsScreen(
                    movie,
                    progress = PlaybackProgressEntity(mediaItemId = "dune", positionMs = 14_000, durationMs = 9_300_000),
                    onPlayClick = {},
                    infoLines = listOf("2 h 35 min · 1080p · MKV · 15.5 GB · Drive")
                )
            }
        }
        rule.waitForIdle()

        val play = rule.onNodeWithTag("details_play")
        play.assertIsFocused().assertIsDisplayed()
        play.assertTextEquals("Resume from 0:14")
        assertTrue("Play is ${playWidth()}", playWidth() < 960.dp * 0.4f)
        rule.onNodeWithText("2021 · 2h 35m · rated 7.8").assertExists()
        rule.onNodeWithText("2 h 35 min · 1080p · MKV · 15.5 GB · Drive").assertIsDisplayed()
    }
}
