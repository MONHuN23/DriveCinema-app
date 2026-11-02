package com.drivecinema.app.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.ui.navigation.AppNavigator

/**
 * Task M.3 (F-38): the card (or header) a TV screen opened, so Back can put focus on it again instead of on the
 * screen's first button. It is saved with the screen's other state while the opened screen is on top (Navigation 3's
 * saveable-state decorator). A plain field, not snapshot state, so opening a card recomposes nothing.
 */
class TvReturnFocus internal constructor(openedKey: String?) {

    var openedKey: String? = openedKey
        private set

    private val requester = FocusRequester()

    /** Call before navigating away from the item with [key]. */
    fun opened(key: String) {
        openedKey = key
    }

    /** Goes on the item with [key]; only the opened item gets the requester [restore] uses. */
    fun modifierFor(key: String): Modifier = if (key == openedKey) Modifier.focusRequester(requester) else Modifier

    /** Focuses the opened item; false on a first visit or when that item isn't on screen. */
    fun restore(): Boolean = openedKey != null && runCatching { requester.requestFocus() }.getOrDefault(false)

    companion object {
        val Saver: Saver<TvReturnFocus, String> = Saver(save = { it.openedKey }, restore = { TvReturnFocus(it) })
    }
}

@Composable
fun rememberTvReturnFocus(): TvReturnFocus = rememberSaveable(saver = TvReturnFocus.Saver) { TvReturnFocus(null) }

/**
 * A TV screen's first focus once it is [ready]: the item it opened when Back brought it back, otherwise [initial].
 * Waits a frame first, so lazy rows and grids have placed their cards (and restored their scroll).
 */
@Composable
fun TvInitialFocus(returnFocus: TvReturnFocus, ready: Boolean = true, initial: () -> Unit) {
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        withFrameNanos { }
        val restored = returnFocus.restore()
        AppLog.d(AppNavigator.TAG, "TV focus: opened ${returnFocus.openedKey}, restored $restored")
        if (!restored) initial()
    }
}
