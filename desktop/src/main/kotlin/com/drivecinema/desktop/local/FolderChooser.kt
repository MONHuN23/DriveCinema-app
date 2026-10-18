package com.drivecinema.desktop.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.JFileChooser

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

    private suspend fun swingDialog(title: String): Path? = withContext(Dispatchers.Main) {
        val chooser = JFileChooser(System.getProperty("user.home")).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
    }
}
