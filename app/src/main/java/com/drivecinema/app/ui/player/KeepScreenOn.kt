package com.drivecinema.app.ui.player

import android.view.Window
import android.view.WindowManager

/**
 * Report F-28: the TV's screensaver (and the phone's screen timeout) turned the picture off during playback, because
 * nothing kept the screen on. The screen stays on only while a video is actually playing; paused, ended or failed,
 * the device may sleep again, as the streaming apps do.
 */
object KeepScreenOn {
    fun update(window: Window, isPlaying: Boolean) {
        if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
