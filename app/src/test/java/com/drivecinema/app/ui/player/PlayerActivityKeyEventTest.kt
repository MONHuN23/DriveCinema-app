package com.drivecinema.app.ui.player

import android.content.Intent
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PlayerActivityKeyEventTest {

    @Test
    fun testDpadRevealsControlsWhenHidden() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DRIVE_FILE_ID, "test_file_id")
            putExtra(PlayerActivity.EXTRA_TITLE, "Test Movie")
        }

        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val field = PlayerActivity::class.java.getDeclaredField("isControlsVisible")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val isControlsVisible = field.get(activity) as androidx.compose.runtime.MutableState<Boolean>
                isControlsVisible.value = false

                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
                val handled = activity.dispatchKeyEvent(downEvent)

                assertTrue("DPAD_CENTER should be consumed when controls are hidden", handled)
                assertTrue("Controls should become visible on DPAD_CENTER", isControlsVisible.value)
            }
        }
    }

    @Test
    fun testBackKeyHidesControlsWhenVisible() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DRIVE_FILE_ID, "test_file_id")
            putExtra(PlayerActivity.EXTRA_TITLE, "Test Movie")
        }

        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val field = PlayerActivity::class.java.getDeclaredField("isControlsVisible")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val isControlsVisible = field.get(activity) as androidx.compose.runtime.MutableState<Boolean>
                isControlsVisible.value = true

                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
                val handled = activity.dispatchKeyEvent(downEvent)

                assertTrue("Back key should be consumed to hide controls", handled)
                assertFalse("Controls should be hidden after Back key", isControlsVisible.value)
                assertFalse("Activity should NOT be finished yet", activity.isFinishing)
            }
        }
    }

    @Test
    fun testDpadArrowsRevealControlsWhenHidden() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DRIVE_FILE_ID, "test_file_id")
            putExtra(PlayerActivity.EXTRA_TITLE, "Test Movie")
        }

        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val field = PlayerActivity::class.java.getDeclaredField("isControlsVisible")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val isControlsVisible = field.get(activity) as androidx.compose.runtime.MutableState<Boolean>

                val navKeys = listOf(
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_ENTER
                )

                for (key in navKeys) {
                    isControlsVisible.value = false
                    val event = KeyEvent(KeyEvent.ACTION_DOWN, key)
                    val handled = activity.dispatchKeyEvent(event)
                    assertTrue("Key $key should be consumed when controls are hidden", handled)
                    assertTrue("Controls should become visible on key $key", isControlsVisible.value)
                }
            }
        }
    }
}
