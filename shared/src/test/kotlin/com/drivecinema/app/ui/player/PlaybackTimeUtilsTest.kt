package com.drivecinema.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeUtilsTest {

    @Test
    fun testFormatTimeZeroAndMinutes() {
        assertEquals("00:00", PlaybackTimeUtils.formatTime(0L))
        assertEquals("00:09", PlaybackTimeUtils.formatTime(9_000L))
        assertEquals("01:05", PlaybackTimeUtils.formatTime(65_000L))
        assertEquals("59:59", PlaybackTimeUtils.formatTime(3_599_000L))
    }

    @Test
    fun testFormatTimeWithHours() {
        assertEquals("1:00:00", PlaybackTimeUtils.formatTime(3_600_000L))
        assertEquals("1:01:05", PlaybackTimeUtils.formatTime(3_665_000L))
        assertEquals("2:34:12", PlaybackTimeUtils.formatTime(9_252_000L))
    }

    @Test
    fun testFormatTimeNegativeReturnsZero() {
        assertEquals("00:00", PlaybackTimeUtils.formatTime(-5000L))
    }

    @Test
    fun testCalculateRelativeSeekWithinBounds() {
        val durationMs = 120_000L
        val currentMs = 30_000L

        val forward = PlaybackTimeUtils.calculateRelativeSeek(currentMs, 10_000L, durationMs)
        assertEquals(40_000L, forward)

        val rewind = PlaybackTimeUtils.calculateRelativeSeek(currentMs, -10_000L, durationMs)
        assertEquals(20_000L, rewind)
    }

    @Test
    fun testCalculateRelativeSeekClampsAtZeroAndDuration() {
        val durationMs = 120_000L

        // Seek before start clamps to 0
        val clampedStart = PlaybackTimeUtils.calculateRelativeSeek(5_000L, -10_000L, durationMs)
        assertEquals(0L, clampedStart)

        // Seek past end clamps to duration
        val clampedEnd = PlaybackTimeUtils.calculateRelativeSeek(115_000L, 10_000L, durationMs)
        assertEquals(120_000L, clampedEnd)
    }

    @Test
    fun testCalculateProgressAndSeekFromProgress() {
        val durationMs = 100_000L

        assertEquals(0f, PlaybackTimeUtils.calculateProgress(0L, durationMs), 0.001f)
        assertEquals(0.5f, PlaybackTimeUtils.calculateProgress(50_000L, durationMs), 0.001f)
        assertEquals(1f, PlaybackTimeUtils.calculateProgress(100_000L, durationMs), 0.001f)
        assertEquals(0f, PlaybackTimeUtils.calculateProgress(10_000L, 0L), 0.001f)

        assertEquals(0L, PlaybackTimeUtils.calculateSeekFromProgress(0f, durationMs))
        assertEquals(50_000L, PlaybackTimeUtils.calculateSeekFromProgress(0.5f, durationMs))
        assertEquals(100_000L, PlaybackTimeUtils.calculateSeekFromProgress(1f, durationMs))
    }

    @Test
    fun testResumeBeyondTheEndOfThisCopyStartsOver() {
        // Regression (found on the emulator): Drive progress at 29:57 applied to a 4 s local copy ended playback
        // instantly and recorded the film as watched.
        assertEquals(true, PlaybackTimeUtils.shouldRestartFromBeginning(resumePositionMs = 1_797_952L, durationMs = 4_000L))
        assertEquals(true, PlaybackTimeUtils.shouldRestartFromBeginning(resumePositionMs = 99_000L, durationMs = 100_000L))
        assertEquals(false, PlaybackTimeUtils.shouldRestartFromBeginning(resumePositionMs = 1_797_952L, durationMs = 9_948_898L))
        assertEquals(false, PlaybackTimeUtils.shouldRestartFromBeginning(resumePositionMs = 0L, durationMs = 4_000L))
        assertEquals(false, PlaybackTimeUtils.shouldRestartFromBeginning(resumePositionMs = 5_000L, durationMs = 0L))
    }
}
