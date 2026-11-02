package com.drivecinema.app.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import com.drivecinema.app.ui.navigation.AppNavigator
import com.drivecinema.app.ui.navigation.GridKind
import com.drivecinema.app.ui.navigation.NavTestLibrary
import com.drivecinema.app.ui.navigation.Screen
import com.drivecinema.app.ui.search.SearchViewModel
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.tv.home.TvPosterCardDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV focus. Since Task M.3 (F-38), Back from a details page puts focus back on the card that opened it, with the
 * row or grid scrolled where it was; it used to land on Home's Search button with every row reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvNavigationFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val searchViewModel = SearchViewModel(NavTestLibrary.mediaDao(), debounceMs = 0L)

    private fun show(navigator: AppNavigator, isTv: Boolean = true) {
        rule.setContent { NavTestLibrary.Screens(navigator, isTv, searchViewModel) }
        rule.waitForIdle()
    }

    private fun card(title: String): SemanticsNodeInteraction = rule.onNode(hasText(title) and hasClickAction())

    private fun open(node: SemanticsNodeInteraction) {
        node.performClick()
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Home's Films row: the last lazy list that holds the first film (the outer list holds it too). */
    private fun filmsRow(): SemanticsNodeInteraction =
        rule.onAllNodes(hasScrollToKeyAction() and hasAnyDescendant(hasText("Arrival"))).onLast()

    /** UI task ui/tv-browse-naming: Home starts on its first card (it was the Search button), so the spotlight has one. */
    @Test
    fun aFirstVisitToHomeFocusesTheFirstCard() {
        show(AppNavigator(listOf(Screen.Home)))

        card("Arrival").assertIsFocused()
    }

    @Test
    fun backFocusesTheHomeCardThatWasOpened() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator)
        filmsRow().performScrollToKey(NavTestLibrary.movie("Up").id)    // the row's last film, off screen at first
        rule.waitForIdle()
        // With the remote, OK opens the focused card. (Home starts on its first card since UI task ui/tv-browse-naming,
        // and the row's focusRestorer would bring back that card if "Up" had never been focused.)
        card("Up").requestFocus()
        rule.waitForIdle()

        open(card("Up"))
        assertEquals(Screen.MovieDetails(NavTestLibrary.movie("Up").id), navigator.current)
        pressBack()

        assertEquals(Screen.Home, navigator.current)
        card("Up").assertIsFocused().assertIsDisplayed()
    }

    /** The Films and Series tabs replaced the rows' "See all A–Z" headers (UI task ui/tv-browse-naming). */
    @Test
    fun backFromAGridFocusesItsTab() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator)

        open(rule.onNodeWithTag("tv_tab_series"))
        assertEquals(Screen.Grid(GridKind.SERIES), navigator.current)
        pressBack()

        rule.onNodeWithTag("tv_tab_series").assertIsFocused()
    }

    @Test
    fun backFromSearchFocusesTheSearchTab() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator)

        open(rule.onNodeWithTag("tv_tab_search"))
        assertEquals(Screen.Search, navigator.current)
        pressBack()

        rule.onNodeWithTag("tv_tab_search").assertIsFocused()
    }

    @Test
    fun backFocusesTheGridCardThatWasOpened() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Grid(GridKind.FILMS)))
        show(navigator)
        card("Arrival").assertIsFocused()    // a first visit starts on the first card

        open(card("Up"))    // second row of the 6-column grid
        assertEquals(Screen.MovieDetails(NavTestLibrary.movie("Up").id), navigator.current)
        pressBack()

        assertEquals(Screen.Grid(GridKind.FILMS), navigator.current)
        card("Up").assertIsFocused()
    }

    @Test
    fun backFocusesTheSearchResultThatWasOpened() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Search))
        show(navigator)
        rule.onNode(hasSetTextAction()).assertIsFocused()    // a first visit starts in the text field
        rule.onNode(hasSetTextAction()).performTextInput("e")
        rule.waitForIdle()

        open(card("Heat"))
        pressBack()

        assertEquals(Screen.Search, navigator.current)
        card("Heat").assertIsFocused()
    }

    /** The phone has no focus to restore, so this shows the row's scroll position alone coming back. */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneHomeRowKeepsItsScrollAfterDetails() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator, isTv = false)
        filmsRow().performScrollToKey(NavTestLibrary.movie("Up").id)
        rule.waitForIdle()
        card("Up").assertIsDisplayed()

        open(card("Up"))
        pressBack()

        assertEquals(Screen.Home, navigator.current)
        card("Up").assertIsDisplayed()
        rule.onNode(hasText("Arrival") and hasClickAction()).assertDoesNotExist()    // still scrolled to the end
    }

    /** Task P.2: Settings replaced the Account popup; Back from it focuses the account chip, as Back from a card does. */
    @Test
    fun backFromSettingsFocusesTheAccountChip() {
        val navigator = AppNavigator(listOf(Screen.Home))
        show(navigator)

        open(rule.onNode(hasText("Account") and hasClickAction()))
        assertEquals(Screen.Settings, navigator.current)
        pressBack()

        assertEquals(Screen.Home, navigator.current)
        rule.onNode(hasText("Account") and hasClickAction()).assertIsFocused()
    }

    /** Task P.2: Back from a screen opened in Settings focuses the row that opened it, in the same category. */
    @Test
    fun backFromLocalFoldersFocusesTheSettingsRowThatOpenedIt() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Settings))
        show(navigator)
        rule.onNodeWithTag("tv_settings_category_Sources").requestFocus()
        rule.waitForIdle()

        open(rule.onNodeWithTag("tv_settings_row_local"))
        assertEquals(Screen.LocalFolders, navigator.current)
        pressBack()

        assertEquals(Screen.Settings, navigator.current)
        rule.onNodeWithTag("tv_settings_row_local").assertIsFocused()
    }

    /** Task P.4: the Help Center starts on its first topic; Back from its list returns to its Settings row. */
    @Test
    fun backFromTheHelpCenterFocusesTheSettingsRowThatOpenedIt() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Settings))
        show(navigator)
        rule.onNodeWithTag("tv_settings_category_HelpAndAbout").requestFocus()
        rule.waitForIdle()

        open(rule.onNodeWithTag("tv_settings_row_help"))
        assertEquals(Screen.SetupGuide, navigator.current)
        rule.onNodeWithTag("tv_help_topic_folders").assertIsFocused()
        pressBack()

        assertEquals(Screen.Settings, navigator.current)
        rule.onNodeWithTag("tv_settings_row_help").assertIsFocused()
    }

    /** Task P.3: My PCs is a screen now (it was a popup); Back from its list returns to its Settings row. */
    @Test
    fun backFromMyPcsFocusesTheSettingsRowThatOpenedIt() {
        val navigator = AppNavigator(listOf(Screen.Home, Screen.Settings))
        show(navigator)
        rule.onNodeWithTag("tv_settings_category_Sources").requestFocus()
        rule.waitForIdle()

        open(rule.onNodeWithTag("tv_settings_row_pcs"))
        assertEquals(Screen.Pcs, navigator.current)
        rule.onNodeWithTag("tv_pcs_item_add").assertIsFocused()
        pressBack()

        assertEquals(Screen.Settings, navigator.current)
        rule.onNodeWithTag("tv_settings_row_pcs").assertIsFocused()
    }

    @Test
    fun testFocusScaleAdheresToSpec() {
        val scale = TvPosterCardDefaults.FocusedScale
        assertTrue("Focus scale must be between 1.05 and 1.08", scale in 1.05f..1.08f)
    }

    /**
     * The cards' 3.5 dp blue border became Milestone P's white ring outside the image (UI task ui/tv-browse-naming):
     * white on the dark screen and lifted 6 %, it needs no extra width to read at 10 feet.
     */
    @Test
    fun theCardFocusRingIsTheControlsWhiteRing() {
        assertEquals(0xFFFFFFFF, DcTokens.Colors.FOCUS)
        assertTrue("the ring must be at least 2dp", DcTokens.Focus.RING_WIDTH >= 2)
    }

    @Test
    fun testTvGridColumnsAdhereToSpec() {
        val columns = com.drivecinema.app.ui.tv.grid.TvCollectionGridDefaults.ColumnCount
        assertTrue("TV grid should have 5 or 6 columns for 16:9 1080p/4K", columns in 5..6)
    }
}
