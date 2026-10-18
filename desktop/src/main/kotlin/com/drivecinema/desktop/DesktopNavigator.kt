package com.drivecinema.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screens of the desktop app. */
sealed interface DesktopScreen {
    data object SignIn : DesktopScreen
    data object Home : DesktopScreen
    data object Search : DesktopScreen
    data object SetupGuide : DesktopScreen
    data object LocalFolders : DesktopScreen
    data class MovieDetails(val itemId: String, val title: String) : DesktopScreen
    data class SeriesDetails(val seriesId: String, val title: String) : DesktopScreen
    /** [fromStart]: "Start over" instead of resuming. */
    data class Player(val itemId: String, val title: String, val fromStart: Boolean = false) : DesktopScreen
}

/** Back stack for the desktop app: Esc / the Back button pop one screen, never below the root. */
class DesktopNavigator(start: DesktopScreen = DesktopScreen.SignIn) {

    private val stack = MutableStateFlow(listOf(start))

    val backStack: StateFlow<List<DesktopScreen>> = stack.asStateFlow()

    val current: DesktopScreen get() = stack.value.last()

    val canGoBack: Boolean get() = stack.value.size > 1

    fun navigate(screen: DesktopScreen) {
        if (current == screen) return
        stack.value = stack.value + screen
    }

    /** Swaps the top screen, e.g. the player moving on to the next episode (Back still returns to the series). */
    fun replace(screen: DesktopScreen) {
        stack.value = stack.value.dropLast(1) + screen
    }

    /** Replaces the whole stack, e.g. after sign-in (Home) or sign-out (SignIn). */
    fun resetTo(screen: DesktopScreen) {
        stack.value = listOf(screen)
    }

    /** Returns false when already at the root (nothing popped). */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.value = stack.value.dropLast(1)
        return true
    }
}
