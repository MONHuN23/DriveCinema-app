package com.drivecinema.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcInstallerTest {

    @Test
    fun aptLinuxInstallsInPlaceWithPasswordDialog() {
        val plan = VlcInstaller.plan("Linux", onPath = { it == "pkexec" || it == "apt-get" })
        assertEquals(VlcInstaller.Plan.Install(listOf("pkexec", "apt-get", "install", "-y", "vlc")), plan)
    }

    @Test
    fun otherSystemsOpenTheDownloadPage() {
        val page = VlcInstaller.Plan.OpenDownloadPage("https://www.videolan.org/vlc/")
        assertEquals(page, VlcInstaller.plan("Windows 11", onPath = { true }))
        assertEquals(page, VlcInstaller.plan("Linux", onPath = { it == "pkexec" })) // Fedora etc.: no apt-get
        assertEquals(page, VlcInstaller.plan("Linux", onPath = { it == "apt-get" })) // no password dialog helper
    }

    @Test
    fun exitCodesMapToResults() {
        val cmd = VlcInstaller.APT_COMMAND
        assertEquals(VlcInstaller.Result.Installed, VlcInstaller.install(cmd) { 0 to "" })
        assertEquals(VlcInstaller.Result.Cancelled, VlcInstaller.install(cmd) { 126 to "" })
        assertEquals(VlcInstaller.Result.Cancelled, VlcInstaller.install(cmd) { 127 to "Error executing command as another user" })
    }

    @Test
    fun failureShowsTheEndOfAptOutput() {
        val output = "Reading package lists...\n\nE: Could not get lock /var/lib/dpkg/lock-frontend\nE: Unable to acquire the dpkg frontend lock\n"
        val result = VlcInstaller.install(VlcInstaller.APT_COMMAND) { 100 to output }
        assertEquals(
            VlcInstaller.Result.Failed("Reading package lists...\nE: Could not get lock /var/lib/dpkg/lock-frontend\nE: Unable to acquire the dpkg frontend lock"),
            result
        )
        assertEquals(VlcInstaller.Result.Failed("apt-get exited with code 100"), VlcInstaller.install(VlcInstaller.APT_COMMAND) { 100 to "" })
    }

    @Test
    fun missingProgramIsAFailureNotACrash() {
        val result = VlcInstaller.install(listOf("pkexec")) { throw java.io.IOException("Cannot run program \"pkexec\"") }
        assertTrue(result is VlcInstaller.Result.Failed)
    }
}
