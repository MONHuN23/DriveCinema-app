package com.drivecinema.app.ui.perf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.logging.LogSink
import com.drivecinema.app.core.logging.Perf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task M.0: a screen logs one `screen.<name>` line once its content is ready, or `abandoned` if left before. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerfScreenTimerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val originalSink = AppLog.sink
    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        AppLog.sink = LogSink { _, tag, message, _ -> if (tag == Perf.TAG) synchronized(lines) { lines += message } }
    }

    @After
    fun tearDown() {
        AppLog.sink = originalSink
    }

    private fun screenLines() = synchronized(lines) { lines.filter { it.startsWith("screen.") } }

    @Test
    fun logsOnceWhenContentBecomesReady() {
        var ready by mutableStateOf(false)
        var readyCalls = 0
        composeTestRule.setContent { PerfScreenTimer("home", key = Unit, ready = ready, onReady = { readyCalls++ }) }
        composeTestRule.waitForIdle()
        assertEquals(emptyList<String>(), screenLines())

        ready = true
        composeTestRule.waitForIdle()
        ready = false
        composeTestRule.waitForIdle()
        ready = true
        composeTestRule.waitForIdle()

        assertEquals(1, screenLines().size)
        assertTrue(screenLines().single(), Regex("""^screen\.home \d+ ms$""").matches(screenLines().single()))
        assertEquals(1, readyCalls)
    }

    @Test
    fun newKeyStartsANewMeasurement() {
        var key by mutableStateOf("m1")
        composeTestRule.setContent { PerfScreenTimer("movieDetails", key = key, ready = true) }
        composeTestRule.waitForIdle()
        key = "m2"
        composeTestRule.waitForIdle()
        assertEquals(2, screenLines().count { it.startsWith("screen.movieDetails ") && "abandoned" !in it })
    }

    @Test
    fun screenLeftBeforeReadyIsLoggedAsAbandoned() {
        var shown by mutableStateOf(true)
        composeTestRule.setContent { if (shown) PerfScreenTimer("seriesDetails", key = "s1", ready = false) }
        composeTestRule.waitForIdle()
        shown = false
        composeTestRule.waitForIdle()
        assertTrue(screenLines().toString(), screenLines().single().startsWith("screen.seriesDetails "))
        assertTrue(screenLines().single().endsWith(" abandoned=true"))
    }
}
