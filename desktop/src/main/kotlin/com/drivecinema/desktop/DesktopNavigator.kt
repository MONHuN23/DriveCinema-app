package com.drivecinema.desktop

import com.drivecinema.desktop.browse.BrowseKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screens of the desktop app. */
sealed interface DesktopScreen {
    data object SignIn : DesktopScreen
    data object Home : DesktopScreen
    data object Search : DesktopScreen
    /** Task L.1: every film or every series, with sort and filters. */
    data class Browse(val kind: BrowseKind) : DesktopScreen
    /** The Help Center (Task P.4), at [topicId] when a page links to one (Task P.6). */
    data class SetupGuide(val topicId: String? = null) : DesktopScreen
    data object LocalFolders : DesktopScreen
    /** Task I.1: upload local videos to the Drive library. */
    data object Import : DesktopScreen
    /** Task I.2: share local folders with paired phones and TVs. */
    data object Sharing : DesktopScreen
    /** Task I.2: play films stored on another PC. */
    data object Pcs : DesktopScreen
    data class MovieDetails(val itemId: String, val title: String) : DesktopScreen
    data class SeriesDetails(val seriesId: String, val title: String) : DesktopScreen
    /** Task R.3: the naming helper, a page of the settings shell. */
    data object NamingHelper : DesktopScreen
    /** Task R.3: a series' Naming check, opened from its page. */
    data class NamingCheck(val seriesId: String, val title: String) : DesktopScreen
    /** [fromStart]: "Start over" instead of resuming. */
    data class Player(val itemId: String, val title: String, val fromStart: Boolean = false) : DesktopScreen
}

/** Task P.6 (decision D-P3): the pages of the settings shell's side list, and the Help Center below it. */
val DesktopScreen.isSettingsPage: Boolean
    get() = this == DesktopScreen.LocalFolders || this == DesktopScreen.Import || this == DesktopScreen.Sharing ||
        this == DesktopScreen.Pcs || this == DesktopScreen.NamingHelper || this is DesktopScreen.SetupGuide

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

    /**
     * Task P.6 (D-P3): opens a settings page. From another settings page it takes that page's place, so moving through
     * the side list never stacks pages and one Esc returns to the library; from anywhere else it opens on top.
     */
    fun openSettingsPage(page: DesktopScreen) {
        if (current == page) return
        if (current.isSettingsPage) replace(page) else navigate(page)
    }

    /** Swaps the top screen, e.g. the player moving on to the next episode (Back still returns to the series). */
    fun replace(screen: DesktopScreen) {
        stack.value = stack.value.dropLast(1) + screen
    }

    /** Replaces the whole stack, e.g. after sign-in (Home) or sign-out (SignIn). */
    fun resetTo(screen: DesktopScreen) {
        stack.value = listOf(screen)
    }

    /**
     * Task L.1: the Home / Films / Series tabs (and Ctrl+1/2/3). Films and Series sit one step above Home, so
     * Esc from them goes Home; whatever was open (details, search…) is closed.
     */
    fun openSection(screen: DesktopScreen) {
        stack.value = if (screen == DesktopScreen.Home) listOf(DesktopScreen.Home) else listOf(DesktopScreen.Home, screen)
    }

    /** The tab to highlight: the section the current screen was opened from (Home when none). */
    val section: DesktopScreen get() = sectionOf(stack.value)

    companion object {
        /** [section] for a collected back stack (Compose reads the stack as state). */
        fun sectionOf(stack: List<DesktopScreen>): DesktopScreen =
            stack.getOrNull(1)?.takeIf { it is DesktopScreen.Browse && stack.first() == DesktopScreen.Home }
                ?: DesktopScreen.Home
    }

    /** Returns false when already at the root (nothing popped). */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.value = stack.value.dropLast(1)
        return true
    }
}
