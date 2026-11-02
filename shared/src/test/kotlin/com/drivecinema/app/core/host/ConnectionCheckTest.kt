package com.drivecinema.app.core.host

import com.drivecinema.app.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task I.2 §2.6: warn only when the connection can't keep up with the film's bitrate (plus a 30 % margin). */
class ConnectionCheckTest {

    @Test
    fun bitrateIsSizeOverLength() {
        // 4.5 GB over 2 hours = 5 Mbit/s.
        assertEquals(5.0, ConnectionCheck.neededMbps(4_500_000_000, 7_200_000), 0.01)
        assertEquals(0.0, ConnectionCheck.neededMbps(1_000, 0), 0.0)
        // 2 MB in 0.5 s = 32 Mbit/s.
        assertEquals(32.0, ConnectionCheck.measuredMbps(2_000_000, 500_000_000), 0.01)
    }

    @Test
    fun warnsOnlyBelowTheMargin() {
        assertFalse(ConnectionVerdict(neededMbps = 5.0, measuredMbps = 50.0).tooSlow)
        assertFalse("6.5 is exactly 1.3 × 5", ConnectionVerdict(5.0, 6.5).tooSlow)
        assertTrue(ConnectionVerdict(5.0, 6.0).tooSlow)
        assertTrue(ConnectionVerdict(40.0, 12.0).tooSlow)
    }

    @Test
    fun theLengthComesFromTheBestSourceAvailable() {
        assertEquals(5_400_000L, ConnectionCheck.durationMs(MediaType.MOVIE, watchedDurationMs = 5_400_000, runtimeSeconds = 7_200))
        assertEquals(7_200_000L, ConnectionCheck.durationMs(MediaType.MOVIE, watchedDurationMs = null, runtimeSeconds = 7_200))
        assertEquals(110 * 60_000L, ConnectionCheck.durationMs(MediaType.MOVIE, null, null))
        assertEquals(42 * 60_000L, ConnectionCheck.durationMs(MediaType.EPISODE, 0, 0))
    }

    @Test
    fun theMessageSaysWhatToDo() {
        val verdict = ConnectionVerdict(9.4, 3.96)
        assertEquals(
            "This film needs about 9.4 Mbit/s, but the connection to the PC gives about 4.0, so it may keep pausing. " +
                "Play the Drive copy instead, or use Ethernet or stronger Wi-Fi.",
            verdict.message(hasDriveCopy = true)
        )
        assertTrue(ConnectionVerdict(42.0, 12.0).message(hasDriveCopy = false).contains("about 42 Mbit/s"))
        assertTrue(ConnectionVerdict(42.0, 12.0).message(hasDriveCopy = false).contains("upload"))
    }
}
