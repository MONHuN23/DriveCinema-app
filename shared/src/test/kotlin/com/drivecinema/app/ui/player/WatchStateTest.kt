package com.drivecinema.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.drivecinema.app.core.metadata.MediaInfoFormat
import org.junit.Test

/** Milestone J.1: the three row states and their labels. */
class WatchStateTest {

    @Test
    fun statesFollowPositionAndCompletion() {
        assertEquals(WatchState.NOT_STARTED, WatchState.of(positionMs = 0, isCompleted = false))
        assertEquals(WatchState.IN_PROGRESS, WatchState.of(positionMs = 5_000, isCompleted = false))
        assertEquals(WatchState.WATCHED, WatchState.of(positionMs = 0, isCompleted = true))
        assertEquals(WatchState.WATCHED, WatchState.of(positionMs = 1_000_000, isCompleted = true))
    }

    @Test
    fun labels() {
        assertNull(WatchState.label(0, 1_440_000, isCompleted = false))
        assertEquals("Watched", WatchState.label(1_300_000, 1_440_000, isCompleted = true))
        // 24-min episode stopped at 12:00 → 12 min left; partial minutes round down, like the length (F-41).
        assertEquals("12 min left", WatchState.label(720_000, 1_440_000, isCompleted = false))
        assertEquals("11 min left", WatchState.label(750_000, 1_440_000, isCompleted = false)) // 11:30 left
        assertEquals("12 min left", WatchState.label(700_000, 1_440_000, isCompleted = false)) // 12:20 left
        assertEquals("1 min left", WatchState.label(1_420_000, 1_440_000, isCompleted = false)) // 0:20 left
        assertEquals("1 min left", WatchState.label(1_380_000, 1_440_000, isCompleted = false)) // 1:00 left
        assertEquals("Started", WatchState.label(5_000, 0, isCompleted = false))
    }

    @Test
    fun neverMoreLeftThanTheLengthBesideIt() {
        // F-41, from the user's TV photos: "51 min left" next to "50 min", "34 min left" next to "33 min".
        assertEquals("50 min", MediaInfoFormat.durationLabel(3_040_000)) // 50:40
        assertEquals("50 min left", WatchState.label(20_000, 3_040_000, isCompleted = false))
        assertEquals("33 min", MediaInfoFormat.durationLabel(2_030_000)) // 33:50
        assertEquals("33 min left", WatchState.label(5_000, 2_030_000, isCompleted = false))
        // Any length and any position: the minutes left never exceed the length's minutes.
        for (lengthMs in 60_000L..7_200_000L step 7_919L) {
            val lengthMinutes = MediaInfoFormat.durationLabel(lengthMs)!!.removeSuffix(" min").let { minutesOf(it) }
            for (positionMs in listOf(1L, 999L, 30_000L, lengthMs / 3, lengthMs - 1)) {
                val left = WatchState.label(positionMs, lengthMs, isCompleted = false)!!.removeSuffix(" min left").toLong()
                assertTrue("$left min left of $lengthMinutes min ($lengthMs ms at $positionMs)", left <= lengthMinutes)
            }
        }
    }

    /** "50" or "1 h 12" (from "1 h 12 min") → minutes. */
    private fun minutesOf(text: String): Long {
        val hours = text.substringBefore(" h", "0").trim().toLong()
        val minutes = text.substringAfter(" h", text).trim().ifEmpty { "0" }.toLong()
        return hours * 60 + minutes
    }
}
