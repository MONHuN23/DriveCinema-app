package com.drivecinema.app.ui.navigation

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.drivecinema.app.ui.search.SearchViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task M.3: a phone rotation rebuilds the screens from saved state. With the back stack itself restored
 * ([AppNavigatorTest.rotationKeepsTheBackStack]), the details page stays open and the Search tab under it comes back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PhoneRotationTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rotationOnDetailsKeepsThePageAndTheSearchTabUnderIt() {
        val navigator = AppNavigator(listOf(Screen.Home))
        val searchViewModel = SearchViewModel(NavTestLibrary.mediaDao(), debounceMs = 0L)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { NavTestLibrary.Screens(navigator, isTv = false, searchViewModel = searchViewModel) }
        rule.onAllNodes(hasText("Search") and hasClickAction()).onFirst().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Dun")
        rule.waitForIdle()
        rule.onAllNodes(hasText("Dune") and hasClickAction()).onFirst().performClick()
        rule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()

        val dune = Screen.MovieDetails(NavTestLibrary.movie("Dune").id)
        assertEquals(dune, navigator.current)
        rule.onNodeWithText("Play Movie", substring = true).assertExists()
        rule.runOnIdle { navigator.back() }
        rule.onNode(hasSetTextAction()).assert(hasText("Dun"))    // the Search tab, not Home's
    }
}
