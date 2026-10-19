package com.drivecinema.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcInstallerTest {

    @Test
    fun aptLinuxInstallsInPlaceWithPasswordDialog() {
        val plan = VlcInstaller.plan("Linux", onPath = { it == "pkexec" || it == "apt-get" })
        assertEquals(VlcInstaller.Plan.Install(listOf("pkexec", "apt-get", "install", "-y", "vlc"), VlcInstaller.APT_HINT), plan)
    }

    @Test
    fun otherSystemsOpenTheDownloadPage() {
        val page = VlcInstaller.Plan.OpenDownloadPage("https://www.videolan.org/vlc/")
        assertEquals(page, VlcInstaller.plan("Mac OS X", onPath = { true }))
        assertEquals(page, VlcInstaller.plan("Windows 11", onPath = { false })) // no winget
        assertEquals(page, VlcInstaller.plan("Linux", onPath = { it == "pkexec" })) // neither apt-get nor dnf
        assertEquals(page, VlcInstaller.plan("Linux", onPath = { it == "apt-get" })) // no password dialog helper
    }

    @Test
    fun fedoraInstallsWithDnfAndExplainsRpmFusionOnFailure() {
        val plan = VlcInstaller.plan("Linux", onPath = { it == "pkexec" || it == "dnf" })
        assertEquals(VlcInstaller.Plan.Install(listOf("pkexec", "dnf", "install", "-y", "vlc"), VlcInstaller.DNF_HINT), plan)
        assertTrue(VlcInstaller.DNF_HINT.contains("RPM Fusion"))
        assertEquals(VlcInstaller.Result.Failed("dnf exited with code 1"), VlcInstaller.install(VlcInstaller.DNF_COMMAND) { 1 to "" })
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

    @Test
    fun windowsInstallsWithWingetBehindTheWindowsPrompt() {
        val plan = VlcInstaller.plan("Windows 11", onPath = { it == "winget.exe" })
        assertEquals(
            VlcInstaller.Plan.Install(
                listOf(
                    "winget", "install", "--id", "VideoLAN.VLC", "--exact", "--silent",
                    "--accept-package-agreements", "--accept-source-agreements"
                ),
                VlcInstaller.WINGET_HINT,
                VlcInstaller.WINDOWS_PERMISSION_NOTE
            ),
            plan
        )
        // A Linux tool on a Windows PATH (e.g. Git Bash) never picks apt.
        assertEquals(plan, VlcInstaller.plan("Windows 10", onPath = { it == "winget.exe" || it == "pkexec" || it == "apt-get" }))
    }

    @Test
    fun wingetFailuresAreNeverReadAsPkexecCancel() {
        val cmd = VlcInstaller.WINGET_COMMAND
        assertEquals(VlcInstaller.Result.Installed, VlcInstaller.install(cmd) { 0 to "Successfully installed" })
        assertEquals(VlcInstaller.Result.Failed("winget exited with code 127"), VlcInstaller.install(cmd) { 127 to "" })
        // Declining the Windows prompt: the installer exits with 1223 and winget prints why.
        val output = "Found VLC media player [VideoLAN.VLC]\nInstaller failed with exit code: 1223\n"
        assertEquals(
            VlcInstaller.Result.Failed("Found VLC media player [VideoLAN.VLC]\nInstaller failed with exit code: 1223"),
            VlcInstaller.install(cmd) { -1978335226 to output }
        )
    }
}
