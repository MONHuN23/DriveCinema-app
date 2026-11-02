package com.drivecinema.app.ui.naming

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.drivecinema.app.core.naming.NamingCheckFilter
import com.drivecinema.app.core.naming.NamingCheckState
import com.drivecinema.app.core.naming.NamingGuideState
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.details.SeriesDetailsScreen
import com.drivecinema.app.ui.details.SeriesDetailsState
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.mobile.home.AccountUiState
import com.drivecinema.app.ui.settings.SettingsActions
import com.drivecinema.app.ui.settings.SettingsScreen
import com.drivecinema.app.ui.settings.SettingsScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task R.2 (phone): the naming helper's search, pick and table with its copy buttons, Back from a pick to the
 * results, the Naming check's rows and "Which episode is this?", and both entry points.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NamingScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val copied = mutableListOf<String>()
    private val calls = mutableListOf<String>()

    private fun helperActions(onClose: () -> Unit = {}) = NamingHelperActions(
        setQuery = {}, setKind = { calls += "kind $it" }, search = { calls += "search" },
        pickSeries = { calls += "pick ${it.metadata.tmdbId}" }, pickFilm = {}, closePick = { calls += "close"; onClose() },
        toggleSeason = { calls += "toggle $it" }, setLayout = { s, l -> calls += "layout $s $l" }, retrySeason = {},
        copy = { copied += it }, back = { calls += "back" }
    )

    private fun showHelper(state: NamingGuideState, actions: NamingHelperActions = helperActions()) {
        rule.setContent { DriveCinemaTheme { NamingHelperScreen(state, actions, isTv = false) } }
        rule.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("naming_helper_list").performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
    }

    @Test
    fun resultsShowTheAnimationChipAndAPickOpensTheSeries() {
        showHelper(NamingFixtures.searched)

        rule.onNodeWithText("One Piece (1999)").assertExists()
        rule.onAllNodesWithText(NamingTexts.ANIMATION).assertCountEquals(1)
        rule.onNodeWithTag("naming_result_s37854").performClick()
        assertEquals(listOf("pick 37854"), calls)
    }

    @Test
    fun thePickedSeriesShowsItsFolderAndCopiesNames() {
        showHelper(NamingFixtures.picked())

        rule.onNodeWithText("Series/One Piece (Anime) (1999)/").assertExists()
        rule.onNodeWithContentDescription("Copy folder name").performClick()
        scrollTo("naming_row_22_1086")
        rule.onNodeWithText("Season 22/One Piece S22E1086.mkv").assertExists()
        rule.onNodeWithContentDescription("Copy One Piece S22E1086.mkv").performClick()
        assertEquals(listOf("One Piece (Anime) (1999)", "One Piece S22E1086.mkv"), copied)
    }

    @Test
    fun aNameThatDoesntReadBackShowsTheWarningAndNoCopy() {
        showHelper(NamingFixtures.picked())
        scrollTo("naming_row_22_1088")

        rule.onNodeWithText(NamingTexts.CANT_READ).assertExists()
        rule.onAllNodesWithContentDescription("Copy One Piece S22E1088.mkv").assertCountEquals(0)
        rule.onAllNodesWithText("Season 22/One Piece S22E1088.mkv").assertCountEquals(0)
    }

    @Test
    fun specialsComeLastAndAHeaderTogglesItsSeason() {
        showHelper(NamingFixtures.picked(open = false))
        scrollTo("naming_season_0")
        rule.onNodeWithText("Specials (Season 00)").assertExists()

        scrollTo("naming_season_22")
        rule.onNodeWithTag("naming_season_22").performClick()
        assertEquals(listOf("toggle 22"), calls)
    }

    @Test
    fun backFromAPickReturnsToTheResults() {
        var state by mutableStateOf(NamingFixtures.picked())
        rule.setContent {
            DriveCinemaTheme { NamingHelperScreen(state, helperActions(onClose = { state = state.copy(picked = null) }), isTv = false) }
        }
        rule.waitForIdle()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertEquals(listOf("close"), calls)
        assertNull(state.picked)
        rule.onNodeWithTag("naming_result_s111110").assertExists()
    }

    @Test
    fun withoutTmdbTheScreenSaysSo() {
        showHelper(NamingGuideState(tmdbAvailable = false))
        rule.onNodeWithTag("naming_needs_tmdb").assertExists()
    }

    // ---- Naming check ----

    private fun checkActions() = NamingCheckActions(
        retry = { calls += "retry" }, setFilter = { calls += "filter $it" }, toggleSeason = { calls += "toggle $it" },
        pick = { id, m -> calls += "pick $id ${m?.episode?.episodeNumber}" }, copy = { copied += it },
        openHelper = { calls += "helper" }, back = { calls += "back" }
    )

    private fun showCheck(state: NamingCheckState) {
        rule.setContent { DriveCinemaTheme { NamingCheckScreen("Breaking Bad", state, checkActions(), isTv = false) } }
        rule.waitForIdle()
    }

    @Test
    fun theCheckShowsEachFileTheMissingEpisodesAndTheNamesToUse() {
        showCheck(NamingFixtures.checkReady)

        rule.onNodeWithText(NamingTexts.UNPLACED).assertExists()
        rule.onNodeWithText("Season 01/Behind the scenes.mkv").assertExists()
        rule.onNodeWithText("No episode number in the name, so the app reads it as episode 1.").assertExists()
        rule.onNodeWithTag("naming_check_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("naming_file_f2"))
        rule.onNodeWithText("Move and rename to: Season 02/Breaking Bad S02E02.mkv").assertExists()
        rule.onNodeWithContentDescription("Copy Breaking Bad S02E02.mkv").performClick()

        rule.onNodeWithTag("naming_check_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("naming_missing_1_4"))
        rule.onNodeWithText("Name: Season 01/Breaking Bad S01E04.mkv").assertExists()
        rule.onNodeWithContentDescription("Copy Breaking Bad S01E04.mkv").performClick()
        assertEquals(listOf("Breaking Bad S02E02.mkv", "Breaking Bad S01E04.mkv"), copied)

        rule.onNodeWithTag("naming_check_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("naming_check_season_2"))
        rule.onNodeWithText("13 episodes, none on Drive").assertExists()
    }

    @Test
    fun aFileWithoutASuggestionGetsAMissingEpisodePicked() {
        showCheck(NamingFixtures.checkReady)

        rule.onNodeWithTag("naming_pick_f3").performClick()
        rule.onNodeWithTag("naming_pick_dialog").assertExists()
        rule.onNodeWithText("S01E04 · BB1 4").performClick()

        assertEquals(listOf("pick f3 4"), calls)
        rule.onAllNodesWithTag("naming_pick_dialog").assertCountEquals(0)
    }

    @Test
    fun theFilterChipsCountAndFilter() {
        showCheck(NamingFixtures.checkReady)

        rule.onNodeWithText(NamingTexts.problems(2)).assertExists()
        rule.onNodeWithText(NamingTexts.missing(2)).performClick()
        assertEquals(listOf("filter ${NamingCheckFilter.MISSING}"), calls)
    }

    @Test
    fun theMissingFilterHidesTheFilesAndOtherSeasons() {
        showCheck(NamingFixtures.checkReady.copy(filter = NamingCheckFilter.MISSING))

        rule.onAllNodesWithText(NamingTexts.UNPLACED).assertCountEquals(0)
        rule.onAllNodesWithTag("naming_file_f1").assertCountEquals(0)
        rule.onNodeWithTag("naming_missing_1_3").assertExists()
        rule.onAllNodesWithTag("naming_check_season_2").assertCountEquals(0)
    }

    @Test
    fun aLocalSeriesPointsToTheHelperAndAFailureCanRetry() {
        var state by mutableStateOf<NamingCheckState>(NamingCheckState.LocalSeries)
        rule.setContent { DriveCinemaTheme { NamingCheckScreen("Show", state, checkActions(), isTv = false) } }
        rule.onNodeWithTag("naming_check_local").assertExists()
        rule.onNode(hasText(NamingTexts.HELPER_TITLE) and androidx.compose.ui.test.hasClickAction()).performClick()

        state = NamingCheckState.Failed(drive = true)
        rule.waitForIdle()
        rule.onNodeWithText(NamingTexts.DRIVE_FAILED).assertExists()
        rule.onNodeWithTag("naming_check_retry").performClick()
        assertEquals(listOf("helper", "retry"), calls)
    }

    // ---- Entry points ----

    @Test
    fun settingsShowsTheNamingHelperOnlyWhenOffered() {
        var opened = 0
        val base = SettingsActions(
            refreshLibrary = {}, switchLibraryFolder = {}, reviewDuplicates = {}, openLocalFolders = {}, addLocalFolder = {},
            removeLocalFolder = {}, openPcs = {}, pairTv = {}, setSkipIntro = {}, openHelp = {}, openAbout = {}, onOpen = {},
            signOut = {}, back = {}, openNamingHelper = { opened++ }
        )
        rule.setContent { DriveCinemaTheme { SettingsScreen(SettingsScreenState(account = AccountUiState(isConnected = false)), base) } }
        rule.onNodeWithTag("settings_naming_helper").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theSeriesPageOpensTheNamingCheck() {
        var opened = 0
        rule.setContent {
            DriveCinemaTheme {
                SeriesDetailsScreen(
                    series = SeriesEntity(id = "show", title = "Breaking Bad", year = 2008),
                    state = SeriesDetailsState(),
                    onSeasonSelect = {},
                    onEpisodeClick = {},
                    onBackClick = {},
                    onNamingCheckClick = { opened++ }
                )
            }
        }
        rule.onNodeWithTag("details_naming_check").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theSeriesPageHasNoNamingCheckWithoutIt() {
        rule.setContent {
            DriveCinemaTheme {
                SeriesDetailsScreen(SeriesEntity(id = "show", title = "Breaking Bad"), SeriesDetailsState(), {}, {}, onBackClick = {})
            }
        }
        rule.onAllNodesWithTag("details_naming_check").assertCountEquals(0)
    }
}
