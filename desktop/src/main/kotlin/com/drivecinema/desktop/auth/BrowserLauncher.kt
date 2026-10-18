package com.drivecinema.desktop.auth

import java.awt.Desktop
import java.net.URI

/** Opens a URL in the user's default browser. */
object BrowserLauncher {

    /** Returns false when no browser could be started; the UI then shows the link to copy. */
    fun open(url: String): Boolean {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                return true
            }
        } catch (_: Exception) {
            // Fall through to the OS command (common on Linux desktops without full AWT support).
        }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val command = when {
            os.startsWith("windows") -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            os.startsWith("mac") -> listOf("open", url)
            else -> listOf("xdg-open", url)
        }
        return try {
            ProcessBuilder(command).start()
            true
        } catch (_: Exception) {
            false
        }
    }
}
