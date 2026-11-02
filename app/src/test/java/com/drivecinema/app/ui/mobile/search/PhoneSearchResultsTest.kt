package com.drivecinema.app.ui.mobile.search

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivecinema.app.ui.navigation.NavTestLibrary
import com.drivecinema.app.ui.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The phone's search shows films and series in one A–Z list, like the TV's (was a Films / Series tab each). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PhoneSearchResultsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun filmsAndSeriesShowTogetherAndOpenTheirOwnPage() {
        val films = NavTestLibrary.movies.filter { it.title.contains("ar", ignoreCase = true) }   // Arrival
        val series = NavTestLibrary.series.filter { it.title.contains("ar", ignoreCase = true) }  // Dark
        var opened = ""
        rule.setContent {
            SearchScreen(
                state = SearchUiState(query = "ar", movies = films, series = series, isEmptyQuery = false),
                onQueryChange = {},
                onClearQuery = {},
                onMovieClick = { opened = "film:${it.title}" },
                onSeriesClick = { opened = "series:${it.title}" }
            )
        }

        rule.onNodeWithText("2 results for \"ar\"").assertExists()
        rule.onNodeWithText("Films", substring = true).assertDoesNotExist()   // no tabs any more
        rule.onNode(hasText("Dark") and hasClickAction()).performClick()
        assertEquals("series:Dark", opened)
        rule.onNode(hasText("Arrival") and hasClickAction()).performClick()
        assertEquals("film:Arrival", opened)
    }
}
