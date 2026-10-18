package com.drivecinema.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AppDataDirectoryTest {

    @Test
    fun linuxDefaultsToLocalShare() {
        assertEquals(
            File("/home/ana/.local/share/DriveCinema"),
            AppDataDirectory.resolve("Linux", emptyMap(), "/home/ana")
        )
    }

    @Test
    fun linuxHonoursAbsoluteXdgDataHome() {
        assertEquals(
            File("/data/xdg/DriveCinema"),
            AppDataDirectory.resolve("Linux", mapOf("XDG_DATA_HOME" to "/data/xdg"), "/home/ana")
        )
    }

    @Test
    fun linuxIgnoresRelativeXdgDataHome() {
        assertEquals(
            File("/home/ana/.local/share/DriveCinema"),
            AppDataDirectory.resolve("Linux", mapOf("XDG_DATA_HOME" to "relative/dir"), "/home/ana")
        )
    }

    @Test
    fun linuxJudgesXdgDataHomeByLinuxRulesOnAnyHost() {
        // F-23: absolute on a Windows host, but relative under Linux rules, so it must be ignored everywhere.
        assertEquals(
            File("/home/ana/.local/share/DriveCinema"),
            AppDataDirectory.resolve("Linux", mapOf("XDG_DATA_HOME" to "C:\\xdg"), "/home/ana")
        )
    }

    @Test
    fun windowsUsesAppData() {
        assertEquals(
            File("C:\\Users\\Ana\\AppData\\Roaming", "DriveCinema"),
            AppDataDirectory.resolve("Windows 11", mapOf("APPDATA" to "C:\\Users\\Ana\\AppData\\Roaming"), "C:\\Users\\Ana")
        )
    }

    @Test
    fun windowsFallsBackToRoamingUnderHome() {
        assertEquals(
            File(File(File("C:\\Users\\Ana", "AppData"), "Roaming"), "DriveCinema"),
            AppDataDirectory.resolve("Windows 10", emptyMap(), "C:\\Users\\Ana")
        )
    }
}
