package com.drivecinema.desktop

import com.drivecinema.desktop.browse.BrowseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopNavigatorTest {

    @Test
    fun startsAtSignInAndCannotGoBack() {
        val navigator = DesktopNavigator()
        assertEquals(DesktopScreen.SignIn, navigator.current)
        assertFalse(navigator.canGoBack)
        assertFalse(navigator.back())
        assertEquals(DesktopScreen.SignIn, navigator.current)
    }

    @Test
    fun backPopsOneScreenAtATime() {
        val navigator = DesktopNavigator(DesktopScreen.Home)
        val details = DesktopScreen.MovieDetails("dune", "Dune Part Two")
        navigator.navigate(details)
        navigator.navigate(DesktopScreen.Player("dune", "Dune Part Two"))

        assertTrue(navigator.back())
        assertEquals(details, navigator.current)
        assertTrue(navigator.back())
        assertEquals(DesktopScreen.Home, navigator.current)
        assertFalse(navigator.back())
    }

    @Test
    fun navigatingToTheCurrentScreenDoesNotStackDuplicates() {
        val navigator = DesktopNavigator(DesktopScreen.Home)
        navigator.navigate(DesktopScreen.Home)
        assertEquals(1, navigator.backStack.value.size)
    }

    @Test
    fun resetReplacesTheWholeStack() {
        val navigator = DesktopNavigator()
        navigator.navigate(DesktopScreen.Home)
        navigator.navigate(DesktopScreen.MovieDetails("x", "X"))
        navigator.resetTo(DesktopScreen.Home)
        assertEquals(listOf<DesktopScreen>(DesktopScreen.Home), navigator.backStack.value)
    }

    @Test
    fun sectionsSitAboveHomeAndBackFromDetailsReturnsToTheView() {
        // Task L.1: Films → details → Back returns to Films; Esc from Films goes Home.
        val films = DesktopScreen.Browse(BrowseKind.FILMS)
        val navigator = DesktopNavigator(DesktopScreen.Home)
        navigator.navigate(DesktopScreen.Search)
        navigator.openSection(films)
        assertEquals(listOf(DesktopScreen.Home, films), navigator.backStack.value)
        assertEquals(films, navigator.section)

        navigator.navigate(DesktopScreen.MovieDetails("dune", "Dune"))
        assertEquals("details opened from Films keep the Films tab", films, navigator.section)
        assertTrue(navigator.back())
        assertEquals(films, navigator.current)
        assertTrue(navigator.back())
        assertEquals(DesktopScreen.Home, navigator.current)
        assertEquals(DesktopScreen.Home, navigator.section)
    }

    @Test
    fun switchingSectionsNeverStacksThem() {
        val navigator = DesktopNavigator(DesktopScreen.Home)
        navigator.openSection(DesktopScreen.Browse(BrowseKind.FILMS))
        navigator.openSection(DesktopScreen.Browse(BrowseKind.SERIES))
        assertEquals(listOf(DesktopScreen.Home, DesktopScreen.Browse(BrowseKind.SERIES)), navigator.backStack.value)
        navigator.openSection(DesktopScreen.Home)
        assertEquals(listOf<DesktopScreen>(DesktopScreen.Home), navigator.backStack.value)
    }

    @Test
    fun replaceSwapsThePlayerAndBackReturnsToTheSeries() {
        val navigator = DesktopNavigator(DesktopScreen.Home)
        val series = DesktopScreen.SeriesDetails("show", "Show")
        navigator.navigate(series)
        navigator.navigate(DesktopScreen.Player("e1", "Show - S1E1"))
        navigator.replace(DesktopScreen.Player("e2", "Show - S1E2"))
        assertEquals(DesktopScreen.Player("e2", "Show - S1E2"), navigator.current)
        assertTrue(navigator.back())
        assertEquals(series, navigator.current)
    }

    /** Task P.6 (D-P3): moving through the settings shell's side list never stacks pages; one Esc returns. */
    @Test
    fun settingsPagesTakeEachOthersPlaceSoOneBackReturnsToTheLibrary() {
        val navigator = DesktopNavigator(DesktopScreen.Home)
        val films = DesktopScreen.Browse(BrowseKind.FILMS)
        navigator.openSection(films)

        navigator.openSettingsPage(DesktopScreen.LocalFolders)
        navigator.openSettingsPage(DesktopScreen.Sharing)
        navigator.openSettingsPage(DesktopScreen.Import)
        navigator.openSettingsPage(DesktopScreen.Pcs)
        navigator.openSettingsPage(DesktopScreen.SetupGuide("computer-folders"))
        assertEquals(listOf(DesktopScreen.Home, films, DesktopScreen.SetupGuide("computer-folders")), navigator.backStack.value)

        assertTrue(navigator.back())
        assertEquals(films, navigator.current)
    }

    @Test
    fun aSettingsPageOpensOnTopOfAnythingElseAndOpeningItAgainDoesNothing() {
        val navigator = DesktopNavigator(DesktopScreen.SignIn)
        navigator.openSettingsPage(DesktopScreen.SetupGuide())
        assertEquals(listOf(DesktopScreen.SignIn, DesktopScreen.SetupGuide()), navigator.backStack.value)

        val home = DesktopNavigator(DesktopScreen.Home)
        home.navigate(DesktopScreen.MovieDetails("dune", "Dune"))
        home.openSettingsPage(DesktopScreen.LocalFolders)
        home.openSettingsPage(DesktopScreen.LocalFolders)
        assertEquals(3, home.backStack.value.size)
        assertTrue(DesktopScreen.LocalFolders.isSettingsPage)
        assertFalse(DesktopScreen.MovieDetails("dune", "Dune").isSettingsPage)
    }
}
