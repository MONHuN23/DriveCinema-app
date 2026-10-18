package com.drivecinema.app.ui.player

import android.content.Intent
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** Task E.5: extra TV remote, keyboard and gamepad keys in the player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RemoteKeyMapperTest {

    @Test
    fun existingMediaKeysKeepTheirBehaviour() {
        assertEquals(PlayerKeyAction.TogglePlayPause, RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false))
        assertEquals(PlayerKeyAction.Play, RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_PLAY, false))
        assertEquals(PlayerKeyAction.Pause, RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_PAUSE, false))
        assertEquals(PlayerKeyAction.SeekBy(10_000L), RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, false))
        assertEquals(PlayerKeyAction.SeekBy(-10_000L), RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_REWIND, false))
    }

    @Test
    fun newMediaKeysAreMapped() {
        assertEquals(PlayerKeyAction.Exit, RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_STOP, false))
        assertEquals(PlayerKeyAction.SeekBy(10_000L), RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_STEP_FORWARD, false))
        assertEquals(PlayerKeyAction.SeekBy(-10_000L), RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD, false))
    }

    @Test
    fun nextKeyOnlyActsWhenThereIsANextEpisode() {
        assertEquals(PlayerKeyAction.NextEpisode, RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_NEXT, hasNextEpisode = true))
        assertNull(RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_MEDIA_NEXT, hasNextEpisode = false))
    }

    @Test
    fun navigationKeysAreNotMediaKeys() {
        assertNull(RemoteKeyMapper.mediaAction(KeyEvent.KEYCODE_DPAD_CENTER, true))
        assertTrue(RemoteKeyMapper.isNavigationKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(RemoteKeyMapper.isNavigationKey(KeyEvent.KEYCODE_MEDIA_STOP))
    }

    @Test
    fun gamepadButtonsAreNormalized() {
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, RemoteKeyMapper.normalizedKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(KeyEvent.KEYCODE_BACK, RemoteKeyMapper.normalizedKeyCode(KeyEvent.KEYCODE_BUTTON_B))

        val up = KeyEvent(100L, 200L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, 0)
        val normalized = RemoteKeyMapper.normalize(up)
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, normalized.keyCode)
        assertEquals(KeyEvent.ACTION_UP, normalized.action)
        assertEquals(100L, normalized.downTime)

        val dpad = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
        assertSame(dpad, RemoteKeyMapper.normalize(dpad))
    }

    private fun launchPlayer(block: (PlayerActivity, androidx.compose.runtime.MutableState<Boolean>) -> Unit) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DRIVE_FILE_ID, "test_file_id")
            putExtra(PlayerActivity.EXTRA_TITLE, "Test Movie")
        }
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val field = PlayerActivity::class.java.getDeclaredField("isControlsVisible")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                block(activity, field.get(activity) as androidx.compose.runtime.MutableState<Boolean>)
            }
        }
    }

    @Test
    fun stopKeyLeavesThePlayer() {
        launchPlayer { activity, _ ->
            assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)))
            assertTrue("Stop should close the player", activity.isFinishing)
        }
    }

    @Test
    fun gamepadARevealsHiddenControls() {
        launchPlayer { activity, controlsVisible ->
            controlsVisible.value = false
            assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
            assertTrue(controlsVisible.value)
        }
    }

    @Test
    fun gamepadBHidesVisibleControlsLikeBack() {
        launchPlayer { activity, controlsVisible ->
            controlsVisible.value = true
            assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
            assertFalse(controlsVisible.value)
            assertFalse(activity.isFinishing)
        }
    }
}
