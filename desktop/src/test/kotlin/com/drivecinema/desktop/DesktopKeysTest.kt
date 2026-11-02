package com.drivecinema.desktop

import androidx.compose.ui.input.key.Key
import com.drivecinema.desktop.browse.BrowseKind
import com.drivecinema.desktop.library.LibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopKeysTest {

    private val ready = LibraryState.Ready("Streaming")

    private fun action(
        key: Key,
        ctrl: Boolean = false,
        fullscreen: Boolean = false,
        current: DesktopScreen = DesktopScreen.Home,
        library: LibraryState = ready
    ) = DesktopKeys.action(key, ctrl, fullscreen, current, library)

    @Test
    fun escLeavesFullScreenFirstThenGoesBack() {
        assertEquals(DesktopKeyAction.LeaveFullscreen, action(Key.Escape, fullscreen = true))
        assertEquals(DesktopKeyAction.Back, action(Key.Escape))
    }

    @Test
    fun searchAndSectionShortcutsWorkInTheLibrary() {
        assertEquals(DesktopKeyAction.OpenSearch, action(Key.F, ctrl = true))
        assertEquals(DesktopKeyAction.OpenSection(DesktopScreen.Browse(BrowseKind.FILMS)), action(Key.Two, ctrl = true))
        assertNull(action(Key.Two, ctrl = true, current = DesktopScreen.Player("dune", "Dune")))
        assertNull(action(Key.F, ctrl = true, current = DesktopScreen.SignIn))
        assertNull(action(Key.F))
    }

    /**
     * Regression (desktop polish 2026-11): the folder picker covers the window, but Esc called navigator.back() on the
     * hidden screens, and Ctrl+F opened Search behind it, so Cancel landed on Search.
     */
    @Test
    fun escClosesAPickerThatCanBeCancelled() {
        val switching = LibraryState.ChoosingFolder(isLoading = false, canCancel = true)
        assertEquals(DesktopKeyAction.CloseFolderPicker, action(Key.Escape, current = DesktopScreen.MovieDetails("dune", "Dune"), library = switching))
        // Leaving full screen still comes first.
        assertEquals(DesktopKeyAction.LeaveFullscreen, action(Key.Escape, fullscreen = true, library = switching))
    }

    @Test
    fun escDoesNothingWhenThePickerCannotBeLeft() {
        val firstRun = LibraryState.ChoosingFolder(isLoading = false, canCancel = false)
        val reading = LibraryState.ChoosingFolder(isLoading = false, canCancel = true, switchingTo = "Films")
        assertEquals(DesktopKeyAction.Ignore, action(Key.Escape, library = firstRun))
        assertEquals(DesktopKeyAction.Ignore, action(Key.Escape, library = reading))
        assertEquals(DesktopKeyAction.Ignore, action(Key.Escape, library = LibraryState.Discovering))
    }

    @Test
    fun navigationShortcutsDoNothingBehindThePicker() {
        val picker = LibraryState.ChoosingFolder(isLoading = false, canCancel = true)
        assertEquals(DesktopKeyAction.Ignore, action(Key.F, ctrl = true, library = picker))
        assertEquals(DesktopKeyAction.Ignore, action(Key.One, ctrl = true, library = picker))
        assertEquals(DesktopKeyAction.Ignore, action(Key.Three, ctrl = true, library = LibraryState.Discovering))
        // Typing in the picker's filter field is left alone.
        assertNull(action(Key.F, library = picker))
    }
}
