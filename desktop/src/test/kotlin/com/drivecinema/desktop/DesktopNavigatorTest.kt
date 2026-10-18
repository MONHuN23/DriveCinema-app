package com.drivecinema.desktop

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
}
