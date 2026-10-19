package com.drivecinema.desktop.player

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.TimeUnit

/**
 * "Install VLC now?" when libVLC is missing (audit F-17: the `.deb` can't depend on VLC).
 * Debian/Ubuntu: installs the `vlc` package with apt behind the system password dialog (pkexec), so it is the
 * libVLC vlcj finds. Fedora: the same with dnf, but VLC lives in RPM Fusion there, so a failure explains that.
 * A snap/Flatpak VLC would not be found. Windows (F.11): installs 64-bit VLC with winget, which shows the normal
 * Windows permission prompt; without winget, or anywhere else, opens the VideoLAN download page.
 */
object VlcInstaller {
    const val DOWNLOAD_PAGE = "https://www.videolan.org/vlc/"
    val APT_COMMAND = listOf("pkexec", "apt-get", "install", "-y", "vlc")
    val DNF_COMMAND = listOf("pkexec", "dnf", "install", "-y", "vlc")
    /** `VideoLAN.VLC` is the official winget package; on 64-bit Windows winget picks the 64-bit VLC this JVM needs. */
    val WINGET_COMMAND = listOf(
        "winget", "install", "--id", "VideoLAN.VLC", "--exact", "--silent",
        "--accept-package-agreements", "--accept-source-agreements"
    )
    const val APT_HINT = "You can also run in a terminal: sudo apt install vlc"
    const val DNF_HINT = "On Fedora, VLC comes from RPM Fusion: enable it (https://rpmfusion.org/Configuration), " +
        "then run in a terminal: sudo dnf install vlc"
    const val WINGET_HINT = "You can also download VLC (64-bit) from https://www.videolan.org/vlc/, install it, " +
        "then restart DriveCinema."
    const val PASSWORD_NOTE = "Your computer will ask for your password."
    const val WINDOWS_PERMISSION_NOTE = "Windows will ask for permission to install it."
    private const val INSTALL_TIMEOUT_MINUTES = 15L

    sealed interface Plan {
        /** Install in place; the user confirms in the system password dialog ([confirmNote] says which). */
        data class Install(
            val command: List<String>,
            val manualHint: String = APT_HINT,
            val confirmNote: String = PASSWORD_NOTE
        ) : Plan
        data class OpenDownloadPage(val url: String) : Plan
    }

    sealed interface Result {
        data object Installed : Result
        /** The password dialog was closed or the password was wrong; nothing changed. */
        data object Cancelled : Result
        data class Failed(val detail: String) : Result
    }

    fun plan(
        osName: String = System.getProperty("os.name").orEmpty(),
        onPath: (String) -> Boolean = ::isOnPath
    ): Plan =
        when {
            osName.startsWith("Windows") ->
                if (onPath("winget.exe")) Plan.Install(WINGET_COMMAND, WINGET_HINT, WINDOWS_PERMISSION_NOTE)
                else Plan.OpenDownloadPage(DOWNLOAD_PAGE)
            !osName.startsWith("Linux") || !onPath("pkexec") -> Plan.OpenDownloadPage(DOWNLOAD_PAGE)
            onPath("apt-get") -> Plan.Install(APT_COMMAND, APT_HINT)
            onPath("dnf") -> Plan.Install(DNF_COMMAND, DNF_HINT)
            else -> Plan.OpenDownloadPage(DOWNLOAD_PAGE)
        }

    /** Runs [command] (blocking; call off the UI thread). [run] returns (exit code, output) and is swapped in tests. */
    fun install(command: List<String>, run: (List<String>) -> Pair<Int, String> = ::runProcess): Result {
        val (exit, output) = try {
            run(command)
        } catch (e: Exception) {
            return Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        return when (exit) {
            0 -> Result.Installed
            // pkexec: 126 = dialog dismissed, 127 = not authorised (e.g. wrong password).
            126, 127 -> if (command.firstOrNull() == "pkexec") Result.Cancelled else failed(command, exit, output)
            else -> failed(command, exit, output)
        }
    }

    private fun failed(command: List<String>, exit: Int, output: String): Result.Failed {
        val program = command.firstOrNull { it != "pkexec" } ?: "The installer"
        return Result.Failed(lastLines(output).ifBlank { "$program exited with code $exit" })
    }

    /** The end of apt's output, where the reason is (e.g. "Could not get lock ..."). */
    internal fun lastLines(output: String, max: Int = 3): String =
        output.lines().map { it.trim() }.filter { it.isNotEmpty() }.takeLast(max).joinToString("\n")

    private fun runProcess(command: List<String>): Pair<Int, String> {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.appendLine(it) } } }
        reader.start()
        if (!process.waitFor(INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return -1 to "Installing VLC took longer than $INSTALL_TIMEOUT_MINUTES minutes."
        }
        reader.join(2_000)
        return process.exitValue() to output.toString()
    }

    // winget.exe is an App Execution Alias (a reparse point in WindowsApps): File.canExecute() and exists() say false
    // for it although it runs, so .exe names also count when the link itself exists.
    private fun isOnPath(name: String): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).any { dir ->
            val file = File(dir, name)
            file.canExecute() || (name.endsWith(".exe") && runCatching { Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) }.getOrDefault(false))
        }
}
