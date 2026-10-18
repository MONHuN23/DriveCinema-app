package com.drivecinema.app.core.sync

import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.network.FolderValidationResult
import com.drivecinema.app.core.network.GoogleDriveGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface FolderSwitchResult {
    data class Switched(val folderId: String, val itemCount: Int) : FolderSwitchResult
    /** Nothing was changed: the previous folder, catalog and credentials remain active. */
    data class Rejected(val message: String) : FolderSwitchResult
}

/** Where the platform keeps the selected library folder (Android: the stored credentials; desktop: settings). */
interface LibrarySelectionStore {
    /** False once the user signed out: a switch must not write a folder for nobody. */
    fun isSignedIn(): Boolean

    /** Persists the new library folder; called inside the sync engine's atomic commit. */
    fun saveLibraryFolder(folder: DriveFolder)
}

/**
 * Switches the active Drive library folder in-app, reusing the existing session (no sign-out,
 * no new OAuth consent). Only the selected folder changes in [selectionStore].
 */
class LibraryFolderSwitcher(
    private val driveGateway: GoogleDriveGateway,
    private val syncEngine: DriveLibrarySyncEngine,
    private val selectionStore: LibrarySelectionStore
) {
    suspend fun switchTo(folder: DriveFolder, accessToken: String): FolderSwitchResult {
        if (!selectionStore.isSignedIn()) {
            return FolderSwitchResult.Rejected("You are signed out. Sign in again to choose a library.")
        }
        if (accessToken.isEmpty()) {
            return FolderSwitchResult.Rejected("Missing session token. Please try again.")
        }

        val validation = driveGateway.validateStreamingFolder(folder.id, accessToken).getOrElse { err ->
            return FolderSwitchResult.Rejected(describeDriveError(folder.name, err))
        }
        val valid = when (validation) {
            is FolderValidationResult.Valid -> validation
            is FolderValidationResult.Invalid -> return FolderSwitchResult.Rejected(
                "'${folder.name}' can't be used as a library: ${validation.reason}."
            )
        }

        val result = withContext(Dispatchers.IO) {
            syncEngine.switchLibrary(
                filmsFolderId = valid.filmsFolderId,
                seriesFolderId = valid.seriesFolderId,
                accessToken = accessToken,
                rootFolderId = folder.id,
                onCommitted = { selectionStore.saveLibraryFolder(folder) }
            )
        }
        return result.fold(
            onSuccess = { count -> FolderSwitchResult.Switched(folder.id, count) },
            onFailure = { err -> FolderSwitchResult.Rejected(describeDriveError(folder.name, err)) }
        )
    }

    companion object {
        private val HTTP_CODE = Regex("""\((\d{3})\)|code (\d{3})|: (\d{3})$""")

        fun describeDriveError(folderName: String, error: Throwable): String {
            val message = error.message.orEmpty()
            val match = HTTP_CODE.find(message)
            val code = match?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
            return when (code) {
                401 -> "Your Google session expired. Please try again."
                403 -> "You don't have permission to access '$folderName'. Ask the owner to share it with this account."
                404 -> "'$folderName' could not be found. It may have been deleted or unshared."
                null -> if (error is IOException) {
                    "Couldn't reach Google Drive. Check your connection and try again."
                } else {
                    "Couldn't switch to '$folderName'. Please try again."
                }
                else -> "Google Drive returned an error ($code). Please try again later."
            }
        }
    }
}
