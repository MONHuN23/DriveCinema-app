package com.drivecinema.desktop.naming

import com.drivecinema.desktop.DesktopNavigator
import com.drivecinema.desktop.DesktopScreen
import com.drivecinema.desktop.isSettingsPage
import com.drivecinema.desktop.ui.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task R.3: the naming helper is a page of the settings shell (its side list swaps it with the others, one Esc returns
 * to the library); the Naming check opens on top of its series page; computer-folder series have no Drive folder.
 */
class DesktopNamingTest {

    @Test
    fun theNamingHelperIsASettingsPageInTheSideList() {
        assertTrue(DesktopScreen.NamingHelper.isSettingsPage)
        assertEquals(DesktopScreen.NamingHelper, SettingsPage.NAMING_HELPER.screen)
        assertEquals("Naming helper", SettingsPage.NAMING_HELPER.label)

        val navigator = DesktopNavigator(DesktopScreen.Home)
        navigator.openSettingsPage(DesktopScreen.LocalFolders)
        navigator.openSettingsPage(DesktopScreen.NamingHelper)
        assertEquals(listOf(DesktopScreen.Home, DesktopScreen.NamingHelper), navigator.backStack.value)
        assertTrue(navigator.back())
        assertEquals(DesktopScreen.Home, navigator.current)
    }

    @Test
    fun theNamingCheckOpensOnTopOfItsSeriesAndBackReturnsThere() {
        val series = DesktopScreen.SeriesDetails("folder1", "Breaking Bad")
        val navigator = DesktopNavigator(DesktopScreen.Home)
        navigator.navigate(series)
        navigator.navigate(DesktopScreen.NamingCheck("folder1", "Breaking Bad"))
        assertFalse(navigator.current.isSettingsPage)

        // "Naming helper" from a local series' check takes the check's place: Back still returns to the series.
        navigator.replace(DesktopScreen.NamingHelper)
        assertTrue(navigator.back())
        assertEquals(series, navigator.current)
    }

    @Test
    fun onlyDriveSeriesAreChecked() {
        assertTrue(DesktopNaming.isDriveSeries("1AbCdEfGhIjK"))
        assertFalse(DesktopNaming.isDriveSeries("local:file:///home/me/Series/Show"))
    }
}
