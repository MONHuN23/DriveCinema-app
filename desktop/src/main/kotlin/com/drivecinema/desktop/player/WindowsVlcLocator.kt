package com.drivecinema.desktop.player

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import java.io.File

/**
 * Finds libVLC on Windows without vlcj's default search (F.11). vlcj walks every PATH entry and Program Files
 * recursively (all of System32 too), which took about a minute on the home PC when VLC was missing. Here only the
 * folders a 64-bit VLC installs into are looked at, none of them recursively.
 */
object WindowsVlcLocator {

    /** Where to look, in order: the VLC installer's registry entry, the default install folder, then PATH. */
    fun candidates(registryInstallDir: String?, env: (String) -> String?): List<String> =
        (listOfNotNull(registryInstallDir, env("ProgramFiles")?.let { "$it\\VideoLAN\\VLC" }) +
            env("PATH").orEmpty().split(';'))
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
            .distinct()

    /** The first folder holding both libVLC DLLs, or null when VLC isn't installed. */
    fun find(candidates: List<String>, hasLibVlc: (File) -> Boolean = ::hasLibVlc): String? =
        candidates.firstOrNull { dir -> runCatching { hasLibVlc(File(dir)) }.getOrDefault(false) }

    /** The install folder the VLC installer writes to the registry, or null. */
    fun registryInstallDir(): String? = runCatching {
        if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, VLC_KEY))
            Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, VLC_KEY, "InstallDir")
        else null
    }.getOrNull()

    fun locate(): String? = find(candidates(registryInstallDir(), System::getenv))

    private fun hasLibVlc(dir: File) = File(dir, "libvlc.dll").isFile && File(dir, "libvlccore.dll").isFile

    private const val VLC_KEY = "SOFTWARE\\VideoLAN\\VLC"
}

/** vlcj's Windows strategy (same file patterns and plugin path), limited to one folder found by [WindowsVlcLocator]. */
class SingleFolderWindowsDiscoveryStrategy(private val folder: String) :
    BaseNativeDiscoveryStrategy(arrayOf("libvlc\\.dll", "libvlccore\\.dll"), arrayOf("%s\\plugins", "%s\\vlc\\plugins")) {

    override fun supported(): Boolean = RuntimeUtil.isWindows()

    override fun discoveryDirectories(): List<String> = listOf(folder)

    override fun setPluginPath(pluginPath: String): Boolean = LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$pluginPath") == 0
}
