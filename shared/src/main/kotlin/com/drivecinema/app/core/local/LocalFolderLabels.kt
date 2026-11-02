package com.drivecinema.app.core.local

/** The phone/TV Local folders screen's supporting lines: where a saved folder is, and what its last scan found. */
object LocalFolderLabels {
    private const val SEPARATOR = " › "

    /**
     * A storage tree's document id ("primary:Movies/Anime", "1A2B-3C4D:Media") as a person reads it:
     * "Internal storage › Movies › Anime", "SD card › Media". Null when the id isn't a storage path (another app's
     * documents provider), so the screen shows no path rather than a raw id.
     */
    fun readablePath(treeDocumentId: String): String? {
        val colon = treeDocumentId.indexOf(':')
        if (colon <= 0) return null
        val volume = treeDocumentId.substring(0, colon)
        val root = when {
            volume.equals("primary", ignoreCase = true) -> "Internal storage"
            volume.equals("home", ignoreCase = true) -> "Documents"
            SD_CARD_VOLUME.matches(volume) -> "SD card"
            else -> return null
        }
        val folders = treeDocumentId.substring(colon + 1).split('/').filter { it.isNotBlank() }
        return (listOf(root) + folders).joinToString(SEPARATOR)
    }

    /**
     * What the last scan found in one folder: [videos] is null until a scan has read it. While [isScanning], an
     * unread folder says so; after the scan, an unread folder couldn't be listed.
     */
    fun videoCount(videos: Int?, isScanning: Boolean): String = when {
        videos == null -> if (isScanning) "Scanning…" else "Couldn't read this folder"
        videos == 0 -> "No videos found"
        videos == 1 -> "1 video"
        else -> "$videos videos"
    }

    /** Removable storage is named by its volume serial ("1A2B-3C4D"). */
    private val SD_CARD_VOLUME = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
}
