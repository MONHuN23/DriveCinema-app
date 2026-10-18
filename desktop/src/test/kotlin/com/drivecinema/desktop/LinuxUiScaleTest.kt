package com.drivecinema.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinuxUiScaleTest {

    private val gnome200 = "Xcursor.size:\t24\nXcursor.theme:\tYaru\nXft.dpi:\t192\n"

    @Test
    fun xftDpiGivesWholeScale() {
        assertEquals(2, LinuxUiScale.scaleFromXrdb(gnome200))
        assertEquals(3, LinuxUiScale.scaleFromXrdb("Xft.dpi:\t288\n"))
    }

    @Test
    fun normalOrTextOnlyDpiLeavesScaleAlone() {
        assertNull(LinuxUiScale.scaleFromXrdb("Xft.dpi:\t96\n"))
        // Large-text setting at 1x: 1.5 x 96, must not double the whole UI.
        assertNull(LinuxUiScale.scaleFromXrdb("Xft.dpi:\t144\n"))
        assertNull(LinuxUiScale.scaleFromXrdb("Xcursor.size:\t24\n"))
        assertNull(LinuxUiScale.scaleFromXrdb(""))
    }

    @Test
    fun setsUiScaleOnLinuxWhenNothingElseChoseOne() {
        val props = mutableMapOf<String, String>()
        val scale = LinuxUiScale.apply(
            osName = "Linux", env = { null }, readXrdb = { gnome200 },
            getProperty = props::get, setProperty = { k, v -> props[k] = v }
        )
        assertEquals(2, scale)
        assertEquals("2", props["sun.java2d.uiScale"])
    }

    @Test
    fun respectsGdkScaleExistingPropertyAndOtherOses() {
        val props = mutableMapOf<String, String>()
        val set: (String, String) -> Unit = { k, v -> props[k] = v }

        assertNull(LinuxUiScale.apply("Linux", { if (it == "GDK_SCALE") "1" else null }, { gnome200 }, props::get, set))
        assertNull(LinuxUiScale.apply("Linux", { null }, { gnome200 }, { "1.5" }, set))
        assertNull(LinuxUiScale.apply("Windows 11", { null }, { gnome200 }, props::get, set))
        assertNull(LinuxUiScale.apply("Linux", { null }, { null }, props::get, set)) // xrdb missing
        assertEquals(emptyMap<String, String>(), props)
    }
}
