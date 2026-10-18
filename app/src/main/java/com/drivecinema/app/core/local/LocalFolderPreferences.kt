package com.drivecinema.app.core.local

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

interface UriPermissionManager {
    fun takePersistableReadPermission(uri: Uri): Boolean
    fun releasePersistableReadPermission(uri: Uri): Boolean
    /** Persisted read grants, or null if they could not be read (never treat that as "none"). */
    fun getPersistedReadPermissions(): Set<Uri>?
}

class SystemUriPermissionManager(private val contentResolver: ContentResolver) : UriPermissionManager {
    override fun takePersistableReadPermission(uri: Uri): Boolean {
        return try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun releasePersistableReadPermission(uri: Uri): Boolean {
        return try {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun getPersistedReadPermissions(): Set<Uri>? {
        return try {
            contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
                .toSet()
        } catch (_: Exception) {
            null
        }
    }
}

data class LocalFolder(
    val uri: Uri,
    val displayName: String
)

sealed interface AddFolderResult {
    data object Success : AddFolderResult
    data object NotLocalStorage : AddFolderResult
    data object PermissionDenied : AddFolderResult
}

class LocalFolderPreferences(
    private val context: Context,
    val prefsName: String = PREFS_NAME,
    private val permissionManager: UriPermissionManager = SystemUriPermissionManager(context.contentResolver)
) {
    companion object {
        const val PREFS_NAME = "drivecinema_local_storage"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val KEY_FOLDER_URIS = "local_folder_uris"
    }

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isLocalStorageUri(uri: Uri): Boolean {
        return uri.authority == EXTERNAL_STORAGE_AUTHORITY
    }

    @Synchronized
    fun getAllFolderUris(): Set<Uri> {
        val rawSet = prefs.getStringSet(KEY_FOLDER_URIS, emptySet()) ?: emptySet()
        val parsedUris = rawSet.map { Uri.parse(it) }.toSet()
        // If the grants can't be read right now, keep the saved list untouched rather than wiping it.
        val grantedUris = permissionManager.getPersistedReadPermissions() ?: return parsedUris
        val validUris = parsedUris.filter { it in grantedUris }.toSet()

        if (validUris.size != parsedUris.size) {
            prefs.edit().putStringSet(KEY_FOLDER_URIS, validUris.map { it.toString() }.toSet()).apply()
        }

        return validUris
    }

    @Synchronized
    fun getAllFolders(): List<LocalFolder> {
        return getAllFolderUris().map { uri ->
            LocalFolder(
                uri = uri,
                displayName = getDisplayName(uri)
            )
        }
    }

    @Synchronized
    fun addFolderUri(uri: Uri): AddFolderResult {
        if (!isLocalStorageUri(uri)) {
            return AddFolderResult.NotLocalStorage
        }

        val granted = permissionManager.takePersistableReadPermission(uri)
        if (!granted) {
            return AddFolderResult.PermissionDenied
        }

        val current = prefs.getStringSet(KEY_FOLDER_URIS, emptySet()) ?: emptySet()
        val updated = current + uri.toString()
        prefs.edit().putStringSet(KEY_FOLDER_URIS, updated).apply()
        return AddFolderResult.Success
    }

    @Synchronized
    fun removeFolderUri(uri: Uri): Boolean {
        permissionManager.releasePersistableReadPermission(uri)
        val current = prefs.getStringSet(KEY_FOLDER_URIS, emptySet()) ?: emptySet()
        val wasPresent = current.contains(uri.toString())
        if (wasPresent) {
            val updated = current - uri.toString()
            prefs.edit().putStringSet(KEY_FOLDER_URIS, updated).apply()
        }
        return wasPresent
    }

    @Synchronized
    fun clear() {
        val rawSet = prefs.getStringSet(KEY_FOLDER_URIS, emptySet()) ?: emptySet()
        for (rawUri in rawSet) {
            permissionManager.releasePersistableReadPermission(Uri.parse(rawUri))
        }
        prefs.edit().remove(KEY_FOLDER_URIS).apply()
    }

    fun getDisplayName(uri: Uri): String {
        val treeDocId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            null
        }

        if (treeDocId != null) {
            try {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (colIdx >= 0) {
                            val name = cursor.getString(colIdx)
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore and fall back to tree ID parsing
            }
        }

        return extractDisplayNameFromTreeId(treeDocId)
    }

    fun extractDisplayNameFromTreeId(treeDocId: String?): String {
        if (treeDocId.isNullOrEmpty()) return "Local Folder"
        val colonIndex = treeDocId.indexOf(':')
        val pathPart = if (colonIndex != -1) treeDocId.substring(colonIndex + 1) else treeDocId
        val trimmedPath = pathPart.trim('/')
        if (trimmedPath.isNotEmpty()) {
            val lastSegment = trimmedPath.substringAfterLast('/')
            if (lastSegment.isNotEmpty()) {
                return lastSegment
            }
        }
        if (colonIndex != -1) {
            val volumeId = treeDocId.substring(0, colonIndex)
            return if (volumeId.equals("primary", ignoreCase = true)) {
                "Internal Storage"
            } else {
                "Storage ($volumeId)"
            }
        }
        return treeDocId
    }
}
