package com.drivecinema.app.ui.player

import android.app.Activity
import android.view.WindowManager
import com.drivecinema.app.core.host.ConnectionVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerScreenAwakeAndNextEpisodeTest {

    /** Report F-28: the screen stays on while a video plays and may sleep again when it stops. */
    @Test
    fun screenStaysOnOnlyWhilePlaying() {
        val window = Robolectric.buildActivity(Activity::class.java).setup().get().window
        fun keptOn() = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

        assertFalse(keptOn())
        KeepScreenOn.update(window, isPlaying = true)
        assertTrue(keptOn())
        KeepScreenOn.update(window, isPlaying = false)
        assertFalse("paused / ended / error: the TV may sleep again", keptOn())
    }

    /** Report F-29: a playable next episode stays in the player; anything needing a dialog or message goes back. */
    @Test
    fun nextEpisodePlaysInPlaceUnlessItNeedsAChoice() {
        val drive = ResolvedPlayback.Playable(PlaybackSource.Drive("e2"), resumePositionMs = 0L)
        assertEquals(drive, drive.playableInPlace())

        val pc = ResolvedPlayback.Playable(PlaybackSource.Host("dchost://pc/file/1", "https://pc/1"), 0L)
        val slow = ResolvedPlayback.SlowConnection(pc, fallback = drive, verdict = ConnectionVerdict(neededMbps = 9.0, measuredMbps = 4.0))
        assertNull("the slow-connection dialog lives in MainActivity", slow.playableInPlace())
        assertNull("MainActivity shows the 'not available' message", ResolvedPlayback.Unavailable.playableInPlace())
    }
}
