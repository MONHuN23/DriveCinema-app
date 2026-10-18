package com.drivecinema.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("✓ Watched", WatchState.label(1_300_000, 1_440_000, isCompleted = true))
        // 24-min episode stopped at 12:00 → 12 min left; partial minutes round up.
        assertEquals("12 min left", WatchState.label(720_000, 1_440_000, isCompleted = false))
        assertEquals("12 min left", WatchState.label(750_000, 1_440_000, isCompleted = false)) // 11:30 left
        assertEquals("13 min left", WatchState.label(700_000, 1_440_000, isCompleted = false)) // 12:20 left
        assertEquals("1 min left", WatchState.label(1_420_000, 1_440_000, isCompleted = false))
        assertEquals("Started", WatchState.label(5_000, 0, isCompleted = false))
    }
}
