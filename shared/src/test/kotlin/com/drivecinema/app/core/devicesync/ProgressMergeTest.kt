package com.drivecinema.app.core.devicesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone Q merge rules (design §4): newest wins; sync never clears a watched mark with older data. */
class ProgressMergeTest {

    private val now = 1_761_740_000_000L
    private fun e(position: Long, completed: Boolean, at: Long) = SyncEntry(position, 1_000_000L, completed, at)

    @Test
    fun theNewerPlayWinsWholly() {
        val older = e(900_000, true, now - 10_000)
        val newer = e(60_000, false, now)
        assertTrue("a re-watch from the start", ProgressMerge.wins(newer, older))
        assertEquals(newer, ProgressMerge.newer(older, newer))
        assertEquals(newer, ProgressMerge.newer(newer, older))
    }

    @Test
    fun anOlderOrEqualUnwatchedStateNeverClearsAWatchedMark() {
        val watched = e(950_000, true, now)
        assertFalse("older", ProgressMerge.wins(e(400_000, false, now - 1), watched))
        assertFalse("same time, further on", ProgressMerge.wins(e(990_000, false, now), watched))
        assertTrue("same time: watched wins", ProgressMerge.wins(watched, e(990_000, false, now)))
    }

    @Test
    fun onEqualTimesTheLargerPositionWinsAndEqualEntriesDoNot() {
        assertTrue(ProgressMerge.wins(e(20, false, now), e(10, false, now)))
        assertFalse(ProgressMerge.wins(e(10, false, now), e(10, false, now)))
        assertTrue("anything beats nothing", ProgressMerge.wins(e(10, false, now), null))
    }

    @Test
    fun zeroAndFarFutureTimesAreIgnored() {
        val merged = ProgressMerge.mergeAll(
            listOf(
                mapOf("a" to e(10, false, 0), "b" to e(10, false, now + ProgressMerge.MAX_FUTURE_MS + 1)),
                mapOf("b" to e(5, false, now - 5), "c" to e(7, false, now + 60_000))
            ),
            now
        )
        assertEquals(setOf("b", "c"), merged.keys)
        assertEquals(e(5, false, now - 5), merged["b"])
    }

    @Test
    fun theResultDoesNotDependOnTheOrderOfDevicesAndIsStableWhenRepeated() {
        val phone = mapOf("x" to e(100, false, now - 30), "y" to e(900, true, now - 20))
        val tv = mapOf("x" to e(300, false, now - 10), "y" to e(50, false, now - 25))
        val pc = mapOf("x" to e(300, true, now - 10), "z" to e(1, false, now - 1))
        val forward = ProgressMerge.mergeAll(listOf(phone, tv, pc), now)
        assertEquals(forward, ProgressMerge.mergeAll(listOf(pc, tv, phone), now))
        assertEquals(forward, ProgressMerge.mergeAll(listOf(forward, phone, pc, forward), now))
        assertEquals(e(300, true, now - 10), forward["x"])
        assertEquals(e(900, true, now - 20), forward["y"])
    }
}
