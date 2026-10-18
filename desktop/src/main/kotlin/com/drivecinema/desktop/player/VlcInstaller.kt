package com.drivecinema.desktop.player

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * "Install VLC now?" when libVLC is missing (audit F-17: the `.deb` can't depend on VLC).
 * Debian/Ubuntu: installs the `vlc` package with apt behind the system password dialog (pkexec), so it is the
 * libVLC vlcj finds. A snap/Flatpak VLC would not be found. Elsewhere: opens the VideoLAN download page.
 */
object VlcInstaller {
    const val DOWNLOAD_PAGE = "https://www.videolan.org/vlc/"
    val APT_COMMAND = listOf("pkexec", "apt-get", "install", "-y", "vlc")
    private const val INSTALL_TIMEOUT_MINUTES = 15L

    sealed interface Plan {
        /** Install in place; the user confirms in the system password dialog. */
        data class Install(val command: List<String>) : Plan
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
        if (osName.startsWith("Linux") && onPath("pkexec") && onPath("apt-get")) Plan.Install(APT_COMMAND)
        else Plan.OpenDownloadPage(DOWNLOAD_PAGE)

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
            126, 127 -> Result.Cancelled
            else -> Result.Failed(lastLines(output).ifBlank { "apt-get exited with code $exit" })
        }
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

    private fun isOnPath(name: String): Boolean =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, name).canExecute() }
}
