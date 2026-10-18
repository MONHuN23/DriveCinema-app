package com.drivecinema.app.ui.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Milestone J.1: episode rows on phone/TV say whether an episode is watched or how much is left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeRowWatchStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun watchedEpisodeShowsWatchedLabel() {
        composeTestRule.setContent {
            EpisodeRowItem(
                episode = EpisodeItem(id = "e1", title = "Pilot", season = 1, episode = 1, isCompleted = true,
                    positionMs = 1_300_000, durationMs = 1_440_000),
                fallbackBackdropPath = null,
                onClick = {}
            )
        }
        composeTestRule.onNodeWithText("✓ Watched").assertIsDisplayed()
    }

    @Test
    fun startedEpisodeShowsTimeLeft() {
        composeTestRule.setContent {
            EpisodeRowItem(
                episode = EpisodeItem(id = "e2", title = "Second", season = 1, episode = 2,
                    positionMs = 720_000, durationMs = 1_440_000),
                fallbackBackdropPath = null,
                onClick = {}
            )
        }
        composeTestRule.onNodeWithText("12 min left").assertIsDisplayed()
    }

    @Test
    fun unstartedEpisodeShowsNoStatus() {
        composeTestRule.setContent {
            EpisodeRowItem(
                episode = EpisodeItem(id = "e3", title = "Third", season = 1, episode = 3),
                fallbackBackdropPath = null,
                onClick = {}
            )
        }
        composeTestRule.onNodeWithText("✓ Watched").assertDoesNotExist()
        composeTestRule.onNodeWithText("min left", substring = true).assertDoesNotExist()
    }
}
