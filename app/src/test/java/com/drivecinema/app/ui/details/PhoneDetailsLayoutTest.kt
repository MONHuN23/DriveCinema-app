package com.drivecinema.app.ui.details

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.theme.DriveCinemaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task P.5: the phone's details pages (design §5): one facts line, genres as text, season tabs, full-width Play. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class PhoneDetailsLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val series = SeriesEntity(id = "bb", title = "Breaking Bad", year = 2008, voteAverage = 8.9, genres = "Drama, Crime")

    private val episodes = (1..2).flatMap { season ->
        (1..3).map { EpisodeItem(id = "s${season}e$it", title = "Episode $season-$it", season = season, episode = it) }
    }

    @Test
    fun theSeriesHeaderHasOneFactsLineAndTheGenresAsText() {
        rule.setContent {
            DriveCinemaTheme {
                SeriesDetailsScreen(
                    series = series,
                    state = SeriesDetailsState(series = series, seasons = listOf(1, 2), selectedSeason = 1, episodes = episodes, nextEpisode = episodes[0]),
                    onSeasonSelect = {}, onEpisodeClick = {}, onBackClick = {}
                )
            }
        }

        rule.onNodeWithTag("details_facts").assertTextEquals("2008 · 2 seasons · rated 8.9")
        rule.onNodeWithText("Drama · Crime").assertExists()
        rule.onNodeWithText("Play S1E1 • Episode 1-1").assertWidthIsAtLeast(300.dp)
    }

    @Test
    fun theSeasonTabsShowTheSelectedSeasonAndSwitchIt() {
        val picked = mutableListOf<Int>()
        rule.setContent {
            DriveCinemaTheme {
                SeriesDetailsScreen(
                    series = series,
                    state = SeriesDetailsState(series = series, seasons = listOf(1, 2), selectedSeason = 1, episodes = episodes),
                    onSeasonSelect = { picked += it }, onEpisodeClick = {}, onBackClick = {}
                )
            }
        }

        rule.onNodeWithTag("season_tab_1").assertIsSelected()
        rule.onNodeWithTag("season_tab_2").assertIsNotSelected()
        rule.onNodeWithText("E2. Episode 1-2").assertExists()
        rule.onNodeWithText("E2. Episode 2-2").assertDoesNotExist()

        rule.onNodeWithTag("season_tab_2").performClick()
        assertEquals(listOf(2), picked)
    }

    @Test
    fun anEpisodeRowShowsItsFactsInOneLine() {
        val episode = EpisodeItem(id = "e", title = "Pilot", season = 1, episode = 1, info = "58 min · 1080p", voteAverage = 8.2)
        rule.setContent { DriveCinemaTheme { EpisodeRowItem(episode = episode, fallbackBackdropPath = null, onClick = {}) } }

        rule.onNodeWithTag("episode_info_line", useUnmergedTree = true).assertTextEquals("58 min · 1080p · rated 8.2")
    }

    @Test
    fun theFilmHeaderHasOneFactsLine() {
        val movie = MediaItemEntity(
            id = "m", type = MediaType.MOVIE, title = "Dune: Part Two", year = 2024, parentFolderId = "films",
            runtimeSeconds = 166 * 60, voteAverage = 8.4, genres = "Science Fiction, Adventure"
        )
        rule.setContent { DriveCinemaTheme { MovieDetailsScreen(movie = movie, progress = null, onPlayClick = {}, onBackClick = {}) } }

        rule.onNodeWithTag("details_facts").assertTextEquals("2024 · 2h 46m · rated 8.4")
        rule.onNodeWithText("Science Fiction · Adventure").assertExists()
        rule.onNodeWithText("Play Movie").assertWidthIsAtLeast(300.dp)
    }
}
