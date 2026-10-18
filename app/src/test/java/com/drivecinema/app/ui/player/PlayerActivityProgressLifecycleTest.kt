package com.drivecinema.app.ui.player

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.player.PlaybackProgressTracker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * Report F-01: after the player went to the background (onStop) and came back (onStart), progress tracking
 * must be attached again, otherwise nothing is saved for the rest of the session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PlayerActivityProgressLifecycleTest {

    private fun activeListener(activity: PlayerActivity): Any? {
        val trackerField = PlayerActivity::class.java.getDeclaredField("progressTracker").apply { isAccessible = true }
        val tracker = trackerField.get(activity) as PlaybackProgressTracker
        val listenerField = PlaybackProgressTracker::class.java.getDeclaredField("activeListener").apply { isAccessible = true }
        return listenerField.get(tracker)
    }

    @Test
    fun progressTrackingResumesAfterBackgroundAndReturn() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DRIVE_FILE_ID, "test_file_id")
            putExtra(PlayerActivity.EXTRA_TITLE, "Test Movie")
        }
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            scenario.onActivity { assertNotNull("tracking is attached while playing", activeListener(it)) }

            scenario.moveToState(Lifecycle.State.CREATED) // onStop: screen locked / Home pressed
            scenario.onActivity { assertNull("tracking is detached in the background", activeListener(it)) }

            scenario.moveToState(Lifecycle.State.RESUMED) // onStart: back in the player
            scenario.onActivity { assertNotNull("tracking must be attached again after returning", activeListener(it)) }
        }
    }
}
