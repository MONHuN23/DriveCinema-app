package com.drivecinema.desktop

import java.io.File

/**
 * Where the desktop app keeps its database, logs and settings.
 *
 * Linux: `$XDG_DATA_HOME/DriveCinema`, default `~/.local/share/DriveCinema`.
 * Windows: `%APPDATA%\DriveCinema`, default `~\AppData\Roaming\DriveCinema`.
 * macOS (not a target, but harmless): `~/Library/Application Support/DriveCinema`.
 */
object AppDataDirectory {

    const val APP_FOLDER = "DriveCinema"

    fun resolve(
        osName: String = System.getProperty("os.name").orEmpty(),
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home").orEmpty()
    ): File {
        val os = osName.lowercase()
        return when {
            os.startsWith("windows") -> {
                val appData = env["APPDATA"]?.takeIf { it.isNotBlank() }
                    ?: File(File(userHome, "AppData"), "Roaming").path
                File(appData, APP_FOLDER)
            }
            os.startsWith("mac") -> File(File(File(userHome, "Library"), "Application Support"), APP_FOLDER)
            else -> {
                // XDG spec: ignore relative paths. "Absolute" means a leading '/', checked as text so the
                // answer doesn't depend on the host's path rules (File.isAbsolute is false for "/x" on Windows, F-23).
                val xdgDataHome = env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() && it.startsWith("/") }
                    ?: File(File(userHome, ".local"), "share").path
                File(xdgDataHome, APP_FOLDER)
            }
        }
    }

    /** Resolves the directory and creates it if needed. */
    fun ensure(): File = resolve().apply { mkdirs() }
}
