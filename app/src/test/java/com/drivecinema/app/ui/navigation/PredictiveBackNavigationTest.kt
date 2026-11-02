package com.drivecinema.app.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.drivecinema.app.ui.search.SearchViewModel
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.setup.SetupGuideContent
import androidx.compose.ui.test.hasTestTag
import com.drivecinema.app.ui.setup.SetupUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task M.3 (F-38): Back (the TV remote's key, the phone's button or predictive gesture) pops to the screen you came
 * from, through the real [AppNavDisplay]. It used to go Home from every details page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class PredictiveBackNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val searchViewModel = SearchViewModel(NavTestLibrary.mediaDao(), debounceMs = 0L)

    private fun show(navigator: AppNavigator, isTv: Boolean = true) {
        rule.setContent { NavTestLibrary.Screens(navigator, isTv, searchViewModel) }
        rule.waitForIdle()
    }

    private fun clickCard(title: String) {
        rule.onAllNodes(hasText(title) and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun backFromDetailsOpenedFromTheGridReturnsToTheGrid() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator)

        rule.onNodeWithTag("tv_tab_films").performClick()
        rule.waitForIdle()
        clickCard("Dune")
        assertEquals(Screen.MovieDetails(NavTestLibrary.movie("Dune").id), navigator.current)

        pressBack()

        assertEquals(Screen.Grid(GridKind.FILMS), navigator.current)
        rule.onNodeWithText("(7)").assertExists()    // the grid's header: Films (7)
        pressBack()
        assertEquals(Screen.Home, navigator.current)
    }

    @Test
    fun predictiveBackGestureFromDetailsReturnsToTheGrid() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Grid(GridKind.SERIES)))
        show(navigator)
        clickCard("Severance")
        assertEquals(Screen.SeriesDetails(NavTestLibrary.series("Severance").id), navigator.current)

        rule.runOnUiThread {
            val dispatcher = rule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 400f, 0.5f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        rule.waitForIdle()

        assertEquals(Screen.Grid(GridKind.SERIES), navigator.current)
    }

    @Test
    fun cancelledBackGestureStaysOnDetails() {
        val details = Screen.MovieDetails(NavTestLibrary.movie("Heat").id)
        val navigator = AppNavigator(listOf(Screen.Home, details))
        show(navigator)

        rule.runOnUiThread {
            val dispatcher = rule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(60f, 400f, 0.2f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackCancelled()
        }
        rule.waitForIdle()

        assertEquals(details, navigator.current)
    }

    @Test
    @Config(qualifiers = "w960dp-h1080dp")    // tall enough to show Home's Series row without scrolling
    fun backFromSeriesOpenedOnHomeReturnsHome() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator)

        clickCard("Severance")
        assertEquals(Screen.SeriesDetails(NavTestLibrary.series("Severance").id), navigator.current)

        pressBack()

        assertEquals(Screen.Home, navigator.current)
    }

    @Test
    fun tvSearchResultBackReturnsToTheSearchWithItsText() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator)
        rule.onAllNodes(hasText("Search") and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("Dun")
        rule.waitForIdle()

        clickCard("Dune")
        assertEquals(Screen.MovieDetails(NavTestLibrary.movie("Dune").id), navigator.current)
        pressBack()

        assertEquals(Screen.Search, navigator.current)
        rule.onNode(hasSetTextAction()).assert(hasText("Dun"))
    }

    @Test
    fun backFromTheSetupGuideReturnsToSignIn() {
        val navigator = AppNavigator(listOf(Screen.SignIn, Screen.SetupGuide))
        show(navigator)

        pressBack()

        assertEquals(Screen.SignIn, navigator.current)
        assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun backWhileSwitchingLibraryCancelsTheSwitch() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.FolderPicker))
        val setup = SetupUiState().apply { isSwitchingLibrary.value = true }
        var cancelled = 0
        rule.setContent {
            NavTestLibrary.Screens(
                navigator, isTv = true, searchViewModel = searchViewModel, setup = setup,
                setupActions = NavTestLibrary.setupActions(navigator, cancelLibrarySwitch = { cancelled++ })
            )
        }
        rule.waitForIdle()

        pressBack()

        // The activity's cancel stops the folder listing and goes Home; a plain pop would leave the listing running.
        assertEquals(1, cancelled)
        assertEquals(Screen.FolderPicker, navigator.current)
    }

    /** Task P.2: the profile chip opens Settings as a screen; Back goes one screen at a time, as everywhere. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneProfileChipOpensSettingsAndBackReturnsHome() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator, isTv = false)

        // The top bar's avatar (it replaced the "Account" chip in the phone Home polish)
        rule.onNodeWithTag("home_account").performClick()
        rule.waitForIdle()
        assertEquals(Screen.Settings, navigator.current)
        rule.onNodeWithText("Settings").assertExists()

        navigator.open(Screen.LocalFolders)
        rule.waitForIdle()
        pressBack()
        assertEquals(Screen.Settings, navigator.current)
        pressBack()
        assertEquals(Screen.Home, navigator.current)
    }

    /** Task P.4: the folder picker's guide link opens "Set up your folders", and Back returns to the picker. */
    @Test
    fun theFolderPickersGuideLinkOpensTheFoldersTopic() {
        val navigator = AppNavigator(listOf(Screen.FolderPicker))
        show(navigator, isTv = false)

        // The phone picker keeps the guide in its overflow menu (UI polish).
        rule.onNodeWithContentDescription("More options").performClick()
        clickCard("Setup guide")
        assertEquals(Screen.HelpTopic(SetupGuideContent.FOLDERS), navigator.current)
        rule.onNodeWithText("Set up your folders").assertExists()

        pressBack()
        assertEquals(Screen.FolderPicker, navigator.current)
    }

    /** Task P.4: phone Help home → topic → Next shows the next topic in its place; Back returns to the Help home. */
    @Test
    fun aHelpTopicsNextLinkReplacesItAndBackReturnsToTheHelpHome() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Settings, Screen.SetupGuide))
        show(navigator, isTv = false)

        rule.onNodeWithTag("help_list").performScrollToNode(hasText("Set up your folders"))
        clickCard("Set up your folders")
        assertEquals(Screen.HelpTopic(SetupGuideContent.FOLDERS), navigator.current)

        rule.onNodeWithTag("help_topic_page").performScrollToNode(hasTestTag("help_next"))
        rule.onNodeWithTag("help_next").performClick()
        rule.waitForIdle()
        assertEquals(Screen.HelpTopic(SetupGuideContent.FILM_NAMES), navigator.current)
        assertEquals(4, navigator.backStack.size)

        pressBack()
        assertEquals(Screen.SetupGuide, navigator.current)
    }

    /** Task P.3: My PCs opens as a screen from Settings (it was a popup) and Back returns to Settings. */
    @Test
    fun myPcsOpensFromSettingsAndBackReturnsToSettings() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Settings))
        show(navigator, isTv = false)

        rule.onNodeWithTag("settings_list").performScrollToNode(hasText(SettingsLabels.MY_PCS))
        rule.onNode(hasText(SettingsLabels.MY_PCS) and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(Screen.Pcs, navigator.current)
        rule.onNodeWithTag("pcs_list").assertExists()

        pressBack()
        assertEquals(Screen.Settings, navigator.current)
    }

    @Test
    fun backAtTheRootLetsTheAppClose() {
        show(AppNavigator(listOf(Screen.Home)))

        // No callback of ours is enabled, so the system closes the app (or goes to the TV's launcher).
        assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneSearchTabBackReturnsToTheHomeTab() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator, isTv = false)
        rule.onAllNodes(hasText("Search") and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Search films and series by title...").assertExists()

        pressBack()

        rule.onNodeWithText("Search films and series by title...").assertDoesNotExist()
        rule.onNodeWithText("DriveCinema").assertExists()
        assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneSearchResultBackReturnsToTheSearchTab() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator, isTv = false)
        rule.onAllNodes(hasText("Search") and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("Dun")
        rule.waitForIdle()

        clickCard("Dune")
        assertEquals(Screen.MovieDetails(NavTestLibrary.movie("Dune").id), navigator.current)
        pressBack()

        assertEquals(Screen.Home, navigator.current)
        rule.onNode(hasSetTextAction()).assert(hasText("Dun"))
    }
}
