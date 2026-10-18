package com.drivecinema.app.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone J.1: watched = 90 % played, or at most 3 minutes left once 85 % is played. */
class CompletionRuleTest {

    private fun min(m: Int, s: Int = 0) = (m * 60 + s) * 1_000L
    private fun watched(position: Long, duration: Long) = PlaybackProgressRules.calculateCompletion(position, duration)

    @Test
    fun episodeStoppedDuringItsEndingSongIsWatched() {
        // 24-min episode: 3 minutes left at 21:00 (87.5 %); the old 90 % rule needed 21:36.
        assertTrue(watched(min(21), min(24)))
        assertFalse(watched(min(20, 59), min(24)))
    }

    @Test
    fun longerEpisodeUsesNinetyPercent() {
        // 45 min: 90 % (40:30) comes before "3 minutes left" (42:00).
        assertTrue(watched(min(40, 30), min(45)))
        assertFalse(watched(min(40, 29), min(45)))
    }

    @Test
    fun filmStoppedFifteenMinutesBeforeTheEndStillResumes() {
        assertFalse(watched(min(105), min(120)))
        assertTrue(watched(min(108), min(120)))
    }

    @Test
    fun shortClipIsNotWatchedJustBecauseLessThanThreeMinutesAreLeft() {
        // A 2-min clip always has under 3 minutes left; the 85 % floor still applies.
        assertFalse(watched(min(0, 10), min(2)))
        assertFalse(watched(min(1, 40), min(2)))
        assertTrue(watched(min(1, 42), min(2)))
    }

    @Test
    fun unknownDurationOrNoPositionIsNeverWatched() {
        assertFalse(watched(min(10), 0L))
        assertFalse(watched(0L, min(24)))
        assertFalse(watched(-1L, min(24)))
    }
}
