package com.drivecinema.app.ui.tv.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task M.1: the TV's ambient backdrop follows focus only after it has rested. `runTest`'s virtual time is the fake
 * clock: `delay` advances it instantly, and `currentTime` says when each backdrop change happened.
 */
@OptIn(ExperimentalCoroutinesApi::class) // currentTime
class CollectAfterRestTest {

    private val rest = TvAmbientBackdropDefaults.RestMillis

    @Test
    fun theRestIsAboutThreeHundredMilliseconds() {
        assertEquals(300L, rest)
    }

    @Test
    fun aHeldKeyGivesOneChangeAfterItIsReleased() = runTest {
        // M.0's S2: Right about 12 times a second (every 83 ms) for about 5 s, through 58 cards.
        val focus = flow {
            for (card in 1..58) {
                emit("/backdrop$card.jpg")
                if (card < 58) delay(83)
            }
        }
        val changes = mutableListOf<Pair<String, Long>>()

        focus.collectAfterRest(rest) { changes += it to currentTime }

        val lastFocus = 57 * 83L
        assertEquals(listOf("/backdrop58.jpg" to lastFocus + rest), changes)
    }

    @Test
    fun eachRestGivesOneChangeToTheCardThatHasFocus() = runTest {
        val focus = flow {
            emit("/a.jpg")          // 0 ms
            delay(100); emit("/b.jpg")
            delay(100); emit("/c.jpg") // 200 ms, then focus rests
            delay(1000); emit("/d.jpg") // 1200 ms
        }
        val changes = mutableListOf<Pair<String, Long>>()

        focus.collectAfterRest(rest) { changes += it to currentTime }

        assertEquals(listOf("/c.jpg" to 500L, "/d.jpg" to 1500L), changes)
    }

    @Test
    fun aMoveJustBeforeTheRestEndsCancelsTheChange() = runTest {
        val focus = flow {
            emit("/a.jpg")
            delay(rest - 1); emit("/b.jpg")
        }
        val changes = mutableListOf<String>()

        focus.collectAfterRest(rest) { changes += it }

        assertEquals(listOf("/b.jpg"), changes)
    }

    @Test
    fun aRestJustLongerThanTheDelayShowsBoth() = runTest {
        val focus = flow {
            emit("/a.jpg")
            delay(rest + 1); emit("/b.jpg")
        }
        val changes = mutableListOf<String>()

        focus.collectAfterRest(rest) { changes += it }

        assertEquals(listOf("/a.jpg", "/b.jpg"), changes)
    }

    @Test
    fun cardsWithoutABackdropKeepTheCurrentFocusPath() {
        val state = TvAmbientBackdropState()
        state.onCardFocused(isFocused = true, backdropPath = "/a.jpg")
        state.onCardFocused(isFocused = true, backdropPath = null)
        state.onCardFocused(isFocused = false, backdropPath = "/b.jpg")

        assertEquals("/a.jpg", state.focusedPath)
        assertEquals("nothing is shown before focus has rested", null, state.shownPath)
    }
}
