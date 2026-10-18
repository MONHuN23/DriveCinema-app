package com.drivecinema.app.ui.player

import android.view.KeyEvent

/** What a remote, keyboard or gamepad key does in the player (besides D-pad navigation of the controls). */
sealed interface PlayerKeyAction {
    data object TogglePlayPause : PlayerKeyAction
    data object Play : PlayerKeyAction
    data object Pause : PlayerKeyAction
    data class SeekBy(val deltaMs: Long) : PlayerKeyAction
    data object Exit : PlayerKeyAction
    data object NextEpisode : PlayerKeyAction
}

/**
 * Maps TV remote, keyboard and gamepad keys for the player. Gamepad A/B are translated to the D-pad
 * centre / Back keys the rest of the player (and Compose) already understands.
 */
object RemoteKeyMapper {

    const val SEEK_STEP_MS = 10_000L

    /** Gamepad A selects like D-pad centre; gamepad B goes back like the remote's Back key. */
    fun normalizedKeyCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
        else -> keyCode
    }

    /** Returns [event] with its key code normalized, or [event] itself when nothing changes. */
    fun normalize(event: KeyEvent): KeyEvent {
        val keyCode = normalizedKeyCode(event.keyCode)
        if (keyCode == event.keyCode) return event
        return KeyEvent(
            event.downTime, event.eventTime, event.action, keyCode, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source
        )
    }

    /** Dedicated media keys; null when the key is not a media key (or Next has no episode to go to). */
    fun mediaAction(keyCode: Int, hasNextEpisode: Boolean): PlayerKeyAction? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerKeyAction.TogglePlayPause
        KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerKeyAction.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerKeyAction.Pause
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> PlayerKeyAction.SeekBy(SEEK_STEP_MS)
        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> PlayerKeyAction.SeekBy(-SEEK_STEP_MS)
        KeyEvent.KEYCODE_MEDIA_STOP -> PlayerKeyAction.Exit
        KeyEvent.KEYCODE_MEDIA_NEXT -> if (hasNextEpisode) PlayerKeyAction.NextEpisode else null
        else -> null
    }

    /** Keys that reveal hidden controls (and otherwise move focus within them). */
    fun isNavigationKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_SPACE -> true
        else -> false
    }
}
