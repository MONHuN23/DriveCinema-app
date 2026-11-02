package com.drivecinema.desktop

import androidx.compose.ui.input.key.Key
import com.drivecinema.desktop.browse.BrowseKind
import com.drivecinema.desktop.library.LibraryState

/** What a key press in the main window does. */
sealed interface DesktopKeyAction {
    data object LeaveFullscreen : DesktopKeyAction
    data object Back : DesktopKeyAction
    data object CloseFolderPicker : DesktopKeyAction
    data object OpenSearch : DesktopKeyAction
    data class OpenSection(val screen: DesktopScreen) : DesktopKeyAction
    /** Handled by doing nothing, so the key can't change the screens hidden behind the folder picker. */
    data object Ignore : DesktopKeyAction
}

/** The window's shortcuts: Esc, Ctrl+F (search) and Ctrl+1/2/3 (Home / Films / Series, Task L.1). */
object DesktopKeys {
    private val sectionKeys = mapOf(
        Key.One to DesktopScreen.Home,
        Key.Two to DesktopScreen.Browse(BrowseKind.FILMS),
        Key.Three to DesktopScreen.Browse(BrowseKind.SERIES)
    )

    /**
     * The action for a key press, or null when the window leaves it to the screen. Esc leaves full screen first, then
     * goes back one screen (like Back on Android). While the library folder is being found or chosen, that screen
     * covers the window and the navigator's screens are hidden: Esc closes the picker (only when a library exists to
     * return to) and the navigation shortcuts do nothing.
     */
    fun action(key: Key, ctrl: Boolean, isFullscreen: Boolean, current: DesktopScreen, library: LibraryState): DesktopKeyAction? {
        val settingUpLibrary = library is LibraryState.Discovering || library is LibraryState.ChoosingFolder
        return when {
            key == Key.Escape && isFullscreen -> DesktopKeyAction.LeaveFullscreen
            key == Key.Escape && library is LibraryState.ChoosingFolder ->
                if (library.canCancel && library.switchingTo == null) DesktopKeyAction.CloseFolderPicker else DesktopKeyAction.Ignore
            key == Key.Escape && settingUpLibrary -> DesktopKeyAction.Ignore
            key == Key.Escape -> DesktopKeyAction.Back
            ctrl && (key == Key.F || key in sectionKeys) && settingUpLibrary -> DesktopKeyAction.Ignore
            ctrl && key == Key.F && current != DesktopScreen.SignIn -> DesktopKeyAction.OpenSearch
            // Not on sign-in, not while a video plays.
            ctrl && key in sectionKeys && current != DesktopScreen.SignIn && current !is DesktopScreen.Player ->
                DesktopKeyAction.OpenSection(sectionKeys.getValue(key))
            else -> null
        }
    }
}
