package com.drivecinema.desktop.library

import org.json.JSONObject
import java.io.File

/** The Drive folder used as the library for one Google account. */
data class LibraryFolderSetting(val folderId: String, val folderName: String)

/**
 * Small non-secret settings in `settings.json` in the app data directory. The library folder is kept per
 * Google account, so signing in with another account never shows the wrong folder.
 */
class DesktopSettings(private val file: File) : com.drivecinema.desktop.local.LocalFolderSettings {

    /** Local library folders on this computer (not per account: they belong to the machine). */
    @Synchronized
    override fun localFolders(): List<String> {
        val array = read().optJSONArray(LOCAL_FOLDERS) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { path -> path.isNotEmpty() } }
    }

    @Synchronized
    override fun setLocalFolders(paths: List<String>) {
        val root = read()
        root.put(LOCAL_FOLDERS, org.json.JSONArray(paths.distinct()))
        write(root)
    }

    @Synchronized
    fun libraryFolder(accountEmail: String): LibraryFolderSetting? {
        val entry = read().optJSONObject(LIBRARY_FOLDERS)?.optJSONObject(accountEmail.lowercase()) ?: return null
        val id = entry.optString("id").takeIf { it.isNotEmpty() } ?: return null
        return LibraryFolderSetting(id, entry.optString("name"))
    }

    @Synchronized
    fun setLibraryFolder(accountEmail: String, setting: LibraryFolderSetting) {
        val root = read()
        val folders = root.optJSONObject(LIBRARY_FOLDERS) ?: JSONObject().also { root.put(LIBRARY_FOLDERS, it) }
        folders.put(accountEmail.lowercase(), JSONObject().put("id", setting.folderId).put("name", setting.folderName))
        write(root)
    }

    private fun read(): JSONObject =
        if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() } else JSONObject()

    private fun write(root: JSONObject) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(root.toString(2))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private companion object {
        const val LIBRARY_FOLDERS = "libraryFolders"
        const val LOCAL_FOLDERS = "localFolders"
    }
}
