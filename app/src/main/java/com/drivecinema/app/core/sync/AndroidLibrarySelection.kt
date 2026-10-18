package com.drivecinema.app.core.sync

import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.network.GoogleDriveGateway
import com.drivecinema.app.core.security.AuthCredentialStore

/** Android keeps the selected library folder as `rootFolderId` in the encrypted credentials. */
class AuthCredentialLibrarySelection(private val credentialStore: AuthCredentialStore) : LibrarySelectionStore {
    override fun isSignedIn(): Boolean = credentialStore.getCredentials() != null

    override fun saveLibraryFolder(folder: DriveFolder) {
        // Re-read so identity/token fields written meanwhile are preserved.
        credentialStore.getCredentials()?.let { current ->
            credentialStore.saveCredentials(current.copy(rootFolderId = folder.id))
        }
    }
}

/** Android construction of the shared [LibraryFolderSwitcher] (keeps existing call sites unchanged). */
fun LibraryFolderSwitcher(
    driveGateway: GoogleDriveGateway,
    syncEngine: DriveLibrarySyncEngine,
    credentialStore: AuthCredentialStore
): LibraryFolderSwitcher = LibraryFolderSwitcher(driveGateway, syncEngine, AuthCredentialLibrarySelection(credentialStore))
