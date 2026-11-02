package com.drivecinema.app.ui.naming

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.naming.NamingCheckState
import com.drivecinema.app.core.naming.NamingGuideState
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.details.EpisodeItem
import com.drivecinema.app.ui.details.SeriesDetailsState
import com.drivecinema.app.ui.details.TvSeriesDetailsScreen
import com.drivecinema.app.ui.mobile.home.AccountUiState
import com.drivecinema.app.ui.settings.SettingsActions
import com.drivecinema.app.ui.settings.SettingsScreenState
import com.drivecinema.app.ui.settings.TvSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task R.2 (TV, view-only): the naming helper and the Naming check with the remote. The search field starts focused,
 * a pick focuses its first season, Down walks through a season's rows, Back from a pick focuses the result it came
 * from; the check starts on its filter; no copy and no episode picking anywhere; Settings and the series page open them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvNamingFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private val calls = mutableListOf<String>()

    private fun move(direction: FocusDirection) {
        rule.runOnUiThread { focusManager.moveFocus(direction) }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun helper(initial: NamingGuideState): () -> NamingGuideState {
        var state by mutableStateOf(initial)
        val actions = NamingHelperActions(
            setQuery = {}, setKind = {}, search = {},
            pickSeries = { calls += "pick"; state = NamingFixtures.picked() },
            pickFilm = {},
            closePick = { calls += "close"; state = state.copy(picked = null) },
            toggleSeason = { calls += "toggle $it" }, setLayout = { _, _ -> }, retrySeason = {},
            copy = { calls += "copy" }, back = {}
        )
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                NamingHelperScreen(state, actions, isTv = true)
            }
        }
        rule.waitForIdle()
        return { state }
    }

    @Test
    fun theSearchFieldStartsFocused() {
        helper(NamingFixtures.searched)
        rule.onNode(hasSetTextAction() and isFocused()).assertExists()
    }

    @Test
    fun theNamesPanelFollowsTheFocusedResultOnceItRests() {
        helper(NamingFixtures.searched)
        // Before any result has the focus: the first one's names.
        rule.onNodeWithTag("naming_panel_names")
            .assertTextEquals("Series/One Piece (Anime) (1999)/\n  Season 01/\n    One Piece S01E01.mkv")

        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_result_s37854").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_result_s111110").assertIsFocused()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithTag("naming_panel_names")
            .assertTextEquals("Series/ONE PIECE (2023)/\n  Season 01/\n    ONE PIECE S01E01.mkv")
        rule.onNodeWithTag("naming_panel_title").assertTextEquals("ONE PIECE")
        rule.onNodeWithTag("naming_result_count").assertTextEquals("2 results for “One Piece”")
    }

    @Test
    fun aPickFocusesItsFirstSeasonAndBackFocusesTheResultAgain() {
        val state = helper(NamingFixtures.searched)

        rule.onNodeWithTag("naming_result_s37854").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("naming_season_1").assertIsFocused()

        pressBack()
        assertEquals(listOf("pick", "close"), calls)
        assertEquals(null, state().picked)
        rule.onNodeWithTag("naming_result_s37854").assertIsFocused()
    }

    @Test
    fun downWalksThroughAnOpenSeasonsRows() {
        helper(NamingFixtures.picked())
        rule.onNodeWithTag("naming_season_1").assertIsFocused()

        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_season_22").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_row_22_1086").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_row_22_1087").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_row_22_1088").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_season_0").assertIsFocused()
    }

    @Test
    fun okOnAHeaderTogglesItsSeason() {
        helper(NamingFixtures.picked())
        rule.onNodeWithTag("naming_season_1").assertIsFocused().performClick()
        assertEquals(listOf("toggle 1"), calls)
    }

    @Test
    fun theTvHasNoCopyButtons() {
        helper(NamingFixtures.picked())
        rule.onAllNodesWithContentDescription("Copy", substring = true).assertCountEquals(0)
    }

    // ---- Naming check ----

    private fun check(state: NamingCheckState) {
        val actions = NamingCheckActions(
            retry = { calls += "retry" }, setFilter = { calls += "filter $it" }, toggleSeason = { calls += "toggle $it" },
            pick = { _, _ -> calls += "pick" }, copy = { calls += "copy" }, openHelper = {}, back = {}
        )
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                NamingCheckScreen("Breaking Bad", state, actions, isTv = true)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theCheckStartsOnItsFilterAndDownReachesTheFiles() {
        check(NamingFixtures.checkReady)
        rule.onNodeWithTag("naming_filter_ALL").assertIsFocused()

        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_file_f3").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_check_season_1").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_file_f1").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_file_f2").assertIsFocused()
        move(FocusDirection.Down)
        rule.onNodeWithTag("naming_missing_1_3").assertIsFocused()
    }

    @Test
    fun theTvCheckIsViewOnly() {
        check(NamingFixtures.checkReady)
        rule.onAllNodesWithContentDescription("Copy", substring = true).assertCountEquals(0)
        rule.onAllNodesWithTag("naming_pick_f3").assertCountEquals(0)
    }

    @Test
    fun aFailedCheckFocusesRetry() {
        check(NamingCheckState.Failed(drive = true))
        rule.onNodeWithTag("naming_check_retry").assertIsFocused()
    }

    // ---- Entry points ----

    @Test
    fun theLibraryPaneEndsWithTheNamingHelper() {
        var opened = 0
        val actions = SettingsActions(
            refreshLibrary = {}, switchLibraryFolder = {}, reviewDuplicates = {}, openLocalFolders = {}, addLocalFolder = {},
            removeLocalFolder = {}, openPcs = {}, pairTv = {}, setSkipIntro = {}, openHelp = {}, openAbout = {}, onOpen = {},
            signOut = {}, back = {}, openNamingHelper = { opened++ }
        )
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                TvSettingsScreen(SettingsScreenState(account = AccountUiState(displayName = "Jani", isConnected = true)), actions)
            }
        }
        rule.waitForIdle()
        move(FocusDirection.Right)
        rule.onNodeWithTag("tv_settings_row_refresh").assertIsFocused()
        move(FocusDirection.Down)
        move(FocusDirection.Down)
        rule.onNodeWithTag("tv_settings_row_naming").assertIsFocused().performClick()
        move(FocusDirection.Down)
        rule.onNodeWithTag("tv_settings_row_naming").assertIsFocused()
        assertEquals(1, opened)
    }

    @Test
    fun rightOfPlayIsTheNamingCheck() {
        var opened = 0
        val episode = EpisodeItem(id = "e1", title = "Pilot", season = 1, episode = 1)
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                TvSeriesDetailsScreen(
                    series = SeriesEntity(id = "show", title = "Breaking Bad", year = 2008),
                    state = SeriesDetailsState(seasons = listOf(1), episodes = listOf(episode), nextEpisode = episode),
                    onSeasonSelect = {},
                    onEpisodeClick = {},
                    onNamingCheckClick = { opened++ }
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("details_play").assertIsFocused()

        move(FocusDirection.Right)
        rule.onNodeWithTag("details_naming_check").assertIsFocused().performClick()
        assertEquals(1, opened)
    }
}
