package com.drivecinema.app.ui.details

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MovieDetailsInsetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testMovieDetailsBackButtonTouchTargetAtLeast48dp() {
        val movie = MediaItemEntity(
            id = "test_movie_1",
            type = MediaType.MOVIE,
            title = "Dune: Part Two",
            year = 2024,
            parentFolderId = "films_folder"
        )

        composeTestRule.setContent {
            MovieDetailsScreen(
                movie = movie,
                progress = null,
                onPlayClick = {},
                onBackClick = {}
            )
        }

        // The back button is the arrow icon (Task P.5; it was the text "←")
        composeTestRule.onNodeWithContentDescription("Back")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun testSeriesDetailsBackButtonTouchTargetAtLeast48dp() {
        val series = SeriesEntity(
            id = "test_series_1",
            title = "Severance",
            year = 2022
        )

        composeTestRule.setContent {
            SeriesDetailsScreen(
                series = series,
                state = SeriesDetailsState(series = series),
                onSeasonSelect = {},
                onEpisodeClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Back")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun testMovieDetailsPlayButtonGainsFocusOnInitialComposition() {
        val movie = MediaItemEntity(
            id = "test_movie_focus",
            type = MediaType.MOVIE,
            title = "Dune: Part Two",
            year = 2024,
            parentFolderId = "films_folder"
        )

        composeTestRule.setContent {
            MovieDetailsScreen(
                movie = movie,
                progress = null,
                onPlayClick = {},
                onBackClick = {}
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Play Movie")
            .assertIsDisplayed()
            .requestFocus()
            .assertIsFocused()
    }

    /** F-30: the details said "1080p" for every film; now one line per known copy, nothing when unknown. */
    @Test
    fun movieDetailsShowTheCopyLinesInsteadOfAFixedResolution() {
        val movie = MediaItemEntity(id = "m", type = MediaType.MOVIE, title = "Dune", year = 2021, parentFolderId = "films")
        val lines = mutableListOf<String>()
        val state = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
        composeTestRule.setContent {
            MovieDetailsScreen(movie = movie, progress = null, onPlayClick = {}, onBackClick = {}, infoLines = state.value)
        }
        composeTestRule.onAllNodesWithText("1080p").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("movie_info_line").assertCountEquals(0)

        lines += "2 h 35 min · 2160p HDR · HEVC · E-AC-3 5.1 · MKV · 8.4 GB · Drive"
        lines += "MKV · 4.1 GB · This device"
        state.value = lines.toList()
        composeTestRule.onAllNodesWithTag("movie_info_line").assertCountEquals(2)
        composeTestRule.onNodeWithText(lines[0]).assertIsDisplayed()
    }
}
