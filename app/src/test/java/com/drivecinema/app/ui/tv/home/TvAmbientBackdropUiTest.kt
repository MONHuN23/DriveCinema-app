package com.drivecinema.app.ui.tv.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.ExperimentalTestApi

/**
 * Task M.1: the ambient backdrop behind TV Home, the grid and search. Fast focus moves across real [TvPosterCard]s
 * (a held D-pad key) give one backdrop change, to the card focus rests on, and no backdrop before that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvAmbientBackdropUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val cards = 1..6

    private fun showCards(state: TvAmbientBackdropState, shown: MutableList<String?>) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            LaunchedEffect(state) { snapshotFlow { state.shownPath }.collect { shown += it } }
            Box(Modifier.trackDpadHold(state)) {
                TvAmbientBackdrop(
                    state = state,
                    height = 450.dp,
                    topScrimColor = Color(0x550C0D10),
                    label = "test_backdrop"
                )
                Row {
                    for (card in cards) {
                        TvPosterCard(
                            title = "Film $card",
                            posterPath = null,
                            backdropPath = "/backdrop$card.jpg",
                            onClick = {},
                            onFocusChange = state::onCardFocused
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(100)
    }

    private fun focusEachCardQuickly() {
        for (card in cards) {
            composeTestRule.onNodeWithText("Film $card").requestFocus()
            composeTestRule.mainClock.advanceTimeBy(80) // faster than the 300 ms rest, like a held key
        }
    }

    @Test
    fun fastFocusChangesGiveOneBackdropChange() {
        val state = TvAmbientBackdropState()
        val shown = mutableListOf<String?>()
        showCards(state, shown)

        focusEachCardQuickly()

        assertEquals("/backdrop6.jpg", state.focusedPath)
        assertEquals("no backdrop while focus moves", listOf<String?>(null), shown)
        composeTestRule.onAllNodesWithTag("tv_ambient_backdrop").assertCountEquals(0)

        composeTestRule.mainClock.advanceTimeBy(TvAmbientBackdropDefaults.RestMillis + 200)
        composeTestRule.mainClock.advanceTimeBy(2_000)

        assertEquals(listOf(null, "/backdrop6.jpg"), shown)
        composeTestRule.onAllNodesWithTag("tv_ambient_backdrop").assertCountEquals(1)
    }

    @OptIn(ExperimentalTestApi::class) // keyDown / keyUp
    @Test
    fun aHeldKeyShowsNoBackdropUntilItIsReleased() {
        // User's TV check (2026-10-27): holding Right moved focus once, and the TV's ~0.5 s wait before key repeat
        // was longer than the rest, so the next card's backdrop showed during the hold.
        val state = TvAmbientBackdropState()
        val shown = mutableListOf<String?>()
        showCards(state, shown)
        composeTestRule.onNodeWithText("Film 1").requestFocus()
        composeTestRule.mainClock.advanceTimeBy(1_000)

        val film2 = composeTestRule.onNodeWithText("Film 2")
        film2.performKeyInput { keyDown(Key.DirectionRight) }
        film2.requestFocus() // the first move of the hold
        composeTestRule.mainClock.advanceTimeBy(600) // the TV's wait before key repeat
        composeTestRule.onNodeWithText("Film 3").requestFocus() // repeats
        composeTestRule.mainClock.advanceTimeBy(80)
        composeTestRule.onNodeWithText("Film 4").requestFocus()
        composeTestRule.mainClock.advanceTimeBy(600)

        assertEquals("nothing changes while the key is down", listOf(null, "/backdrop1.jpg"), shown)

        composeTestRule.onNodeWithText("Film 4").performKeyInput { keyUp(Key.DirectionRight) }
        composeTestRule.waitForIdle() // lets the key-up's state write reach the backdrop, as the TV's main loop does
        composeTestRule.mainClock.advanceTimeBy(TvAmbientBackdropDefaults.RestMillis + 200)

        assertEquals(listOf(null, "/backdrop1.jpg", "/backdrop4.jpg"), shown)
    }

    @Test
    fun aSecondRestMovesTheBackdropAgain() {
        val state = TvAmbientBackdropState()
        val shown = mutableListOf<String?>()
        showCards(state, shown)

        composeTestRule.onNodeWithText("Film 2").requestFocus()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onNodeWithText("Film 3").requestFocus()
        composeTestRule.mainClock.advanceTimeBy(80)
        composeTestRule.onNodeWithText("Film 4").requestFocus()
        composeTestRule.mainClock.advanceTimeBy(1_000)

        assertEquals(listOf(null, "/backdrop2.jpg", "/backdrop4.jpg"), shown)
    }
}
