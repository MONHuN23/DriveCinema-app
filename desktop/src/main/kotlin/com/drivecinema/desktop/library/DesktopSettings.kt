package com.drivecinema.desktop.library

import org.json.JSONObject
import java.io.File

/** The Drive folder used as the library for one Google account. */
data class LibraryFolderSetting(val folderId: String, val folderName: String)

/**
 * Small non-secret settings in `settings.json` in the app data directory. The library folder is kept per
 * Google account, so signing in with another account never shows the wrong folder.
 */
class DesktopSettings(private val file: File) : com.drivecinema.desktop.local.LocalFolderSettings,
    com.drivecinema.desktop.importer.ImportSettings, com.drivecinema.desktop.host.HostSettings {

    /** Milestone Q: "Sync with my devices" in the account menu, off by default. */
    @Synchronized
    fun deviceSyncEnabled(): Boolean = read().optBoolean(DEVICE_SYNC, false)

    @Synchronized
    fun setDeviceSyncEnabled(enabled: Boolean) {
        val root = read()
        root.put(DEVICE_SYNC, enabled)
        write(root)
    }

    /** Milestone Q: this install's name in the Drive app folder, made on first use. */
    @Synchronized
    fun deviceSyncId(): String {
        val root = read()
        root.optString(DEVICE_SYNC_ID).takeIf { it.isNotEmpty() }?.let { return it }
        val id = com.drivecinema.app.core.devicesync.DeviceSyncIdentity.newDeviceId()
        root.put(DEVICE_SYNC_ID, id)
        write(root)
        return id
    }

    /** Milestone O: the "Skip intro" switch in the account menu, on by default (D3). Off = no lookups at all. */
    @Synchronized
    fun skipIntroEnabled(): Boolean = read().optBoolean(SKIP_INTRO, true)

    @Synchronized
    fun setSkipIntroEnabled(enabled: Boolean) {
        val root = read()
        root.put(SKIP_INTRO, enabled)
        write(root)
    }

    @Synchronized
    override fun sharingEnabled(): Boolean = read().optJSONObject(HOST)?.optBoolean(SHARING) ?: false

    @Synchronized
    override fun setSharingEnabled(enabled: Boolean) = updateHost { it.put(SHARING, enabled) }

    @Synchronized
    override fun allowVpn(): Boolean = read().optJSONObject(HOST)?.optBoolean(ALLOW_VPN) ?: false

    @Synchronized
    override fun setAllowVpn(allowed: Boolean) = updateHost { it.put(ALLOW_VPN, allowed) }

    private fun updateHost(change: (JSONObject) -> Unit) {
        val root = read()
        val host = root.optJSONObject(HOST) ?: JSONObject().also { root.put(HOST, it) }
        change(host)
        write(root)
    }

    @Synchronized
    override fun importFolder(kind: com.drivecinema.desktop.importer.ImportFolderKind): String? =
        read().optJSONObject(IMPORT)?.optString(kind.name)?.takeIf { it.isNotEmpty() }

    @Synchronized
    override fun setImportFolder(kind: com.drivecinema.desktop.importer.ImportFolderKind, path: String?) =
        updateImport { if (path == null) it.remove(kind.name) else it.put(kind.name, path) }

    @Synchronized
    override fun importPaused(): Boolean = read().optJSONObject(IMPORT)?.optBoolean(PAUSED) ?: false

    @Synchronized
    override fun setImportPaused(paused: Boolean) = updateImport { it.put(PAUSED, paused) }

    private fun updateImport(change: (JSONObject) -> Unit) {
        val root = read()
        val import = root.optJSONObject(IMPORT) ?: JSONObject().also { root.put(IMPORT, it) }
        change(import)
        write(root)
    }

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
        /** Task I.1: import folders per kind (MOVIES, SERIES) and the user's pause switch. */
        const val IMPORT = "import"
        const val PAUSED = "paused"
        /** Task I.2: sharing the local folders with paired devices, and whether VPN (Tailscale) addresses may connect. */
        const val HOST = "host"
        const val SHARING = "sharing"
        const val ALLOW_VPN = "allowVpn"
        const val SKIP_INTRO = "skipIntro"
        const val DEVICE_SYNC = "deviceSync"
        const val DEVICE_SYNC_ID = "deviceSyncId"
    }
}
