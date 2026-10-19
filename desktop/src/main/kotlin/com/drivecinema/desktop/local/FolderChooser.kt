package com.drivecinema.desktop.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Lets the user pick a folder: the desktop's own dialog when available (zenity / kdialog), otherwise Swing. */
object FolderChooser {

    suspend fun chooseFolder(title: String = "Choose a folder with Films and/or Series"): Path? {
        nativeDialog(title)?.let { return it.getOrNull() }
        return swingDialog(title)
    }

    /** null = no native dialog tool; Result(null) = dialog shown but cancelled. */
    private suspend fun nativeDialog(title: String): Result<Path?>? = withContext(Dispatchers.IO) {
        val home = System.getProperty("user.home")
        val commands = listOf(
            listOf("zenity", "--file-selection", "--directory", "--title=$title", "--filename=$home/"),
            listOf("kdialog", "--getexistingdirectory", home, "--title", title)
        )
        for (command in commands) {
            val process = try {
                ProcessBuilder(command).redirectErrorStream(false).start()
            } catch (_: Exception) {
                continue // tool not installed
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            return@withContext Result.success(if (exit == 0 && output.isNotEmpty()) Path.of(output) else null)
        }
        null
    }

    /** Windows (and Linux without zenity/kdialog): Swing's chooser with the system look, e.g. the Windows style. */
    private suspend fun swingDialog(title: String): Path? = onSwingThread {
        useSystemLookAndFeel()
        val chooser = JFileChooser(System.getProperty("user.home")).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
    }

    /**
     * Runs [block] on the Swing event thread. Not `Dispatchers.Main`: the desktop app has no
     * kotlinx-coroutines-swing, so Main is missing there and "Add folder" failed on Windows.
     */
    internal suspend fun <T> onSwingThread(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        SwingUtilities.invokeLater {
            runCatching(block).fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
        }
    }

    private var lookAndFeelSet = false

    private fun useSystemLookAndFeel() {
        if (lookAndFeelSet) return
        lookAndFeelSet = true
        // Compose doesn't draw with Swing's look and feel, so this only changes Swing dialogs like this one.
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }
}
